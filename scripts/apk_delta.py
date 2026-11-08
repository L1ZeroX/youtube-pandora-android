"""Create a bounded, streaming COPY/DATA patch that reconstructs a signed APK byte-for-byte.

Unchanged compressed ZIP payloads are copied from the installed APK. Headers, changed
payloads, signing block and central directory come from the new APK. The whole command
stream is gzipped. No native patch library or decompression/recompression of APK entries.
"""
import argparse
import gzip
import hashlib
from pathlib import Path
import struct
import zipfile


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').digest()


def entries(path):
    result = []
    with zipfile.ZipFile(path) as archive, path.open('rb') as stream:
        for item in sorted(archive.infolist(), key=lambda i: i.header_offset):
            stream.seek(item.header_offset)
            header = stream.read(30)
            assert header[:4] == b'PK\x03\x04'
            name, extra = struct.unpack_from('<HH', header, 26)
            start = item.header_offset + 30 + name + extra
            assert start + item.compress_size <= path.stat().st_size
            stream.seek(start)
            digest = hashlib.sha256()
            remaining = item.compress_size
            while remaining:
                data = stream.read(min(remaining, 1024 * 1024))
                assert data
                digest.update(data)
                remaining -= len(data)
            result.append((start, item.compress_size, digest.digest()))
    return result


def create_patch(base, target, output):
    assert output.resolve() not in (base.resolve(), target.resolve())
    assert 0 < base.stat().st_size <= 1024**3 and 0 < target.stat().st_size <= 1024**3
    available = {(length, digest): start for start, length, digest in entries(base) if length >= 256}
    operations = []

    def append(kind, offset, length):
        if not length:
            return
        if operations and operations[-1][0] == kind and operations[-1][1] + operations[-1][2] == offset:
            previous = operations.pop()
            length += previous[2]
            offset = previous[1]
        operations.append((kind, offset, length))

    position = 0
    for start, length, digest in entries(target):
        append(2, position, start - position)
        old = available.get((length, digest))
        append(1 if old is not None else 2, old if old is not None else start, length)
        position = start + length
    append(2, position, target.stat().st_size - position)
    assert 0 < len(operations) <= 100_000
    with output.open('wb') as raw, gzip.GzipFile(filename='', mode='wb', fileobj=raw, mtime=0) as patch, target.open('rb') as source:
        patch.write(b'PNDRDLT1' + struct.pack('>QQ', base.stat().st_size, target.stat().st_size))
        patch.write(sha(base) + sha(target) + struct.pack('>I', len(operations)))
        for kind, offset, length in operations:
            patch.write(bytes([kind]))
            if kind == 1:
                patch.write(struct.pack('>Q', offset))
            patch.write(struct.pack('>Q', length))
            if kind == 2:
                source.seek(offset)
                while length:
                    data = source.read(min(length, 1024 * 1024))
                    assert data
                    patch.write(data)
                    length -= len(data)
    return {'base': base.stat().st_size, 'target': target.stat().st_size, 'patch': output.stat().st_size,
            'saved_percent': round((1-output.stat().st_size/target.stat().st_size)*100, 1),
            'sha256': sha(output).hex(), 'operations': len(operations)}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, required=True)
    parser.add_argument('--target', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    print(create_patch(args.base, args.target, args.output))

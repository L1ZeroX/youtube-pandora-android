"""Build, align and sign an installable APK; keep the local signing key private."""
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import zipfile
import re

beta = "--beta" in sys.argv
version = "0.4.25-beta.11" if beta else "0.4.27"
root = Path(__file__).resolve().parent.parent
config = json.loads((root / '.tools/environment.json').read_text())
private = root / '.signing'
private.mkdir(exist_ok=True)
password_file = private / 'password.txt'
if not password_file.exists():
    password_file.write_text(secrets.token_urlsafe(48), encoding='utf-8')
env = dict(os.environ, JAVA_HOME=config['java'], PANDORA_KEY_PASSWORD=password_file.read_text().strip())
env['PATH'] = str(Path(config['java']) / 'bin') + os.pathsep + env['PATH']
key = private / 'pandora.jks'
if not key.exists():
    subprocess.run([str(Path(config['java']) / 'bin/keytool.exe'), '-genkeypair', '-keystore', str(key),
                    '-storetype', 'JKS', '-storepass:env', 'PANDORA_KEY_PASSWORD', '-keypass:env', 'PANDORA_KEY_PASSWORD',
                    '-alias', 'pandora', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
                    '-dname', 'CN=Youtube Pandora Android'], env=env, check=True)
subprocess.run([sys.executable, str(root / 'scripts/build.py'), 'assembleRelease', 'testReleaseUnitTest', 'lintRelease', *(['-PpandoraBeta=true'] if beta else [])], check=True)
release = root / 'release'
release.mkdir(exist_ok=True)
tools = Path(config['sdk']) / 'build-tools/35.0.0'
unsigned = root / 'app/build/outputs/apk/release/app-release-unsigned.apk'
aligned = root / '.tools/aligned.apk'
apk = release / f"YoutubePandora-Android-v{version}.apk"
subprocess.run([str(tools / 'zipalign.exe'), '-f', '-P', '16', '4', str(unsigned), str(aligned)], check=True)
subprocess.run([str(tools / 'apksigner.bat'), 'sign', '--ks', str(key), '--ks-key-alias', 'pandora',
                '--ks-pass', 'env:PANDORA_KEY_PASSWORD', '--key-pass', 'env:PANDORA_KEY_PASSWORD',
                '--out', str(apk), str(aligned)], env=env, check=True)
subprocess.run([str(tools / 'apksigner.bat'), 'verify', '--verbose', str(apk)], env=env, check=True)
(release / f"SHA256SUMS-v{version}.txt").write_text(hashlib.sha256(apk.read_bytes()).hexdigest() + '  ' + apk.name + '\n')
if '--delta-from' in sys.argv:
    from apk_delta import create_patch
    base = Path(sys.argv[sys.argv.index('--delta-from') + 1]).resolve(strict=True)
    badging = subprocess.check_output([str(tools / 'aapt.exe'), 'dump', 'badging', str(base)], text=True, encoding='utf-8')
    base_version = re.search(r"versionName='([^']+)'", badging).group(1)
    assert re.fullmatch(r'\d+\.\d+\.\d+(?:-beta\.\d+)?', base_version) and base_version != version
    assert "package: name='io.github.l1zerox.pandora'" in badging
    patch = release / f'YoutubePandora-Android-v{version}-from-v{base_version}.pdelta.gz'
    result = create_patch(base, apk, patch)
    with (release / f'SHA256SUMS-v{version}.txt').open('a', encoding='utf-8') as sums:
        sums.write(result['sha256'] + '  ' + patch.name + '\n')
    print('Delta: ' + str(result))
source = release / f"YoutubePandora-Android-v{version}-source.zip"
with zipfile.ZipFile(source, 'w', zipfile.ZIP_DEFLATED) as archive:
    # Include new implementation/build scripts before commit; never export signing material.
    paths = subprocess.check_output(['git', 'ls-files', '-z'], cwd=root).decode().split('\0')
    paths += subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard', '-z', '--', 'app/src', 'scripts'], cwd=root).decode().split('\0')
    for name in sorted(set(filter(None, paths))):
        path = root / name
        if path.is_file():
            archive.write(path, name)
for name in ('README.md', 'LICENSE', 'THIRD_PARTY.md', 'VALIDATION.md'):
    shutil.copy2(root / name, release / name)
print('APK: ' + str(apk))
print('Source: ' + str(source))

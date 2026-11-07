"""Fetch the pinned official zipapp, verifying GitHub's published SHA-256 digest."""
import hashlib
from pathlib import Path
import urllib.request
import zipfile

VERSION = "2026.10.26"
SHA256 = "e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3"
root = Path(__file__).resolve().parent.parent
target = root / "app/src/main/res/raw/ytdlp"
url = f"https://github.com/yt-dlp/yt-dlp/releases/download/{VERSION}/yt-dlp"
with urllib.request.urlopen(url, timeout=30) as response:
    content = response.read(16 * 1024 * 1024)
assert hashlib.sha256(content).hexdigest() == SHA256, "yt-dlp checksum mismatch"
target.parent.mkdir(parents=True, exist_ok=True)
temporary = target.with_suffix(".tmp")
try:
    temporary.write_bytes(content)
    with zipfile.ZipFile(temporary) as archive:
        assert f"__version__ = '{VERSION}'" in archive.read("yt_dlp/version.py").decode()
        assert any(name.startswith("yt_dlp_ejs/") for name in archive.namelist()), "EJS missing"
    temporary.replace(target)
finally:
    temporary.unlink(missing_ok=True)
print(f"Verified official yt-dlp {VERSION}: {SHA256}")

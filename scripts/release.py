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
subprocess.run([sys.executable, str(root / 'scripts/build.py'), 'assembleRelease', 'testReleaseUnitTest', 'lintRelease'], check=True)
release = root / 'release'
release.mkdir(exist_ok=True)
tools = Path(config['sdk']) / 'build-tools/35.0.0'
unsigned = root / 'app/build/outputs/apk/release/app-release-unsigned.apk'
aligned = root / '.tools/aligned.apk'
apk = release / 'YoutubePandora-Android-v0.2.2.apk'
subprocess.run([str(tools / 'zipalign.exe'), '-f', '-P', '16', '4', str(unsigned), str(aligned)], check=True)
subprocess.run([str(tools / 'apksigner.bat'), 'sign', '--ks', str(key), '--ks-key-alias', 'pandora',
                '--ks-pass', 'env:PANDORA_KEY_PASSWORD', '--key-pass', 'env:PANDORA_KEY_PASSWORD',
                '--out', str(apk), str(aligned)], env=env, check=True)
subprocess.run([str(tools / 'apksigner.bat'), 'verify', '--verbose', str(apk)], env=env, check=True)
(release / 'SHA256SUMS.txt').write_text(hashlib.sha256(apk.read_bytes()).hexdigest() + '  ' + apk.name + '\n')
source = release / 'YoutubePandora-Android-v0.2.2-source.zip'
with zipfile.ZipFile(source, 'w', zipfile.ZIP_DEFLATED) as archive:
    for path in root.rglob('*'):
        relative = path.relative_to(root)
        if any(part in {'.tools', '.signing', '.gradle', '.kotlin', '.idea', '.git', 'build', 'release', '.test-artifacts', '__pycache__'} for part in relative.parts):
            continue
        if path.is_file() and path.name != 'local.properties' and path.suffix not in {'.log', '.jks', '.keystore'}:
            archive.write(path, relative)
for name in ('README.md', 'LICENSE', 'THIRD_PARTY.md', 'VALIDATION.md'):
    shutil.copy2(root / name, release / name)
print('APK: ' + str(apk))
print('Source: ' + str(source))

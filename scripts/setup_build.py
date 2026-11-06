"""Fetch verified, project-local JDK/Gradle/Android SDK. Does not change PATH."""
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import subprocess
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent.parent
TOOLS = ROOT / '.tools'
TOOLS.mkdir(exist_ok=True)

def read(url):
    with urllib.request.urlopen(url, timeout=90) as response:
        return response.read()

def fetch(url, name, digest, algorithm='sha256'):
    archive = TOOLS / name
    if not archive.exists() or hashlib.new(algorithm, archive.read_bytes()).hexdigest() != digest:
        print('Downloading ' + name, flush=True)
        with urllib.request.urlopen(url, timeout=180) as source, archive.open('wb') as target:
            while chunk := source.read(1024 * 1024):
                target.write(chunk)
    assert hashlib.new(algorithm, archive.read_bytes()).hexdigest() == digest, name + ' checksum mismatch'
    return archive

def jdk():
    assets = json.loads(read('https://api.github.com/repos/adoptium/temurin21-binaries/releases/latest'))['assets']
    data = next(a for a in assets if a['name'].startswith('OpenJDK21U-jdk_x64_windows_hotspot_') and a['name'].endswith('.zip'))
    checksum = next(a for a in assets if a['name'] == data['name'] + '.sha256.txt')
    path = fetch(data['browser_download_url'], data['name'], read(checksum['browser_download_url']).decode().split()[0])
    with zipfile.ZipFile(path) as z:
        folder = TOOLS / z.namelist()[0].split('/')[0]
        if not (folder / 'bin/java.exe').exists(): z.extractall(TOOLS)
    return folder

def gradle():
    url = 'https://services.gradle.org/distributions/gradle-8.13-bin.zip'
    path = fetch(url, 'gradle-8.13-bin.zip', read(url + '.sha256').decode().strip())
    folder = TOOLS / 'gradle-8.13'
    if not folder.exists():
        with zipfile.ZipFile(path) as z: z.extractall(TOOLS)
    return folder

def sdk_tools():
    tree = ET.fromstring(read('https://dl.google.com/android/repository/repository2-1.xml'))
    package = next(p for p in tree.findall('remotePackage') if p.attrib['path'] == 'cmdline-tools;19.0')
    archive = next(a for a in package.findall('./archives/archive') if a.findtext('host-os') == 'windows')
    path = fetch('https://dl.google.com/android/repository/' + archive.findtext('./complete/url'),
                 'android-commandline-tools-19.zip', archive.findtext('./complete/checksum'), 'sha1')
    sdk = TOOLS / 'android-sdk'
    destination = sdk / 'cmdline-tools/19.0'
    if not (destination / 'bin/sdkmanager.bat').exists():
        destination.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(path) as z:
            for entry in z.infolist():
                relative = Path(entry.filename).relative_to('cmdline-tools')
                target = destination / relative
                if entry.is_dir(): target.mkdir(parents=True, exist_ok=True)
                else:
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(z.read(entry))
    return sdk

with concurrent.futures.ThreadPoolExecutor(3) as pool:
    a, b, c = pool.submit(jdk), pool.submit(gradle), pool.submit(sdk_tools)
    java, gradle_path, sdk = a.result(), b.result(), c.result()
environment = dict(os.environ, JAVA_HOME=str(java), ANDROID_HOME=str(sdk), GRADLE_USER_HOME=str(TOOLS / 'gradle-cache'))
environment['PATH'] = str(java / 'bin') + os.pathsep + environment['PATH']
(TOOLS / 'tmp').mkdir(exist_ok=True)
environment['JAVA_TOOL_OPTIONS'] = f'-Djdk.net.unixdomain.tmpdir="{TOOLS / "tmp"}"'
environment['ANDROID_SDK_DISABLE_ANALYTICS'] = 'true'
manager = sdk / 'cmdline-tools/19.0/bin/sdkmanager.bat'
subprocess.run([str(manager), '--sdk_root=' + str(sdk), '--licenses'], input='y\n' * 100, text=True, env=environment, check=True)
subprocess.run([str(manager), '--sdk_root=' + str(sdk), 'platforms;android-36', 'build-tools;35.0.0', 'platform-tools'], env=environment, check=True)
(ROOT / 'local.properties').write_text('sdk.dir=' + sdk.as_posix().replace(':', '\\:') + '\n', encoding='utf-8')
(TOOLS / 'environment.json').write_text(json.dumps({'java': str(java), 'gradle': str(gradle_path), 'sdk': str(sdk)}))
print('Build environment ready', flush=True)

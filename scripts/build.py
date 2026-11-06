import json, os, subprocess, sys
from pathlib import Path
root = Path(__file__).resolve().parent.parent
config = json.loads((root / '.tools/environment.json').read_text())
env = dict(os.environ, JAVA_HOME=config['java'], ANDROID_HOME=config['sdk'], GRADLE_USER_HOME=str(root / '.tools/gradle-cache'))
env['PATH'] = str(Path(config['java']) / 'bin') + os.pathsep + env['PATH']
socket_dir = root / '.tools/tmp'
socket_dir.mkdir(exist_ok=True)
env['JAVA_TOOL_OPTIONS'] = f'-Djdk.net.unixdomain.tmpdir="{socket_dir}"'
args = sys.argv[1:] or ['assembleDebug', 'testDebugUnitTest', 'lintDebug']
raise SystemExit(subprocess.call([str(Path(config['gradle']) / 'bin/gradle.bat'), '--console=plain', '--no-daemon', *args], cwd=root, env=env))

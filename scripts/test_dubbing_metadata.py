"""Round-trip the app's metadata writer/reader through real host FFmpeg (no network/device)."""
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent.parent
WORK = ROOT / ".test-artifacts/dubbing-metadata"
WORK.mkdir(parents=True, exist_ok=True)
config = json.loads((ROOT / ".tools/environment.json").read_text())
java = Path(config["java"]) / "bin/java.exe"
modules = ROOT / ".tools/gradle-cache/caches/modules-2/files-2.1"
stdlib = next((modules / "org.jetbrains.kotlin/kotlin-stdlib/2.1.21").rglob("*.jar"))
json_jar = next((modules / "org.json/json/20240303").rglob("*.jar"))
classes = ROOT / "app/build/tmp/kotlin-classes/debug"
classpath = os.pathsep.join(map(str, [classes, stdlib, json_jar]))
ffmpeg = ROOT.parent / "youtube dl/tools/ffmpeg.exe"
ffprobe = ffmpeg.with_name("ffprobe.exe")
driver = WORK / "DubbingMarkerHost.java"
driver.write_text('''
import io.github.l1zerox.pandora.*;
import java.io.*;
import java.util.*;
import java.nio.file.*;
import org.json.*;
public class DubbingMarkerHost {
  public static void main(String[] args) throws Exception {
    if (args[0].equals("args")) {
      AudioDecision decision = AudioLanguageKt.audioDecision(
        List.of(new AudioTrack("aac", "ja", "日本語（自動吹き替え）", "", "")), List.of());
      if (!decision.getAutoDubbed()) throw new AssertionError("Localized note was missed");
      for (String arg : AutoDubbedKt.autoDubbedMetadataArgs(decision, true)) System.out.println(arg);
    } else if (args[0].equals("reports")) {
      JSONArray cases = new JSONArray(Files.readString(Path.of(args[1])));
      for (int i=0; i<cases.length(); i++) {
        JSONObject item = cases.getJSONObject(i);
        AudioDecision decision = AudioLanguageKt.parseAudioSelection("PANDORA_AUDIO|" + item.getJSONObject("report"));
        if (decision == null || decision.getAutoDubbed() != item.getBoolean("expected"))
          throw new AssertionError("Wrong selected audio role in report " + i);
        if (AutoDubbedKt.autoDubbedMetadataArgs(decision,true).isEmpty() == item.getBoolean("expected"))
          throw new AssertionError("Wrong MP4 metadata arguments in report " + i);
      }
      System.out.println(cases.length() + " actual extractor reports verified by Kotlin");
    } else {
      try (FileInputStream input = new FileInputStream(args[1])) {
        boolean actual = Mp4DubbingMarkerKt.mp4DubbingMarker(input.getChannel(), 0, -1);
        if (actual != Boolean.parseBoolean(args[2])) throw new AssertionError("Wrong file label");
        System.out.println("verified");
      }
    }
  }
}
''', encoding="utf-8")


def run(args):
    result = subprocess.run(list(map(str, args)), capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=45)
    if result.returncode:
        raise RuntimeError(result.stderr[-3000:])
    return result.stdout


metadata = run([java, "-Dfile.encoding=UTF-8", "-cp", classpath, driver, "args"]).splitlines()
assert metadata == ["-metadata", "genre=YoutubePandora:AutoDubbed:v1"]
print(run([java,"-Dfile.encoding=UTF-8","-cp",classpath,driver,"reports",ROOT / "app/build/reports/audio-role-results.json"]).strip())
for portrait in [False, True]:
    for fast in [False, True]:
        output = WORK / f"dubbed-{portrait}-{fast}.mp4"
        command = [ffmpeg, "-y", "-f", "lavfi", "-i", f"color=c=blue:s={'90x160' if portrait else '160x90'}:r=2",
                   "-f", "lavfi", "-i", "anullsrc=r=8000:cl=mono", "-t", "0.5", "-c:v", "libx264", "-c:a", "aac", *metadata]
        if fast:
            command += ["-movflags", "+faststart"]
        run(command + [output])
        tags = json.loads(run([ffprobe, "-v", "error", "-show_format", "-of", "json", output]))["format"]["tags"]
        assert tags["genre"] == "YoutubePandora:AutoDubbed:v1"
        run([java, "-cp", classpath, driver, "verify", output, "true"])
plain = WORK / "ordinary-japanese.mp4"
run([ffmpeg, "-y", "-i", output, "-map_metadata", "-1", "-metadata:s:a:0", "language=jpn", "-c", "copy", plain])
run([java, "-cp", classpath, driver, "verify", plain, "false"])
print("Auto-dubbing metadata: 5 real MP4 round trips passed (portrait/landscape, metadata at start/end, ordinary Japanese).")

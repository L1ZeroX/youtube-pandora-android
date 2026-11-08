"""Exercise compiled Kotlin range/conversion arguments with real local FFmpeg fixtures.

Run testDebugUnitTest before this script. No device, network or personal files are used.
"""
import array
import hashlib
import json
import math
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent.parent
WORK = ROOT / ".test-artifacts/save-range/ffmpeg"
WORK.mkdir(parents=True, exist_ok=True)
config = json.loads((ROOT / ".tools/environment.json").read_text())
java = Path(config["java"]) / "bin/java.exe"
modules = ROOT / ".tools/gradle-cache/caches/modules-2/files-2.1"
stdlib = next((modules / "org.jetbrains.kotlin/kotlin-stdlib/2.1.21").rglob("*.jar"))
json_jar = next((modules / "org.json/json/20240303").rglob("*.jar"))
classpath = os.pathsep.join(map(str, [ROOT / "app/build/tmp/kotlin-classes/debug", stdlib,
                                    json_jar, Path(config["sdk"]) / "platforms/android-36/android.jar"]))
ffmpeg = ROOT.parent / "youtube dl/tools/ffmpeg.exe"
ffprobe = ffmpeg.with_name("ffprobe.exe")


def run(args, binary=False):
    result = subprocess.run(list(map(str, args)), capture_output=True, timeout=90)
    if result.returncode:
        raise RuntimeError(result.stderr.decode("utf-8", errors="replace")[-4000:])
    return result.stdout if binary else result.stdout.decode("utf-8")


driver = WORK / "SaveRangeHost.java"
driver.write_text('''
import io.github.l1zerox.pandora.*;
import java.util.*;
public class SaveRangeHost {
  public static void main(String[] a) {
    SaveRange selection = new SaveRange(Boolean.parseBoolean(a[0]), Long.parseLong(a[1]),
      a[2].equals("end") ? null : Long.valueOf(a[2]));
    ResolvedSaveRange range = SaveRangeKt.resolveSaveRange(selection, Double.valueOf(a[3]));
    List<String> args = new ArrayList<>(ConversionKt.conversionInputArgs(a[4],range));
    if (a[5].equals("mp3")) args.addAll(Mp3ArtworkKt.mp3EncodingArgs("192kbps",a[6].equals("none") ? null : a[6]));
    else {
      String fps=a[5].equals("interpolated") ? ConversionKt.INTERPOLATED_FPS : "元のまま";
      String filter=a[5].equals("interpolated") ? ConversionKt.interpolationFilter(16.0,range.getDuration()) : null;
      args.addAll(List.of("-map","0:v:0","-map","0:a:0?"));
      args.addAll(ConversionKt.videoEncodingArgs(range == null,fps,filter));
      args.addAll(range == null ? List.of("-c:a","copy") : List.of("-c:a","aac","-b:a","192k"));
      args.addAll(List.of("-movflags","+faststart","-metadata","genre=YoutubePandora:AutoDubbed:v1"));
    }
    if (range != null) args.addAll(range.outputArgs());
    args.forEach(System.out::println);
  }
}
''', encoding="utf-8")

# A single long GOP makes inaccurate keyframe-only cuts fail the first-frame check.
source = WORK / "source.mp4"
run([ffmpeg, "-y", "-f", "lavfi", "-i",
     "color=red:s=160x96:r=16:d=2[v0];color=blue:s=160x96:r=16:d=2[v1];"
     "color=lime:s=160x96:r=16:d=2[v2];[v0][v1][v2]concat=n=3:v=1:a=0",
     "-f", "lavfi", "-i",
     "aevalsrc=0.2*sin(2*PI*if(lt(t\\,2)\\,440\\,if(lt(t\\,4)\\,880\\,1320))*t):s=48000:d=6",
     "-c:v", "libx264", "-pix_fmt", "yuv420p", "-g", "240", "-keyint_min", "240", "-sc_threshold", "0",
     "-c:a", "aac", "-b:a", "192k", source])
silent = WORK / "silent.mp4"
run([ffmpeg,"-y","-i",source,"-an","-c:v","copy",silent])
cover = WORK / "cover.jpg"
run([ffmpeg,"-y","-f","lavfi","-i","color=yellow:s=160x96","-frames:v","1",cover])


def probe(path):
    return json.loads(run([ffprobe,"-v","error","-show_format","-show_streams","-of","json",path]))


def color(path, expected):
    pixels = run([ffmpeg,"-v","error","-i",path,"-map","0:v:0","-frames:v","1",
                  "-vf","scale=1:1","-pix_fmt","rgb24","-f","rawvideo","pipe:1"], binary=True)
    assert len(pixels) == 3 and pixels[expected] > 200 and all(v < 50 for i,v in enumerate(pixels) if i != expected), pixels


def frequency(path, expected):
    raw = run([ffmpeg,"-v","error","-i",path,"-map","0:a:0","-vn","-ss","0.15","-t","0.2",
               "-ar","8000","-ac","1","-f","f32le","pipe:1"], binary=True)
    samples = array.array("f",raw)
    assert len(samples) > 1000
    # Goertzel-style direct projection at the three fixture frequencies.
    energy = {f: sum(v*math.cos(2*math.pi*f*i/8000) for i,v in enumerate(samples))**2 +
                 sum(v*math.sin(2*math.pi*f*i/8000) for i,v in enumerate(samples))**2 for f in [440,880,1320]}
    assert max(energy,key=energy.get) == expected, energy


cases = [
    # name, enabled, start, end, source length, kind, input, cover, duration, color channel, audio Hz
    ("mp4-accurate",True,3,"4",6,"mp4",source,None,1,2,880),
    ("mp3-artwork",True,3,"4",6,"mp3",source,cover,1,None,880),
    ("mp3-no-artwork",True,1,"3",6,"mp3",source,None,2,None,440),
    ("whole-unchanged",False,3,"4",6,"mp4",source,None,6,0,440),
    ("end-clamped",True,4,"99",6,"mp4",source,None,2,1,1320),
    ("to-end",True,4,"end",6,"mp4",source,None,2,1,1320),
    ("start-zero",True,0,"1",6,"mp4",source,None,1,0,440),
    ("unknown-length",True,3,"4",0,"mp4",source,None,1,2,880),
    ("unknown-to-end",True,4,"end",0,"mp4",source,None,2,1,1320),
    ("silent-video",True,3,"4",6,"mp4",silent,None,1,2,None),
    ("interpolation",True,3,"4",6,"interpolated",source,None,1,2,880),
]
results = []
for name,enabled,start,end,length,kind,media,picture,seconds,channel,tone in cases:
    args = run([java,"-Dfile.encoding=UTF-8","-cp",classpath,driver,str(enabled).lower(),start,end,
                length,media,kind,picture or "none"]).splitlines()
    output = WORK / (name + (".mp3" if kind == "mp3" else ".mp4"))
    run([ffmpeg,*args,output])
    report = probe(output)
    actual = float(report["format"]["duration"])
    assert abs(actual-seconds) < .12, (name,actual,seconds)
    if channel is not None:
        color(output,channel)
        assert report["format"]["tags"]["genre"] == "YoutubePandora:AutoDubbed:v1"
    if tone is not None:
        frequency(output,tone)
    else:
        assert not any(s["codec_type"] == "audio" for s in report["streams"])
    if picture:
        assert any(s.get("disposition",{}).get("attached_pic") == 1 for s in report["streams"])
        extracted = WORK / "extracted.jpg"
        run([ffmpeg,"-y","-i",output,"-map","0:v:0","-c","copy",extracted])
        assert hashlib.sha256(extracted.read_bytes()).digest() == hashlib.sha256(picture.read_bytes()).digest()
    if kind == "mp3" and not picture:
        assert not any(s["codec_type"] == "video" for s in report["streams"])
    if kind == "interpolated":
        assert next(s for s in report["streams"] if s["codec_type"] == "video")["avg_frame_rate"] == "60/1"
    results.append({"case":name,"duration":actual,"expected":seconds,"passed":True})
    print(f"PASS {name}: {actual:.3f}s, start/content/streams verified",flush=True)

(WORK / "results.json").write_text(json.dumps(results,indent=2),encoding="utf-8")
print(f"PASS: {len(results)} real FFmpeg range conversions using compiled Kotlin helpers")

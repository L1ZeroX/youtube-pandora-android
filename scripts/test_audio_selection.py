"""Exercise the app's exported selectors against its bundled yt-dlp, without YouTube/network access.

Run testDebugUnitTest first. The final transport checks use only a loopback HTTP fixture.
"""
import copy
import http.server
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import threading

ROOT = Path(__file__).resolve().parent.parent
CONFIG = json.loads((ROOT / "app/build/reports/audio-selection-fixture.json").read_text(encoding="utf-8"))
BUNDLE = ROOT / "app/src/main/res/raw/ytdlp"
sys.path.insert(0, str(BUNDLE))
from yt_dlp import YoutubeDL


class Logger:
    def debug(self, message): pass
    def warning(self, message): pass
    def error(self, message): pass


def audio(identifier, language, original=False, bitrate=128):
    return dict(format_id=identifier, url=f"https://example.invalid/{identifier}.m4a", ext="m4a",
                vcodec="none", acodec="aac", language=language, abr=bitrate,
                language_preference=10 if original else -1,
                format_note="English (Original)" if original else "auto-dubbed")


def video(height):
    return dict(format_id=f"v{height}", url=f"https://example.invalid/{height}.mp4", ext="mp4",
                vcodec="h264", acodec="none", width=height*16//9, height=height, fps=30)


def info(formats):
    return dict(id="fixture", title="Audio language fixture", extractor="fixture", language="en",
                webpage_url="https://example.invalid/fixture", formats=formats)


def select(formats, format="MP4", quality="720p以下", enabled=True):
    selector=CONFIG["selectors"][f"{format}|{quality}|{str(enabled).lower()}"]
    with YoutubeDL(dict(format=selector, quiet=True, logger=Logger(),
                        format_sort=["res", "fps", "vcodec:h264", "acodec:aac"])) as ydl:
        result=ydl.process_ie_result(copy.deepcopy(info(formats)), download=False)
        selected=result.get("requested_formats") or [result]
        chosen=[item for item in selected if item.get("acodec") != "none"]
        payload=ydl.evaluate_outtmpl(CONFIG["selectionTemplate"], result).split("|", 1)[1]
        report=json.loads(payload)
        assert "url" not in payload and "webpage_url" not in payload
        return result, chosen, report


def main():
    checks=0
    videos=[video(720), video(1080), video(2160)]
    for language in ["ja", "ja-JP", "ja_JP", "jpn", "ja-orig"]:
        formats=videos+[audio("en", "en", True, 256), audio("ja", language, False, 64)]
        for kind in ["MP4", "MP3"]:
            result, chosen, report=select(formats, format=kind)
            assert chosen[0]["language"] == language
            if kind == "MP4": assert result["height"] == 720
            assert any(item.get("language") == language for item in report["available"])
            _, chosen, _=select(formats, format=kind, enabled=False)
            assert chosen[0]["language"] == "en"
            checks+=2
    for language in ["ja-desc", "ja-JP-desc", "jv", "jam"]:
        _, chosen, _=select(videos+[audio("original", "es", True, 64), audio("other", language, False, 256)])
        assert chosen[0]["language"] == "es"
        checks+=1
    for quality, height in [("720p以下",720), ("1080p以下",1080), ("最高",2160)]:
        result, chosen, _=select(videos+[audio("ja", "ja")], quality=quality)
        assert result["height"] == height and chosen[0]["language"] == "ja"
        checks+=1
    combined=dict(video(720), acodec="aac", language="ja", format_id="combined-ja")
    result, chosen, report=select(videos+[audio("en", "en", True), combined])
    assert chosen[0]["format_id"] == "combined-ja" and not report["selected"]
    assert report["single"]["language"] == "ja"
    checks+=1
    _, chosen, _=select(videos+[audio("unknown", None)])
    assert chosen[0]["format_id"] == "unknown"
    checks+=1

    # Real CLI download: --print must not silently enable simulation, and a changed
    # language must not reuse a previously completed file during resume.
    bodies={"/ja.mp4": b"Japanese fixture bytes", "/en.mp4": b"Original fixture bytes"}
    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            body=bodies[self.path]
            self.send_response(200); self.send_header("Content-Length",str(len(body)))
            self.send_header("Content-Type","video/mp4"); self.end_headers(); self.wfile.write(body)
        def log_message(self, *args): pass
    server=http.server.ThreadingHTTPServer(("127.0.0.1",0),Handler)
    thread=threading.Thread(target=server.serve_forever,daemon=True); thread.start()
    try:
        with tempfile.TemporaryDirectory(prefix="pandora-audio-") as directory:
            work=Path(directory)
            ja=dict(combined,url=f"http://127.0.0.1:{server.server_port}/ja.mp4")
            en=dict(ja,language="en",format_id="combined-en",format_note="original",url=f"http://127.0.0.1:{server.server_port}/en.mp4")
            paths=[]
            for formats, expected in [([en,ja],"ja"), ([en],"en"), ([en],"en")]:
                fixture=work/"fixture.json"; fixture.write_text(json.dumps(info(formats)),encoding="utf-8")
                command=[sys.executable,str(BUNDLE),"--ignore-config","--no-playlist","--no-colors",
                         "--load-info-json",str(fixture),"-f",CONFIG["selectors"]["MP4|720p以下|true"],
                         "-S","res,fps,vcodec:h264,acodec:aac","-o",str(work/CONFIG["output"]),
                         "--merge-output-format","mkv","--continue",*CONFIG["options"]]
                completed=subprocess.run(command,text=True,encoding="utf-8",capture_output=True,timeout=30)
                assert completed.returncode == 0, completed.stderr
                lines=completed.stdout.splitlines()
                report=json.loads(next(line.split("|",1)[1] for line in lines if line.startswith("PANDORA_AUDIO|")))
                assert report["single"]["language"] == expected
                path=Path(next(line.removeprefix(CONFIG["filePrefix"]) for line in lines if line.startswith(CONFIG["filePrefix"])))
                assert path.read_bytes() == bodies[f"/{expected}.mp4"]
                assert path.resolve().parent == work.resolve()
                paths.append(path)
                checks+=1
            assert paths[0] != paths[1] and paths[1] == paths[2]
    finally:
        server.shutdown(); server.server_close(); thread.join(timeout=5)
    print(f"Bundled yt-dlp audio selection: {checks} checks passed (including 3 loopback download/resume checks).")


if __name__ == "__main__":
    main()

# Third-party software

- youtubedl-android 0.18.1 (library, ffmpeg, common): GPL-3.0, https://github.com/yausername/youtubedl-android/tree/0.18.1
- Native build instructions and source references: https://github.com/yausername/youtubedl-android/blob/0.18.1/BUILD_FFMPEG.md and https://github.com/yausername/youtubedl-android/blob/0.18.1/BUILD_PYTHON.md
- yt-dlp zipapp 2026.10.26: Unlicense (bundled dependencies retain their own licenses), https://github.com/yt-dlp/yt-dlp/releases/tag/2026.10.26 . The app overrides the library's 2025.11.12 resource with the unmodified official zipapp, including yt-dlp-ejs. SHA-256: e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3 . Reproduce with `python scripts/sync_ytdlp.py`.
- FFmpeg: LGPL / GPL according to enabled components, https://ffmpeg.org/legal.html and https://ffmpeg.org/download.html
- CPython: PSF License, https://github.com/python/cpython
- QuickJS: MIT, https://github.com/bellard/quickjs
- AndroidX / Jetpack Compose: Apache-2.0, https://android.googlesource.com/platform/frameworks/support/
- AndroidX Media3 1.11.1 (ExoPlayer, session, UI): Apache-2.0, https://github.com/androidx/media/tree/1.11.1
- Kotlin / kotlinx.coroutines: Apache-2.0, https://github.com/JetBrains/kotlin and https://github.com/Kotlin/kotlinx.coroutines
- Coil: Apache-2.0, https://github.com/coil-kt/coil

See the resolved Maven artifacts and upstream repositories for transitive dependencies and their notices. Source code for this app is provided alongside the APK. Native binaries are unmodified upstream artifacts.

# 初版の確認状況

## v0.1.3（2026-11-06）

直接起動するffprobe / FFmpegのLD_LIBRARY_PATHにPythonパッケージのusr/libを追加。ARM64・x86_64の同梱ELFの推移的依存関係（ZIP内シンボリックリンクも解決）を確認し、旧参照先ではlibandroid-posix-semaphore.so、libandroid-support.so、libc++_shared.so、libcrypto.so.3、libexpat.so.1の5本が不足すること、修正後はシステム標準ライブラリー以外の不足がなくなることを確認した。

アプリ本体と実行テストで起動環境を共通化。失敗状態・エラー保存とコピー表示を追加。実機からのエラー全文はまだ未取得で、今回の端末症状との一致および実機での修正後の保存成功は未確認。

## v0.1.2（2026-11-06）

保存先を内部ストレージのDownload/YoutubePandora!/mp4・mp3へ変更。Download配下を許容するMediaStore.DownloadsへMIME付きで挿入し、完了時に公開。画面の保存先表示も共通化。実行テストに指定パスと動画・音楽コレクションからの参照確認を追加。実機での保存・ギャラリー表示は未確認。

## v0.1.1（2026-11-06）

MP4の保存先をMediaStore.Video / Movies/YoutubePandoraへ変更。MP3はMediaStore.Audio / Music/YoutubePandoraへ変更。実行テストに保存先・MIME・公開状態の確認を追加。実機でのギャラリー表示は未確認。

2026-11-06、Windows上でビルド。

- Debug / Release APK生成: 成功
- 単体テスト: 5件成功（URL検証・重複除外、時間表示、画質選択、ファイル名処理）
- Android Lint: エラーなし。依存ライブラリーの新バージョン通知などの警告あり
- 実行テストAPKのコンパイル: 成功
- 配布APKの署名検証: 成功
- ARM64同梱実行ファイルのELF確認: Python / QuickJS / FFmpeg / ffprobeは16KBアラインメント。FFmpegの圧縮パッケージ内WebP関連5ライブラリーは4KBであり、互換モードを指定

Android 16のエミュレーターをハードウェア支援なしで起動したが、ADB offlineのままで起動完了しなかったため、実行テストは未実施。テスト用プロセスは終了済み。

実機での起動、ネットワーク越しの動画取得、MP4 / MP3の保存結果、通知からの停止、画面表示は未検証。端末の接続後に `scripts/build.py connectedDebugAndroidTest` を実行し、さらに実際のYouTube・他サイトと長時間ダウンロードの停止を確認する必要がある。

実行テストには、外部サイトに依存せず端末内の合成動画をHTTP配信し、取得・MP4再圧縮回避・24fps変換・MP3変換・MediaStore保存をffprobeで調べるテスト、および設定・画質メニューの画面操作テストを用意している。

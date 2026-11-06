# Youtube Pandora! Android v0.1.3（保存前の解析・変換エラー修正）

スマートフォンから下のリンクを開いてAPKをダウンロードできます。

**[APKをダウンロード](https://github.com/L1ZeroX/youtube-pandora-android/releases/download/v0.1.3/YoutubePandora-Android-v0.1.3.apk)**

ダウンロードしたAPKをタップしてインストールしてください。旧版を使っている場合は、削除せずに上書き更新できます。

## 修正内容

- ffprobe / FFmpegを直接起動する際に、Pythonパッケージ内の共通ライブラリーも参照するよう修正。解析・変換で必要なライブラリーが見つからず、保存先へ出力されない不具合に対応しました。
- 一覧に失敗理由を表示し、詳細画面に「エラーをコピー」を追加しました。
- 再起動後も失敗状態とエラーを保持し、「再試行」を使えるようにしました。

更新後に「再試行」を押してください。旧版ですでにエラー情報が消えて待機中に戻っている場合は、動画を選択して「ダウンロード」を押してください。ファイルがなかった場合は再ダウンロードが必要です。

## 保存先

- 動画：内部ストレージ/Download/YoutubePandora!/mp4
- 音楽：内部ストレージ/Download/YoutubePandora!/mp3

旧版で保存したファイルは元の場所に残ります。

## 機能

yt-dlp対応サイト、MP4 / MP3、画質・fps選択、サムネイル・時間・進捗表示、停止・再試行、共有メニューからのURL追加、ダークモード、背景画像・透過・ぼかしを実装しています。

## 対応と確認状況

Android 10以降、64bit ARM / x86_64向け。Android 16 / API 36をターゲットにしています。ビルド・単体テスト5件・静的チェック・APK署名検証に加え、両CPU向けFFmpeg/ffprobeのELF依存関係を確認しています。実機での保存・ギャラリー表示は未検証です。詳しくは[確認状況](https://github.com/L1ZeroX/youtube-pandora-android/blob/main/VALIDATION.md)を参照してください。

APK、ソースZIP、SHA256SUMS.txtを添付しています。ソースのライセンスはGPL-3.0です。

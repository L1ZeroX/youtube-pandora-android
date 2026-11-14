# Youtube Pandora! Android v0.4.33

[正式版APKをダウンロード](https://github.com/L1ZeroX/youtube-pandora-android/releases/download/v0.4.33/YoutubePandora-Android-v0.4.33.apk)

- **一覧が開けない場合の接続修正**：Android Autoのブラウズ接続を拒否し得る署名判定を修正。Google公式サンプルの正式なAndroid Auto証明書を確認し、Google Play開発者サービスと同じ署名を要求する前提を取り除きました。
- **アルバム→曲選択**：「アルバム」を先頭へ移動。アルバムは画像付きグリッド、曲は曲名リストで表示する指定を追加しました。曲を押すと、その曲からアルバム内の順番で再生する処理です。
- **確認用ログ**：ブラウズ接続の可否と一覧取得件数を処理ログへ残します。曲名やファイルURIはこの診断ログに含めません。

更新後はAndroid Autoを再接続し、車側でYoutube Pandora!を開き直して「アルバム」→アルバム画像→曲名の順に選んでください。

単体テスト331件成功、Androidテストのコンパイル成功、Lintエラー0。署名と差分復元を検証済みです。

前版のユーザー提供画像で曲名・アートワーク・再生時刻の表示は確認できています。今回の一覧・曲選択の修正後の動作は、実車・Desktop Head Unitでは未確認です。[検証状況](VALIDATION.md)。

正式版v0.4.32から差分更新に対応。同じアプリID・署名、versionCode 72で設定と保存ファイルを引き継ぎます。

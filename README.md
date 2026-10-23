# BLUDEX 📡

Androidで周囲のBluetooth Low Energy（BLE）広告を探索する、ローカル中心のデバイス図鑑アプリ。

## Features

- **BLEスキャン**：近くのデバイスを電波強度（RSSI）順で表示
- **メーカー・カテゴリー推定**：Company Identifier / 広告デバイス名 / Service UUIDから推定。型番は明示的な名前がある場合のみ表示
- **デバイス詳細**：RSSI、TX Power、接続可否、Service UUID、メーカー独自データ、RAW広告データを表示・コピー
- **ペアリング要求**：利用者が明示的に要求した場合に限り、Android標準のペアリング処理を開始
- **お気に入りと履歴**：ローカルに最大500件（お気に入りは保持）を保存、表示名を編集可能
- **検索とフィルター**：メーカー、名前、アドレス、識別済み、お気に入り
- **ダークUI**：Kotlin + Jetpack Compose + Material 3

## Requirements

- Android 8.0 (API 26) 以降
- BLE対応Android端末
- Bluetoothと位置情報権限（BLE広告の幅広い検出のため）
- Android 12以上ではNearby devices権限も許可
- Android Studio + JDK 17。Gradle 8.11.1

## Build

1. Android Studioでこのリポジトリのフォルダを開きます。
2. Gradle 8.11.1 / JDK 17を使って同期します。ローカル環境にGradleがある場合は `gradle :app:assembleDebug` でビルドできます。
3. ビルドしたAPKは `app/build/outputs/apk/debug/app-debug.apk` です。
4. GitHub Actionsの **Android CI** がmainへのpushごとにDebug APKをビルドし、成功時に **BLUDEX-debug** artifactを公開します。

> 本リポジトリはGradle WrapperのバイナリJARを含みません。GitHub ActionsではGradleを自動セットアップします。Android StudioのGradle設定からGradle 8.11.1を指定してください。

## Permissions and privacy

- `BLUETOOTH_SCAN`：BLE広告を受信する
- `BLUETOOTH_CONNECT`：デバイス名／ペアリング状態の確認とペアリング要求
- `ACCESS_FINE_LOCATION`：BLE広告を幅広くスキャンするための権限。**アプリはGPS座標を読み取りません**
- お気に入りと履歴は端末内SharedPreferencesにのみ保存。インターネット権限を宣言せず、送信機能もありません
- アプリがバックグラウンドに移るとスキャンは停止します

## Limitations

- **BLE広告を受信できることと接続・ペアリングできることは別です。**
- ペアリングは自動では行いません。確認ダイアログの後にAndroidのシステム処理を開始します。
- Company IDだけで正確な機種を判定することはできません。機種情報が不明な場合は「不明」と表示します。
- MACアドレスをランダム変更するデバイスは、同一機種でも別アドレスとして記録される場合があります。
- **0.1.0ではカテゴリー別ベクターアイコンを表示します。製品の実写真・モデル別カタログは今後の追加機能です。**
- Bluetooth Classic専用機器の探索やGATT接続は対象外。BLE広告のみに対応しています。

## Source

`app/src/main/java/jp/bludex/app/` 以下：
- `bluetooth/BleScanner.kt` BLE広告取得とUI更新
- `bluetooth/BondController.kt` Androidペアリング要求と結果監視
- `data/DeviceModels.kt` デバイス情報と識別ロジック
- `data/DeviceStore.kt` ローカル履歴・お気に入り
- `ui/BludexApp.kt` 一覧・詳細・お気に入り・履歴・設定
- `MainActivity.kt` 実行時権限とBluetooth有効化

## License

License not yet specified by the repository owner.

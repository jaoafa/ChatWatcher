# ChatWatcher

VC の音声を聞いて文字起こしする。(Voice)ChatWatcher

Web Speech API の [SpeechRecognition](https://developer.mozilla.org/ja/docs/Web/API/SpeechRecognition) を使用したリアルタイム文字起こし

- `make`: 起動
- `make stop`: 停止
- `make attach`: コンテナに入る
- `make logs`: ログリアルタイム表示

## コマンド

スラッシュコマンドにて実装。すべてのコマンドは `/chatwatcher` の下に作成される

- `/chatwatcher add-server`: コマンドを実行したサーバを ChatWatcher の対象サーバとして登録する
- `/chatwatcher remove-server`: コマンドを実行したサーバを ChatWatcher の対象サーバから削除する
- `/chatwatcher summon`: コマンド実行者が参加しているボイスチャンネルに参加する
- `/chatwatcher disconnect`: 参加しているボイスチャンネルから退出する
- `/chatwatcher add-channel [Channel]`: 指定したチャンネル、またはコマンド実行したチャンネルを文字起こし結果送信チャンネルとして登録する
- `/chatwatcher remove-channel [Channnel]`: 指定したチャンネル、またはコマンド実行したチャンネルの文字起こし結果送信チャンネル登録を解除する

## シーケンス図

### 起動

```mermaid
sequenceDiagram
  autonumber
  participant Discord
  participant ChatWatcher
  participant Docker

  Note over ChatWatcher: 0.0.0.0:9092 で Socket.io サーバを起動
  ChatWatcher->>Discord: ログイン
  ChatWatcher->>Docker: 起動中の<br>文字起こしコンテナを削除
  Note over ChatWatcher: シャットダウンフックに各サーバ・クライアント停止処理を追加
```

## ユーザーが参加・退出する場合

```mermaid
sequenceDiagram
  autonumber
  actor ユーザー
  participant Discord
  participant ChatWatcher
  participant 文字起こしコンテナ

  Discord->>ChatWatcher: ユーザーがVCに参加
  Note over ChatWatcher: Dockerで文字起こしコンテナを起動
  文字起こしコンテナ->>+ChatWatcher: Socket.ioで接続
  文字起こしコンテナ->>ChatWatcher: join イベントを発火<br>(ルームに参加)
  ChatWatcher->>文字起こしコンテナ: joined イベントを発火

  loop ユーザーがVCに参加中
    ユーザー->>Discord: ユーザーが喋る
    Discord->>ChatWatcher: 音声データを受け取る<br>(20ミリ秒毎に受信)
    ChatWatcher->>文字起こしコンテナ: 音声データを送信
    文字起こしコンテナ->>文字起こしコンテナ: SpeechRecognitionで文字起こし開始
    loop 文字起こし中
      文字起こしコンテナ->>ChatWatcher: 文字起こし結果を逐次送信 (message イベント)
      ChatWatcher->>Discord: 文字起こし結果を作成・編集
    end
  end

  Discord->>ChatWatcher: ユーザーがVCから退出
  Note over ChatWatcher: 文字起こしコンテナを終了
```

## その他

- 文字起こしコンテナが残った場合: `docker rm $(docker ps -f "name=chatwatcher-recognizer" -q -a)`

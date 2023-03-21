# ChatWatcher

VC の音声を聞いて文字起こしする。(Voice)ChatWatcher

## Flow

### VC への参加

以下いずれかの方法で @ChatWatcher#5297 が VC に参加する

- ユーザーの参加・移動をトリガーとした自動参加
    - Bot がその Guild においてどこにも入っていない状態で、ユーザーがいずれかの VC に参加した場合その VC に参加する。
    - Bot がその Guild においていずれかの VC に入っている状態で、その VC から誰もいなくなった場合（Bot を除く）抜ける、または別のユーザーがいるチャンネルに移動する。
- ユーザーのテキストチャンネルにおける発言をトリガーとした参加
    - コマンド `<PREFIX>join` や `<PREFIX>summon` を実行したユーザーがいる VC に参加する

## VC での音声

- ユーザー毎に音声を拾って、`UserAudioStreams/{UserId}-{StartMilliSecond}.cw` に逐次保存する
- 最終録音時刻から 1 秒経過時点で wav ファイルに変換
- 変換した wav ファイルを SpeechRecognition に通す
- 結果を適宜送信

## VC からの退出

以下いずれかの方法で退出

- ユーザーの退出をトリガーとした自動退出
    - Bot が入っている VC から Bot 以外のユーザーが全員抜けた場合、退出する
- コマンド `<PREFIX>leave` が叩かれた場合退出する

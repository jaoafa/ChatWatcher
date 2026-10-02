# koemoji

Discord のボイスチャンネル文字起こし bot です。参加者ごとの発話をローカルの CPU だけで認識し(クラウドの ASR は使いません)、話している最中は約 2 秒ごとに更新しながらテキストチャンネルへ投稿します。

```
Discord voice (JDA + DAVE) -> ユーザー別 PCM -> Silero VAD -> 発話ファイル (追記のみ)
  -> partial/final ジョブ (SQLite キュー) -> ASR ワーカー (sherpa-onnx) -> Discord メッセージ作成/編集
```

## 使い方

```sh
echo 'DISCORD_TOKEN=...' > .env
# モデルは初回起動時に ./data/models へ自動ダウンロードされ、キャッシュされます。事前に全部取得したい場合は scripts/download-models.sh
docker compose up -d --build
```

Discord 上で、文字起こしを投稿したいテキストチャンネルで `/register` を一度実行します(`channel` で投稿先を指定することもできます)。VC への参加方式は jaoafa/ChatWatcher と同じです。

- **自動参加**: 登録済みサーバーで bot がどの VC にもいないとき、人間が VC に入る(AFK チャンネルを除く)と bot も参加します。
- **自動移動**: 人間が bot のいるチャンネルから、より人間の多いチャンネルへ移ると bot も追従します。
- **自動退出**: bot のいるチャンネルから人間がいなくなる(最後の 1 人が AFK へ移った場合を含む)と bot も退出します。
- `/join [channel]` と `/leave` で手動操作もできます。`/unregister` でそのサーバーの機能をすべて無効にします。

コマンドはいずれも「サーバーの管理」権限が必要です。未登録のサーバーには参加も録音もしません。

投稿は `ユーザー名: 発言` の形式です。複数のエンジンを指定した場合は、エンジンごとに別メッセージとなり、先頭に `[エンジン名]` が付くので、モデルを並べて比較できます。本番運用ではエンジンを 1 つにしてください。

## 設定 (環境変数)

| 変数 | 既定値 | 説明 |
|---|---|---|
| `MODE` | `all` | `all`、`capture`、`worker` のいずれか。分けるとワーカーだけをスケールできます。両者は `QUEUE_DB` と `AUDIO_DIR` を共有します |
| `ASR_ENGINES` | `sensevoice` | すべての発話を送るエンジン(カンマ区切り) |
| `MESSAGE_FORMAT` | `{user}: {text}` | 投稿メッセージの書式。`{user}`、`{text}`、`{engine}` が使えます。エンジンが複数で書式に `{engine}` が無い場合は、先頭に `[エンジン名] ` が付きます |
| `WORKER_ENGINES` | `ASR_ENGINES` と同じ | 1 エントリにつきワーカースレッドを 1 つ起動します。`name:N` で N 個 |
| `ASR_THREADS` | `4` | ワーカー 1 つあたりの ONNX スレッド数 |
| `ASR_LANGUAGE` | `ja` | SenseVoice / Whisper への言語ヒント(空なら自動判定) |
| `ASR_PAD_MS` | `0` | ASR に渡す音声の前後に足す無音の長さ |
| `PARTIAL_INTERVAL_MS` | `2000` | partial 認識の間隔 |
| `MAX_UTTERANCE_MS` | `30000` | この長さで発話を確定し、続きを新しい発話として扱います |
| `VAD_THRESHOLD` | `0.5` | Silero の発話確率のしきい値 |
| `VAD_START_MS` / `VAD_END_SILENCE_MS` / `VAD_PREROLL_MS` | `96` / `700` / `320` | 発話開始とみなす連続長、発話終了とみなす無音長、開始検出より前に残しておく音声の長さ |
| `MIN_UTTERANCE_MS` | `300` | これより短い発話は捨てます |
| `AUDIO_TTL_MIN` / `FAILED_AUDIO_TTL_MIN` | `30` / `1440` | すべての final が確定してから、音声を削除するまでの時間 |
| `RETRY_MAX` / `RETRY_BACKOFF_MS` | `5` / `2000` | final ジョブの再試行回数と、指数バックオフの基準時間 |
| `INCLUDE_BOTS` | `true` | 他の bot の音声も文字起こしする |
| `HEALTH_PORT` | `8080` | `/health` と `/metrics` の HTTP ポート(`0` で無効) |
| `MODELS_DIR`、`VAD_MODEL`、`AUDIO_DIR`、`QUEUE_DB` | `Config.java` 参照 | 各種パス |

エンジン: `sensevoice`、`reazon-ja`、`reazon-ja-en`、`qwen3-asr`、`whisper-small`、`whisper-turbo`、`parakeet-ja`、`dolphin-small`(`SherpaEngine.DIRS` 参照)。

## 運用

- `/health` は、Discord に接続済み(capture)かつ、設定した全ワーカースレッドが生存(worker)のときだけ 200 を返します。Docker の `HEALTHCHECK` がこれを呼びます。
- `/metrics` は Prometheus 形式で、`koemoji_jobs{engine,status}`(キューの深さと結果)、`koemoji_workers_alive`、エンジンごとの認識回数と所要時間を返します。
- 起動時に、未知のエンジン名は即エラーになります。`MODE=all` では、ワーカーのないエンジンもエラーです。`MODE=capture` ではワーカー側が分からないため、ワーカーコンテナの `WORKER_ENGINES` が `ASR_ENGINES` を網羅するようにしてください。
- ユーザー別の音声パイプライン(VAD)は、音声が 5 分途切れると解放されます。

## 設計メモ

- partial はスナップショットです。ジョブは、伸び続ける音声ファイルの先頭 `snapshot_bytes` だけを読むので、追記が並行しても、各リビジョンの見る内容は変わりません。
- 発話のキュー済み partial は、より新しいジョブに置き換えられます。final は捨てません。取り出す順は、final が先で、次に最新の partial です。
- 表示するのは最新のリビジョンだけです。遅れて届いた古い結果は無視し、追いつかない編集は最新の結果にまとめます。
- オフラインモデルは partial のたびに発話全体を再認識します。30 秒の発話では、ASR の所要時間が発話の長さの約 8 倍になります。
- Discord は、ユーザーが黙っている間はパケットを送らないため、発話の終了はパケットのタイムアウトでも検出します。

## モデル比較

FLEURS で各エンジンを評価しました(日本語は CER、英語は WER)。4 コアでは、SenseVoice (2024-07 int8) が最もバランスが良く、日本語 CER 7.9%、英語 WER 6.9%、リアルタイム係数 0.19 でした。2025-09 版の SenseVoice は広東語向けの fine-tune で、日本語には使えません。

## 開発

```sh
scripts/install-sherpa.sh   # 初回のみ: sherpa-onnx は Maven Central に無いため、リリースの jar を ~/.m2 に入れます
mvn test      # PipelineTest は data/models に sensevoice と test_silero_vad.wav があるときだけ実行されます
mvn package   # target/koemoji.jar
```

コンテナは意図的に root で動かしています。rootless Docker では、非 root ユーザーがバインドマウントした `./data` に書き込めないためです。

# koemoji

Discord voice channel transcription bot. Every user's speech is recognized locally (CPU only, no cloud ASR) and posted to a text channel, updated every ~2 seconds while the user is still talking.

```
Discord voice (JDA + DAVE) -> per-user PCM -> Silero VAD -> utterance file (append-only)
  -> partial/final jobs (SQLite queue) -> ASR workers (sherpa-onnx) -> Discord message create/edit
```

## Run

```sh
echo 'DISCORD_TOKEN=...' > .env
# models are downloaded automatically on first start into ./data/models (cached); use scripts/download-models.sh to prefetch everything
docker compose up -d --build
```

In Discord, run `/register` once in the text channel where transcripts should go (or pass `channel`). Joining works like jaoafa/ChatWatcher:

- **Auto join**: when the bot is in no voice channel of a registered guild and a human joins one (not the AFK channel), the bot joins it.
- **Auto move**: when a human moves from the bot's channel to a channel with more humans, the bot follows.
- **Auto leave**: when no humans remain in the bot's channel (including the last one moving to AFK), the bot leaves.
- `/join [channel]` and `/leave` override manually; `/unregister` disables everything for the guild.

All commands need the Manage Server permission. Unregistered guilds are never joined or recorded.

Each utterance is posted once per configured engine as `[engine] user: text`, so models can be compared side by side. Use a single engine in production.

## Configuration (environment variables)

| Variable | Default | Meaning |
|---|---|---|
| `MODE` | `all` | `all`, `capture` or `worker`. Split them to scale workers separately; both share `QUEUE_DB` and `AUDIO_DIR` |
| `ASR_ENGINES` | `sensevoice` | Engines every utterance is sent to (comma separated) |
| `WORKER_ENGINES` | = `ASR_ENGINES` | One worker thread per entry; `name:N` runs N workers |
| `ASR_THREADS` | `4` | ONNX threads per worker |
| `ASR_LANGUAGE` | `ja` | Language hint for SenseVoice / Whisper (empty = auto) |
| `ASR_PAD_MS` | `0` | Silence added before and after audio given to ASR |
| `PARTIAL_INTERVAL_MS` | `2000` | Partial recognition interval |
| `MAX_UTTERANCE_MS` | `30000` | Utterance is finalized and continued as a new one at this length |
| `VAD_THRESHOLD` | `0.5` | Silero speech probability threshold |
| `VAD_START_MS` / `VAD_END_SILENCE_MS` / `VAD_PREROLL_MS` | `96` / `700` / `320` | Speech start run, end-of-utterance silence, audio kept before detected start |
| `MIN_UTTERANCE_MS` | `300` | Shorter utterances are dropped |
| `AUDIO_TTL_MIN` / `FAILED_AUDIO_TTL_MIN` | `30` / `1440` | Audio is deleted this long after all finals settled |
| `RETRY_MAX` / `RETRY_BACKOFF_MS` | `5` / `2000` | Final job retries with exponential backoff |
| `INCLUDE_BOTS` | `true` | Also transcribe other bots' audio |
| `HEALTH_PORT` | `8080` | HTTP port for `/health` and `/metrics` (`0` disables) |
| `MODELS_DIR`, `VAD_MODEL`, `AUDIO_DIR`, `QUEUE_DB` | see `Config.java` | Paths |

Engines: `sensevoice`, `reazon-ja`, `reazon-ja-en`, `qwen3-asr`, `whisper-small`, `whisper-turbo`, `parakeet-ja`, `dolphin-small` (see `SherpaEngine.DIRS`).

## Operations

- `/health` returns 200 only when Discord is connected (capture) and every configured worker thread is alive (worker). The Docker `HEALTHCHECK` calls it.
- `/metrics` is Prometheus text: `koemoji_jobs{engine,status}` (queue depth and outcomes), `koemoji_workers_alive`, and per-engine recognition counts and time.
- Startup fails fast on unknown engine names, and in `MODE=all` on engines that have no worker. In `MODE=capture` the worker side is unknown, so make sure `WORKER_ENGINES` of the worker containers cover `ASR_ENGINES`.
- Per-user audio pipelines (VAD) are released after 5 minutes without audio.

## Design notes

- Partials are snapshots: a job reads only the first `snapshot_bytes` of the growing audio file, so a concurrent append never changes what a revision sees.
- Queued partials of an utterance are superseded by any newer job; finals are never dropped. Claim order is final first, then newest partial.
- Only the newest revision is shown; older results arriving late are ignored, and edits that cannot keep up coalesce to the latest.
- Offline models re-recognize the whole utterance on every partial, so a 30 s utterance costs about 8x its length in ASR time.
- Discord sends no packets while a user is silent, so the end of an utterance is also detected by packet timeout.

## Model comparison

Engines were benchmarked on FLEURS (CER for Japanese, WER for English). On 4 cores, SenseVoice (2024-07 int8) was the best trade-off: Japanese CER 7.9%, English WER 6.9%, real-time factor 0.19. The 2025-09 SenseVoice release is a Cantonese fine-tune and is unusable for Japanese.

## Development

```sh
mvn test      # PipelineTest runs only if data/models has sensevoice and test_silero_vad.wav
mvn package   # target/koemoji.jar
```

The container runs as root on purpose: with rootless Docker, a non-root user inside cannot write to the bind-mounted `./data`.

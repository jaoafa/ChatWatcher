FROM python:3

WORKDIR /app

# hadolint ignore=DL3008
RUN apt-get update && \
    apt-get install -y --no-install-recommends aria2 && \
    rm -rf /var/lib/apt/lists/* && \
    aria2c -x16 -s16 --file-allocation=none https://alphacephei.com/vosk/models/vosk-model-ja-0.22.zip -o model.zip && \
    unzip model.zip && \
    mv vosk-model-ja-0.22 model && \
    rm model.zip && \
    apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    gcc \
    git \
    ffmpeg \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
# hadolint ignore=DL3013
RUN pip install --no-cache-dir -r requirements.txt && \
    pip install --no-cache-dir git+https://github.com/openai/whisper.git soundfile

COPY . /app

CMD [ "python", "main.py" ]

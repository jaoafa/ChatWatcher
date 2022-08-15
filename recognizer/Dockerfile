FROM alpine:edge

RUN apk update && \
  apk add --no-cache dumb-init && \
  apk add --no-cache curl fontconfig font-noto-cjk && \
  fc-cache -fv && \
  apk add --no-cache \
  chromium \
  nss \
  freetype \
  freetype-dev \
  harfbuzz \
  ca-certificates \
  ttf-freefont \
  nodejs \
  yarn \
  xvfb \
  xauth \
  dbus \
  dbus-x11 \
  x11vnc \
  pulseaudio \
  pulseaudio-utils && \
  apk add --update --no-cache tzdata && \
  cp /usr/share/zoneinfo/Asia/Tokyo /etc/localtime && \
  echo "Asia/Tokyo" > /etc/timezone && \
  apk del tzdata && \
  addgroup -S speech-recognizer && \
  adduser -G speech-recognizer -D speech-recognizer && \
  addgroup speech-recognizer audio && \
  sed -i 's/load-module module-console-kit/# load-module module-console-kit/g' /etc/pulse/default.pa && \
  sed -i 's/load-module module-stream-restore/load-module module-stream-restore restore_device=false/' /etc/pulse/default.pa

WORKDIR /app

COPY package.json ./
RUN yarn
COPY src/ src/
COPY tsconfig.json .

COPY entrypoint.sh .
RUN chmod +x entrypoint.sh

USER speech-recognizer:speech-recognizer

ENV RECOGNIZER_HEADLESS false
ENV DISPLAY :99

ENTRYPOINT ["dumb-init", "--"]
CMD ["/app/entrypoint.sh"]
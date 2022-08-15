#!/bin/sh

docker network create chatwatcher-network
# docker build -t chatwatcher-recognizer recognizer
docker pull ghcr.io/jaoafa/ChatWatcher-recognizer

docker-compose up --build -d
FROM alpine:3.16.2

WORKDIR /app

RUN apk update && \
  apk add --no-cache openssl

CMD openssl req -x509 -newkey rsa:4096 -sha256 -days 365 -nodes \
  -keyout domain.key \
  -out signed.crt \
  -subj "/CN=${DOMAIN}" \
  -addext "subjectAltName=DNS:${DOMAIN}" \
  -addext "extendedKeyUsage=serverAuth"
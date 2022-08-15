FROM nginx:1.22-alpine

RUN apk update

COPY --chown=nginx:nginx ./default.conf.template /etc/nginx/templates/
COPY --chown=nginx:nginx html/ /usr/share/nginx/html/
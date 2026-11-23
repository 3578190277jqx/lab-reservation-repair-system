#!/bin/sh
set -eu
umask 077
cd "$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
if [ "$#" -ne 1 ]; then echo 'Usage: sh deploy/deploy-2g.sh http://YOUR_PUBLIC_IP' >&2; exit 1; fi
sh deploy/preflight-2g.sh
origin=$1
case "$origin" in http://*) ;; *) echo 'Use the public HTTP origin without a trailing path.' >&2; exit 1;; esac
host=${origin#http://}
case "$host" in ''|*[!a-zA-Z0-9.:-]*) echo 'Invalid host/origin.' >&2; exit 1;; esac
[ -f .env ] || { echo 'Copy private server.env to .env first.' >&2; exit 1; }
# Persist origin; SMTP and DB credentials remain in the existing private file.
awk '!/^CORS_ALLOWED_ORIGINS=/' .env > .env.next
printf "CORS_ALLOWED_ORIGINS='%s'\n" "$origin" >> .env.next
mv .env.next .env
chmod 600 .env
docker compose -f docker-compose.yml -f deploy/compose-2g.yml -f deploy/compose-runtime.yml config --quiet
# Only runtime base image+JAR/static COPY; no Maven/npm compilation on this machine.
docker compose -f docker-compose.yml -f deploy/compose-2g.yml -f deploy/compose-runtime.yml build backend
docker compose -f docker-compose.yml -f deploy/compose-2g.yml -f deploy/compose-runtime.yml build frontend
docker compose -f docker-compose.yml -f deploy/compose-2g.yml -f deploy/compose-runtime.yml up -d --no-build --wait --wait-timeout 360
docker compose -f docker-compose.yml -f deploy/compose-2g.yml -f deploy/compose-runtime.yml ps
printf 'Web demo ready: %s/login\n' "$origin"

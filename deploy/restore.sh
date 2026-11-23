#!/bin/sh
set -eu
if [ "$#" -ne 2 ] || [ "$2" != "--confirm-restore" ]; then
 echo 'Usage: sh deploy/restore.sh /absolute/backup-directory --confirm-restore' >&2; exit 1
fi
backup=$(CDPATH= cd -- "$1" && pwd)
[ -f "$backup/database.sql" ] && [ -f "$backup/uploads.tar.gz" ] && [ -f "$backup/SHA256SUMS" ]
(cd "$backup" && sha256sum -c SHA256SUMS)
cd "$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
compose_file=${COMPOSE_FILE:-docker-compose.ecs.yml}
# This replaces target SQL records; take a current backup before explicit confirmation.
was_running=$(docker compose -f "$compose_file" ps --status running -q backend)
if [ -n "$was_running" ]; then
 docker compose -f "$compose_file" stop backend
 trap 'docker compose -f "$compose_file" start backend >/dev/null' EXIT HUP INT TERM
fi
docker compose -f "$compose_file" exec -T mysql sh -ec 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root --database="$MYSQL_DATABASE"' < "$backup/database.sql"
docker compose -f "$compose_file" run --rm --no-deps -T --entrypoint sh backend -ec 'tar -C /app/uploads -xzf -' < "$backup/uploads.tar.gz"
echo 'SQL and attachments restored.'

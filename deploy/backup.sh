#!/bin/sh
set -eu
umask 077
cd "$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
compose_file=${COMPOSE_FILE:-docker-compose.ecs.yml}
destination=${BACKUP_DIR:-./deploy/backups}/$(date -u +%Y%m%dT%H%M%SZ)
mkdir -p "$destination"
# Stop application writers for a consistent SQL+attachment snapshot; always restart on exit.
was_running=$(docker compose -f "$compose_file" ps --status running -q backend)
if [ -n "$was_running" ]; then
 docker compose -f "$compose_file" stop backend
 trap 'docker compose -f "$compose_file" start backend >/dev/null' EXIT HUP INT TERM
fi
docker compose -f "$compose_file" exec -T mysql sh -ec 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump --user=root --single-transaction --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE"' > "$destination/database.sql"
docker compose -f "$compose_file" run --rm --no-deps -T --entrypoint sh backend -ec 'tar -C /app/uploads -czf - .' > "$destination/uploads.tar.gz"
(cd "$destination" && sha256sum database.sql uploads.tar.gz > SHA256SUMS)
printf '%s\n' "$compose_file" > "$destination/compose-file.txt"
printf 'Backup saved: %s\n' "$destination"

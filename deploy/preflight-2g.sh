#!/bin/sh
# 只读部署前检查；不打印渲染后的配置或任何凭证。
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
for file in .env docker-compose.yml deploy/compose-2g.yml deploy/compose-runtime.yml deploy/runtime/backend/app.jar deploy/runtime/frontend/dist/index.html; do
 [ -s "$file" ] || { echo "Missing required file: $file" >&2; exit 1; }
done
command -v docker >/dev/null || { echo 'Docker is not installed.' >&2; exit 1; }
docker info --format '{{.ServerVersion}}' >/dev/null
docker compose -f docker-compose.yml -f deploy/compose-2g.yml -f deploy/compose-runtime.yml config --quiet
total=$(awk '/^MemTotal:/{print $2}' /proc/meminfo)
available=$(awk '/^MemAvailable:/{print $2}' /proc/meminfo)
[ "$total" -ge 1700000 ] || { echo 'This deployment expects approximately 2GB RAM.' >&2; exit 1; }
disk=$(df -Pk . | awk 'NR==2{print $4}')
[ "$disk" -ge 1572864 ] || { echo 'Keep at least 1.5GB free disk for images and application updates.' >&2; exit 1; }
if [ "$available" -lt 300000 ]; then echo 'Warning: less than 300MB RAM currently available; inspect running services before updating.' >&2; fi
printf 'Preflight OK. RAM total=%sKB available=%sKB disk-free=%sKB
' "$total" "$available" "$disk"
echo 'Four containers: MySQL 512MiB, Redis 160MiB, backend 768MiB, frontend 48MiB.'
echo 'Build Java and Vue locally. Run backup.sh before updating an existing deployment.'

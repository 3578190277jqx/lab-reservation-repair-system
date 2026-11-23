#!/usr/bin/env bash
set -Eeuo pipefail

# Validate BEFORE the official entrypoint creates its data directory. A typo must
# not leave a half-initialized volume which skips init scripts on the next run.
hash_pattern='^\$2[aby]\$(0[4-9]|[12][0-9]|3[01])\$[./A-Za-z0-9]{53}$'
if [[ ! "${INITIAL_ADMIN_PASSWORD_HASH:-}" =~ $hash_pattern ]]; then
  printf '%s\n' 'INITIAL_ADMIN_PASSWORD_HASH must be a valid BCrypt hash. Generate it with PasswordHashTool.' >&2
  exit 1
fi

exec /usr/local/bin/docker-entrypoint.sh "$@"

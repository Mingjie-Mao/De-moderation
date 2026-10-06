#!/bin/sh
# Refuse a syntactically valid but empty PostgreSQL custom dump before it is
# uploaded off-site or mistaken for the newest usable backup.
set -eu

if [ "$#" -ne 1 ] || [ ! -f "$1" ]; then
  echo "Usage: scripts/verify-backup.sh /path/to/campusguard-db.dump" >&2
  exit 2
fi

DUMP=$(CDPATH= cd -- "$(dirname -- "$1")" && pwd)/$(basename -- "$1")
if [ -z "${BACKUP_VALIDATOR_IMAGE:-}" ] && command -v pg_restore >/dev/null 2>&1; then
  TOC=$(pg_restore --list "$DUMP")
else
  TOC=$(docker run --rm -v "$DUMP:/backup.dump:ro" \
    "${BACKUP_VALIDATOR_IMAGE:-postgres:16.10-alpine}" pg_restore --list /backup.dump)
fi

for table in flyway_schema_history users posts comments reports moderation_cases audit_log moderation_rules; do
  if ! printf '%s\n' "$TOC" | grep -Eq "^[0-9]+; .* TABLE public $table "; then
    echo "Backup verification failed: missing table $table in the dump TOC." >&2
    exit 1
  fi
done

echo "Backup TOC contains all required application tables."

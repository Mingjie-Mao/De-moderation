#!/bin/sh
set -eu
umask 077

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BACKUP_DIR=${BACKUP_DIR:-"$ROOT_DIR/backups"}
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
mkdir -p "$BACKUP_DIR"

if [ -f "$ROOT_DIR/.env" ]; then
  set -a
  . "$ROOT_DIR/.env"
  set +a
fi

DB_FILE="$BACKUP_DIR/campusguard-db-$STAMP.dump"
MEDIA_FILE="$BACKUP_DIR/campusguard-media-$STAMP.tar.gz"

if ! docker compose -f "$ROOT_DIR/docker-compose.prod.yml" exec -T postgres \
  pg_dump -U "${DB_USER:-campusguard}" -d "${DB_NAME:-campusguard}" \
  --format=custom --compress=9 --no-owner --no-privileges > "$DB_FILE"; then
  mv "$DB_FILE" "$DB_FILE.invalid"
  echo "pg_dump failed; the incomplete file was kept as $DB_FILE.invalid." >&2
  exit 1
fi

if ! "$ROOT_DIR/scripts/verify-backup.sh" "$DB_FILE"; then
  mv "$DB_FILE" "$DB_FILE.invalid"
  echo "The failed dump was kept as $DB_FILE.invalid for diagnosis." >&2
  exit 1
fi

# --- media, only where this script can see it ----------------------------
#
# The tarball covers /data/media inside the backend container, which is the
# whole of the media only while MEDIA_BACKEND is FILESYSTEM. Under S3 the images
# are in a bucket this script has no credentials for, and /data/media is an empty
# mounted volume: tarring it would succeed, produce a 100-byte archive, and leave
# a file named campusguard-media-<stamp>.tar.gz next to the dump. That file is
# the failure this script exists to prevent — a backup that reports success and
# stores nothing — so it is not written at all, and the reason is said out loud.
#
# Held as positional parameters rather than in one space-separated string, so a
# backup directory with a space in its path stays one path.
set -- "$DB_FILE"
if [ "${MEDIA_BACKEND:-FILESYSTEM}" = "FILESYSTEM" ]; then
  docker compose -f "$ROOT_DIR/docker-compose.prod.yml" exec -T backend \
    tar -C /data -czf - media > "$MEDIA_FILE"
  set -- "$@" "$MEDIA_FILE"
else
  echo "MEDIA_BACKEND=${MEDIA_BACKEND}: media lives in a bucket and is NOT in this backup." >&2
fi

SUMS_FILE="$BACKUP_DIR/campusguard-$STAMP.sha256"
: > "$SUMS_FILE"
for file in "$@"; do
  name=$(basename "$file")
  if command -v sha256sum >/dev/null 2>&1; then
    (cd "$BACKUP_DIR" && sha256sum "$name") >> "$SUMS_FILE"
  else
    (cd "$BACKUP_DIR" && shasum -a 256 "$name") >> "$SUMS_FILE"
  fi
done
echo "Local backup written to $BACKUP_DIR."
echo "Optional manual restore check: scripts/restore-drill.sh $DB_FILE"

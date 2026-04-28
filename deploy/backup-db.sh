#!/bin/bash
# SQLite WAL-safe online backup for reservations DB
# Install cron: 0 3 * * * /usr/local/bin/backup-db.sh >> /var/log/reservations-backup.log 2>&1

set -euo pipefail

DB_PATH="/opt/reservations/data/reservations.db"
BACKUP_DIR="/opt/reservations/backups"
DATE=$(date +%Y%m%d-%H%M%S)
BACKUP_FILE="$BACKUP_DIR/reservations-$DATE.db"

mkdir -p "$BACKUP_DIR"

# sqlite3 .backup flushes WAL and creates a consistent snapshot — safe for WAL-mode DBs
sqlite3 "$DB_PATH" ".backup '$BACKUP_FILE'"

gzip "$BACKUP_FILE"

# Retain 30 days of daily backups
find "$BACKUP_DIR" -name "*.db.gz" -mtime +30 -delete

echo "[$(date -Is)] Backup OK: $BACKUP_FILE.gz ($(du -sh "$BACKUP_FILE.gz" | cut -f1))"

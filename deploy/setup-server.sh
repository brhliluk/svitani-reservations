#!/bin/bash
# Run once on fresh server as ubuntu user with sudo
# Usage: bash setup-server.sh

set -euo pipefail

echo "==> Creating directories"
sudo mkdir -p /opt/reservations/data
sudo mkdir -p /opt/reservations/backups
sudo chown -R ubuntu:ubuntu /opt/reservations

echo "==> Installing backup script"
sudo cp backup-db.sh /usr/local/bin/backup-db.sh
sudo chmod +x /usr/local/bin/backup-db.sh

echo "==> Installing systemd service"
sudo cp reservations.service /etc/systemd/system/reservations.service
sudo systemctl daemon-reload
sudo systemctl enable reservations

echo "==> Installing Caddyfile"
# Edit Caddyfile first with your domain, then:
sudo cp Caddyfile /etc/caddy/Caddyfile
sudo systemctl reload caddy

echo ""
echo "Next steps:"
echo "  1. Copy .env.production to /opt/reservations/.env and fill in secrets"
echo "  2. chmod 640 /opt/reservations/.env"
echo "  3. Copy reservations.jar to /opt/reservations/reservations.jar"
echo "  4. sudo systemctl start reservations"
echo "  5. Add cron: 0 3 * * * /usr/local/bin/backup-db.sh >> /var/log/reservations-backup.log 2>&1"
echo "  6. sudo journalctl -fu reservations   # watch logs"

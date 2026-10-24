#!/bin/bash
set -euxo pipefail

dnf update -y
dnf install -y docker rsync
systemctl enable --now docker

mkdir -p /usr/libexec/docker/cli-plugins
curl -fsSL -o /usr/libexec/docker/cli-plugins/docker-compose \
  https://github.com/docker/compose/releases/download/v2.32.4/docker-compose-linux-x86_64
chmod 755 /usr/libexec/docker/cli-plugins/docker-compose
docker compose version

usermod -aG docker ec2-user
mkdir -p /opt/vehicle-telemetry
chown ec2-user:ec2-user /opt/vehicle-telemetry

if ! swapon --show | grep -q /swapfile; then
  dd if=/dev/zero of=/swapfile bs=1M count=2048
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

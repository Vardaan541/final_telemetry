#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ROOT=$(cd "$SCRIPT_DIR/../.." && pwd)
REGION="${AWS_REGION:-ap-south-1}"
INSTANCE_TYPE="${INSTANCE_TYPE:-t3.small}"
SSH_CIDR="${SSH_CIDR:-0.0.0.0/0}"
KEY_NAME="${KEY_NAME:-vehicle-telemetry}"

if ! command -v aws >/dev/null 2>&1; then
  echo "AWS CLI is required. Install it and run aws configure." >&2
  exit 1
fi
if ! command -v terraform >/dev/null 2>&1; then
  echo "Terraform is required. Install it from https://developer.hashicorp.com/terraform/install" >&2
  exit 1
fi
if ! command -v rsync >/dev/null 2>&1; then
  echo "rsync is required." >&2
  exit 1
fi

aws sts get-caller-identity >/dev/null

if [[ -n "${PUBLIC_KEY:-}" ]]; then
  PUBLIC_KEY_PATH=$PUBLIC_KEY
elif [[ -f "$HOME/.ssh/id_ed25519.pub" ]]; then
  PUBLIC_KEY_PATH=$HOME/.ssh/id_ed25519.pub
elif [[ -f "$HOME/.ssh/id_rsa.pub" ]]; then
  PUBLIC_KEY_PATH=$HOME/.ssh/id_rsa.pub
else
  mkdir -p "$SCRIPT_DIR/.ssh"
  PUBLIC_KEY_PATH=$SCRIPT_DIR/.ssh/vehicle-telemetry.pub
  if [[ ! -f "$SCRIPT_DIR/.ssh/vehicle-telemetry" ]]; then
    ssh-keygen -t ed25519 -f "$SCRIPT_DIR/.ssh/vehicle-telemetry" -N "" -C "vehicle-telemetry"
  fi
fi

PRIVATE_KEY=${PUBLIC_KEY_PATH%.pub}
SSH_OPTS=(-o StrictHostKeyChecking=accept-new -o ConnectTimeout=8 -i "$PRIVATE_KEY")

cd "$SCRIPT_DIR"
terraform init -input=false
terraform apply -input=false -auto-approve \
  -var "aws_region=$REGION" \
  -var "instance_type=$INSTANCE_TYPE" \
  -var "key_name=$KEY_NAME" \
  -var "public_key_path=$PUBLIC_KEY_PATH" \
  -var "ssh_cidr=$SSH_CIDR"

HOST=$(terraform output -raw public_ip)
echo "Waiting for SSH on $HOST"
ready=0
for _ in $(seq 1 36); do
  if ssh "${SSH_OPTS[@]}" "ec2-user@$HOST" "echo ready" >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 5
done
if [[ $ready -ne 1 ]]; then
  echo "SSH did not become ready. Check the security group and key pair." >&2
  exit 1
fi

echo "Waiting for Docker to finish installing"
ssh "${SSH_OPTS[@]}" "ec2-user@$HOST" "cloud-init status --wait"

rsync -az --delete \
  --exclude '.git' \
  --exclude 'target' \
  --exclude '.idea' \
  --exclude '.env' \
  --exclude 'infra/ec2/.terraform' \
  --exclude 'infra/ec2/.ssh' \
  --exclude 'infra/ec2/*.tfstate' \
  --exclude 'infra/ec2/*.tfstate.*' \
  -e "ssh ${SSH_OPTS[*]}" \
  "$ROOT/" "ec2-user@$HOST:/opt/vehicle-telemetry/"

if ! ssh "${SSH_OPTS[@]}" "ec2-user@$HOST" "test -f /opt/vehicle-telemetry/.env"; then
  ENV_FILE=$(mktemp)
  chmod 600 "$ENV_FILE"
  cat > "$ENV_FILE" <<EOF
JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
APP_USER_PASSWORD=$(openssl rand -base64 18 | tr -d '\n' | tr '/+' 'Aa')
APP_ADMIN_PASSWORD=$(openssl rand -base64 18 | tr -d '\n' | tr '/+' 'Aa')
VEHICLE_SIMULATOR_ENABLED=true
VEHICLE_SIMULATOR_COUNT=0
VEHICLE_SIMULATOR_INTERVAL_MS=2000
EOF
  scp "${SSH_OPTS[@]}" "$ENV_FILE" "ec2-user@$HOST:/opt/vehicle-telemetry/.env"
  rm -f "$ENV_FILE"
  echo "Wrote a new /opt/vehicle-telemetry/.env on the instance."
  echo "Read the generated passwords with: ssh -i $PRIVATE_KEY ec2-user@$HOST cat /opt/vehicle-telemetry/.env"
fi

echo "Building and starting the live stack. The first build can take several minutes."
ssh "${SSH_OPTS[@]}" "ec2-user@$HOST" "cd /opt/vehicle-telemetry && sudo docker compose up -d --build"

echo "Dashboard: http://$HOST/"
echo "Alerts:    http://$HOST/api/alerts"

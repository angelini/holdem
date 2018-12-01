#!/usr/bin/env bash

set -eux

SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd)"

# shellcheck source=./config.sh
source "${SCRIPTS_DIR}/config.sh"

g_compute ssh "${SSH_NODE}" -- -T sudo systemctl stop holdem-nginx
g_compute ssh "${SSH_NODE}" -- -T sudo certonly -n --standalone --preferred-challenges=http
g_compute ssh "${SSH_NODE}" -- -T sudo systemctl start holdem-nginx

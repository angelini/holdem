#!/usr/bin/env bash

set -eu

SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd)"

# shellcheck source=./config.sh
source "${SCRIPTS_DIR}/config.sh"

g_compute ssh "${SSH_NODE}" -- -N -L 7001:127.0.0.1:7001 &

lein repl :connect 7001
kill 0

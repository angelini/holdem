#!/usr/bin/env bash

set -eu

SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd)"
PROJECT_DIR="$(cd "${SCRIPTS_DIR}/.."; pwd)"

SQL_PASS="$("${SCRIPTS_DIR}/decrypt.sh" sql-1-password)"

cat <<EOF > "${PROJECT_DIR}/holdem.env"
PORT=3000
NREPL_PORT=7001
DATABASE_URL=postgres://localhost:5432/holdem?user=root&password=${SQL_PASS}
EOF

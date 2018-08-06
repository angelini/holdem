#!/usr/bin/env bash

set -eux

SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd)"
USER_DIR="$(cd "${SCRIPTS_DIR}/../.."; pwd)"

if [[ $# -ne 1 ]]; then
  echo "usage: ${0} <versioned_dir>"
  exit 1
fi

DIR_WITH_SHA="${1}"

mkdir -p "${DIR_WITH_SHA}/log"
"${DIR_WITH_SHA}/scripts/setup_proxy.sh"
"${DIR_WITH_SHA}/scripts/generate_env_file.sh"

for service in "cloud-sql-proxy" "holdem-nginx" "holdem-web"; do
  sudo cp "${DIR_WITH_SHA}/resources/server/${service}.service" /lib/systemd/system/
done

sudo systemctl daemon-reload

rm -rf "${USER_DIR}/holdem"
ln -s "${DIR_WITH_SHA}" "${USER_DIR}/holdem"

for service in "cloud-sql-proxy" "holdem-nginx" "holdem-web"; do
  sudo systemctl restart "${service}.service"
  sudo systemctl enable "${service}.service"
done

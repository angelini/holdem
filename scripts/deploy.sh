#!/usr/bin/env bash

set -eux

SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd)"
PROJECT_DIR="$(cd "${SCRIPTS_DIR}/.."; pwd)"
TARGET="${PROJECT_DIR}/deploy_target"
DIR_WITH_SHA="holdem_$(git rev-parse HEAD)"

if [[ $# -ne 1 ]]; then
  echo "usage: ${0} <gce_instance>"
  exit 1
fi

ZONE="us-east1-b"
INSTANCE="${1}"

cd "${PROJECT_DIR}"

if [[ -d "${TARGET}" ]]; then
  rm -rf "${TARGET}"
fi
mkdir -p "${TARGET}"

lein uberjar
cp "${PROJECT_DIR}/target/uberjar/holdem.jar" "${TARGET}/"
cp -r "${PROJECT_DIR}/resources" "${TARGET}/"
cp -r "${SCRIPTS_DIR}" "${TARGET}/"

gcloud compute ssh --zone "${ZONE}" "${INSTANCE}" -- -T rm -rf "${DIR_WITH_SHA}"
gcloud compute scp --zone "${ZONE}" --recurse "${TARGET}" "${INSTANCE}:${DIR_WITH_SHA}"
gcloud compute ssh --zone "${ZONE}" "${INSTANCE}" -- -T <<EOF
  set -eux
  rm -f holdem
  ln -s "${DIR_WITH_SHA}" holdem
  mkdir -p ./holdem/log
  ./holdem/scripts/setup_proxy.sh
  ./holdem/scripts/generate_env_file.sh
  sudo cp ./holdem/resources/server/cloud-sql-proxy.service /lib/systemd/system/
  sudo cp ./holdem/resources/server/holdem-nginx.service /lib/systemd/system/
  sudo cp ./holdem/resources/server/holdem-web.service /lib/systemd/system/
  sudo systemctl daemon-reload
  sudo systemctl restart cloud-sql-proxy.service
  sudo systemctl restart holdem-nginx.service
  sudo systemctl restart holdem-web.service
EOF

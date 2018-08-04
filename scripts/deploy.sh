#!/usr/bin/env bash

set -eux

SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd)"
PROJECT_DIR="$(realpath "${SCRIPTS_DIR}/..")"
TARGET="${PROJECT_DIR}/deploy_target"
GIT_SHA="$(git rev-parse HEAD)"

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
cp -r "${PROJECT_DIR}/boot" "${TARGET}/"
cp -r "${PROJECT_DIR}/secrets" "${TARGET}/"
cp -r "${SCRIPTS_DIR}" "${TARGET}/"

gcloud compute ssh --zone "${ZONE}" "${INSTANCE}" -- rm -rf "holdem_${GIT_SHA}"
gcloud compute scp --zone "${ZONE}" --recurse "${TARGET}" "${INSTANCE}:holdem_${GIT_SHA}"
gcloud compute ssh --zone "${ZONE}" "${INSTANCE}" -- -T <<EOF
  rm -f holdem
  ln -s "holdem_${GIT_SHA}" holdem
  ./holdem/scripts/setup_proxy.sh
  ./holdem/scripts/generate_env_file.sh
EOF

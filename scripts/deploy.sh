#!/usr/bin/env bash

set -eux

SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd)"
PROJECT_DIR="$(cd "${SCRIPTS_DIR}/.."; pwd)"
TARGET="${PROJECT_DIR}/deploy_target"
DIR_WITH_SHA="holdem_$(git rev-parse HEAD)"

# shellcheck source=./config.sh
source "${SCRIPTS_DIR}/config.sh"

cd "${PROJECT_DIR}"

if [[ -d "${TARGET}" ]]; then
  rm -rf "${TARGET}"
fi
mkdir -p "${TARGET}"

lein uberjar
cp "${PROJECT_DIR}/target/uberjar/holdem.jar" "${TARGET}/"
cp -r "${PROJECT_DIR}/resources" "${TARGET}/"
cp -r "${SCRIPTS_DIR}" "${TARGET}/"

g_compute ssh "${SSH_NODE}" -- -T rm -rf "${DIR_WITH_SHA}"
g_compute scp --recurse "${TARGET}" "${SSH_NODE}:${DIR_WITH_SHA}"
g_compute ssh "${SSH_NODE}" -- -T \
          "exec /bin/bash -l ${DIR_WITH_SHA}/scripts/swap_version.sh ${DIR_WITH_SHA}"

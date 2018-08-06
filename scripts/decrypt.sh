#!/usr/bin/env bash

set -eu

SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd)"
SECRETS_DIR="$(cd "${SCRIPTS_DIR}/../resources/secrets"; pwd)"

if [[ $# -ne 1 ]]; then
  echo "usage: ${0} <name>"
  exit 1
fi

NAME="${1}"
TMP_FILE="_tmp.txt"

function remove_tmp {
  rm -f "${TMP_FILE}"
}
trap remove_tmp EXIT

gcloud kms decrypt \
       --location=global \
       --keyring=key-1 \
       --key=passwords \
       --plaintext-file="${TMP_FILE}" \
       --ciphertext-file="${SECRETS_DIR}/${NAME}.enc"

cat "${TMP_FILE}"

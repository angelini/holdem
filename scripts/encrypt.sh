#!/usr/bin/env bash

set -eu

SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd)"
SECRETS_DIR="$(realpath "${SCRIPTS_DIR}/../secrets")"

if [[ $# -ne 2 ]]; then
  echo "usage: ${0} <name> <data>"
  exit 1
fi

NAME="${1}"
DATA="${2}"
TMP_FILE="_tmp.txt"

echo "${DATA}" > "${TMP_FILE}"

function remove_tmp {
  rm "${TMP_FILE}"
}
trap remove_tmp EXIT

gcloud kms encrypt \
    --location=global  \
    --keyring=key-1 \
    --key=passwords \
    --plaintext-file="${TMP_FILE}" \
    --ciphertext-file="${SECRETS_DIR}/${NAME}.enc"

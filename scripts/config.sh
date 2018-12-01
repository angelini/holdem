#!/usr/bin/env bash

set -eu

export GCE_USER="alex"
export GCE_ZONE="us-east1-b"
export GCE_NODE="web-1"
export GCE_PROJECT="holdem-211616"

export SSH_NODE="${GCE_USER}@${GCE_NODE}"

function g_compute() {
  local command="${1}"
  shift
  gcloud compute --project "${GCE_PROJECT}" \
         "${command}" --zone "${GCE_ZONE}" "$@"
}

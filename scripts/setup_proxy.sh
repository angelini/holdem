#!/usr/bin/env bash

set -eux

cd "${HOME}"

if [[ -f "${HOME}/cloud_sql_proxy" ]]; then
  wget https://dl.google.com/cloudsql/cloud_sql_proxy.linux.amd64 -O cloud_sql_proxy
  chmod +x cloud_sql_proxy
fi

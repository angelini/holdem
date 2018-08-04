#!/bin/bash

gcloud compute ssh web-1 -- -N -L 7001:127.0.0.1:7001 -vvv &
lein repl :connect 7001

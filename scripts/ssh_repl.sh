#!/bin/bash

gcloud compute ssh --zone=us-east1-b web-1 -- -N -L 7001:127.0.0.1:7001 &
lein repl :connect 7001

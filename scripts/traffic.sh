#!/bin/sh
# Runs the traffic generator from your machine against locally exposed services.
# The real script lives in k8s/base/files/ so Kubernetes can mount it from a ConfigMap.
exec sh "$(dirname "$0")/../k8s/base/files/traffic.sh" "$@"

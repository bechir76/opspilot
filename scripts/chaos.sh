#!/bin/sh
# Injects faults into a running service through its /chaos API.
set -eu

usage() {
    cat <<USAGE
Usage: $0 <order|inventory> <fault> [value]

Faults:
  latency <ms>            add <ms> of latency to every business request (0 clears)
  errors <rate>           fail a fraction (0.0-1.0) of business requests with HTTP 500
  memory-leak <mb/s>      leak <mb/s> megabytes per second until reset or the JVM dies
  cpu <seconds>           burn all CPU cores for <seconds> (max 600)
  crash                   exit the process after 500 ms
  reset                   clear all faults and free leaked memory
  status                  show active faults

Targets default to http://localhost:8081 (order) and http://localhost:8082 (inventory);
override with ORDER_URL / INVENTORY_URL.

Examples:
  $0 inventory latency 3000
  $0 order errors 0.5
  $0 inventory reset
USAGE
    exit 1
}

[ $# -ge 2 ] || usage

case "$1" in
    order) base="${ORDER_URL:-http://localhost:8081}" ;;
    inventory) base="${INVENTORY_URL:-http://localhost:8082}" ;;
    *) usage ;;
esac

fault="$2"
value="${3:-}"

require_value() {
    [ -n "$value" ] || { echo "error: '$fault' needs a value" >&2; usage; }
}

case "$fault" in
    latency) require_value; method=POST; path="/chaos/latency?ms=$value" ;;
    errors) require_value; method=POST; path="/chaos/errors?rate=$value" ;;
    memory-leak) require_value; method=POST; path="/chaos/memory-leak?mbPerSecond=$value" ;;
    cpu) require_value; method=POST; path="/chaos/cpu?seconds=$value" ;;
    crash) method=POST; path="/chaos/crash" ;;
    reset) method=POST; path="/chaos/reset" ;;
    status) method=GET; path="/chaos" ;;
    *) usage ;;
esac

curl -sS --fail-with-body -X "$method" "$base$path"
echo

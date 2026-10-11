#!/bin/sh
# Background traffic for the OpsPilot demo shop: places orders with varying SKUs and quantities,
# plus some read traffic, forever. Gives metrics and logs a realistic baseline to break.
#
# Config (env): ORDER_URL, INVENTORY_URL, INTERVAL (seconds between iterations, may be fractional).
set -u

ORDER_URL="${ORDER_URL:-http://localhost:8081}"
INVENTORY_URL="${INVENTORY_URL:-http://localhost:8082}"
INTERVAL="${INTERVAL:-1}"
SKUS="KB-001 MS-001 MN-001 HS-001 DK-001 WC-001"
SKU_COUNT=6

trap 'echo "traffic: stopping"; exit 0' INT TERM

# Random integer in [0, $1). POSIX sh has no $RANDOM, so read from /dev/urandom.
rand() {
    n=$(od -An -N2 -tu2 /dev/urandom | tr -d ' ')
    echo $((n % $1))
}

# Prints the HTTP status, or 000 if the request failed (connection refused, timeout, ...).
call() {
    curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$@" || true
}

log() {
    echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) $*"
}

log "traffic: orders=$ORDER_URL inventory=$INVENTORY_URL interval=${INTERVAL}s"
i=0
while true; do
    i=$((i + 1))

    # 2% of orders use an unknown SKU (realistic 409s); quantities are mostly small.
    if [ "$(rand 50)" -eq 0 ]; then
        sku="XX-404"
    else
        sku=$(echo "$SKUS" | cut -d' ' -f$(( $(rand $SKU_COUNT) + 1 )))
    fi
    if [ "$(rand 10)" -eq 0 ]; then qty=$(( $(rand 10) + 1 )); else qty=$(( $(rand 3) + 1 )); fi

    status=$(call -X POST -H 'Content-Type: application/json' \
        -d "{\"sku\":\"$sku\",\"quantity\":$qty}" "$ORDER_URL/api/orders")
    log "POST /api/orders sku=$sku quantity=$qty -> $status"

    if [ $((i % 5)) -eq 0 ]; then
        log "GET /api/products -> $(call "$INVENTORY_URL/api/products")"
    fi
    if [ $((i % 10)) -eq 0 ]; then
        log "GET /api/orders -> $(call "$ORDER_URL/api/orders?limit=20")"
    fi

    sleep "$INTERVAL"
done

#!/usr/bin/env bash
# Shows why the transactional outbox matters: the broker is down while a payment is accepted.
# Defaults to docker compose; override STOP_BROKER / START_BROKER for other setups.
set -euo pipefail

INGEST=${INGEST_URL:-http://localhost:8081}
PROCESSOR=${PROCESSOR_URL:-http://localhost:8082}
STOP_BROKER=${STOP_BROKER:-docker compose -f deploy/docker-compose.yml stop rabbitmq}
START_BROKER=${START_BROKER:-docker compose -f deploy/docker-compose.yml start rabbitmq}
RUN=$(date +%s)
DEBTOR="CH5604835012345678009"

step() { printf '\n== %s\n' "$*"; }

wait_for() {
  for _ in $(seq 1 120); do if eval "$2" >/dev/null 2>&1; then return 0; fi; sleep 0.5; done
  echo "timeout waiting for: $1" >&2; exit 1
}

step "Fund a fresh account"
curl -sS -X POST "$PROCESSOR/api/v1/accounts" -H 'Content-Type: application/json' \
  -d "{\"iban\":\"$DEBTOR\",\"currency\":\"CHF\",\"balance\":100.00}" | jq -c '{iban, balance}' || true

step "Stop the message broker"
eval "$STOP_BROKER" >/dev/null
sleep 3

step "Submit a payment while the broker is DOWN: it is accepted and stored (HTTP 202)"
ID=$(curl -sS -X POST "$INGEST/api/v1/payments" -H 'Content-Type: application/json' -H "Idempotency-Key: outage-$RUN" \
  -d "{\"debtorIban\":\"$DEBTOR\",\"creditorIban\":\"DE89370400440532013000\",\"amount\":25.00,\"currency\":\"CHF\"}" | tee /dev/stderr | jq -r .paymentId)
echo
sleep 3
echo "status while broker is down: $(curl -sS "$INGEST/api/v1/payments/$ID" | jq -r .status)  (event waits in the outbox table)"

step "Start the broker again"
eval "$START_BROKER" >/dev/null
wait_for "payment booked" "[ \"\$(curl -s $INGEST/api/v1/payments/$ID | jq -r .status)\" = BOOKED ]"
echo "status after recovery: $(curl -sS "$INGEST/api/v1/payments/$ID" | jq -r .status)  (nothing lost, nothing duplicated)"
curl -sS "$PROCESSOR/api/v1/accounts/$DEBTOR" | jq -c '{iban, balance}'

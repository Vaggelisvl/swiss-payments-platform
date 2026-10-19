#!/usr/bin/env bash
# End-to-end walkthrough against running services (docker compose up, or the three jars on 8081-8083).
set -euo pipefail

INGEST=${INGEST_URL:-http://localhost:8081}
PROCESSOR=${PROCESSOR_URL:-http://localhost:8082}
RECON=${RECON_URL:-http://localhost:8083}
RUN=$(date +%s)

DEBTOR="CH9300762011623852957"          # internal account, funded below
QR_IBAN="CH4431999123000889012"         # creditor with a QR-IBAN: needs a QR reference
QRR="210000000003139471430009017"
EXTERNAL="DE89370400440532013000"

step() { if [ -t 1 ]; then printf '\n\033[1m== %s\033[0m\n' "$*"; else printf '\n== %s\n' "$*"; fi; }
post() { curl -sS -X POST "$1" -H 'Content-Type: application/json' "${@:2}"; }

# Valid QR reference for a second invoice (modulo 10 recursive check digit)
qr_reference() {
  python3 - "$1" <<'PY'
import sys
t = [0, 9, 4, 6, 8, 2, 7, 1, 3, 5]
digits = sys.argv[1].zfill(26)
c = 0
for ch in digits:
    c = t[(c + int(ch)) % 10]
print(digits + str((10 - c) % 10))
PY
}

wait_for() { # wait_for <description> <command that succeeds when ready>
  for _ in $(seq 1 40); do if eval "$2" >/dev/null 2>&1; then return 0; fi; sleep 0.5; done
  echo "timeout waiting for: $1" >&2; exit 1
}

step "1. Fund the debtor account at the processor (5000.00 CHF)"
post "$PROCESSOR/api/v1/accounts" -d "{\"iban\":\"$DEBTOR\",\"currency\":\"CHF\",\"balance\":5000.00}" | jq -c .

step "2. Register an invoice at reconciliation (1949.75 CHF, QR reference $QRR)"
INVOICE=$(post "$RECON/api/v1/invoices" -d "{\"referenceType\":\"QRR\",\"reference\":\"$QRR\",\"amount\":1949.75,\"currency\":\"CHF\"}")
echo "$INVOICE" | jq -c '{id, reference, amount, status}'
INVOICE_ID=$(echo "$INVOICE" | jq -r .id)

step "3. Submit the payment with an Idempotency-Key (HTTP 202, accepted asynchronously)"
KEY="demo-$RUN"
BODY="{\"debtorIban\":\"$DEBTOR\",\"creditorIban\":\"$QR_IBAN\",\"amount\":1949.75,\"currency\":\"CHF\",\"reference\":{\"type\":\"QRR\",\"value\":\"$QRR\"},\"remittanceInformation\":\"Invoice 10978\"}"
RESPONSE=$(post "$INGEST/api/v1/payments" -H "Idempotency-Key: $KEY" -d "$BODY" -w '\nHTTP %{http_code}')
echo "$RESPONSE" | head -n1 | jq -c '{paymentId, status}'; echo "$RESPONSE" | tail -n1
PAYMENT_ID=$(echo "$RESPONSE" | head -n1 | jq -r .paymentId)

step "4. Retry with the same key (network timeout simulation): HTTP 200, same payment, nothing duplicated"
curl -sS -X POST "$INGEST/api/v1/payments" -H 'Content-Type: application/json' -H "Idempotency-Key: $KEY" -d "$BODY" \
  -D - -o /tmp/replay.json | grep -iE '^(HTTP|idempotent-replayed)'
jq -c '{paymentId, status}' /tmp/replay.json

step "5. The processor books it; the ingest read model follows through events"
wait_for "payment booked" "[ \"\$(curl -s $INGEST/api/v1/payments/$PAYMENT_ID | jq -r .status)\" = BOOKED ]"
curl -sS "$INGEST/api/v1/payments/$PAYMENT_ID" | jq -c '{paymentId, status, amount, currency}'
curl -sS "$PROCESSOR/api/v1/accounts/$DEBTOR" | jq -c '{iban, balance}'

step "6. Reconciliation matched the credit by QR reference: invoice is PAID"
wait_for "invoice paid" "[ \"\$(curl -s $RECON/api/v1/invoices/$INVOICE_ID | jq -r .status)\" = PAID ]"
curl -sS "$RECON/api/v1/invoices/$INVOICE_ID" | jq -c '{status, amount, paidAmount}'

step "7. A payment the debtor cannot afford is rejected with a stable reason code"
REJECTED=$(post "$INGEST/api/v1/payments" -H "Idempotency-Key: big-$RUN" \
  -d "{\"debtorIban\":\"$DEBTOR\",\"creditorIban\":\"$EXTERNAL\",\"amount\":9000.00,\"currency\":\"CHF\",\"remittanceInformation\":\"too much\"}" | jq -r .paymentId)
wait_for "payment rejected" "[ \"\$(curl -s $INGEST/api/v1/payments/$REJECTED | jq -r .status)\" = REJECTED ]"
curl -sS "$INGEST/api/v1/payments/$REJECTED" | jq -c '{status, rejectionCode, rejectionReason}'

step "8. Business rules and validation: invalid input never enters the system (HTTP 422, RFC 9457 problem details)"
post "$INGEST/api/v1/payments" -H "Idempotency-Key: bad-$RUN" -d "${BODY/$QRR/210000000003139471430009018}" | jq -c '{title, status, detail}'

step "9. ISO 20022 pain.001 file with two transfers (second one is in EUR from a CHF account)"
sed "s/MSG-2026-0001/MSG-$RUN/" services/ingest-service/src/test/resources/pain001-sample.xml \
  | curl -sS -X POST "$INGEST/api/v1/payments/pain001" -H 'Content-Type: application/xml' --data-binary @- | jq -c '.[] | {paymentId, amount, currency}' | tee /tmp/pain001-ids.json
for id in $(jq -r .paymentId /tmp/pain001-ids.json); do
  wait_for "payment $id final" "[ \"\$(curl -s $INGEST/api/v1/payments/$id | jq -r .status)\" != RECEIVED ]"
  curl -sS "$INGEST/api/v1/payments/$id" | jq -c '{status, rejectionCode}'
done

step "10. The first pain.001 transfer paid the same invoice again: reconciliation flags it for review"
wait_for "overpayment flagged" "curl -s $RECON/api/v1/reconciliation/review | jq -e 'map(select(.result==\"OVERPAID\")) | length > 0'"
curl -sS "$RECON/api/v1/reconciliation/review" | jq -c '.[] | {entryId, reference, amount, result}'

step "11. Bank statement import (camt.054): a second invoice is settled from the file; re-importing changes nothing"
REF2=$(qr_reference "$RUN")
post "$RECON/api/v1/invoices" -d "{\"referenceType\":\"QRR\",\"reference\":\"$REF2\",\"amount\":250.00,\"currency\":\"CHF\"}" | jq -c '{reference, status}'
CAMT=$(cat <<XML
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.054.001.04"><BkToCstmrDbtCdtNtfctn><Ntfctn><Id>NTF-$RUN</Id>
<Ntry><Amt Ccy="CHF">250.00</Amt><CdtDbtInd>CRDT</CdtDbtInd><BookgDt><Dt>2026-10-19</Dt></BookgDt><AcctSvcrRef>ASR-$RUN</AcctSvcrRef>
<NtryDtls><TxDtls><RmtInf><Strd><CdtrRefInf><Ref>$REF2</Ref></CdtrRefInf></Strd></RmtInf></TxDtls></NtryDtls></Ntry>
</Ntfctn></BkToCstmrDbtCdtNtfctn></Document>
XML
)
for attempt in first second; do
  printf '%s import: ' "$attempt"
  echo "$CAMT" | curl -sS -X POST "$RECON/api/v1/camt054" -H 'Content-Type: application/xml' --data-binary @- | jq -c .
done

step "12. Metrics (Prometheus format)"
for url in "$INGEST" "$PROCESSOR" "$RECON"; do
  curl -sS "$url/actuator/prometheus" | grep -E '^(payments_received_total|payments_duplicates_total|payments_booked_total|payments_rejected_total|reconciliation_credits_total|outbox_published_total)\{' | sed -E 's/application="[^"]*",?//; s/\{,/{/; s/\{\}//'
done

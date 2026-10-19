# ADR 0002: Transactional outbox with at-least-once delivery

**Status:** accepted

## Problem
"Save to the database, then publish to the broker" cannot be atomic. A crash or broker outage between the two steps either loses the event or publishes an event for a state that was rolled back.

## Decision
Services append events to an `outbox` table in the same transaction as the state change (`OutboxStore` requires an active transaction). A scheduled `OutboxRelay` claims unpublished rows with `FOR UPDATE SKIP LOCKED`, publishes them with publisher confirms and marks them published. Messages the broker cannot route (no queue bound yet) count as failures and are retried.

Delivery is **at-least-once**: a crash between the broker confirm and the commit republishes the row. Therefore every consumer is idempotent (processed-payment table, credit entry ids, status transitions that ignore replays).

## Alternatives considered
- Change data capture (Debezium): lower latency, more infrastructure. Polling every 500 ms is enough here and easy to reason about.
- Exactly-once messaging: not available end to end; idempotent consumers are the practical answer.

## Consequences
Events are delayed by up to the polling interval. The outbox table needs housekeeping in production (not implemented). Ordering is preserved per relay batch but not guaranteed across services, so consumers must not depend on cross-queue ordering.

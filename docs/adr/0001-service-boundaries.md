# ADR 0001: Three services, one database each

**Status:** accepted

## Context
Accepting an instruction, processing it and reconciling money received change for different reasons, scale differently and fail differently (a slow bank file import must not slow down payment intake).

## Decision
`ingest`, `processor` and `reconciliation` are separate deployables with their own PostgreSQL database and Flyway history. They only communicate through events defined in `payments-contracts`; there is no shared table and no synchronous call between them. The ingest service keeps its own read model of the payment status, updated from outcome events.

## Consequences
Eventual consistency: a payment is `RECEIVED` for a short moment before it becomes `BOOKED` or `REJECTED`, which the API makes explicit (`202 Accepted`). Each service can be scaled or redeployed independently. The price is more moving parts than a modular monolith, which would be the better choice for a team of one in production; here the split is the point of the exercise.

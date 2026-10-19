# ADR 0003: Pessimistic row lock for debits, atomic update for credits

**Status:** accepted

## Context
Payments for the same debtor are processed concurrently (several consumers, redeliveries). A read-check-write on the balance would allow overspending (lost update).

## Decision
The processor locks the debtor account row (`SELECT ... FOR UPDATE`) at the start of a payment's transaction. Concurrent payments of one debtor are serialized; different debtors do not block each other. The duplicate check runs after the lock is held, so concurrent redeliveries of one message are skipped cleanly. Credits to the creditor use `UPDATE account SET balance = balance + ?`, which needs no read and cannot deadlock with debits. A database `CHECK (balance >= 0)` is the last line of defence. The reconciliation service locks the invoice row for the same reason.

## Alternatives considered
Optimistic locking (`@Version`) with retries: fine at low contention but produces retry storms for a hot account. For payments, correctness and predictability win over throughput, and the lock is held only for one short transaction.

## Evidence
`ProcessingIT.concurrentPaymentsCanNeverOverspendAnAccount`: 40 parallel payments of 10 CHF against 100 CHF result in exactly 10 booked, 30 rejected and a final balance of 0.

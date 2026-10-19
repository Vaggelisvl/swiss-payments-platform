# ADR 0005: Integration tests against real infrastructure

**Status:** accepted

## Decision
Business logic (rules, value objects, parsers, invoice matching) has fast unit tests. Everything involving transactions, locks, constraints or messaging is tested in `*IT` classes against **real PostgreSQL and RabbitMQ**, never against mocks or H2, because the interesting failures (unique-constraint races, `SKIP LOCKED`, unroutable messages, dead-lettering) only exist there. They run with `mvn verify -Pit`. In CI the infrastructure comes from GitHub Actions service containers; locally from `docker compose` or locally installed servers.

Testcontainers would remove the manual setup and is the natural next step; service containers were chosen first because they run unchanged in environments without a Docker socket.

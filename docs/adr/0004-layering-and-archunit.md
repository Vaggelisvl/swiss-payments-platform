# ADR 0004: Pragmatic layering, enforced by ArchUnit

**Status:** accepted

## Decision
Each service has `domain` (pure rules), `application` (use cases, transactions), `adapter.in` (REST, messaging) and `adapter.out` (persistence), plus `config`. The shared value objects live in `payments-domain` and have no framework dependency.

Pure hexagonal purism (separate domain model and JPA entities with mappers) is **not** used: JPA entities live in `adapter.out.persistence` and application services use them directly. That trades some purity for much less mapping code. The rules that matter are enforced by `ArchitectureTest` in every service:

1. `domain` depends on no other layer and on no framework (Spring, JPA, RabbitMQ).
2. `application` does not depend on inbound adapters.
3. Persistence does not depend on `application` or the web layer.

The tests were verified by adding a deliberately violating class: the build fails as expected.

---
name: CDI Observer Wording Fix
overview: "The architect's review is correct: WebhookFanoutProcessor must be described as observing the same CDI SqlOutboxEvent as SqlEventsProcessor, not reading the outbox table. The plan has been updated accordingly; architecture is unchanged."
todos:
  - id: wording-round4
    content: "Plan updated: dual-observer CDI architecture, diagrams, §2.11 round 4 resolutions"
    status: pending
  - id: impl-fanout
    content: Implement WebhookFanoutProcessor as @TransactionalEventListener(AFTER_SUCCESS) on SqlOutboxEvent — not outbox table reader
    status: pending
isProject: false
---

# CDI Observer Wording — Verification and Plan Updates

## Verdict: Architect is correct

The review is a **wording/mental-model correction**, not an architecture change. The implementation design was already CDI-based (`@TransactionalEventListener(AFTER_SUCCESS)` on `SqlOutboxEvent`), but diagrams and some phrases implied the webhook path reads or depends on the `outbox` table.

### What the codebase actually does today

```mermaid
flowchart LR
    Repo[SqlVersionRepository] -->|Event.fire| CDI[SqlOutboxEvent]
    CDI -->|@Observes sync| SqlEventsProc[SqlEventsProcessor]
    CDI -->|AFTER_SUCCESS planned| FanoutProc[WebhookFanoutProcessor]
    SqlEventsProc -->|INSERT+DELETE| Outbox[(outbox)]
    Outbox --> Debezium[Debezium CDC]
    FanoutProc --> WebhookTables[(webhook_fanout / webhook_deliveries)]
```




| Component                  | File                                                                                                                          | Role                                                                                                                                                                                      |
| -------------------------- | ----------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Event source               | `[SqlVersionRepository.java](app/src/main/java/io/apicurio/registry/storage/impl/sql/repositories/SqlVersionRepository.java)` | `outboxEvent.fire(SqlOutboxEvent.of(...))` during storage write                                                                                                                           |
| Debezium observer          | `[SqlEventsProcessor.java](app/src/main/java/io/apicurio/registry/storage/impl/sql/SqlEventsProcessor.java)`                  | `@Observes SqlOutboxEvent` → `[SqlEventRepository.createEvent()](app/src/main/java/io/apicurio/registry/storage/impl/sql/repositories/SqlEventRepository.java)` INSERT+DELETE on `outbox` |
| Webhook observer (planned) | `WebhookFanoutProcessor`                                                                                                      | `@TransactionalEventListener(AFTER_SUCCESS)` on **same** `SqlOutboxEvent`; payload from in-memory `OutboxEvent`, persisted to `webhook_fanout.sourcePayload`                              |


**The outbox table is ephemeral** (INSERT+DELETE in same TX). Webhooks cannot read it even if they wanted to — by commit time the row is gone.

---

## Critique of the old plan wording


| Issue                                                           | Why it mattered                                                           |
| --------------------------------------------------------------- | ------------------------------------------------------------------------- |
| `GroupsAPI --> Outbox` in architecture diagram                  | Implied webhooks/API write to or read from outbox                         |
| Sequence: `Storage->>OutboxRepo: fire ARTIFACT_VERSION_CREATED` | Conflated CDI fire with SqlEventRepository; hid the dual-observer pattern |
| "Integration point for outbox events"                           | Sounded like webhooks integrate with the outbox table                     |
| "Fanout reads CDI event payload"                                | Close, but "observes the same CDI event" is maintainer-aligned language   |


**What was already correct:** `AFTER_SUCCESS` separation, `webhook_fanout` as durable source, reconciler not reading `outbox`, no changes to `SqlEventRepository`.

---

## Rationale for architect's preferred wording

1. **Matches maintainer docs** — `[assembly-registry-events.adoc](docs/modules/ROOT/pages/getting-started/assembly-registry-events.adoc)` documents outbox as the Debezium CDC delivery mechanism.
2. **Prevents implementation mistakes** — Engineers might otherwise poll `outbox` or join it in the reconciler (already flagged as invalid in round 2).
3. **Clarifies independence** — Webhooks are a parallel observer; disabling webhooks does not affect Debezium; disabling Debezium does not affect webhooks.
4. **Accurate data flow** — Payload source is the CDI `OutboxEvent` object in memory at `AFTER_SUCCESS`, snapshotted to `webhook_fanout.sourcePayload`.

---

## Changes applied to the plan

Updated `[.cursor/plans/cloudevents_webhook_notifications_a29b4a22.plan.md](.cursor/plans/cloudevents_webhook_notifications_a29b4a22.plan.md)`:

- Added **§2.4 Dual-observer CDI architecture** with diagram and observer comparison table
- Added **§2.11 Architect Review — Round 4 Resolutions**
- Fixed **§2.1 system architecture** diagram: CDI bus, `SqlEventsProcessor`, removed `GroupsAPI --> Outbox`
- Fixed **§2.1.1 production dry-run** sequence diagram and step table
- Fixed **§2.4 delivery flow** sequence diagram
- Updated **§2.8 component map** integration point wording
- Updated **§3.6 Relationship to Existing Outbox**
- Updated traceability matrix subtask 4 and Phase 3 todo

**Architecture unchanged:** same subscription API, `WebhookFanoutProcessor`, `webhook_fanout` / `webhook_deliveries` tables, delivery worker, reconciler.

---

## Implementation guidance (when coding)

```java
// WebhookFanoutProcessor — correct pattern
@ApplicationScoped
public class WebhookFanoutProcessor {

    @TransactionalEventListener(phase = AFTER_SUCCESS)
    void onOutboxEvent(@Observes SqlOutboxEvent event) {
        fanoutExecutor.execute(() -> processFanout(event.getOutboxEvent()));
    }
}
```

Do **not** query `SELECT * FROM outbox` or hook into `SqlEventRepository.createEvent()`.
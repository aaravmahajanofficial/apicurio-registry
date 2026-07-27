package io.apicurio.registry.storage.impl.sql;

/**
 * PostgreSQL implementation of the sql statements interface. Provides sql statements that are specific to
 * PostgreSQL, where applicable.
 */
public class PostgreSQLSqlStatements extends CommonSqlStatements {

    /**
     * Constructor.
     */
    public PostgreSQLSqlStatements() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#dbType()
     */
    @Override
    public String dbType() {
        return "postgresql";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#isPrimaryKeyViolation(java.lang.Exception)
     */
    @Override
    public boolean isPrimaryKeyViolation(Exception error) {
        return error.getMessage().contains("violates unique constraint");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#isForeignKeyViolation(java.lang.Exception)
     */
    @Override
    public boolean isForeignKeyViolation(Exception error) {
        return error.getMessage().contains("violates foreign key constraint");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#getNextSequenceValue()
     */
    @Override
    public String getNextSequenceValue() {
        return "INSERT INTO sequences (seqName, seqValue) VALUES (?, 1) ON CONFLICT (seqName) DO UPDATE SET seqValue = sequences.seqValue + 1 RETURNING seqValue";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#resetSequenceValue()
     */
    @Override
    public String resetSequenceValue() {
        return "INSERT INTO sequences (seqName, seqValue) VALUES (?, ?) ON CONFLICT (seqName) DO UPDATE SET seqValue = ?";
    }

    @Override
    public String upsertBranch() {
        return """
                INSERT INTO branches (groupId, artifactId, branchId, description, systemDefined, owner, createdOn, modifiedBy, modifiedOn)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (groupId, artifactId, branchId) DO NOTHING
                """;
    }

    @Override
    public String createDataSnapshot() {
        throw new IllegalStateException("Snapshot creation is not supported for Postgresql storage");
    }

    @Override
    public String restoreFromSnapshot() {
        throw new IllegalStateException("Restoring from snapshot is not supported for Postgresql storage");
    }

    @Override
    public String createOutboxEvent() {
        return """
                INSERT INTO outbox (id, aggregatetype, aggregateid, type, payload)
                VALUES (?, ?, ?, ?, ?::jsonb)
                """;
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#acquireInitLock()
     */
    @Override
    public String acquireInitLock() {
        // Use PostgreSQL advisory locks with a fixed key derived from "apicurio-init"
        // Key: 1886352239 (hash of "apicurio-init")
        // This is a session-level lock that blocks until acquired
        // Note: pg_advisory_lock() returns void, so we wrap it to return 1 for consistency
        return "SELECT pg_advisory_lock(1886352239), 1";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#releaseInitLock()
     */
    @Override
    public String releaseInitLock() {
        // Note: pg_advisory_unlock() returns boolean, where true = successfully unlocked
        return "SELECT CASE WHEN pg_advisory_unlock(1886352239) THEN 1 ELSE 0 END";
    }

    @Override
    public String insertWebhookSubscription() {
        return """
                INSERT INTO webhook_subscriptions
                (subscriptionId, url, eventTypes, groupIdFilter, artifactTypeFilter, secretHash,
                 enabled, description, createdBy, createdOn, modifiedOn)
                VALUES (?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
    }

    @Override
    public String updateWebhookSubscription() {
        return """
                UPDATE webhook_subscriptions
                SET url = ?, eventTypes = ?::jsonb, groupIdFilter = ?, artifactTypeFilter = ?,
                    secretHash = ?, enabled = ?, description = ?, modifiedOn = ?
                WHERE subscriptionId = ?
                """;
    }

    @Override
    public String insertWebhookFanout() {
        return """
                INSERT INTO webhook_fanout
                (outboxEventId, sourcePayload, storageEventType, fanoutStatus, fanoutAttempts,
                 lastError, createdOn, fanoutOn)
                VALUES (?, ?::jsonb, ?, ?, ?, ?, ?, ?)
                """;
    }

    @Override
    public String insertWebhookDelivery() {
        return """
                INSERT INTO webhook_deliveries
                (subscriptionId, cloudEventId, eventType, payload, status, attemptCount,
                 nextAttemptOn, lastError, createdOn, modifiedOn)
                VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
                """;
    }

    @Override
    public String claimWebhookDeliveries() {
        return """
                UPDATE webhook_deliveries d
                SET status = 'IN_PROGRESS', modifiedOn = CURRENT_TIMESTAMP
                FROM (
                    SELECT deliveryId
                    FROM webhook_deliveries
                    WHERE status = 'PENDING' AND nextAttemptOn <= CURRENT_TIMESTAMP
                    ORDER BY nextAttemptOn
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                ) batch
                WHERE d.deliveryId = batch.deliveryId
                RETURNING d.*
                """;
    }

}
package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.dto.WebhookDeliveryDto;
import io.apicurio.registry.storage.dto.WebhookDeliveryLogDto;
import io.apicurio.registry.storage.dto.WebhookFanoutDto;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.jdb.HandleAction;
import io.apicurio.registry.storage.impl.sql.mappers.WebhookDeliveryDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.WebhookDeliveryLogDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.WebhookFanoutDtoMapper;
import org.slf4j.Logger;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

public class SqlWebhookDeliveryRepository {

    private final Logger log;
    private final SqlStatements sqlStatements;
    private final HandleFactory handles;

    public SqlWebhookDeliveryRepository(HandleFactory handles, SqlStatements sqlStatements,
            Logger log) {
        this.handles = handles;
        this.sqlStatements = sqlStatements;
        this.log = log;
    }

    public void insertFanout(WebhookFanoutDto fanout) throws RegistryStorageException {
        log.debug("Inserting webhook fanout: {}", fanout.getOutboxEventId());
        Date now = new Date();
        if (fanout.getCreatedOn() == null) {
            fanout.setCreatedOn(now);
        }
        handles.withHandleNoException((HandleAction<RegistryStorageException>) handle -> {
            handle.createUpdate(sqlStatements.insertWebhookFanout())
                    .bind(0, fanout.getOutboxEventId())
                    .bind(1, fanout.getSourcePayload())
                    .bind(2, fanout.getStorageEventType())
                    .bind(3, fanout.getFanoutStatus())
                    .bind(4, fanout.getFanoutAttempts())
                    .bind(5, fanout.getLastError())
                    .bind(6, toTimestamp(fanout.getCreatedOn()))
                    .bind(7, fanout.getFanoutOn() != null ? toTimestamp(fanout.getFanoutOn()) : null)
                    .execute();
        });
    }

    public void updateFanoutStatus(WebhookFanoutDto fanout) throws RegistryStorageException {
        log.debug("Updating webhook fanout status: {}", fanout.getOutboxEventId());
        handles.withHandleNoException((HandleAction<RegistryStorageException>) handle -> {
            handle.createUpdate(sqlStatements.updateWebhookFanoutStatus())
                    .bind(0, fanout.getFanoutStatus())
                    .bind(1, fanout.getFanoutAttempts())
                    .bind(2, fanout.getLastError())
                    .bind(3, fanout.getFanoutOn() != null ? toTimestamp(fanout.getFanoutOn()) : null)
                    .bind(4, fanout.getOutboxEventId())
                    .execute();
        });
    }

    public List<WebhookFanoutDto> getPendingFanouts(int maxAttempts, int limit)
            throws RegistryStorageException {
        log.debug("Getting pending webhook fanouts (maxAttempts={}, limit={})", maxAttempts, limit);
        return handles.withHandle(handle -> handle
                .createQuery(sqlStatements.selectPendingWebhookFanouts())
                .bind(0, maxAttempts)
                .bind(1, limit)
                .map(WebhookFanoutDtoMapper.instance)
                .list());
    }

    public long insertDelivery(WebhookDeliveryDto delivery) throws RegistryStorageException {
        log.debug("Inserting webhook delivery for subscription: {}", delivery.getSubscriptionId());
        Date now = new Date();
        if (delivery.getCreatedOn() == null) {
            delivery.setCreatedOn(now);
        }
        if (delivery.getModifiedOn() == null) {
            delivery.setModifiedOn(now);
        }
        if (delivery.getNextAttemptOn() == null) {
            delivery.setNextAttemptOn(now);
        }
        return handles.withHandle(handle -> {
            handle.createUpdate(sqlStatements.insertWebhookDelivery())
                    .bind(0, delivery.getSubscriptionId())
                    .bind(1, delivery.getCloudEventId())
                    .bind(2, delivery.getEventType())
                    .bind(3, delivery.getPayload())
                    .bind(4, delivery.getStatus())
                    .bind(5, delivery.getAttemptCount())
                    .bind(6, toTimestamp(delivery.getNextAttemptOn()))
                    .bind(7, delivery.getLastError())
                    .bind(8, toTimestamp(delivery.getCreatedOn()))
                    .bind(9, toTimestamp(delivery.getModifiedOn()))
                    .execute();
            return handle.createQuery(
                    "SELECT deliveryId FROM webhook_deliveries WHERE subscriptionId = ? AND cloudEventId = ?")
                    .bind(0, delivery.getSubscriptionId())
                    .bind(1, delivery.getCloudEventId())
                    .mapTo(Long.class)
                    .one();
        });
    }

    public List<WebhookDeliveryDto> claimDeliveries(int batchSize) throws RegistryStorageException {
        if (!isPostgresql()) {
            throw new RegistryStorageException(
                    "Webhook delivery claim is only supported on PostgreSQL");
        }
        log.debug("Claiming webhook deliveries (batchSize={})", batchSize);
        return handles.withHandle(handle -> handle
                .createQuery(sqlStatements.claimWebhookDeliveries())
                .bind(0, batchSize)
                .map(WebhookDeliveryDtoMapper.instance)
                .list());
    }

    public void updateDelivery(WebhookDeliveryDto delivery) throws RegistryStorageException {
        log.debug("Updating webhook delivery: {}", delivery.getDeliveryId());
        delivery.setModifiedOn(new Date());
        handles.withHandleNoException((HandleAction<RegistryStorageException>) handle -> {
            handle.createUpdate(sqlStatements.updateWebhookDelivery())
                    .bind(0, delivery.getStatus())
                    .bind(1, delivery.getAttemptCount())
                    .bind(2, toTimestamp(delivery.getNextAttemptOn()))
                    .bind(3, delivery.getLastError())
                    .bind(4, toTimestamp(delivery.getModifiedOn()))
                    .bind(5, delivery.getDeliveryId())
                    .execute();
        });
    }

    public List<WebhookDeliveryDto> getDeliveriesBySubscription(String subscriptionId, int offset,
            int limit) throws RegistryStorageException {
        return handles.withHandle(handle -> handle
                .createQuery(sqlStatements.selectWebhookDeliveriesBySubscription())
                .bind(0, subscriptionId)
                .bind(1, limit)
                .bind(2, offset)
                .map(WebhookDeliveryDtoMapper.instance)
                .list());
    }

    public long countDeliveriesBySubscription(String subscriptionId) throws RegistryStorageException {
        return handles.withHandle(handle -> handle
                .createQuery(sqlStatements.countWebhookDeliveriesBySubscription())
                .bind(0, subscriptionId)
                .mapTo(Long.class)
                .one());
    }

    public void insertDeliveryLog(WebhookDeliveryLogDto entry) throws RegistryStorageException {
        log.debug("Inserting webhook delivery log for delivery: {}", entry.getDeliveryId());
        Date now = new Date();
        if (entry.getAttemptedOn() == null) {
            entry.setAttemptedOn(now);
        }
        handles.withHandleNoException((HandleAction<RegistryStorageException>) handle -> {
            handle.createUpdate(sqlStatements.insertWebhookDeliveryLog())
                    .bind(0, entry.getDeliveryId())
                    .bind(1, entry.getSubscriptionId())
                    .bind(2, entry.getCloudEventId())
                    .bind(3, entry.getAttemptNumber())
                    .bind(4, entry.getHttpStatus())
                    .bind(5, entry.getDurationMs())
                    .bind(6, entry.getError())
                    .bind(7, toTimestamp(entry.getAttemptedOn()))
                    .execute();
        });
    }

    public List<WebhookDeliveryLogDto> getDeliveryLogBySubscription(String subscriptionId, int offset,
            int limit) throws RegistryStorageException {
        return handles.withHandle(handle -> handle
                .createQuery(sqlStatements.selectWebhookDeliveryLogBySubscription())
                .bind(0, subscriptionId)
                .bind(1, limit)
                .bind(2, offset)
                .map(WebhookDeliveryLogDtoMapper.instance)
                .list());
    }

    public void deleteOldDeliveryLogs(long cutoffTimestamp) throws RegistryStorageException {
        log.debug("Deleting webhook delivery logs older than {}", cutoffTimestamp);
        handles.withHandleNoException((HandleAction<RegistryStorageException>) handle -> {
            handle.createUpdate(sqlStatements.deleteOldWebhookDeliveryLogs())
                    .bind(0, new Timestamp(cutoffTimestamp))
                    .execute();
        });
    }

    private boolean isPostgresql() {
        return "postgresql".equals(sqlStatements.dbType());
    }

    private static Timestamp toTimestamp(Date date) {
        return new Timestamp(date.getTime());
    }
}

package io.apicurio.registry.storage.impl.sql.repositories;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.storage.dto.WebhookSubscriptionDto;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.WebhookSubscriptionNotFoundException;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.jdb.HandleAction;
import io.apicurio.registry.storage.impl.sql.mappers.WebhookSubscriptionDtoMapper;
import io.apicurio.registry.util.JsonObjectMapper;
import org.slf4j.Logger;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public class SqlWebhookSubscriptionRepository {

    private final Logger log;
    private final SqlStatements sqlStatements;
    private final HandleFactory handles;

    public SqlWebhookSubscriptionRepository(HandleFactory handles, SqlStatements sqlStatements,
            Logger log) {
        this.handles = handles;
        this.sqlStatements = sqlStatements;
        this.log = log;
    }

    public void createSubscription(WebhookSubscriptionDto subscription) throws RegistryStorageException {
        log.debug("Creating webhook subscription: {}", subscription.getSubscriptionId());
        Date now = new Date();
        if (subscription.getCreatedOn() == null) {
            subscription.setCreatedOn(now);
        }
        if (subscription.getModifiedOn() == null) {
            subscription.setModifiedOn(now);
        }
        handles.withHandleNoException((HandleAction<RegistryStorageException>) handle -> {
            handle.createUpdate(sqlStatements.insertWebhookSubscription())
                    .bind(0, subscription.getSubscriptionId())
                    .bind(1, subscription.getUrl())
                    .bind(2, serializeEventTypes(subscription.getEventTypes()))
                    .bind(3, subscription.getGroupIdFilter())
                    .bind(4, subscription.getArtifactTypeFilter())
                    .bind(5, subscription.getSecretHash())
                    .bind(6, subscription.isEnabled())
                    .bind(7, subscription.getDescription())
                    .bind(8, subscription.getCreatedBy())
                    .bind(9, toTimestamp(subscription.getCreatedOn()))
                    .bind(10, toTimestamp(subscription.getModifiedOn()))
                    .execute();
        });
    }

    public void updateSubscription(WebhookSubscriptionDto subscription) throws RegistryStorageException {
        log.debug("Updating webhook subscription: {}", subscription.getSubscriptionId());
        subscription.setModifiedOn(new Date());
        handles.withHandle(handle -> {
            int updated = handle.createUpdate(sqlStatements.updateWebhookSubscription())
                    .bind(0, subscription.getUrl())
                    .bind(1, serializeEventTypes(subscription.getEventTypes()))
                    .bind(2, subscription.getGroupIdFilter())
                    .bind(3, subscription.getArtifactTypeFilter())
                    .bind(4, subscription.getSecretHash())
                    .bind(5, subscription.isEnabled())
                    .bind(6, subscription.getDescription())
                    .bind(7, toTimestamp(subscription.getModifiedOn()))
                    .bind(8, subscription.getSubscriptionId())
                    .execute();
            if (updated == 0) {
                throw new WebhookSubscriptionNotFoundException(subscription.getSubscriptionId());
            }
            return null;
        });
    }

    public void deleteSubscription(String subscriptionId) throws RegistryStorageException {
        log.debug("Deleting webhook subscription: {}", subscriptionId);
        handles.withHandle(handle -> {
            int deleted = handle.createUpdate(sqlStatements.deleteWebhookSubscription())
                    .bind(0, subscriptionId)
                    .execute();
            if (deleted == 0) {
                throw new WebhookSubscriptionNotFoundException(subscriptionId);
            }
            return null;
        });
    }

    public WebhookSubscriptionDto getSubscription(String subscriptionId) throws RegistryStorageException {
        log.debug("Getting webhook subscription: {}", subscriptionId);
        return handles.withHandle(handle -> {
            Optional<WebhookSubscriptionDto> result = handle
                    .createQuery(sqlStatements.selectWebhookSubscriptionById())
                    .bind(0, subscriptionId)
                    .map(WebhookSubscriptionDtoMapper.instance)
                    .findOne();
            return result.orElseThrow(() -> new WebhookSubscriptionNotFoundException(subscriptionId));
        });
    }

    public List<WebhookSubscriptionDto> listSubscriptions(int offset, int limit)
            throws RegistryStorageException {
        log.debug("Listing webhook subscriptions (offset={}, limit={})", offset, limit);
        return handles.withHandle(handle -> handle
                .createQuery(sqlStatements.selectWebhookSubscriptions() + " LIMIT ? OFFSET ?")
                .bind(0, limit)
                .bind(1, offset)
                .map(WebhookSubscriptionDtoMapper.instance)
                .list());
    }

    public List<WebhookSubscriptionDto> getEnabledSubscriptions() throws RegistryStorageException {
        log.debug("Getting enabled webhook subscriptions");
        return handles.withHandle(handle -> handle
                .createQuery(sqlStatements.selectEnabledWebhookSubscriptions())
                .map(WebhookSubscriptionDtoMapper.instance)
                .list());
    }

    public long countSubscriptions() throws RegistryStorageException {
        return handles.withHandle(handle -> handle
                .createQuery(sqlStatements.countWebhookSubscriptions())
                .mapTo(Long.class)
                .one());
    }

    private static String serializeEventTypes(List<String> eventTypes) throws RegistryStorageException {
        try {
            return JsonObjectMapper.MAPPER.writeValueAsString(
                    eventTypes != null ? eventTypes : List.of());
        } catch (JsonProcessingException ex) {
            throw new RegistryStorageException("Failed to serialize webhook event types", ex);
        }
    }

    private static Timestamp toTimestamp(Date date) {
        return new Timestamp(date.getTime());
    }
}

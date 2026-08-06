package io.apicurio.registry.storage.impl.sql.mappers;

import com.fasterxml.jackson.core.type.TypeReference;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.util.JsonObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Maps {@code webhook_subscriptions} rows to {@link io.apicurio.registry.storage.dto.WebhookSubscriptionDto}.
 */
public class WebhookSubscriptionDtoMapper implements RowMapper<io.apicurio.registry.storage.dto.WebhookSubscriptionDto> {

    public static final WebhookSubscriptionDtoMapper instance = new WebhookSubscriptionDtoMapper();

    private static final TypeReference<List<String>> EVENT_TYPES_TYPE = new TypeReference<>() {
    };

    private WebhookSubscriptionDtoMapper() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public io.apicurio.registry.storage.dto.WebhookSubscriptionDto map(ResultSet rs) throws SQLException {
        io.apicurio.registry.storage.dto.WebhookSubscriptionDto dto =
                new io.apicurio.registry.storage.dto.WebhookSubscriptionDto();
        dto.setSubscriptionId(rs.getString("subscriptionId"));
        dto.setUrl(rs.getString("url"));
        dto.setEventTypes(deserializeEventTypes(rs.getString("eventTypes")));
        dto.setGroupIdFilter(rs.getString("groupIdFilter"));
        dto.setArtifactTypeFilter(rs.getString("artifactTypeFilter"));
        dto.setSecretHash(rs.getString("secretHash"));
        dto.setSecretEncrypted(rs.getString("secretEncrypted"));
        dto.setEnabled(rs.getBoolean("enabled"));
        dto.setDescription(rs.getString("description"));
        dto.setCreatedBy(rs.getString("createdBy"));
        dto.setCreatedOn(rs.getTimestamp("createdOn"));
        dto.setModifiedOn(rs.getTimestamp("modifiedOn"));
        return dto;
    }

    private static List<String> deserializeEventTypes(String json) throws SQLException {
        if (json == null) {
            return List.of();
        }
        try {
            return JsonObjectMapper.MAPPER.readValue(json, EVENT_TYPES_TYPE);
        } catch (Exception ex) {
            throw new SQLException("Failed to deserialize webhook eventTypes JSON", ex);
        }
    }
}

package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.WebhookDeliveryLogDto;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps {@code webhook_delivery_log} rows to {@link WebhookDeliveryLogDto}.
 */
public class WebhookDeliveryLogDtoMapper implements RowMapper<WebhookDeliveryLogDto> {

    public static final WebhookDeliveryLogDtoMapper instance = new WebhookDeliveryLogDtoMapper();

    private WebhookDeliveryLogDtoMapper() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WebhookDeliveryLogDto map(ResultSet rs) throws SQLException {
        WebhookDeliveryLogDto dto = new WebhookDeliveryLogDto();
        dto.setLogId(rs.getLong("logId"));
        dto.setDeliveryId(rs.getLong("deliveryId"));
        dto.setSubscriptionId(rs.getString("subscriptionId"));
        dto.setCloudEventId(rs.getString("cloudEventId"));
        dto.setAttemptNumber(rs.getInt("attemptNumber"));
        int httpStatus = rs.getInt("httpStatus");
        dto.setHttpStatus(rs.wasNull() ? null : httpStatus);
        int durationMs = rs.getInt("durationMs");
        dto.setDurationMs(rs.wasNull() ? null : durationMs);
        dto.setError(rs.getString("error"));
        dto.setAttemptedOn(rs.getTimestamp("attemptedOn"));
        return dto;
    }
}

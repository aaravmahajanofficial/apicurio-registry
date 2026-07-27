package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.WebhookDeliveryDto;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WebhookDeliveryDtoMapper implements RowMapper<WebhookDeliveryDto> {

    public static final WebhookDeliveryDtoMapper instance = new WebhookDeliveryDtoMapper();

    private WebhookDeliveryDtoMapper() {
    }

    @Override
    public WebhookDeliveryDto map(ResultSet rs) throws SQLException {
        WebhookDeliveryDto dto = new WebhookDeliveryDto();
        dto.setDeliveryId(rs.getLong("deliveryId"));
        dto.setSubscriptionId(rs.getString("subscriptionId"));
        dto.setCloudEventId(rs.getString("cloudEventId"));
        dto.setEventType(rs.getString("eventType"));
        dto.setPayload(rs.getString("payload"));
        dto.setStatus(rs.getString("status"));
        dto.setAttemptCount(rs.getInt("attemptCount"));
        dto.setNextAttemptOn(rs.getTimestamp("nextAttemptOn"));
        dto.setLastError(rs.getString("lastError"));
        dto.setCreatedOn(rs.getTimestamp("createdOn"));
        dto.setModifiedOn(rs.getTimestamp("modifiedOn"));
        return dto;
    }
}

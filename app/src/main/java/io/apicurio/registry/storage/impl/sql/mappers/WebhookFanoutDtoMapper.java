package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.WebhookFanoutDto;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps {@code webhook_fanout} rows to {@link WebhookFanoutDto}.
 */
public class WebhookFanoutDtoMapper implements RowMapper<WebhookFanoutDto> {

    public static final WebhookFanoutDtoMapper instance = new WebhookFanoutDtoMapper();

    private WebhookFanoutDtoMapper() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WebhookFanoutDto map(ResultSet rs) throws SQLException {
        WebhookFanoutDto dto = new WebhookFanoutDto();
        dto.setOutboxEventId(rs.getString("outboxEventId"));
        dto.setSourcePayload(rs.getString("sourcePayload"));
        dto.setStorageEventType(rs.getString("storageEventType"));
        dto.setFanoutStatus(rs.getString("fanoutStatus"));
        dto.setFanoutAttempts(rs.getInt("fanoutAttempts"));
        dto.setLastError(rs.getString("lastError"));
        dto.setCreatedOn(rs.getTimestamp("createdOn"));
        dto.setFanoutOn(rs.getTimestamp("fanoutOn"));
        return dto;
    }
}

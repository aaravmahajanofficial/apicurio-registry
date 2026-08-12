-- *********************************************************************
-- DDL for the Apicurio Registry - Database: postgresql
-- Upgrade Script from 109 to 110
-- *********************************************************************

UPDATE apicurio SET propValue = 110 WHERE propName = 'db_version';

CREATE TABLE IF NOT EXISTS webhook_subscriptions (subscriptionId VARCHAR(36) NOT NULL, url VARCHAR(2048) NOT NULL, eventTypes JSONB NOT NULL, groupIdFilter VARCHAR(512), artifactTypeFilter VARCHAR(64), secretHash VARCHAR(128), secretEncrypted VARCHAR(512), enabled BOOLEAN NOT NULL DEFAULT TRUE, description VARCHAR(1024), createdBy VARCHAR(256), createdOn TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, modifiedOn TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
ALTER TABLE webhook_subscriptions ADD PRIMARY KEY (subscriptionId);
CREATE INDEX IF NOT EXISTS IDX_webhook_subs_enabled ON webhook_subscriptions(enabled);

CREATE TABLE IF NOT EXISTS webhook_fanout (outboxEventId VARCHAR(128) NOT NULL, sourcePayload JSONB NOT NULL, storageEventType VARCHAR(64) NOT NULL, fanoutStatus VARCHAR(32) NOT NULL DEFAULT 'PENDING', fanoutAttempts INT NOT NULL DEFAULT 0, lastError TEXT, createdOn TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, fanoutOn TIMESTAMP);
ALTER TABLE webhook_fanout ADD PRIMARY KEY (outboxEventId);
CREATE INDEX IF NOT EXISTS IDX_webhook_fanout_pending ON webhook_fanout(fanoutStatus, createdOn) WHERE fanoutStatus IN ('PENDING', 'FAILED');

CREATE TABLE IF NOT EXISTS webhook_deliveries (deliveryId BIGSERIAL NOT NULL, subscriptionId VARCHAR(36) NOT NULL, cloudEventId VARCHAR(36) NOT NULL, eventType VARCHAR(128) NOT NULL, payload JSONB NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'PENDING', attemptCount INT NOT NULL DEFAULT 0, nextAttemptOn TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, lastError TEXT, createdOn TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, modifiedOn TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
ALTER TABLE webhook_deliveries ADD PRIMARY KEY (deliveryId);
ALTER TABLE webhook_deliveries ADD CONSTRAINT FK_webhook_del_sub FOREIGN KEY (subscriptionId) REFERENCES webhook_subscriptions(subscriptionId) ON DELETE CASCADE;
CREATE UNIQUE INDEX IF NOT EXISTS UQ_webhook_del_event ON webhook_deliveries(subscriptionId, cloudEventId);
CREATE INDEX IF NOT EXISTS IDX_webhook_del_poll ON webhook_deliveries(status, nextAttemptOn) WHERE status IN ('PENDING', 'IN_PROGRESS');

CREATE TABLE IF NOT EXISTS webhook_delivery_log (logId BIGSERIAL NOT NULL, deliveryId BIGINT NOT NULL, subscriptionId VARCHAR(36) NOT NULL, cloudEventId VARCHAR(36) NOT NULL, attemptNumber INT NOT NULL, httpStatus INT, durationMs INT, error TEXT, attemptedOn TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
ALTER TABLE webhook_delivery_log ADD PRIMARY KEY (logId);
CREATE INDEX IF NOT EXISTS IDX_webhook_log_sub ON webhook_delivery_log(subscriptionId, attemptedOn DESC);

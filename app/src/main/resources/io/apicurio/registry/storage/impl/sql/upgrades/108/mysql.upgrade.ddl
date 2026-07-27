-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mysql
-- Upgrade Script from 107 to 108
-- *********************************************************************

UPDATE apicurio SET propValue = 108 WHERE propName = 'db_version';

CREATE TABLE webhook_subscriptions (subscriptionId VARCHAR(36) NOT NULL, url VARCHAR(2048) NOT NULL, eventTypes JSON NOT NULL, groupIdFilter VARCHAR(512), artifactTypeFilter VARCHAR(64), secretHash VARCHAR(128), enabled BOOLEAN NOT NULL DEFAULT TRUE, description VARCHAR(1024), createdBy VARCHAR(256), createdOn TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, modifiedOn TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (subscriptionId));
CREATE INDEX IDX_webhook_subs_enabled ON webhook_subscriptions(enabled);

CREATE TABLE webhook_fanout (outboxEventId VARCHAR(128) NOT NULL, sourcePayload JSON NOT NULL, storageEventType VARCHAR(64) NOT NULL, fanoutStatus VARCHAR(32) NOT NULL DEFAULT 'PENDING', fanoutAttempts INT NOT NULL DEFAULT 0, lastError TEXT, createdOn TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, fanoutOn TIMESTAMP, PRIMARY KEY (outboxEventId));
CREATE INDEX IDX_webhook_fanout_pending ON webhook_fanout(fanoutStatus, createdOn);

CREATE TABLE webhook_deliveries (deliveryId BIGINT NOT NULL AUTO_INCREMENT, subscriptionId VARCHAR(36) NOT NULL, cloudEventId VARCHAR(36) NOT NULL, eventType VARCHAR(128) NOT NULL, payload JSON NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'PENDING', attemptCount INT NOT NULL DEFAULT 0, nextAttemptOn TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, lastError TEXT, createdOn TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, modifiedOn TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (deliveryId));
ALTER TABLE webhook_deliveries ADD CONSTRAINT FK_webhook_del_sub FOREIGN KEY (subscriptionId) REFERENCES webhook_subscriptions(subscriptionId) ON DELETE CASCADE;
CREATE UNIQUE INDEX UQ_webhook_del_event ON webhook_deliveries(subscriptionId, cloudEventId);
CREATE INDEX IDX_webhook_del_poll ON webhook_deliveries(status, nextAttemptOn);

CREATE TABLE webhook_delivery_log (logId BIGINT NOT NULL AUTO_INCREMENT, deliveryId BIGINT NOT NULL, subscriptionId VARCHAR(36) NOT NULL, cloudEventId VARCHAR(36) NOT NULL, attemptNumber INT NOT NULL, httpStatus INT, durationMs INT, error TEXT, attemptedOn TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (logId));
CREATE INDEX IDX_webhook_log_sub ON webhook_delivery_log(subscriptionId, attemptedOn);

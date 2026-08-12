-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mssql
-- Upgrade Script from 109 to 110
-- *********************************************************************

UPDATE apicurio SET propValue = 110 WHERE propName = 'db_version';

CREATE TABLE webhook_subscriptions (subscriptionId VARCHAR(36) NOT NULL, url NVARCHAR(2048) NOT NULL, eventTypes NVARCHAR(MAX) NOT NULL, groupIdFilter NVARCHAR(512), artifactTypeFilter NVARCHAR(64), secretHash NVARCHAR(128), secretEncrypted VARCHAR(512), enabled BIT NOT NULL DEFAULT 1, description NVARCHAR(1024), createdBy NVARCHAR(256), createdOn DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(), modifiedOn DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());
ALTER TABLE webhook_subscriptions ADD PRIMARY KEY (subscriptionId);
CREATE INDEX IDX_webhook_subs_enabled ON webhook_subscriptions(enabled);

CREATE TABLE webhook_fanout (outboxEventId VARCHAR(128) NOT NULL, sourcePayload NVARCHAR(MAX) NOT NULL, storageEventType NVARCHAR(64) NOT NULL, fanoutStatus NVARCHAR(32) NOT NULL DEFAULT 'PENDING', fanoutAttempts INT NOT NULL DEFAULT 0, lastError NVARCHAR(MAX), createdOn DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(), fanoutOn DATETIME2);
ALTER TABLE webhook_fanout ADD PRIMARY KEY (outboxEventId);
CREATE INDEX IDX_webhook_fanout_pending ON webhook_fanout(fanoutStatus, createdOn) WHERE fanoutStatus IN ('PENDING', 'FAILED');

CREATE TABLE webhook_deliveries (deliveryId BIGINT IDENTITY NOT NULL, subscriptionId VARCHAR(36) NOT NULL, cloudEventId VARCHAR(36) NOT NULL, eventType NVARCHAR(128) NOT NULL, payload NVARCHAR(MAX) NOT NULL, status NVARCHAR(32) NOT NULL DEFAULT 'PENDING', attemptCount INT NOT NULL DEFAULT 0, nextAttemptOn DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(), lastError NVARCHAR(MAX), createdOn DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(), modifiedOn DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());
ALTER TABLE webhook_deliveries ADD PRIMARY KEY (deliveryId);
ALTER TABLE webhook_deliveries ADD CONSTRAINT FK_webhook_del_sub FOREIGN KEY (subscriptionId) REFERENCES webhook_subscriptions(subscriptionId) ON DELETE CASCADE;
CREATE UNIQUE INDEX UQ_webhook_del_event ON webhook_deliveries(subscriptionId, cloudEventId);
CREATE INDEX IDX_webhook_del_poll ON webhook_deliveries(status, nextAttemptOn) WHERE status IN ('PENDING', 'IN_PROGRESS');

CREATE TABLE webhook_delivery_log (logId BIGINT IDENTITY NOT NULL, deliveryId BIGINT NOT NULL, subscriptionId VARCHAR(36) NOT NULL, cloudEventId VARCHAR(36) NOT NULL, attemptNumber INT NOT NULL, httpStatus INT, durationMs INT, error NVARCHAR(MAX), attemptedOn DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());
ALTER TABLE webhook_delivery_log ADD PRIMARY KEY (logId);
CREATE INDEX IDX_webhook_log_sub ON webhook_delivery_log(subscriptionId, attemptedOn DESC);

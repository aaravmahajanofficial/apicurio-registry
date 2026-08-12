-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mssql
-- Upgrade Script from 110 to 111
-- *********************************************************************

ALTER TABLE webhook_subscriptions ADD COLUMN consecutiveDeliveryFailures INT NOT NULL DEFAULT 0;
UPDATE apicurio SET propValue = 111 WHERE propName = 'db_version';

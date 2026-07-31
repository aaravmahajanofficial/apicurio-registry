-- *********************************************************************
-- DDL for the Apicurio Registry - Database: postgresql
-- Upgrade Script from 108 to 109
-- *********************************************************************

UPDATE apicurio SET propValue = 109 WHERE propName = 'db_version';

ALTER TABLE webhook_subscriptions ADD COLUMN IF NOT EXISTS secretEncrypted VARCHAR(512);

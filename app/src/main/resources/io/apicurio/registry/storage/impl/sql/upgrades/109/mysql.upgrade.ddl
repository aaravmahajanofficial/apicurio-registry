-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mysql
-- Upgrade Script from 108 to 109
-- *********************************************************************

UPDATE apicurio SET propValue = 109 WHERE propName = 'db_version';

ALTER TABLE webhook_subscriptions ADD COLUMN secretEncrypted VARCHAR(512);

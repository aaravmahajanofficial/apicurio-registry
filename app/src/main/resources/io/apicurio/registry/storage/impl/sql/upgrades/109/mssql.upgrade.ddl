-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mssql
-- Upgrade Script from 108 to 109
-- *********************************************************************

UPDATE apicurio SET propValue = 109 WHERE propName = 'db_version';

ALTER TABLE webhook_subscriptions ADD secretEncrypted VARCHAR(512) NULL;

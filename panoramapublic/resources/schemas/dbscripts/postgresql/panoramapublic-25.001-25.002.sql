/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
CREATE TABLE panoramapublic.DatasetStatus
(
    _ts                       TIMESTAMP,
    Id                        SERIAL NOT NULL,
    CreatedBy                 USERID,
    Created                   TIMESTAMP,
    ModifiedBy                USERID,
    Modified                  TIMESTAMP,

    ShortUrl                  ENTITYID NOT NULL,
    LastReminderDate          TIMESTAMP,
    ExtensionRequestedDate    TIMESTAMP,
    DeletionRequestedDate     TIMESTAMP,

    CONSTRAINT PK_DatasetStatus PRIMARY KEY (Id),

    CONSTRAINT FK_DatasetStatus_ShortUrl FOREIGN KEY (ShortUrl) REFERENCES core.shorturl (entityId),

    CONSTRAINT UQ_DatasetStatus_ShortUrl UNIQUE (ShortUrl)
);
CREATE INDEX IX_DatasetStatus_ShortUrl ON panoramapublic.DatasetStatus(ShortUrl);

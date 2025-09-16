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

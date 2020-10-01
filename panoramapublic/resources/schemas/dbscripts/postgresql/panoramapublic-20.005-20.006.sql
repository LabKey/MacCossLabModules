CREATE TABLE panoramapublic.speclibinfo
(
    _ts               TIMESTAMP,
    Id                SERIAL NOT NULL,
    CreatedBy         USERID,
    Created           TIMESTAMP,
    ModifiedBy        USERID,
    Modified          TIMESTAMP,

    Filename          VARCHAR(255),
    SourceType        INT NOT NULL,
    SourceUrl         TEXT,
    SourcePxid        VARCHAR(10),
    SourceAccession   TEXT,
    SourceUsername    VARCHAR(100),
    SourcePassword    VARCHAR(100),
    DependencyType    INT NOT NULL,

    CONSTRAINT PK_SpecLibInfo PRIMARY KEY (Id)
);

CREATE TABLE panoramapublic.speclibinforun
(
    _ts             TIMESTAMP,
    Id              SERIAL NOT NULL,
    CreatedBy       USERID,
    Created         TIMESTAMP,
    ModifiedBy      USERID,
    Modified        TIMESTAMP,

    RunId           INT NOT NULL,
    SpecLibInfoId   INT NOT NULL,

    CONSTRAINT PK_SpecLibInfoRun PRIMARY KEY (Id),
    CONSTRAINT FK_SpecLibInfoRun_Run FOREIGN KEY (RunId) REFERENCES targetedms.runs(id),
    CONSTRAINT FK_SpecLibInfoRun_SpecLibInfo FOREIGN KEY (SpecLibInfoId) REFERENCES panoramapublic.speclibinfo(id)
);

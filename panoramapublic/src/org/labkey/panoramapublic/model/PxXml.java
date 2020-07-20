package org.labkey.panoramapublic.model;

import java.util.Date;

public class PxXml
{
    private int _id;
    private int _journalExperimentId;
    private Date _created;
    private int _createdBy;
    private String _xml;
    private int _version;
    private String _updateLog;

    public PxXml() {}

    public PxXml(int journalExperimentId, String xml, int version, String updateLog)
    {
        _journalExperimentId = journalExperimentId;
        _xml = xml;
        _version = version;
        _updateLog = updateLog;
    }

    public int getId()
    {
        return _id;
    }

    public void setId(int id)
    {
        _id = id;
    }

    public int getJournalExperimentId()
    {
        return _journalExperimentId;
    }

    public void setJournalExperimentId(int journalExperimentId)
    {
        _journalExperimentId = journalExperimentId;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public int getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(int createdBy)
    {
        _createdBy = createdBy;
    }

    public String getXml()
    {
        return _xml;
    }

    public void setXml(String xml)
    {
        _xml = xml;
    }

    public int getVersion()
    {
        return _version;
    }

    public void setVersion(int version)
    {
        _version = version;
    }

    public String getUpdateLog()
    {
        return _updateLog;
    }

    public void setUpdateLog(String updateLog)
    {
        _updateLog = updateLog;
    }
}

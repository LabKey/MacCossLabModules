package org.labkey.panoramapublic.model;

public class PxXml extends DbEntity
{
    private int _journalExperimentId;
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

    public int getJournalExperimentId()
    {
        return _journalExperimentId;
    }

    public void setJournalExperimentId(int journalExperimentId)
    {
        _journalExperimentId = journalExperimentId;
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

package org.labkey.panoramapublic.model;

import java.util.Date;

public class SpecLibInfoRun
{
    private int _createdBy;
    private Date _created;
    private int _modifiedBy;
    private Date _modified;
    private int _runId;
    private int _specLibInfoId;

    public SpecLibInfoRun() {}

    public SpecLibInfoRun(int runId, int specLibInfoId)
    {
        _runId = runId;
        _specLibInfoId = specLibInfoId;
    }

    public int getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(int createdBy)
    {
        _createdBy = createdBy;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public int getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(int modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }

    public Date getModified()
    {
        return _modified;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }

    public int getRunId()
    {
        return _runId;
    }

    public void setRunId(int runId)
    {
        _runId = runId;
    }

    public int getSpecLibInfoId()
    {
        return _specLibInfoId;
    }

    public void setSpecLibInfoId(int specLibInfoId)
    {
        _specLibInfoId = specLibInfoId;
    }
}

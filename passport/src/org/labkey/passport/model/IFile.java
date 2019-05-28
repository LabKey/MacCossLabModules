package org.labkey.passport.model;

import org.labkey.api.data.Container;

import java.util.Date;

public class IFile
{
    String fileName;

    public Container getContainer()
    {
        return container;
    }

    public void setContainer(Container container)
    {
        this.container = container;
    }

    Container container;

    public int getRunId()
    {
        return runId;
    }

    public void setRunId(int runId)
    {
        this.runId = runId;
    }

    private int runId;

    public int getPepGroupId()
    {
        return pepGroupId;
    }

    public void setPepGroupId(int pepGroupId)
    {
        this.pepGroupId = pepGroupId;
    }

    private int pepGroupId;

    public Date getCreatedDate()
    {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate)
    {
        this.createdDate = createdDate;
    }

    Date createdDate;

    public Date getModifiedDate()
    {
        return modifiedDate;
    }

    public void setModifiedDate(Date modifiedDate)
    {
        this.modifiedDate = modifiedDate;
    }

    Date modifiedDate;

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    public String getSoftwareVersion()
    {
        return softwareVersion;
    }

    public void setSoftwareVersion(String softwareVersion)
    {
        this.softwareVersion = softwareVersion;
    }



    String softwareVersion;
}

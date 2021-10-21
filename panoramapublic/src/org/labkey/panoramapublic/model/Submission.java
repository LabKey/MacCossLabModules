package org.labkey.panoramapublic.model;

import org.apache.commons.lang3.StringUtils;

import java.util.Date;

public class Submission
{
    private int _id;
    private Date _created;
    private int _createdBy;
    private Date _modified;
    private int _modifiedBy;
    private int _journalExperimentId;
    private boolean _keepPrivate;
    private boolean _pxidRequested;
    private boolean _incompletePxSubmission;
    private String _labHeadName;
    private String _labHeadEmail;
    private String _labHeadAffiliation;
    private DataLicense _dataLicense;
    private Integer _copiedExperimentId;
    private Date _copied;

    public int getId()
    {
        return _id;
    }

    public void setId(int id)
    {
        _id = id;
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

    public Date getModified()
    {
        return _modified;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }

    public int getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(int modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }

    public int getJournalExperimentId()
    {
        return _journalExperimentId;
    }

    public void setJournalExperimentId(int journalExperimentId)
    {
        _journalExperimentId = journalExperimentId;
    }

    public boolean isKeepPrivate()
    {
        return _keepPrivate;
    }

    public void setKeepPrivate(boolean keepPrivate)
    {
        _keepPrivate = keepPrivate;
    }

    public boolean isPxidRequested()
    {
        return _pxidRequested;
    }

    public void setPxidRequested(boolean pxidRequested)
    {
        _pxidRequested = pxidRequested;
    }

    public boolean isIncompletePxSubmission()
    {
        return _incompletePxSubmission;
    }

    public void setIncompletePxSubmission(boolean incompletePxSubmission)
    {
        this._incompletePxSubmission = incompletePxSubmission;
    }

    public String getLabHeadName()
    {
        return _labHeadName;
    }

    public void setLabHeadName(String labHeadName)
    {
        _labHeadName = labHeadName;
    }

    public String getLabHeadEmail()
    {
        return _labHeadEmail;
    }

    public void setLabHeadEmail(String labHeadEmail)
    {
        _labHeadEmail = labHeadEmail;
    }

    public boolean hasLabHeadDetails()
    {
        return !(StringUtils.isBlank(_labHeadName) && StringUtils.isBlank(_labHeadEmail) && StringUtils.isBlank(_labHeadAffiliation));
    }

    public String getLabHeadAffiliation()
    {
        return _labHeadAffiliation;
    }

    public void setLabHeadAffiliation(String labHeadAffiliation)
    {
        _labHeadAffiliation = labHeadAffiliation;
    }

    public DataLicense getDataLicense()
    {
        return _dataLicense;
    }

    public void setDataLicense(DataLicense dataLicense)
    {
        _dataLicense = dataLicense;
    }

    public Integer getCopiedExperimentId()
    {
        return _copiedExperimentId;
    }

    public void setCopiedExperimentId(Integer copiedExperimentId)
    {
        _copiedExperimentId = copiedExperimentId;
    }

    public Date getCopied()
    {
        return _copied;
    }

    public void setCopied(Date copied)
    {
        _copied = copied;
    }

    public boolean hasCopy()
    {
        return _copiedExperimentId != null;
    }

    /**
     * @return true if this submission request has not yet been copied.
     */
    public boolean isPending()
    {
        return _copiedExperimentId == null && _copied == null;
    }

    /**
     * @return true if this submission was copied (has a copied timestamp) but no longer has a reference to the
     * copied experiment because the copy was deleted. The row in kept in the Submission table as a log of
     * all the submissions.
     */
    public boolean isObsolete()
    {
        return _copied != null && !hasCopy();
    }
}
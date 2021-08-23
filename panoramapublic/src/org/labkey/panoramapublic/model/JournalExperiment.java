/*
 * Copyright (c) 2014-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.panoramapublic.model;


import org.apache.commons.collections4.comparators.ReverseComparator;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.NumberUtilsLabKey;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.panoramapublic.query.JournalManager;

import javax.validation.constraints.Null;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * User: vsharma
 * Date: 08/07/14
 * Time: 12:50 PM
 */
public class JournalExperiment
{
    private int _id;
    private int _journalId;
    private int _experimentAnnotationsId;
    private ShortURLRecord _shortAccessUrl;
    private ShortURLRecord _shortCopyUrl;
    private Date _created;
    private int _createdBy;
    private Date _modified;
    private int _modifiedBy;
    private Integer _announcementId;
    // private List<Submission> _submissions;

//    private Date _copied;
//    private boolean _keepPrivate;
//    private boolean _pxidRequested;
//    private boolean _incompletePxSubmission;
//    private String _labHeadName;
//    private String _labHeadEmail;
//    private String _labHeadAffiliation;
//    private DataLicense _dataLicense;
//    private Integer _copiedExperimentId;
//    private Integer _version;

    public int getId()
    {
        return _id;
    }

    public void setId(int id)
    {
        _id = id;
    }

    public int getJournalId()
    {
        return _journalId;
    }

    public void setJournalId(int journalId)
    {
        _journalId = journalId;
    }

    public int getExperimentAnnotationsId()
    {
        return _experimentAnnotationsId;
    }

    public void setExperimentAnnotationsId(int experimentAnnotationsId)
    {
        _experimentAnnotationsId = experimentAnnotationsId;
    }

    public ShortURLRecord getShortAccessUrl()
    {
        return _shortAccessUrl;
    }

    public void setShortAccessUrl(ShortURLRecord shortAccessUrl)
    {
        _shortAccessUrl = shortAccessUrl;
    }

    public ShortURLRecord getShortCopyUrl()
    {
        return _shortCopyUrl;
    }

    public void setShortCopyUrl(ShortURLRecord shortCopyUrl)
    {
        _shortCopyUrl = shortCopyUrl;
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

    public Integer getAnnouncementId()
    {
        return _announcementId;
    }

    public void setAnnouncementId(Integer announcementId)
    {
        _announcementId = announcementId;
    }



//    public @Nullable Date getCopied()
//    {
//        Submission submission = getNewestSubmission();
//        return submission != null ? submission.getCopied() : null;
//    }
//
//
//    public boolean isKeepPrivate()
//    {
//        return _keepPrivate;
//    }
//
//    public void setKeepPrivate(boolean keepPrivate)
//    {
//        _keepPrivate = keepPrivate;
//    }
//
//    public boolean isPxidRequested()
//    {
//        return _pxidRequested;
//    }
//
//    public void setPxidRequested(boolean pxidRequested)
//    {
//        _pxidRequested = pxidRequested;
//    }
//
//    public boolean isIncompletePxSubmission()
//    {
//        return _incompletePxSubmission;
//    }
//
//    public void setIncompletePxSubmission(boolean incompletePxSubmission)
//    {
//        this._incompletePxSubmission = incompletePxSubmission;
//    }
//
//    public String getLabHeadName()
//    {
//        return _labHeadName;
//    }
//
//    public void setLabHeadName(String labHeadName)
//    {
//        _labHeadName = labHeadName;
//    }
//
//    public String getLabHeadEmail()
//    {
//        return _labHeadEmail;
//    }
//
//    public void setLabHeadEmail(String labHeadEmail)
//    {
//        _labHeadEmail = labHeadEmail;
//    }

//    public boolean hasLabHeadDetails()
//    {
//        return !(StringUtils.isBlank(_labHeadName) && StringUtils.isBlank(_labHeadEmail) && StringUtils.isBlank(_labHeadAffiliation));
//    }

//    public String getLabHeadAffiliation()
//    {
//        return _labHeadAffiliation;
//    }
//
//    public void setLabHeadAffiliation(String labHeadAffiliation)
//    {
//        _labHeadAffiliation = labHeadAffiliation;
//    }
//
//    public DataLicense getDataLicense()
//    {
//        return _dataLicense;
//    }
//
//    public void setDataLicense(DataLicense dataLicense)
//    {
//        _dataLicense = dataLicense;
//    }

//    public Integer getCopiedExperimentId()
//    {
//        return _copiedExperimentId;
//    }
//
//    public void setCopiedExperimentId(Integer copiedExperimentId)
//    {
//        _copiedExperimentId = copiedExperimentId;
//    }
//
//    public Integer getVersion()
//    {
//        return _version;
//    }
//
//    public void setVersion(Integer version)
//    {
//        _version = version;
//    }


}

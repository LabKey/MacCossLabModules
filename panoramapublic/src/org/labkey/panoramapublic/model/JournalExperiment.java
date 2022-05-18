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


import org.labkey.api.view.ShortURLRecord;

/**
 * User: vsharma
 * Date: 08/07/14
 * Time: 12:50 PM
 */
public class JournalExperiment extends DbEntity
{
    private int _journalId;
    private int _experimentAnnotationsId;
    private ShortURLRecord _shortAccessUrl;
    private ShortURLRecord _shortCopyUrl;
    private Integer _announcementId;
    private Integer _reviewer;

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

    public Integer getAnnouncementId()
    {
        return _announcementId;
    }

    public void setAnnouncementId(Integer announcementId)
    {
        _announcementId = announcementId;
    }

    public Integer getReviewer()
    {
        return _reviewer;
    }

    public void setReviewer(Integer reviewer)
    {
        _reviewer = reviewer;
    }
}

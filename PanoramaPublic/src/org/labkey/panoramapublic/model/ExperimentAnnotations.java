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
package org.labkey.targetedms.model;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.view.ShortURLRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: vsharma
 * Date: 2/18/13
 * Time: 3:42 PM
 */
public class ExperimentAnnotations
{
    private int _id;
    private int _experimentId;
    private Container _container;
    private String _title;
    private String _experimentDescription;
    private String _sampleDescription;
    private String _organism;
    private String _instrument;
    private Boolean _spikeIn;
    private String _citation;
    private String _abstract;
    private String _publicationLink;
    private Date _created;
    private int _createdBy;
    private boolean _includeSubfolders;
    private ExpExperiment _experiment;

    private boolean _journalCopy; // true if this experiment was copied by a journal (e.g. "Panorama Public" on panoramaweb.org)
    // The following fields (_sourceExperimentId, _sourceExperimentPath, _shortUrl) will be populated only if _journalCopy is true.
    private Integer _sourceExperimentId;
    private String _sourceExperimentPath; // Store this in case the original source experiment and/or container is deleted by user.
    // Store the shortAccessUrl if this is a journal copy.  We can get to this by doing a lookup for the sourceExperimentId
    // in the JournalExperiment table.  But we lose the link if the source experiment gets deleted.
    private ShortURLRecord _shortUrl;

    private String _keywords;
    private Integer _labHead;
    private String _labHeadAffiliation;
    private Integer _submitter;
    private String _submitterAffiliation;
    private String _pxid;

    private static Pattern taxIdPattern = Pattern.compile("(.*)\\(taxid:(\\d+)\\)");

    public ExperimentAnnotations() {}

    public ExperimentAnnotations(ExperimentAnnotations experiment)
    {
        _title = experiment.getTitle();
        _experimentDescription = experiment.getExperimentDescription();
        _sampleDescription = experiment.getSampleDescription();
        _organism = experiment.getOrganism();
        _instrument = experiment.getInstrument();
        _spikeIn = experiment.getSpikeIn();
        _citation = experiment.getCitation();
        _abstract = experiment.getAbstract();
        _publicationLink = experiment.getPublicationLink();
        _includeSubfolders = experiment.isIncludeSubfolders();
        _keywords = experiment.getKeywords();
        _labHead = experiment.getLabHead();
        _submitter = experiment.getSubmitter();
        _labHeadAffiliation = experiment.getLabHeadAffiliation();
        _submitterAffiliation = experiment.getSubmitterAffiliation();
        _pxid = experiment.getPxid();
    }

    public int getId()
    {
        return _id;
    }

    public void setId(int id)
    {
        _id = id;
    }

    public int getExperimentId()
    {
        return _experimentId;
    }

    public void setExperimentId(int experimentId)
    {
        _experimentId = experimentId;
    }

    public ExpExperiment getExperiment()
    {
        if(_experiment == null)
        {
            _experiment = ExperimentService.get().getExpExperiment(getExperimentId());
        }
        return _experiment;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public String getTitle()
    {
        return _title;
    }

    public void setTitle(String title)
    {
        _title = title;
    }

    public String getExperimentDescription()
    {
        return _experimentDescription;
    }

    public void setExperimentDescription(String experimentDescription)
    {
        _experimentDescription = experimentDescription;
    }

    public String getSampleDescription()
    {
        return _sampleDescription;
    }

    public void setSampleDescription(String sampleDescription)
    {
        _sampleDescription = sampleDescription;
    }

    public String getOrganism()
    {
        return _organism;
    }

    @Nullable
    public String getOrganismsNoTaxId()
    {
        return getOrganismsNoTaxId(getOrganism());
    }

    @Nullable
    public static String getOrganismsNoTaxId(String organismStr)
    {
        if(StringUtils.isBlank(organismStr))
        {
            return null;
        }

        String[] orgs = organismStr.split(",");
        for(int i = 0; i < orgs.length; i++)
        {
           String org = orgs[i];
           org = org.replaceAll(" \\(taxid:\\d+\\)", "");
           orgs[i] = org.trim();
        }
        return StringUtils.join(orgs, ", ");
    }

    public Map<String, Integer> getOrganismAndTaxId()
    {
        if(StringUtils.isBlank(_organism))
        {
            return Collections.emptyMap();
        }

        Map<String, Integer> orgTaxIdMap = new HashMap<>();
        String[] orgs = StringUtils.split(_organism,",");
        for(String org: orgs)
        {
            org = org.trim();
            Matcher match = taxIdPattern.matcher(org);
            if(match.matches())
            {
                String name = match.group(1).trim();
                String taxid = match.group(2).trim();
                orgTaxIdMap.put(name, Integer.parseInt(taxid));
            }
            else
            {
                orgTaxIdMap.put(org, null);
            }
        }
        return orgTaxIdMap;
    }

    public void setOrganism(String organism)
    {
        _organism = organism;
    }

    public String getInstrument()
    {
        return _instrument;
    }

    public List<String> getInstruments()
    {
        if(StringUtils.isBlank(_instrument))
        {
            return Collections.emptyList();
        }

        List<String> instruments = new ArrayList<>();
        String[] tokens = StringUtils.split(_instrument, ",");
        for(String token: tokens)
        {
            instruments.add(token.trim());
        }
        return instruments;
    }

    public void setInstrument(String instrument)
    {
        _instrument = instrument;
    }

    public String getCitation()
    {
        return _citation;
    }

    public Boolean getSpikeIn()
    {
        return _spikeIn;
    }

    public void setSpikeIn(Boolean spikeIn)
    {
        _spikeIn = spikeIn;
    }

    public void setCitation(String citation)
    {
        _citation = citation;
    }

    public String getAbstract()
    {
        return _abstract;
    }

    public void setAbstract(String anAbstract)
    {
        _abstract = anAbstract;
    }

    public String getPublicationLink()
    {
        return _publicationLink;
    }

    public void setPublicationLink(String publicationLink)
    {
        _publicationLink = publicationLink;
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

    public Integer getSourceExperimentId()
    {
        return _sourceExperimentId;
    }

    public void setSourceExperimentId(Integer sourceExperiment)
    {
        _sourceExperimentId = sourceExperiment;
    }

    public boolean isJournalCopy()
    {
        return _journalCopy;
    }

    public void setJournalCopy(boolean journalCopy)
    {
        _journalCopy = journalCopy;
    }

    public boolean isIncludeSubfolders()
    {
        return _includeSubfolders;
    }

    public void setIncludeSubfolders(boolean includeSubfolders)
    {
        _includeSubfolders = includeSubfolders;
    }

    public String getSourceExperimentPath()
    {
        return _sourceExperimentPath;
    }

    public void setSourceExperimentPath(String sourceExperimentPath)
    {
        _sourceExperimentPath = sourceExperimentPath;
    }

    public ShortURLRecord getShortUrl()
    {
        return _shortUrl;
    }

    public void setShortUrl(ShortURLRecord shortAccessUrl)
    {
        _shortUrl = shortAccessUrl;
    }

    public String getKeywords()
    {
        return _keywords;
    }

    public void setKeywords(String keywords)
    {
        _keywords = keywords;
    }

    public Integer getLabHead()
    {
        return _labHead;
    }

    public void setLabHead(Integer labHead)
    {
        _labHead = labHead;
    }

    public User getLabHeadUser()
    {
        return _labHead != null ? UserManager.getUser(_labHead) : null;
    }

    @Nullable
    public String getLabHeadName()
    {
        return getUserName(getLabHeadUser());
    }

    public static String getUserName(User user)
    {
       if (user != null)
        {
            String name = user.getFullName();
            if (StringUtils.isBlank(name))
            {
                name = user.getDisplayName(null);
            }
            return name;
        }
        return null;
    }

    public String getLabHeadAffiliation()
    {
        return _labHeadAffiliation;
    }

    public void setLabHeadAffiliation(String labHeadAffiliation)
    {
        _labHeadAffiliation = labHeadAffiliation;
    }

    public Integer getSubmitter()
    {
        return _submitter;
    }

    public User getSubmitterUser()
    {
        return _submitter != null ? UserManager.getUser(_submitter) : null;
    }

    @Nullable
    public String getSubmitterName()
    {
        return getUserName(getSubmitterUser());
    }

    public void setSubmitter(Integer submitter)
    {
        _submitter = submitter;
    }

    public String getSubmitterAffiliation()
    {
        return _submitterAffiliation;
    }

    public void setSubmitterAffiliation(String submitterAffiliation)
    {
        _submitterAffiliation = submitterAffiliation;
    }

    public String getPxid()
    {
        return _pxid;
    }

    public void setPxid(String pxid)
    {
        _pxid = pxid;
    }

    public boolean isPublished()
    {
        if(!StringUtils.isBlank(_publicationLink) && !StringUtils.isBlank(_citation))
        {
            return true;
        }
        return false;
    }
}

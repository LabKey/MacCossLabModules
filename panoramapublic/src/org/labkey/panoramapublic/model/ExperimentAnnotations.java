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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.panoramapublic.query.SubmissionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.labkey.api.targetedms.TargetedMSService.FolderType.Experiment;

/**
 * User: vsharma
 * Date: 2/18/13
 * Time: 3:42 PM
 */
public class ExperimentAnnotations extends DbEntity
{
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
    private boolean _includeSubfolders;
    private ExpExperiment _experiment;

    // The following fields (_sourceExperimentId, _sourceExperimentPath, _shortUrl, _dataVersion) will be populated only if this is a journal copy
    private Integer _sourceExperimentId;
    private String _sourceExperimentPath; // Store this in case the original source experiment and/or container is deleted
    private ShortURLRecord _shortUrl;
    private Integer _dataVersion;

    private String _keywords;
    private Integer _labHead;
    private String _labHeadAffiliation;
    private Integer _submitter;
    private String _submitterAffiliation;
    private String _pxid;
    private String _pubmedId;
    private String _doi;

    private static final Pattern taxIdPattern = Pattern.compile("(.*)\\(taxid:(\\d+)\\)");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    public ExperimentAnnotations() {}

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

    public @NotNull HtmlString getHtmlCitation()
    {
        return getHtmlCitation(_citation);
    }

    public static @NotNull HtmlString getHtmlCitation(String citation)
    {
        if (citation != null)
        {
            var matcher = HTML_TAG.matcher(citation);
            if (matcher.find())
            {
                var errors = new ArrayList<String>();
                PageFlowUtil.validateHtml(citation, errors,
                        false); // errors will be returned if there are <script> tags or any parsing errors
                var sanitized = PageFlowUtil.sanitizeHtml(citation, errors);
                return errors.isEmpty() ? HtmlString.unsafe(sanitized) : HtmlString.of(matcher.replaceAll(""));
            }
            else return HtmlString.of(citation);
        }
        return HtmlString.EMPTY_STRING;
    }

    public boolean hasCitation()
    {
        return !StringUtils.isBlank(_citation);
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
        return _sourceExperimentId != null ;
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

    public Integer getDataVersion()
    {
        return _dataVersion;
    }

    public void setDataVersion(Integer dataVersion)
    {
        _dataVersion = dataVersion;
    }

    public String getStringVersion(Integer currentVersion)
    {
        return _dataVersion == null ? "" : (_dataVersion.equals(currentVersion) ? "Current" : String.valueOf(_dataVersion));
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

    public boolean hasPxid()
    {
        return !StringUtils.isBlank(_pxid);
    }

    public boolean isPublished()
    {
        return !StringUtils.isBlank(_publicationLink);
    }

    public boolean isPeerReviewed()
    {
        String publicationLink = getPublicationLink();
        // Authors use the medRxiv and bioRxiv services to make their manuscripts available as preprints before peer review, allowing
        // other scientists to see, discuss, and comment on the findings immediately.
        return isPublished() && !(publicationLink.contains("www.biorxiv.org") || publicationLink.contains("www.medrxiv.org"));
    }

    public String getPubmedId()
    {
        return _pubmedId;
    }

    public boolean hasPubmedId()
    {
        return !StringUtils.isBlank(_pubmedId);
    }

    public void setPubmedId(String pubmedId)
    {
        _pubmedId = pubmedId;
    }

    public boolean isPublic()
    {
        // If the container where this experiment lives is readable to site:guests then the data is public.
        return getContainer().hasPermission(SecurityManager.getGroup(Group.groupGuests), ReadPermission.class);
    }

    public DataLicense getDataLicense()
    {
        // Return a data license only if this experiment has been copied to Panorama Public (i.e is a "journalCopy").
        if(!isJournalCopy())
        {
            return null;
        }
        return SubmissionManager.getDataLicenseForCopiedExperiment(getId());
    }

    /**
     * Returns true if the experiment is in an 'Experimental Data' folder that is public and the experiment is
     * associated with a published paper.
     */
    public boolean isFinal()
    {
        TargetedMSService.FolderType folderType = TargetedMSService.get().getFolderType(getContainer());
        return Experiment.equals(folderType) && isPublic() && isPublished();
    }

    public boolean hasCompletePublicationInfo()
    {
        return isPeerReviewed() && hasPubmedId();
    }

    public String getDoi()
    {
        return _doi;
    }

    public void setDoi(String doi)
    {
        _doi = doi;
    }

    public boolean hasDoi()
    {
        return !StringUtils.isBlank(_doi);
    }
}

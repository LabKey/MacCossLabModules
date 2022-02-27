/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.panoramapublic.proteomexchange;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.panoramapublic.PanoramaPublicController;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.Submission;
import org.labkey.panoramapublic.model.validation.Modification;
import org.labkey.panoramapublic.model.validation.PxStatus;
import org.labkey.panoramapublic.model.validation.Status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PxHtmlWriter extends PxWriter
{
    private final StringBuilder _output;

    private static final Logger LOG = LogHelper.getLogger(PxHtmlWriter.class, "ProteomeXchange XML summary writer");

    public PxHtmlWriter(StringBuilder out)
    {
        _output = out;
    }

    @Override
    public void write(PanoramaPublicController.PxExperimentAnnotations bean) throws PxException
    {
        super.write(bean);
    }

    @Override
    void begin(ExperimentAnnotations experimentAnnotations)
    {
        _output.append("<div><table class=\"table-condensed table-striped table-bordered\">");
        tr("Experiment ID", String.valueOf(experimentAnnotations.getId()));
        tr("Title", experimentAnnotations.getTitle());
        String pxid = experimentAnnotations.getPxid();
        tr("PX ID", pxid != null ? pxid : "NOT ASSIGNED", pxid == null);
    }

    @Override
    void end()
    {
        _output.append("</table></div>");
    }

    @Override
    void close()
    {
        // Nothing to do.
    }

    @Override
    void writeChangeLog(String pxChangeLog)
    {
        if(!StringUtils.isBlank(pxChangeLog))
        {
            tr("Change Log", pxChangeLog);
        }
    }

    @Override
    void writeDatasetSummary(ExperimentAnnotations expAnnotations, Submission submission, Status validationStatus)
    {
        tr("Description", expAnnotations.getAbstract());
        tr("Review Level", (expAnnotations.isPeerReviewed()) ? "Peer Reviewed" : "Not Peer Reviewed");

        final String complete = "Supported dataset by repository";
        final String incomplete = "supported by repository but incomplete data and/or metadata";
        final String repoSupport = "Repository Support";
        final String override = " (override) ";
        PxStatus pxStatus = validationStatus.getValidation().getStatus();
        if(pxStatus == PxStatus.Complete)
        {
            tr(repoSupport, complete);
        }
        else
        {
            HtmlList list = new HtmlList();
            if(!validationStatus.allModificationsValid())
            {
               list.addItem("Modifications without UNIMOD Ids", "Yes", true);
            }
            if(!validationStatus.specLibsComplete())
            {
                list.addItem("Missing spectrum library source files", "Yes", true);
            }
            if (!validationStatus.foundAllSampleFiles())
            {
                list.addItem("Missing raw files", "Yes", true);
            }
            if(validationStatus.hasMissingMetadata())
            {
                list.addItem("Missing metadata", "Yes", true);
            }
            list.end();
            String submissionTypeTxt;
            if (pxStatus == PxStatus.IncompleteMetadata)
            {
                submissionTypeTxt = submission.isIncompletePxSubmission() ? incomplete
                        // Data validator tell us that his is an incomplete submission but there was an admin override
                        // to submit this as a complete submission.
                        // Use case: data for .blib spectrum libraries was not uploaded to Panorama Public but
                        // was uploaded to another PX repository, and has been verified by an admin.
                        : complete + override;
            }
            else if (expAnnotations.getPxid() != null)
            {
                // Data is not a valid PX submission. User could not have requested a PX ID but a PX ID was assigned by an admin.
                // Use case: This will allow PX submission of data from Jeff Whiteaker's group.  They do not collect .wiff.scan files
                // but we require .wiff.scan files for valid PX submissions. The Whiteaker lab uses an instrument setting that allows
                // them to collect everything in .wiff files.  However, this is not a setting recommended by SCIEX.  It is not
                // easy to determine, just by looking at the Skyline document, that a .wiff file contains all the scans. We will continue
                // to require .wiff.scan files but make an exception for the Whiteaker group.
                submissionTypeTxt = submission.isIncompletePxSubmission() ? incomplete + override : complete + override;
            }
            else
            {
                submissionTypeTxt = String.format("PX ID was %s assigned.  Data cannot be announced on ProteomeXchange",
                        submission.isPxidRequested() ? "not" : "neither requested nor");
            }
            trNoFilter(repoSupport,  submissionTypeTxt + list.getHtml(), true);
        }
    }

    @Override
    void writeDatasetIdentifierList(String pxId, int version, ShortURLRecord accessUrl)
    {
        HtmlList list = new HtmlList();
        list.addItem("PX ID", pxId, StringUtils.isBlank(pxId));
        if(!StringUtils.isBlank(pxId))
        {
            list.addItem("PX Version", String.valueOf(version), false);
        }
        list.addItem("Access URL", getAccessUrlString(accessUrl), accessUrl == null);
        list.end();
        trNoFilter("Dataset identifier", list.getHtml());
    }

    @Override
    void writeDatasetOriginList()
    {
        // Nothing to do.
    }

    @Override
    void writeSpeciesList(ExperimentAnnotations experimentAnnotations)
    {
        Map<String, Integer> organisms = experimentAnnotations.getOrganismAndTaxId();

        List<Integer> taxIds = new ArrayList<>();
        for(Integer taxId: organisms.values())
        {
            if(taxId != null)
            {
                taxIds.add(taxId);
            }
        }

        boolean ncbiLookupError = false;
        Map<Integer, String> sciNameMap = new HashMap<>();
        try
        {
            sciNameMap = getScientificNames(taxIds);
        }
        catch (PxException e)
        {
            ncbiLookupError = true;
            LOG.error("Error getting scientific names from NCBI", e);
        }

        HtmlList list = new HtmlList();
        for(String orgName: organisms.keySet())
        {
            Integer taxid = organisms.get(orgName);
            String sciName = orgName;
            if(taxid != null)
            {
                sciName = sciNameMap.get(taxid);
                if(StringUtils.isBlank(sciName))
                {
                    sciName = "NO_SCI_NAME" + (ncbiLookupError ? " (NCBI lookup error)" : "");
                }
            }
            String name = sciName.equalsIgnoreCase(orgName) ? orgName : sciName + " (" + orgName + ")";
            list.addItem(name, ((taxid != null) ? String.valueOf(taxid) : NO_TAX_ID), taxid == null);
        }
        list.end();
        trNoFilter("Species", list.getHtml());
    }

    @Override
    void writeInstrumentList(ExperimentAnnotations experimentAnnotations)
    {
        List<String> instruments = experimentAnnotations.getInstruments();
        PsiInstrumentParser parser = new PsiInstrumentParser();
        HtmlList instrumentList = new HtmlList();
        for(String instrumentName: instruments)
        {
            boolean lookupError = false;
            PsiInstrumentParser.PsiInstrument instrument = null;
            try
            {
                instrument = parser.getInstrument(instrumentName);
            }
            catch (PxException e)
            {
                lookupError = true;
                LOG.error("Error getting PSI instruments", e);
            }

            instrumentList.addItem(instrumentName, ((instrument != null) ? instrument.getId() : (NO_PSI_ID + (lookupError ? " (Error getting PSI instruments)" : ""))), instrument == null);
        }
        instrumentList.end();
        trNoFilter("Instruments", instrumentList.getHtml());
    }

    @Override
    void writeModificationList(Status validationStatus)
    {
        var mods = validationStatus.getModifications();
        HtmlList modList = new HtmlList();
        for(Modification mod: mods)
        {
            String name = mod.getNameString();
            String value = mod.getUnimodIdStr();
            modList.addItem(name, value, !mod.isValid());
        }
        modList.end();
        trNoFilter("Modifications", modList.getHtml());
    }

    @Override
    void writeContactList(ExperimentAnnotations experimentAnnotations, Submission submission)
    {
        HtmlList contactList = new HtmlList();

        User labHead = experimentAnnotations.getLabHeadUser();
        String labHeadName = labHead != null ? labHead.getFullName() : submission.getLabHeadName();
        String labHeadEmail = labHead != null ? labHead.getEmail() : submission.getLabHeadEmail();
        String labHeadAffiliation = labHead != null ? experimentAnnotations.getLabHeadAffiliation() : submission.getLabHeadAffiliation();

        boolean contactErr = StringUtils.isBlank(labHeadName);
        contactList.addItem("Lab head name", StringUtils.isBlank(labHeadName) ? "NO LAB HEAD. Submitter details will be used." : labHeadName, contactErr);
        contactList.addItem("Lab head email", StringUtils.isBlank(labHeadEmail) ? "NO EMAIL" : labHeadEmail, contactErr);
        if(!StringUtils.isBlank(labHeadAffiliation))
        {
            contactList.addItem("Lab head affiliation", labHeadAffiliation, false);
        }

        User submitter = experimentAnnotations.getSubmitterUser();
        if(submitter == null)
        {
            contactList.addItem("Submitter name","NO SUBMITTER", true);
            contactList.addItem("Submitter email", "NO EMAIL", true);
        }
        else
        {
            String fullName = submitter.getFullName();
            boolean err = StringUtils.isBlank(fullName);
            contactList.addItem("Submitter name", err ? "MISSING NAME" : fullName, err); // We need a name to submit to PX
            contactList.addItem("Submitter email", submitter.getEmail(), false);
        }
        if(experimentAnnotations.getSubmitterAffiliation() != null)
        {
            contactList.addItem("Submitter affiliation", experimentAnnotations.getSubmitterAffiliation(), false);
        }
        trNoFilter("Contact List", contactList.getHtml());
    }

    @Override
    void writePublicationList(ExperimentAnnotations experimentAnnotations)
    {
        HtmlList publicationList = new HtmlList();
        if(experimentAnnotations.isPublished())
        {
            publicationList.addItem("Link: ", experimentAnnotations.getPublicationLink(), false);

            if(experimentAnnotations.hasPubmedId())
            {
                publicationList.addItem("PMID", experimentAnnotations.getPubmedId(), false);
            }
            else
            {
                publicationList.addItem(null, "NO_PUBMED_ID", false);
            }
            if(!StringUtils.isBlank(experimentAnnotations.getCitation()))
            {
                publicationList.addItem("Reference", experimentAnnotations.getCitation(), false);
            }
            else
            {
                publicationList.addItem("Reference", "NO REFERNCE", true);
            }
        }
        else
        {
            publicationList.addItem(null, "NONE", false);
        }
        String announcedAs;
        if(experimentAnnotations.isPublished() && experimentAnnotations.hasCitation())
        {
            announcedAs = experimentAnnotations.hasPubmedId() ? "with pubmedid id: " + experimentAnnotations.getCitation()
                    : "pubmed_id_pending: " + experimentAnnotations.getCitation();
        }
        else if(experimentAnnotations.isPublished() || experimentAnnotations.hasCitation())
        {
            announcedAs = "Dataset with its publication pending";
        }
        else
        {
            announcedAs = "Dataset with no associated published manuscript";
        }
        publicationList.addItem("Announcement Type", announcedAs, false);
        publicationList.end();
        trNoFilter("Publication", publicationList.getHtml());
    }

    @Override
    void writeKeywordList(ExperimentAnnotations experimentAnnotations)
    {
        String keywords = experimentAnnotations.getKeywords();
        HtmlList keywordList = new HtmlList();
        if(!StringUtils.isBlank(keywords))
        {
            String[] array = StringUtils.split(keywords, ",");
            for (String keyword : array)
            {
                keywordList.addItem(null, keyword.trim(), false);
            }
            keywordList.end();
        }
        trNoFilter("Keywords", keywordList.getHtml());
    }

    @Override
    void writeDatasetLinkList(ShortURLRecord accessUrl)
    {
        tr("Dataset URI", PageFlowUtil.filter(getAccessUrlString(accessUrl)), accessUrl == null);
    }

    private void tr(String cell1, String cell2)
    {
        tr(PageFlowUtil.filter(cell1), PageFlowUtil.filter(cell2), false);
    }

    private void trNoFilter(String cell1, String cell2)
    {
        tr(cell1, cell2, false);
    }

    private void trNoFilter(String cell1, String cell2, boolean error)
    {
        tr(cell1, cell2, error);
    }

    // Expects cell1 and cell2 to be HTML encoded.
    private void tr(String cell1, String cell2, boolean error)
    {
        _output.append("<tr><td>").append(cell1).append("</td><td>");
        if(error)
        {
            _output.append("<span style=\"font-weight:bold;color:red\">");
        }
        _output.append(cell2);
        if(error)
        {
            _output.append("</span>");
        }
        _output.append("</td></tr>");
    }

    private static final class HtmlList
    {
        private final StringBuilder _list;

        public HtmlList()
        {
            _list = new StringBuilder();
            _list.append("<ul>");
        }

        public void addItem(String name, String value, boolean error)
        {
            _list.append("<li>");
            if(name != null)
            {
                _list.append(PageFlowUtil.filter(name)).append(": ");
            }
            _list.append("<span style=\"font-weight:bold;color:").append(error ? "red" : "green").append(";\">").append(PageFlowUtil.filter(value)).append("</span>");
            _list.append("</li>");
        }

        public void end()
        {
            _list.append("</ul>");
        }

        public String getHtml()
        {
            return _list.toString();
        }
    }
}

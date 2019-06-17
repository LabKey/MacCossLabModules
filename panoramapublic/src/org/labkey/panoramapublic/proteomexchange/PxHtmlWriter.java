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
package org.labkey.targetedms.proteomexchange;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.targetedms.PublishTargetedMSExperimentsController;
import org.labkey.targetedms.model.ExperimentAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PxHtmlWriter extends PxWriter
{
    private final StringBuilder _output;
    private boolean _usePxTestDb;

    private static final Logger LOG = Logger.getLogger(PxHtmlWriter.class);

    public PxHtmlWriter(StringBuilder out)
    {
        _output = out;
    }

    public void write(PublishTargetedMSExperimentsController.PxExperimentAnnotations bean) throws PxException
    {
        _usePxTestDb = bean.getForm().isTestDatabase();
        super.write(bean);
    }

    @Override
    void begin(ExperimentAnnotations experimentAnnotations)
    {
        _output.append("<div><table class=\"table-condensed table-striped table-bordered\">");
        tr("PX Test Database", String.valueOf(_usePxTestDb));
        tr("Experiment ID", String.valueOf(experimentAnnotations.getId()));
        tr("Title", experimentAnnotations.getTitle());
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
    void writeChangeLog(PublishTargetedMSExperimentsController.PxExportForm form)
    {
        if(!StringUtils.isBlank(form.getChangeLog()))
        {
            tr("Change Log", form.getChangeLog());
        }
    }

    @Override
    void writeDatasetSummary(ExperimentAnnotations expAnnotations, PublishTargetedMSExperimentsController.PxExportForm form)
    {
        tr("Description", expAnnotations.getAbstract());
        tr("Review Level", (form.getPeerReviewed() || expAnnotations.isPublished()) ? "Peer Reviewed" : "Not Peer Reviewed");
    }

    @Override
    void writeDatasetIdentifierList(String pxId, ShortURLRecord accessUrl)
    {
        HtmlList list = new HtmlList();
        list.addItem("PX ID", pxId, StringUtils.isBlank(pxId));
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
            list.addItem(name, ((taxid != null) ? String.valueOf(taxid) : "NO_TAX_ID"), taxid == null);
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

            instrumentList.addItem(instrumentName, ((instrument != null) ? instrument.getId() : ("NO_PSI_ID" + (lookupError ? " (Error getting PSI instruments)" : ""))), instrument == null);
        }
        instrumentList.end();
        trNoFilter("Instruments", instrumentList.getHtml());
    }

    @Override
    void writeModificationList(ExperimentAnnotations experimentAnnotations)
    {
        List<ExperimentModificationGetter.PxModification> mods = ExperimentModificationGetter.getModifications(experimentAnnotations);
        HtmlList modList = new HtmlList();
        for(ExperimentModificationGetter.PxModification mod: mods)
        {
            String name = mod.getName();
            String value = mod.hasUnimodId() ? mod.getUnimodId() : "NO_UNIMOD_ID";
            if(!mod.getName().equals(mod.getSkylineName()))
            {
               name += " (" + mod.getSkylineName() + ")";
            }
            modList.addItem(name, value, !mod.hasUnimodId());
        }
        modList.end();
        trNoFilter("Modifications", modList.getHtml());
    }

    @Override
    void writeContactList(ExperimentAnnotations experimentAnnotations, PublishTargetedMSExperimentsController.PxExportForm form)
    {
        HtmlList contactList = new HtmlList();

        User labHead = experimentAnnotations.getLabHeadUser();
        String labHeadName = labHead != null ? labHead.getFullName() : form.getLabHeadName();
        String labHeadEmail = labHead != null ? labHead.getEmail() : form.getLabHeadEmail();
        String labHeadAffiliation = labHead != null ? experimentAnnotations.getLabHeadAffiliation() : form.getLabHeadAffiliation();

        boolean contactErr = StringUtils.isBlank(labHeadName);
        contactList.addItem("Lab head name", StringUtils.isBlank(labHeadName) ? "NO LAB HEAD. Submitter details will be used." : labHeadName, contactErr);
        contactList.addItem("Lab head email", StringUtils.isBlank(labHeadEmail) ? "NO EMAIL" : labHeadEmail, contactErr);
        if(!StringUtils.isBlank(labHeadAffiliation))
        {
            contactList.addItem("Lab head affiliation", labHeadAffiliation, false);
        }

        User submitter = experimentAnnotations.getSubmitterUser();
        contactErr = submitter == null;
        contactList.addItem("Submitter name", submitter == null ? "NO SUBMITTER" : submitter.getFullName(), contactErr);
        contactList.addItem("Submitter email", submitter == null ? "NO EMAIL" : submitter.getEmail(), contactErr);
        if(experimentAnnotations.getSubmitterAffiliation() != null)
        {
            contactList.addItem("Submitter affiliation", experimentAnnotations.getSubmitterAffiliation(), false);
        }
        trNoFilter("Contact List", contactList.getHtml());
    }

    @Override
    void writePublicationList(ExperimentAnnotations experimentAnnotations, PublishTargetedMSExperimentsController.PxExportForm form)
    {
        HtmlList publicationList = new HtmlList();
        boolean hasPubmedId = !StringUtils.isBlank(form.getPublicationId());
        if(form.getPeerReviewed() || hasPubmedId)
        {
            if(hasPubmedId)
            {
                publicationList.addItem("PMID", form.getPublicationId(), false);
            }
            else
            {
                publicationList.addItem(null, "NO_PUBMED_ID", false);
            }
            String reference = form.getPublicationReference();
            if(StringUtils.isBlank(reference))
            {
                reference = experimentAnnotations.getCitation();
            }
            publicationList.addItem("Reference", reference, false);
        }
        else
        {
            publicationList.addItem(null, "NONE", false);
        }
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
        private StringBuilder _list;

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

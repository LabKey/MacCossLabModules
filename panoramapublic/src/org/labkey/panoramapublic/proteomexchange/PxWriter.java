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

import org.labkey.api.view.ShortURLRecord;
import org.labkey.panoramapublic.PanoramaPublicController;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.Journal;
import org.labkey.panoramapublic.model.JournalExperiment;
import org.labkey.panoramapublic.query.JournalManager;

import java.util.List;
import java.util.Map;

public abstract class PxWriter
{
    public void write(PanoramaPublicController.PxExperimentAnnotations bean) throws PxException
    {
        ExperimentAnnotations expAnnotations = bean.getExperimentAnnotations();
        ShortURLRecord accessUrl = expAnnotations.getShortUrl();
        if(accessUrl == null)
        {
            // This is an experiment in a user project.  short access url is saved in JournalExperiment.
            JournalExperiment je = getJournalExperiment(expAnnotations);
            accessUrl = je == null ? null : je.getShortAccessUrl();
        }

        try
        {
            begin(expAnnotations);
            writeChangeLog(bean.getPxChangeLog());
            writeDatasetSummary(expAnnotations);
            writeDatasetIdentifierList(expAnnotations.getPxid(), bean.getVersion(), accessUrl);
            writeDatasetOriginList();
            writeSpeciesList(expAnnotations);
            writeInstrumentList(expAnnotations);
            writeModificationList(expAnnotations);
            writeContactList(expAnnotations, bean.getJournalExperiment());
            writePublicationList(expAnnotations);
            writeKeywordList(expAnnotations);
            writeDatasetLinkList(accessUrl);
            end();
        }
        finally
        {
            close();
        }
    }

    private  JournalExperiment getJournalExperiment(ExperimentAnnotations expAnnotations)
    {
        Journal journal = JournalManager.getJournal("Panorama Public");
        if(journal == null)
        {
            return null;
        }
        return JournalManager.getJournalExperiment(expAnnotations, journal);
    }

    String getAccessUrlString(ShortURLRecord accessUrl)
    {
        return accessUrl != null ? accessUrl.renderShortURL() : "NO_ACCESS_URL";
    }

    Map<Integer, String> getScientificNames(List<Integer> taxIds) throws PxException
    {
        return NcbiUtils.getScientificNames(taxIds);
    }

    abstract void begin(ExperimentAnnotations experimentAnnotations) throws PxException;
    abstract void end() throws PxException;
    abstract void close() throws PxException;
    abstract void writeChangeLog(String pxChangeLog) throws PxException;
    abstract void writeDatasetSummary(ExperimentAnnotations expAnnotations) throws PxException;
    abstract void writeDatasetIdentifierList(String pxId, int version, ShortURLRecord accessUrl) throws PxException;
    abstract void writeDatasetOriginList() throws PxException;
    abstract void writeSpeciesList(ExperimentAnnotations experimentAnnotations) throws PxException;
    abstract void writeInstrumentList(ExperimentAnnotations experimentAnnotations) throws PxException;
    abstract void writeModificationList(ExperimentAnnotations experimentAnnotations) throws PxException;
    abstract void writeContactList(ExperimentAnnotations experimentAnnotationsn, JournalExperiment je) throws PxException;
    abstract void writePublicationList(ExperimentAnnotations experimentAnnotations) throws PxException;
    abstract void writeKeywordList(ExperimentAnnotations experimentAnnotations) throws PxException;
    abstract void writeDatasetLinkList(ShortURLRecord accessUrl) throws PxException;

}

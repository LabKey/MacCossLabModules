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
package org.labkey.targetedms;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExperimentListener;
import org.labkey.api.security.User;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.api.view.ShortURLService;
import org.labkey.targetedms.model.ExperimentAnnotations;
import org.labkey.targetedms.model.Journal;
import org.labkey.targetedms.model.JournalExperiment;
import org.labkey.targetedms.parser.blib.BlibSpectrumReader;
import org.labkey.targetedms.query.ExperimentAnnotationsManager;
import org.labkey.targetedms.query.JournalManager;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * User: vsharma
 * Date: 8/22/2014
 * Time: 3:22 PM
 */
public class TargetedMSListener implements ExperimentListener, ContainerManager.ContainerListener,
        ShortURLService.ShortURLListener
{
    @Override
    public void beforeExperimentDeleted(Container c, User user, ExpExperiment experiment)
    {
        ExperimentAnnotationsManager.beforeDeleteExpExperiment(experiment, user);
    }

    @Override
    public void containerCreated(Container c, User user)
    {
    }

    @Override
    public void containerDeleted(Container c, User user)
    {
        JournalManager.deleteProjectJournal(c, user);

        // Delete any runs that might have failed to fully import and therefore won't have a wrapper experiment run.
        // See issue 34752
        TargetedMSManager.deleteIncludingExperimentWrapper(c, user);

        // Clean up QC annotations
        new SqlExecutor(TargetedMSManager.getSchema()).execute("DELETE FROM " + TargetedMSManager.getTableInfoQCAnnotation() + " WHERE Container = ?", c);
        new SqlExecutor(TargetedMSManager.getSchema()).execute("DELETE FROM " + TargetedMSManager.getTableInfoQCAnnotationType() + " WHERE Container = ?", c);

        // Clean up Guide Sets
        new SqlExecutor(TargetedMSManager.getSchema()).execute("DELETE FROM " + TargetedMSManager.getTableInfoGuideSet() + " WHERE Container = ?", c);

        // Clean up any orphaned iRT scales
        TargetedMSManager.deleteiRTscales(c);

        // Clean up AutoQCPing
        new SqlExecutor(TargetedMSManager.getSchema()).execute("DELETE FROM " + TargetedMSManager.getTableInfoAutoQCPing() + " WHERE Container = ?", c);

        // Clean up Metric Configurations
        new SqlExecutor(TargetedMSManager.getSchema()).execute("DELETE FROM " + TargetedMSManager.getTableInfoQCMetricConfiguration() + " WHERE Container = ?", c);

        //Clean up QC enabled metrics
        new SqlExecutor(TargetedMSManager.getSchema()).execute("DELETE FROM " + TargetedMSManager.getTableInfoQCEnabledMetrics() + " WHERE Container = ?", c);

        BlibSpectrumReader.clearBlibCache(c);
    }

    @Override
    public void containerMoved(Container c, Container oldParent, User user)
    {
    }

    @NotNull
    @Override
    public Collection<String> canMove(Container c, Container newParent, User user)
    {
        return Collections.emptyList();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
    }

    @NotNull
    @Override
    public List<String> canDelete(ShortURLRecord shortUrl)
    {
        List<JournalExperiment> journalExperiments = JournalManager.getRecordsForShortUrl(shortUrl);
        if(journalExperiments.size() > 0)
        {
            List<String> errors = new ArrayList<>();
            String url = shortUrl.getShortURL();
            for(JournalExperiment je: journalExperiments)
            {
                ExperimentAnnotations experiment = ExperimentAnnotationsManager.get(je.getExperimentAnnotationsId());
                Journal journal = JournalManager.getJournal(je.getJournalId());

                errors.add("Short URL \"" + url + "\" is associated with the experiment \"" + experiment.getTitle() + "\" published to \"" + journal.getName() + "\"");
            }
            return errors;
        }
        else
        {
            // Check if this short URL is associated with a published experiment in a journal (e.g. Panorama Public) folder.
            ExperimentAnnotations expAnnotations = ExperimentAnnotationsManager.getExperimentForShortUrl(shortUrl);
            if(expAnnotations != null)
            {
                List<String> errors = new ArrayList<>();
                errors.add("Short URL \"" + shortUrl.getShortURL() + "\" is associated with the experiment \"" + expAnnotations.getTitle()
                        + "\" in the folder \"" + expAnnotations.getContainer().getPath() + "\"");
                return errors;
            }
        }
        return Collections.emptyList();
    }
}

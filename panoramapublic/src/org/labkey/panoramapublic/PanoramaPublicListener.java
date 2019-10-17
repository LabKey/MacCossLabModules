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
package org.labkey.panoramapublic;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentListener;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.SkylineDocumentImportListener;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.api.view.ShortURLService;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.Journal;
import org.labkey.panoramapublic.model.JournalExperiment;
import org.labkey.panoramapublic.query.ExperimentAnnotationsManager;
import org.labkey.panoramapublic.query.JournalManager;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * User: vsharma
 * Date: 8/22/2014
 * Time: 3:22 PM
 */
public class PanoramaPublicListener implements ExperimentListener, ContainerManager.ContainerListener,
        ShortURLService.ShortURLListener, SkylineDocumentImportListener
{
    @Override
    public void beforeExperimentDeleted(Container c, User user, ExpExperiment experiment)
    {
        ExperimentAnnotationsManager.beforeDeleteExpExperiment(experiment, user);
    }

    @Override
    public void containerCreated(Container c, User user)
    {
        if(TargetedMSService.get().isPanoramaExperimentalDataFolder(c))
        {
            if(!c.getActiveModules().contains(PanoramaPublicModule.class))
            {
                Set<Module> modules = c.getActiveModules();
                modules.add(ModuleLoader.getInstance().getModule(PanoramaPublicModule.class));
                c.setActiveModules(modules);
            }
        }
    }

    @Override
    public void containerDeleted(Container c, User user)
    {
        JournalManager.deleteProjectJournal(c, user);
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

    @Override
    public void onDocumentImport(Container container, User user, ITargetedMSRun run)
    {
        // Check if the PanoramaPublic module is enabled in this folder
        if (!container.getActiveModules().contains(ModuleLoader.getInstance().getModule(PanoramaPublicModule.class)))
        {
            return;
        }

        // Check if an experiment is defined in the this folder, or if an experiment defined in a parent folder
        // has been configured to include subfolders.
        ExperimentAnnotations expAnnotations = ExperimentAnnotationsManager.getExperimentIncludesContainer(container);
        if (expAnnotations != null)
        {
            ExpData expData = ExperimentService.get().getExpData(run.getDataId());
            if(expData != null)
            {
                expAnnotations.getExperiment().addRuns(user, new ExpRun[] {expData.getRun()});
            }
        }
    }
}

/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.targetedms.pipeline;

import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.targetedms.model.ExperimentAnnotations;
import org.labkey.targetedms.model.Journal;

import java.io.File;
import java.io.IOException;

/**
 * User: vsharma
 * Date: 8/21/2014
 * Time: 9:31 AM
 */
public class CopyExperimentPipelineJob extends PipelineJob implements CopyExperimentJobSupport
{

    private final ExperimentAnnotations _experimentAnnotations;
    private final Journal _journal;
    private final String _description;

    public CopyExperimentPipelineJob(ViewBackgroundInfo info, PipeRoot root, ExperimentAnnotations experiment, Journal journal) throws IOException
    {
        super(CopyExperimentPipelineProvider.NAME, info, root);
        _experimentAnnotations = experiment;
        _journal = journal;
        _description = "Copying experiment:  " + experiment.getTitle();

        setLogFile(getLogFileFor(root, experiment));

        header("Copying experiment \"" + experiment.getTitle() + "\" from folder "
                + experiment.getContainer().getPath() + " to " + getContainer().getPath());
    }

    public ActionURL getStatusHref()
    {
        if (getContainer() != null)
        {
            return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(getContainer());
        }
        return null;
    }

    public String getDescription()
    {
        return _description;
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(CopyExperimentPipelineJob.class));
    }

    public static File getLogFileFor(PipeRoot root, ExperimentAnnotations experimentAnnotations) throws IOException
    {
        File rootDir = root.getRootPath();
        if (!rootDir.exists())
        {
            throw new IOException("Pipeline root directory " + rootDir.getAbsolutePath() + " does not exist.");
        }

        String logFileName = "Experiment_" + experimentAnnotations.getExperimentId() + ".log";

        return new File(rootDir, logFileName);
    }

    @Override
    public ExperimentAnnotations getExpAnnotations()
    {
        return _experimentAnnotations;
    }

    @Override
    public Journal getJournal()
    {
        return _journal;
    }

    @Override
    public File getExportDir()
    {
        Container source = _experimentAnnotations.getContainer();
        PipeRoot root = getPipeRoot();
        if (!root.isValid())
        {
            throw new NotFoundException("No valid pipeline root found for " + source.getPath());
        }

        return root.resolvePath("export");
    }

    @Override
    public File getImportDir()
    {
        PipeRoot pipelineRoot = getPipeRoot();
        return pipelineRoot.resolvePath(PipelineService.UNZIP_DIR);
    }

    @Override
    public String getExportZipFileName()
    {
        return "experiment_" + _experimentAnnotations.getExperimentId() + ".folder.zip";
    }
}

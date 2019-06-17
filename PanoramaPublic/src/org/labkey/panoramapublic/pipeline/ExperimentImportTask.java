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
package org.labkey.targetedms.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporterImpl;
import org.labkey.api.admin.PipelineJobLoggerGetter;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.security.User;
import org.labkey.api.util.FileType;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.VirtualFile;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * User: vsharma
 * Date: 8/28/2014
 * Time: 7:28 AM
 */
public class ExperimentImportTask extends PipelineJob.Task<ExperimentImportTask.Factory>
{
    private ExperimentImportTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        CopyExperimentJobSupport support = job.getJobSupport(CopyExperimentJobSupport.class);
        try
        {
            job.getLogger().info("");
            job.getLogger().info("Importing experiment.");
            doImport(job, support);
            job.getLogger().info("");
            job.getLogger().info("Experiment import completed successfully.");

        }
        catch (Throwable t)
        {
            job.getLogger().fatal("");
            job.getLogger().fatal("Exception during experiment import", t);
            job.getLogger().fatal("Experiment import FAILED");
            throw new PipelineJobException(t);
        }

        return new RecordedActionSet();
    }

    public static void doImport(PipelineJob job, CopyExperimentJobSupport jobSupport) throws Exception
    {
        File importDir = jobSupport.getExportDir();

        if (!importDir.exists())
        {
            throw new Exception("TargetedMS experiment import failed: Could not find directory \"" + importDir.getName() + "\"");
        }

        File folderXml = new File(importDir, "folder.xml");
        if(!folderXml.exists()){
            throw new Exception("This directory doesn't contain an appropriate xml: " + importDir.getAbsolutePath());
        }

        User user = job.getUser();
        Container container = job.getContainer();
        VirtualFile importJobRoot = new FileSystemFile(folderXml.getParentFile());
        FolderImportContext importCtx = new FolderImportContext(user, container, folderXml,
                null, new PipelineJobLoggerGetter(job),
                importJobRoot);
        importCtx.setSkipQueryValidation(true);

        FolderImporterImpl importer = new FolderImporterImpl(job);
        importer.process(job, importCtx, importJobRoot);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(ExperimentImportTask.class);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new ExperimentImportTask(this, job);
        }

        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        public String getStatusName()
        {
            return "IMPORT EXPERIMENT";
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}

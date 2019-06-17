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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.FolderWriterImpl;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.StaticLoggerGetter;
import org.labkey.api.data.Container;
import org.labkey.api.data.PHI;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.security.User;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.targetedms.model.ExperimentAnnotations;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * User: vsharma
 * Date: 8/28/2014
 * Time: 7:28 AM
 */
public class ExperimentExportTask extends PipelineJob.Task<ExperimentExportTask.Factory>
{
    private ExperimentExportTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        CopyExperimentJobSupport support = job.getJobSupport(CopyExperimentJobSupport.class);
        doExport(job, support);

        return new RecordedActionSet();
    }

    public void doExport(PipelineJob job, CopyExperimentJobSupport jobSupport) throws PipelineJobException
    {
        try
        {
            ExperimentAnnotations exptAnnotations = jobSupport.getExpAnnotations();

            job.getLogger().info("");
            job.getLogger().info("Exporting experiment.");
            writeExperiment(jobSupport, exptAnnotations, job.getUser());
            job.getLogger().info("");
            job.getLogger().info("Experiment export completed successfully.");
        }
        catch (Throwable t)
        {
            job.getLogger().fatal("");
            job.getLogger().fatal("Exception during experiment export", t);
            job.getLogger().fatal("Experiment export FAILED");
            throw new PipelineJobException(t) {};
        }
    }

    public void writeExperiment(CopyExperimentJobSupport support, ExperimentAnnotations exptAnnotations, User user) throws Exception
    {
        // This is what we will export from the folder (and subfolders, if required)
        String[] templateWriterTypes = new String[] {
                FolderArchiveDataTypes.FOLDER_TYPE_AND_ACTIVE_MODULES, // "Folder type and active modules",
                FolderArchiveDataTypes.WEBPART_PROPERTIES_AND_LAYOUT, // "Webpart properties and layout",
                FolderArchiveDataTypes.QUERIES, // "Queries",
                FolderArchiveDataTypes.GRID_VIEWS, // "Custom Grid Views",
                FolderArchiveDataTypes.REPORTS_AND_CHARTS, // "Reports & Charts",
                FolderArchiveDataTypes.WIKIS_AND_THEIR_ATTACHMENTS, // "Wikis and their attachments",
                // DO NOT copy module properties.  Set the TargetedMS Folder Type property in CopyExperimentFinalTask instead.
                // For properties such as SKIP_CHROMATOGRAM_IMPORT_PROPERTY we want the copied folders to inherit the values
                // in the Panorama Public project.
                // FolderArchiveDataTypes.CONTAINER_SPECIFIC_MODULE_PROPERTIES, // "Container specific module properties",
                FolderArchiveDataTypes.EXPERIMENTS_AND_RUNS, // "Experiments and runs"
                FolderArchiveDataTypes.LISTS, // "Lists"
                FolderArchiveDataTypes.FILES  // "Files"
                };


        boolean includeSubfolders = exptAnnotations.isIncludeSubfolders();
        Container source = exptAnnotations.getContainer();
        FolderWriterImpl writer = new FolderWriterImpl();

        FolderExportContext ctx = new FolderExportContext(user, source, PageFlowUtil.set(templateWriterTypes),
                null, includeSubfolders, PHI.NotPHI, false,
                false, false, new StaticLoggerGetter(Logger.getLogger(FolderWriterImpl.class)));


        File exportDir = support.getExportDir();
        FileUtil.deleteDir(exportDir);
        if(exportDir.exists())
        {
            throw new Exception("Could not delete already existing export directory " + exportDir.getAbsolutePath());
        }
        if(!exportDir.mkdir())
        {
            throw new Exception("Could not create directory " + exportDir.getAbsolutePath());
        }

        writer.write(source, ctx, new FileSystemFile(exportDir));
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(ExperimentExportTask.class);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new ExperimentExportTask(this, job);
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
            return "EXPORT EXPERIMENT";
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}

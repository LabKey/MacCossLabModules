/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.nextflow.pipeline;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.ParamParser;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.file.AbstractFileAnalysisJob;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.writer.PrintWriters;
import org.labkey.nextflow.NextFlowManager;
import org.labkey.vfs.FileLike;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Getter
public class NextFlowPipelineJob extends AbstractFileAnalysisJob
{
    protected static final Logger LOG = LogHelper.getLogger(NextFlowPipelineJob.class, "NextFlow jobs");

    private FileLike config;

    @SuppressWarnings("unused") // For serialization
    protected NextFlowPipelineJob()
    {}

    public static NextFlowPipelineJob create(ViewBackgroundInfo info, @NotNull PipeRoot root, Path templateConfig, List<FileLike> inputFiles) throws IOException
    {
        FileLike parentDir = inputFiles.getFirst().getParent();

        String jobName = FileUtil.makeFileNameWithTimestamp("NextFlow");
        FileLike jobDir = parentDir.resolveChild(jobName);
        FileLike log = jobDir.resolveChild(jobName + ".log");
        FileUtil.createDirectory(jobDir);

        FileLike config = createConfig(templateConfig, parentDir, jobDir, info.getContainer());

        return new NextFlowPipelineJob(info, root, config, inputFiles, log);
    }

    public NextFlowPipelineJob(ViewBackgroundInfo info, @NotNull PipeRoot root, FileLike config, List<FileLike> inputFiles, FileLike log) throws IOException
    {
        super(new NextFlowProtocol(), NextFlowPipelineProvider.NAME, info, root, config.getName(), config, inputFiles, false);
        this.config = config;
        setLogFile(log);
    }

    public JSONObject getJsonJobInfo(boolean includeInvocationCount)
    {
        JSONObject result = new JSONObject();
        result.put("user", getUser().getEmail());
        result.put("container", getContainer().getPath());
        result.put("filePath", getLogFilePath().getParent().toString());
        result.put("runName", getNextFlowRunName(includeInvocationCount));
        result.put("configFile", getConfig().getName());
        return result;
    }

    protected String getNextFlowRunName(boolean includeInvocationCount)
    {
        PipelineStatusFile file = PipelineService.get().getStatusFile(getJobGUID());
        String result = file == null ? "Unknown" : ("LabKeyJob" + file.getRowId());
        result += includeInvocationCount ? ("_" + NextFlowManager.get().getInvocationCount(this)) : "";
        return result;
    }

    @Override
    public ParamParser getInputParameters()
    {
        return PipelineJobService.get().createParamParser();
    }

    /** Take the template config file and substitute in the values for this job */
    private static FileLike createConfig(Path configTemplate, FileLike parentDir, FileLike jobDir, Container container) throws IOException
    {
        String template;
        try (InputStream in = Files.newInputStream(configTemplate))
        {
            template = PageFlowUtil.getStreamContentsAsString(in);
        }

        String webdavUrl = FileContentService.get().getWebDavUrl(parentDir, container, FileContentService.PathType.full).toString();
        webdavUrl = StringUtils.stripEnd(webdavUrl, "/");
        String substitutedContent = template.replace("${quant_spectra_dir}", "quant_spectra_dir = '" + webdavUrl + "'");

        String uploadUrl = FileContentService.get().getWebDavUrl(jobDir, container, FileContentService.PathType.full).toString();
        uploadUrl = StringUtils.stripEnd(uploadUrl, "/");
        substitutedContent = substitutedContent.replace("${panorama.upload_url}", "panorama.upload_url = '" + uploadUrl + "'");

        FileLike substitutedFile = jobDir.resolveChild(configTemplate.getFileName().toString());
        try (BufferedWriter writer = new BufferedWriter(PrintWriters.getPrintWriter(substitutedFile.openOutputStream())))
        {
            writer.write(substitutedContent);
        }
        return substitutedFile;
    }

    @Override
    public String getDescription()
    {
        return "NextFlow analysis of " + StringUtilsLabKey.pluralize(getInputFiles().size(), "file") + " using config: " + config.getName();
    }

    @Override
    public TaskPipeline<?> getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(getTaskPipelineId());
    }

    @Override
    public TaskId getTaskPipelineId()
    {
        return new TaskId(NextFlowPipelineJob.class);
    }

    @Override
    public AbstractFileAnalysisJob createSingleFileJob(FileLike file)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileLike findInputFile(String name)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileLike findOutputFile(String name)
    {
        return null;
    }
}

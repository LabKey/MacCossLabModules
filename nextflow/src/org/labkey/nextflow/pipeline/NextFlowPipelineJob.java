package org.labkey.nextflow.pipeline;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.ParamParser;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.file.AbstractFileAnalysisJob;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Getter
public class NextFlowPipelineJob extends AbstractFileAnalysisJob
{
    private Path config;

    @SuppressWarnings("unused") // For serialization
    protected NextFlowPipelineJob()
    {}

    public static NextFlowPipelineJob create(ViewBackgroundInfo info, @NotNull PipeRoot root, Path templateConfig, List<Path> inputFiles) throws IOException
    {
        Path parentDir = inputFiles.get(0).getParent();

        String jobName = FileUtil.makeFileNameWithTimestamp("NextFlow");
        Path jobDir = parentDir.resolve(jobName);
        Path log = jobDir.resolve(jobName + ".log");
        FileUtil.createDirectory(jobDir);

        Path config = createConfig(templateConfig, parentDir, jobDir, info.getContainer());

        return new NextFlowPipelineJob(info, root, config, inputFiles, log);
    }

    public NextFlowPipelineJob(ViewBackgroundInfo info, @NotNull PipeRoot root, Path config, List<Path> inputFiles, Path log) throws IOException
    {
        super(new NextFlowProtocol(), NextFlowPipelineProvider.NAME, info, root, config.getFileName().toString(), config, inputFiles, false, false);
        this.config = config;
        setLogFile(log);
    }

    @Override
    public ParamParser getInputParameters()
    {
        return PipelineJobService.get().createParamParser();
    }

    /** Take the template config file and substitute in the values for this job */
    private static Path createConfig(Path configTemplate, Path parentDir, Path jobDir, Container container) throws IOException
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

        Path substitutedFile = jobDir.resolve(configTemplate.getFileName());
        try (BufferedWriter writer = Files.newBufferedWriter(substitutedFile))
        {
            writer.write(substitutedContent);
        }
        return substitutedFile;
    }

    @Override
    public String getDescription()
    {
        return "NextFlow analysis using " + config.getFileName() + " of " + getInputFilePaths().size() + " files";
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
    public AbstractFileAnalysisJob createSingleFileJob(File file)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public File findInputFile(String name)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public File findOutputFile(String name)
    {
        return null;
    }
}

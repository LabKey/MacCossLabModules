package org.labkey.lincs.psp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.labkey.api.pipeline.LocalDirectory;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.lincs.LincsModule;

public class LincsPspPipelineJob extends PipelineJob implements LincsPspJobSupport
{
    private ITargetedMSRun _run;
    private String _description;
    private LincsPspJob _pspJob;
    private LincsPspJob _oldPspJob;

    @JsonCreator
    protected LincsPspPipelineJob(@JsonProperty("_run") ITargetedMSRun run, @JsonProperty("_pspJob") LincsPspJob pspJob,
                                  @JsonProperty("_oldPspJob") LincsPspJob oldPspJob,
                                  @JsonProperty("_description") String description)
    {
        super();
        _run = run;
        _pspJob = pspJob;
        _oldPspJob = oldPspJob;
        _description = description;
    }

    public LincsPspPipelineJob(ViewBackgroundInfo info, PipeRoot root, ITargetedMSRun run, LincsPspJob pspJob, LincsPspJob oldPspJob)
    {
        super(LincsPspPipelineProvider.NAME, info, root);
        _run = run;
        _pspJob = pspJob;
        _oldPspJob = oldPspJob;
        _description = (_oldPspJob != null ? "Re-r" : "R") + "unning LINCS pipeline for:  " + run.getBaseName();

        String baseLogFileName = FileUtil.makeFileNameWithTimestamp("LincsPSP_" + (_oldPspJob != null ? "rerun_" : "") + run.getBaseName().replace(" ", "_"));

        LocalDirectory localDirectory = LocalDirectory.create(root, LincsModule.NAME, baseLogFileName,
                !root.isCloudRoot() ? root.getRootPath().getAbsolutePath() : FileUtil.getTempDirectory().getPath());
        setLocalDirectory(localDirectory);
        setLogFile(localDirectory.determineLogFile());

        header(_description);
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(LincsPspPipelineJob.class));
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public ITargetedMSRun getRun()
    {
        return _run;
    }

    @Override
    public LincsPspJob getPspJob()
    {
        return _pspJob;
    }

    @Override
    public LincsPspJob getOldPspJob()
    {
        return _oldPspJob;
    }
}

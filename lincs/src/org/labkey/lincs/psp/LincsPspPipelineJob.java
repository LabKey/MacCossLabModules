/*
 * Copyright (c) 2019-2026 LabKey Corporation
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

public class LincsPspPipelineJob extends PipelineJob implements LincsPspJobSupport
{
    private final ITargetedMSRun _run;
    private final String _description;
    private final LincsPspJob _pspJob;
    private final LincsPspJob _oldPspJob;

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

        LocalDirectory localDirectory = LocalDirectory.create(root, baseLogFileName,
                !root.isCloudRoot() ? root.getRootFileLike() : FileUtil.getTempDirectoryFileLike());
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

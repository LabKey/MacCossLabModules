/*
 * Copyright (c) 2022-2026 LabKey Corporation
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
package org.labkey.panoramapublic.pipeline;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.panoramapublic.model.ExperimentAnnotations;

public class PxDataValidationPipelineJob extends PipelineJob implements PxDataValidationJobSupport
{
    private final ExperimentAnnotations _experimentAnnotations;
    private final int _validationId;
    private final String _description;

    @JsonCreator
    protected PxDataValidationPipelineJob(@JsonProperty("_experimentAnnotations") ExperimentAnnotations experiment,
                                          @JsonProperty("_validation") int validationId,
                                          @JsonProperty("_description") String description)
    {
        super();
        _experimentAnnotations = experiment;
        _validationId = validationId;
        _description = description;
    }

    public PxDataValidationPipelineJob(ViewBackgroundInfo info, PipeRoot root, ExperimentAnnotations experiment, int validationId)
    {
        super(PxValidationPipelineProvider.NAME, info, root);
        _experimentAnnotations = experiment;
        _validationId = validationId;
        _description = String.format("Validating data for experiment Id: %d, validation Id: %d", experiment.getId(), validationId);

        String baseLogFileName = FileUtil.makeFileNameWithTimestamp("Experiment_Validation_" + experiment.getExperimentId(), "log");
        setLogFile(root.getLogDirectory(true).resolveChild(baseLogFileName));

        header("Validating data for a ProteomeXchange submission.");
    }

    @Override
    public ActionURL getStatusHref()
    {
        if (getContainer() != null)
        {
            return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(getContainer());
        }
        return null;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(PxDataValidationPipelineJob.class));
    }

    @Override
    public ExperimentAnnotations getExpAnnotations()
    {
        return _experimentAnnotations;
    }

    @Override
    public int getValidationId()
    {
        return _validationId;
    }
}

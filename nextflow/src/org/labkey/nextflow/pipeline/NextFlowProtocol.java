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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProtocol;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProtocolFactory;
import org.labkey.api.util.FileType;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.vfs.FileLike;

import java.util.List;
import java.util.Map;

public class NextFlowProtocol extends AbstractFileAnalysisProtocol<NextFlowPipelineJob>
{
    public static final List<FileType> INPUT_TYPES = List.of(
            new FileType(".raw"),
            new FileType(".mzML"));

    public NextFlowProtocol()
    {
        super("NextFlow", null, null);
    }

    @Override
    public List<FileType> getInputTypes()
    {
        return INPUT_TYPES;
    }

    @Override
    public void setXml(String xml)
    {
        // No-op since NextFlow doesn't use XML
    }

    @Override
    public AbstractFileAnalysisProtocolFactory<NextFlowProtocol> getFactory()
    {
        return new AbstractFileAnalysisProtocolFactory<>()
        {
            @Override
            public NextFlowProtocol createProtocolInstance(String name, String description, String xml, Container container)
            {
                return new NextFlowProtocol();
            }

            @Override
            public FileLike getDefaultParametersFile(PipeRoot root)
            {
                return null;
            }

            @Override
            public String getName()
            {
                return "NextFlow";
            }
        };
    }

    @Override
    public NextFlowPipelineJob createPipelineJob(ViewBackgroundInfo info, PipeRoot root, List<FileLike> filesInput, FileLike fileParameters, @Nullable Map<String, String> variableMap)
    {
        throw new UnsupportedOperationException();
    }
}

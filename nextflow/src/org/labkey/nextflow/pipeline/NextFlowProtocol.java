package org.labkey.nextflow.pipeline;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProtocol;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProtocolFactory;
import org.labkey.api.util.FileType;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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
            public NextFlowProtocol createProtocolInstance(String name, String description, String xml)
            {
                return new NextFlowProtocol();
            }

            @Override
            public Path getDefaultParametersFile(PipeRoot root)
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
    public NextFlowPipelineJob createPipelineJob(ViewBackgroundInfo info, PipeRoot root, List<File> filesInput, File fileParameters, @Nullable Map<String, String> variableMap) throws IOException
    {
        throw new UnsupportedOperationException();
    }
}

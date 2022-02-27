package org.labkey.panoramapublic.model.validation;

import org.labkey.api.data.Container;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.stream.Collectors;

public abstract class SkylineDocValidation<S extends SkylineDocSampleFile>
{
    private int _id;
    private int _validationId;
    private long _runId; // Refers to targetedms.runs.id
    private Container _container; // Container in which the Skyline document was imported
    private String _name; // Name of the .sky.zip file

    public abstract @NotNull List<S> getSampleFiles();

    public int getId()
    {
        return _id;
    }

    public void setId(int id)
    {
        _id = id;
    }

    public int getValidationId()
    {
        return _validationId;
    }

    public void setValidationId(int validationId)
    {
        _validationId = validationId;
    }

    public long getRunId()
    {
        return _runId;
    }

    public void setRunId(long runId)
    {
        _runId = runId;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public boolean isValid()
    {
        return foundAllSampleFiles();
    }

    public boolean foundAllSampleFiles()
    {
        return getSampleFiles().stream().allMatch(f -> !f.isPending() && f.found());
    }

    public boolean isPending()
    {
        return getSampleFiles().stream().anyMatch(DataFile::isPending);
    }

    public List<String> getMissingSampleFileNames()
    {
        return getSampleFiles().stream().filter(f -> !f.found()).map(DataFile::getName).collect(Collectors.toList());
    }
}

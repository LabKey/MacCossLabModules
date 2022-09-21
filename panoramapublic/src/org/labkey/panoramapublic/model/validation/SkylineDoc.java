package org.labkey.panoramapublic.model.validation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.old.JSONArray;
import org.json.old.JSONObject;
import org.labkey.api.data.Container;

import java.util.Collections;
import java.util.List;

// For table panoramapublic.skylinedocvalidation
public class SkylineDoc extends SkylineDocValidation<SkylineDocSampleFile>
{
    private List<SkylineDocSampleFile> _sampleFiles;
    private Container _container;

    public void setSampleFiles(List<SkylineDocSampleFile> sampleFiles)
    {
        _sampleFiles = sampleFiles;
    }

    @Override
    public @NotNull List<SkylineDocSampleFile> getSampleFiles()
    {
        return _sampleFiles != null ? Collections.unmodifiableList(_sampleFiles) : Collections.emptyList();
    }

    public void setRunContainer(Container container)
    {
        _container = container;
    }

    public @Nullable Container getRunContainer()
    {
        return _container;
    }

    public String getNameAndUserGivenName()
    {
        String userGivenName = getUserGivenName();
        return getName() + (userGivenName != null && !userGivenName.equals(getName()) ? " (" + userGivenName + ") " : "");
    }

    @NotNull
    public JSONObject toJSON(Container experimentContainer)
    {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", getId());
        jsonObject.put("runId", getRunId());
        if (getRunContainer() != null)
        {
            jsonObject.put("container", getRunContainer().getPath());
            if (experimentContainer != null)
            {
                String relPath = "/" + experimentContainer.getParsedPath().relativize(getRunContainer().getParsedPath()).getName();
                jsonObject.put("rel_container", relPath);
            }
        }
        jsonObject.put("name", getNameAndUserGivenName());
        jsonObject.put("valid", foundAllSampleFiles());
        jsonObject.put("sampleFiles", getSampleFilesJSON());
        return jsonObject;
    }

    @NotNull
    private JSONArray getSampleFilesJSON()
    {
        JSONArray result = new JSONArray();
        for (SkylineDocSampleFile sampleFile: getSampleFiles())
        {
            result.put(sampleFile.toJSON(getRunContainer()));
        }
        return result;
    }
}

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
package org.labkey.panoramapublic.model.validation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
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
                String relPath = "/" + experimentContainer.getParsedPath().relativize(getRunContainer().getParsedPath()).toString();
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

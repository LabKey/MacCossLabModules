/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.panoramapublic.datacite;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

public class Doi
{
    private final String _doi;
    private final String _state; // e.g. 'draft', 'findable' etc.

    private Doi(String doi, String state)
    {
        _doi = doi;
        _state = state;
    }

    public String getDoi()
    {
        return _doi;
    }

    public String getState()
    {
        return _state;
    }

    public boolean isFindable()
    {
        return "findable".equals(_state);
    }

    @Nullable
    static Doi fromJson(@NotNull JSONObject json)
    {
        if (json.has("data"))
        {
            JSONObject data = json.getJSONObject("data");
            JSONObject attribs = data.getJSONObject("attributes");
            if (attribs != null)
            {
                String doi = attribs.getString("doi");
                String state = attribs.getString("state");
                return new Doi(doi, state);
            }
        }
        return null;
    }
}

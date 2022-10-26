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

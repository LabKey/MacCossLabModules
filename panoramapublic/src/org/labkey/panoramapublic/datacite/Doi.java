package org.labkey.panoramapublic.datacite;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    static Doi fromJson(@NotNull org.json.simple.JSONObject json)
    {
        if(json.containsKey("data"))
        {
            org.json.simple.JSONObject data = (org.json.simple.JSONObject) json.get("data");
            org.json.simple.JSONObject attribs = (org.json.simple.JSONObject) data.get("attributes");
            if(attribs != null)
            {
                String doi = (String) attribs.get("doi");
                String state = (String) attribs.get("state");
                return new Doi(doi, state);
            }
        }
        return null;
    }
}

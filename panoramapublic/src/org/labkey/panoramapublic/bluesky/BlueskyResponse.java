package org.labkey.panoramapublic.bluesky;

import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.panoramapublic.datacite.DataCiteException;
import org.labkey.panoramapublic.datacite.DataCiteService;
import org.labkey.panoramapublic.datacite.Doi;

import java.net.HttpURLConnection;

class BlueskyResponse
{
    private final int _responseCode;
    private final String _message;
    private final String _responseBody;

    public BlueskyResponse(int responseCode, String message, String responseBody)
    {
        _responseCode = responseCode;
        _message = message;
        _responseBody = responseBody;
    }

    public int getResponseCode()
    {
        return _responseCode;
    }

    public String getMessage()
    {
        return _message;
    }

    public String getResponseBody()
    {
        return _responseBody;
    }

    public boolean success()
    {
        return _responseCode == HttpURLConnection.HTTP_OK || _responseCode == HttpURLConnection.HTTP_CREATED;
    }

    private JSONObject getJsonObject(String response) throws DataCiteException
    {
        try
        {
            return new JSONObject(response);
        }
        catch (JSONException e)
        {
            throw new DataCiteException("Error parsing JSON from response: " + response);
        }
    }
}

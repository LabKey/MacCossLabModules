package org.labkey.panoramapublic.bluesky;


import org.apache.hc.core5.http.HttpStatus;
import org.json.JSONException;
import org.json.JSONObject;

public class BlueskyResponse
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
        return _responseCode == HttpStatus.SC_OK || _responseCode == HttpStatus.SC_CREATED;
    }

    public JSONObject getJsonObject() throws BlueskyException
    {
        try
        {
            return new JSONObject(_responseBody);
        }
        catch (JSONException e)
        {
            throw new BlueskyException("Error parsing JSON from response", this);
        }
    }
}

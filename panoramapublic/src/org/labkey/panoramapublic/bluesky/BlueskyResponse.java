package org.labkey.panoramapublic.bluesky;


import org.apache.hc.core5.http.HttpStatus;
import org.json.JSONException;
import org.json.JSONObject;

public class BlueskyResponse
{
    private final int _statusCode;
    private final String _message;
    private final String _responseBody;

    public BlueskyResponse(int statusCode, String message, String responseBody)
    {
        _statusCode = statusCode;
        _message = message;
        _responseBody = responseBody;
    }

    public int getStatusCode()
    {
        return _statusCode;
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
        return _statusCode == HttpStatus.SC_OK || _statusCode == HttpStatus.SC_CREATED;
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

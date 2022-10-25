package org.labkey.panoramapublic.datacite;

import org.json.JSONException;
import org.json.JSONObject;

class DataCiteResponse
{
    private final int _responseCode;
    private final String _message;
    private final String _responseBody;

    public DataCiteResponse(int responseCode, String message, String responseBody)
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

    public boolean success(DataCiteService.METHOD method)
    {
        return switch (method)
        {
            case GET, PUT -> _responseCode == 200;
            case POST -> _responseCode == 201;
            case DELETE -> _responseCode == 204;
            default -> throw new IllegalStateException("Unexpected method: " + method);
        };
    }

    public Doi getDoi() throws DataCiteException
    {
        return _responseBody == null ? null : Doi.fromJson(getJsonObject(_responseBody));
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

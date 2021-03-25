package org.labkey.panoramapublic.datacite;

import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.StringReader;

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
        switch (method)
        {
            case GET: case PUT: return _responseCode == 200;
            case POST: return _responseCode == 201;
            case DELETE: return _responseCode == 204;
            default:
                throw new IllegalStateException("Unexpected method: " + method);
        }
    }

    public Doi getDoi() throws DataCiteException
    {
        return _responseBody == null ? null : Doi.fromJson(getJsonObject(_responseBody));
    }

    private org.json.simple.JSONObject getJsonObject(String response) throws DataCiteException
    {
        JSONParser parser = new JSONParser();
        try
        {
            return (org.json.simple.JSONObject) parser.parse(new StringReader(response));
        }
        catch (IOException | ParseException e)
        {
            throw new DataCiteException("Error parsing JSON from response: " + response);
        }
    }
}

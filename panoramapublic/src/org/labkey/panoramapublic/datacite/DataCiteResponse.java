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

/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.panoramapublic.bluesky;


import org.apache.hc.core5.http.HttpStatus;
import org.json.JSONException;
import org.json.JSONObject;

public class BlueskyResponse
{
    private final int _statusCode;
    private final String _message;
    private final String _responseBody;
    private final String _account;
    private final String _endpoint;

    public BlueskyResponse(int statusCode, String message, String responseBody, String blueskyAccount, String endpoint)
    {
        _statusCode = statusCode;
        _message = message;
        _responseBody = responseBody;
        _account = blueskyAccount;
        _endpoint = endpoint;
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

    public String getAccount()
    {
        return _account;
    }

    public String getEndpoint()
    {
        return _endpoint;
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

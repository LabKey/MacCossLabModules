/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.panoramapublic.proteomexchange;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProteomeXchangeService
{
    public static final String PX_CREDENTIALS = "ProteomeXchange Credentials";
    public static final String PX_USER = "ProteomeXchange User";
    public static final String PX_PASSWORD = "ProteomeXchange Password";

    public static final String PXID = "PX[DT]\\d{6}";
    private static final Pattern PXID_IN_RESPONSE = Pattern.compile("identifier=(" + PXID + ")");

    private enum METHOD {submitDataset, validateXML, requestID}

    public static String validatePxXml(File pxxmlFile, boolean testDatabase, String user, String pass) throws ProteomeXchangeServiceException
    {
        return postPxXml(pxxmlFile, testDatabase, user, pass, METHOD.validateXML);
    }

    public static String submitPxXml(File pxxmlFile, boolean testDatabase, String user, String pass) throws ProteomeXchangeServiceException
    {
        return postPxXml(pxxmlFile, testDatabase, user, pass, METHOD.submitDataset);
    }

    private static String postPxXml(File pxxmlFile, boolean testDatabase, String user, String pass, METHOD method) throws ProteomeXchangeServiceException
    {
        String responseMessage;
        try {
            MultipartEntityBuilder builder = getMultipartEntityBuilder(pxxmlFile, testDatabase, method, user, pass);
            responseMessage = postRequest(builder);
        }
        catch(ProteomeXchangeServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            String exMsg = e.getMessage() == null ? e.toString() : e.getMessage();
            throw new ProteomeXchangeServiceException("Error with service request " + method + " to ProteomeXchange. " + exMsg);
        }

        return responseMessage;
    }

    public static String getPxIdResponse(boolean testDatabase, String user, String pass) throws ProteomeXchangeServiceException
    {
        String responseMessage;
        try
        {
            MultipartEntityBuilder builder = getMultipartEntityBuilder(testDatabase, METHOD.requestID, user, pass);
            responseMessage = postRequest(builder);

        }
        catch (Exception e)
        {
            throw new ProteomeXchangeServiceException("Error requesting a ID from ProteomeXchange.", e);
        }

        return responseMessage;
    }

    public static String parsePxIdFromResponse(String response)
    {
        Matcher match = PXID_IN_RESPONSE.matcher(response);
        if(match.find())
        {
            return match.group(1);
        }
        return null;
    }

    public static String getPxId(boolean testDatabase, String user, String pass) throws ProteomeXchangeServiceException
    {
        String response = getPxIdResponse(testDatabase, user, pass);
        String pxId = parsePxIdFromResponse(response);
        if(pxId == null)
        {
            throw new ProteomeXchangeServiceException("Could not parse ProteomeXchage ID from response \"" + response + "\"");
        }
        return pxId;
    }

    @NotNull
    private static MultipartEntityBuilder getMultipartEntityBuilder(File pxxmlFile, boolean testDatabase, METHOD method, String user, String pass)
    {
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        if(pxxmlFile != null)
        {
            builder.addPart("ProteomeXchangeXML", new FileBody(pxxmlFile));
        }
        builder.addTextBody("PXPartner", user);
        builder.addTextBody("authentication", pass);
        builder.addTextBody("method",  method.name());
        if(testDatabase)
        {
            builder.addTextBody("test", "yes");
        }
        else
        {
            builder.addTextBody("test", "no");
        }
        builder.addTextBody("verbose",  "yes");
        return builder;
    }

    private static MultipartEntityBuilder getMultipartEntityBuilder(boolean testDatabase, METHOD method, String user, String pass)
    {
        return getMultipartEntityBuilder(null, testDatabase, method, user, pass);
    }

    private static String postRequest(MultipartEntityBuilder builder) throws IOException, ProteomeXchangeServiceException
    {
        String responseMessage;
        HttpPost post = new HttpPost("http://proteomecentral.proteomexchange.org/cgi/Dataset");
        post.setEntity(builder.build());

        // execute the POST request
        HttpClient client = HttpClientBuilder.create().build();
        HttpResponse response = client.execute(post);

        // retrieve and inspect the response
        HttpEntity entity = response.getEntity();
        responseMessage = EntityUtils.toString(entity);

        // check the response status code
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200)
        {
            throw new ProteomeXchangeServiceException("Error " + statusCode + " from ProteomeXchange server: " + responseMessage);
        }
        return responseMessage;
    }

    public static boolean responseHasErrors(String response)
    {
        return !response.contains("result=SUCCESS")
                || !response.contains("info=File does appear to be XML")
                || !response.contains("info=Submitted XML is valid according to the XSD.")
                || !response.contains("info=There were a total of 0 different CV errors or warnings.")
                || !response.contains("info=There was a total of 0 non-CV warnings.")
                || !response.contains("info=There was a total of 0 non-CV errors.");
    }
}


package org.labkey.lincs.cromwell;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CromwellUtil
{
    public static CromwellJobStatus submitJob(CromwellConfig config, String wdl, String inputsJson, Logger logger) throws CromwellException
    {
        URI uri = config.buildJobSubmitUri();

        try (CloseableHttpClient client = HttpClientBuilder.create().build())
        {
            // String url = "http://127.0.0.1:8000/api/workflows/v1";
            HttpPost post = new HttpPost(uri);

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            builder.addPart("workflowSource", new StringBody(wdl, ContentType.DEFAULT_BINARY));
            builder.addPart("workflowInputs", new StringBody(inputsJson, ContentType.APPLICATION_JSON));
            HttpEntity entity = builder.build();
            post.setEntity(entity);
            logger.info("Submitting job to " + uri);

            try (CloseableHttpResponse response = client.execute(post))
            {
                ResponseHandler<String> handler = new BasicResponseHandler();
                StatusLine status = response.getStatusLine();

                if (status.getStatusCode() == HttpStatus.SC_OK || status.getStatusCode() == HttpStatus.SC_CREATED)
                {
                    String resp = handler.handleResponse(response);
                    JSONObject json = new JSONObject(resp);
                    // {"id":"ac73fdba-51f5-4aa2-8a09-bd5f7f9611fa","status":"Submitted"}
                    return new CromwellJobStatus(json.getString("id"), json.getString("status"));
                }
                else
                {
                    logger.error("Job submission failed. Response code was " + status.getStatusCode() + " " +  status.getReasonPhrase());
                    EntityUtils.consume(response.getEntity());
                    return null;
                }
            }
        }
        catch (IOException e)
        {
            throw new CromwellException("Error occurred submitting job.", e);
        }
    }

    public static CromwellJobStatus getJobStatus(URI jobStatusUri, Logger logger) throws CromwellException
    {
        try (CloseableHttpClient client = HttpClientBuilder.create().build())
        {
            HttpGet get = new HttpGet(jobStatusUri);

            try (CloseableHttpResponse response = client.execute(get))
            {
                ResponseHandler<String> handler = new BasicResponseHandler();
                StatusLine status = response.getStatusLine();

                if (status.getStatusCode() == HttpStatus.SC_OK)
                {
                    String resp = handler.handleResponse(response);
                    JSONObject json = new JSONObject(resp);
                    // {"id":"ac73fdba-51f5-4aa2-8a09-bd5f7f9611fa","status":"Submitted"}
                    return new CromwellJobStatus(json.getString("id"), json.getString("status"));
                }
                else
                {
                    logger.error("Checking job status failed. Response code was " + status.getStatusCode() + " " +  status.getReasonPhrase());
                    EntityUtils.consume(response.getEntity());
                    return null;
                }
            }
        }
        catch (IOException e)
        {
            throw new CromwellException("Error checking status of job.", e);
        }
    }

    public static List<String> getJobLogs(URI jobLogsUri) throws CromwellException
    {
        List<String> logFiles = new ArrayList<>();

        try (CloseableHttpClient client = HttpClientBuilder.create().build())
        {
            HttpGet get = new HttpGet(jobLogsUri);

            try (CloseableHttpResponse response = client.execute(get))
            {
                ResponseHandler<String> handler = new BasicResponseHandler();
                StatusLine status = response.getStatusLine();

                if (status.getStatusCode() == HttpStatus.SC_OK)
                {
                    String resp = handler.handleResponse(response);
                    JSONObject json = new JSONObject(resp);
                    JSONObject calls = json.getJSONObject("calls");
                    for (Iterator<String> it = calls.keys(); it.hasNext(); )
                    {
                        String key = it.next();
                        JSONArray values = calls.getJSONArray(key);
                        for(int i = 0; i < values.length(); i++)
                        {
                            JSONObject info = values.getJSONObject(i);
                            String stdout = info.getString("stdout");
                            String stderr = info.getString("stderr");

                            JSONObject callCaching = info.getJSONObject("callCaching");
                            if(callCaching != null && callCaching.containsKey("hit"))
                            {
                                if(callCaching.getBoolean("hit"))
                                {
                                    var callRoot = info.getString("callRoot");
                                    stdout = callRoot + "/cacheCopy/execution/stdout";
                                    stderr = callRoot + "/cacheCopy/execution/stderr";
                                }
                            }

                            logFiles.add(stdout);
                            logFiles.add(stderr);
                        }
                    }
                }
                else
                {
                    EntityUtils.consume(response.getEntity());
                    throw new CromwellException("Error getting list of log files for job. Response code was " + status.getStatusCode() + " " +  status.getReasonPhrase());
                }
            }
        }
        catch (IOException e)
        {
            throw new CromwellException("Error getting list of log files for job.", e);
        }

        return logFiles;
    }

    public static CromwellMetadata getJobMetadata(URI metadataUri) throws CromwellException
    {
        List<String> logFiles = new ArrayList<>();

        try (CloseableHttpClient client = HttpClientBuilder.create().build())
        {
            HttpGet get = new HttpGet(metadataUri);

            try (CloseableHttpResponse response = client.execute(get))
            {
                ResponseHandler<String> handler = new BasicResponseHandler();
                StatusLine status = response.getStatusLine();

                if (status.getStatusCode() == HttpStatus.SC_OK)
                {
                    String resp = handler.handleResponse(response);
                    JSONObject json = new JSONObject(resp);
                    JSONObject calls = json.getJSONObject("calls");
                    for (Iterator<String> it = calls.keys(); it.hasNext(); )
                    {
                        String key = it.next();
                        JSONArray values = calls.getJSONArray(key);
                        for(int i = 0; i < values.length(); i++)
                        {
                            JSONObject info = values.getJSONObject(i);
                            String stdout = info.getString("stdout");
                            String stderr = info.getString("stderr");

                            JSONObject callCaching = info.getJSONObject("callCaching");
                            if(callCaching != null && callCaching.containsKey("hit"))
                            {
                                if(callCaching.getBoolean("hit"))
                                {
                                    var callRoot = info.getString("callRoot");
                                    stdout = callRoot + "/cacheCopy/execution/stdout";
                                    stderr = callRoot + "/cacheCopy/execution/stderr";
                                }
                            }

                            logFiles.add(stdout);
                            logFiles.add(stderr);
                        }
                    }
                }
                else
                {
                    EntityUtils.consume(response.getEntity());
                    throw new CromwellException("Error getting list of log files for job. Response code was " + status.getStatusCode() + " " +  status.getReasonPhrase());
                }
            }
        }
        catch (IOException e)
        {
            throw new CromwellException("Error getting list of log files for job.", e);
        }

        return null; // TODO
    }

    public static class CromwellJobStatus
    {
        private final String _jobId;
        private final String _jobStatus;

        public CromwellJobStatus(String jobId, String jobStatus)
        {
            _jobId = jobId;
            _jobStatus = jobStatus;
        }

        public String getJobId()
        {
            return _jobId;
        }

        public String getJobStatus()
        {
            return _jobStatus;
        }

        public boolean success()
        {
            return "Succeeded".equalsIgnoreCase(_jobStatus);
        }
        public boolean submitted()
        {
            return "Submitted".equalsIgnoreCase(_jobStatus);
        }
        public boolean running()
        {
            return "Running".equalsIgnoreCase(_jobStatus);
        }
        public boolean failed()
        {
            return "Failed".equalsIgnoreCase(_jobStatus);
        }
    }
}

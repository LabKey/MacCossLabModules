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
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;

public class CromwellUtil
{
    public static CromwellJobStatus submitJob(CromwellConfig config, String wdl, String inputsJson, Logger log)
    {
        URI uri = config.getJobSubmitUri();

        try (CloseableHttpClient client = HttpClientBuilder.create().build())
        {
            // Example: "http://127.0.0.1:8000/api/workflows/v1";
            HttpPost post = new HttpPost(uri);

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            builder.addPart("workflowSource", new StringBody(wdl, ContentType.DEFAULT_BINARY));
            builder.addPart("workflowInputs", new StringBody(inputsJson, ContentType.APPLICATION_JSON));
            HttpEntity entity = builder.build();
            post.setEntity(entity);
            log.info("Submitting job to " + uri);

            try (CloseableHttpResponse response = client.execute(post))
            {
                ResponseHandler<String> handler = new BasicResponseHandler();
                StatusLine status = response.getStatusLine();

                if (status.getStatusCode() == HttpStatus.SC_OK || status.getStatusCode() == HttpStatus.SC_CREATED)
                {
                    String resp = handler.handleResponse(response);
                    JSONObject json = new JSONObject(resp);
                    // Example: {"id":"ac73fdba-51f5-4aa2-8a09-bd5f7f9611fa","status":"Submitted"}
                    return new CromwellJobStatus(json.getString("id"), json.getString("status"));
                }
                else
                {
                    log.error("Job submission failed. Response code was " + status.getStatusCode() + " " +  status.getReasonPhrase());
                    EntityUtils.consume(response.getEntity());
                    return null;
                }
            }
        }
        catch (IOException e)
        {
            log.error("Could not submit job. Error was:" + e.getMessage(), e);
            return null;
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

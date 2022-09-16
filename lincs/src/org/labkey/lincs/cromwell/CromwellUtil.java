package org.labkey.lincs.cromwell;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.entity.mime.StringBody;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
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
            builder.setMode(HttpMultipartMode.LEGACY);
            builder.addPart("workflowSource", new StringBody(wdl, ContentType.DEFAULT_BINARY));
            builder.addPart("workflowInputs", new StringBody(inputsJson, ContentType.APPLICATION_JSON));
            // disable call caching for this workflow
            builder.addPart("workflowOptions", new StringBody("{\"write_to_cache\": false, \"read_from_cache\": false}", ContentType.APPLICATION_JSON));
            HttpEntity entity = builder.build();
            post.setEntity(entity);
            log.info("Submitting job to " + uri);

            return executeRequest(log, client, post, "Submitting job");
        }
        catch (IOException | HttpException e)
        {
            log.error("Could not submit job. Error was:" + e.getMessage(), e);
            return null;
        }
    }

    public static CromwellJobStatus getJobStatus(URI jobStatusUri, Logger log) throws CromwellException
    {
        try (CloseableHttpClient client = HttpClientBuilder.create().build())
        {
            HttpGet get = new HttpGet(jobStatusUri);
            return executeRequest(log, client, get, "Checking job status");
        }
        catch (IOException | HttpException e)
        {
            throw new CromwellException("Error checking status of job.", e);
        }
    }

    public static void abortJob(URI abortJobUri, Logger log) throws CromwellException
    {
        try (CloseableHttpClient client = HttpClientBuilder.create().build())
        {
            HttpPost post = new HttpPost(abortJobUri);
            CromwellJobStatus status = executeRequest(log, client, post, "Aborting job");
            if(status != null)
            {
                log.info("Sent request to abort job. Returned status: " + status.getJobStatus());
            }
        }
        catch (IOException | HttpException e)
        {
            throw new CromwellException("Error aborting job. Error message: " + e.getMessage(), e);
        }
    }

    @Nullable
    private static CromwellUtil.CromwellJobStatus executeRequest(Logger log, CloseableHttpClient client, HttpUriRequest request, String requestDesc) throws IOException, HttpException
    {
        try (CloseableHttpResponse response = client.execute(request))
        {
            HttpClientResponseHandler<String> handler = new BasicHttpClientResponseHandler();

            if (response.getCode() == HttpStatus.SC_OK || response.getCode() == HttpStatus.SC_CREATED)
            {
                String resp = handler.handleResponse(response);
                JSONObject json = new JSONObject(resp);
                // {"id":"ac73fdba-51f5-4aa2-8a09-bd5f7f9611fa","status":"Submitted"}
                return new CromwellJobStatus(json.getString("id"), json.getString("status"));
            }
            else
            {
                log.error(requestDesc + " failed. Response code was " + response.getCode() + " " +  response.getReasonPhrase());
                EntityUtils.consume(response.getEntity());
                return null;
            }
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

        // Submitted, Running, Failed, Succeeded
        public boolean success()
        {
            return "Succeeded".equalsIgnoreCase(_jobStatus);
        }
        public boolean failed()
        {
            return "Failed".equalsIgnoreCase(_jobStatus);
        }
    }
}

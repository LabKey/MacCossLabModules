package org.labkey.lincs.psp;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.webdav.WebdavService;
import org.labkey.lincs.LincsController;
import org.labkey.lincs.LincsManager;
import org.labkey.lincs.LincsModule;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class LincsPspUtil
{
    public static PspEndpoint getPspEndpoint(Container container) throws LincsPspException
    {
        LincsModule.ClueCredentials credentials;
        String pspUrl = null;
        String pspApiKey = null;
        try
        {
            // Only run if the psp endpoint configuration has been saved in the container
            credentials = LincsModule.getClueCredentials(container);
            if (credentials != null)
            {
                pspUrl = credentials.getServerUrl();
                pspApiKey = credentials.getApiKey();
            }
        }
        catch(Exception e)
        {
            throw new LincsPspException("Error looking up PSP endpoint configuration in container " + container.getPath() + ". Error: " + e.getMessage(), e);
        }
        if(credentials == null)
        {
            throw new LincsPspException(LincsPspException.NO_PSP_CONFIG);
        }
        if(StringUtils.isBlank(pspUrl))
        {
            throw new LincsPspException("Could not find PSP endpoint URL in saved properties.");
        }
        if(StringUtils.isBlank(pspApiKey))
        {
            throw new LincsPspException("Could not find PSP API Key in the saved properties.");
        }
        return new PspEndpoint(pspUrl, pspApiKey);
    }

    public static void submitPspJob(PspEndpoint endPoint, LincsPspJob pspJob, ITargetedMSRun run, User user, Logger log) throws LincsPspException
    {
        try
        {
            submitJob(run, endPoint, pspJob, log);
            log.info("Sent POST request to " + endPoint.getUrl());
        }
        catch (IOException e)
        {
            throw new LincsPspException("Error sending POST request to " + endPoint.getUrl(), e);
        }

        LincsManager.get().updateLincsPspJob(pspJob, user);
    }

    private static void submitJob(ITargetedMSRun run, PspEndpoint server, LincsPspJob pspJob, Logger log) throws IOException, LincsPspException
    {
        // Make a POST request
        URL url = new URL(server.getUrl());
        JSONObject json = getPostJson(pspJob.getPspJobName(), getAssayName(run.getContainer()), run);
        log.info("Sending JSON:");
        log.info(json.toString(2));
        byte[] postData = json.toString().getBytes(StandardCharsets.UTF_8);
        int postDataLength = postData.length;

        HttpURLConnection conn = null;

        try
        {
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("user_key", server.getApiKey());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("charset", "utf-8");
            conn.setRequestProperty("Content-Length", Integer.toString(postDataLength));
            conn.setUseCaches(false);
            try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream()))
            {
                wr.write(postData);
                wr.flush();
            }

            log.info("Sending POST request to " + server.getUrl());
            int responseCode = conn.getResponseCode();
            log.info("Response code - " + responseCode);
            String response;
            try (InputStream in = conn.getInputStream())
            {
                response = IOUtils.toString(in, StandardCharsets.UTF_8);
                log.info("Response from server: " + response);
            }

            // String response = "{\"name\":\"LINCS_P100_DIA_Plate52y_annotated_minimized_2017-08-23_11-20-58\",\"assay\":\"P100\",\"status\":\"Waiting_To_Download\",\"id\":\"5c324f97b306063b135bf99c\",\"created\":\"2019-01-06T18:57:27.484Z\",\"last_modified\":\"2019-01-06T18:57:27.484Z\",\"level 2\":{\"panorama\":{\"method\":\"GET\",\"url\":\"https://panoramaweb-dr.gs.washington.edu/lincs/LINCS-DCIC/PSP/P100/runGCTReportApi.view?runId=32394&remote=true&reportName=GCT%20File%20P100\"}},\"level 3\":{\"panorama\":{\"method\":\"PUT\",\"url\":\"https://panoramaweb-dr.gs.washington.edu/_webdav/LINCS-DCIC/PSP/P100/%40files/GCT/LINCS_P100_DIA_Plate52y_annotated_minimized_2017-08-23_11-20-58_LVL3.gct\"}},\"level 4\":{\"panorama\":{\"method\":\"PUT\",\"url\":\"https://panoramaweb-dr.gs.washington.edu/_webdav/LINCS-DCIC/PSP/P100/%40files/GCT/LINCS_P100_DIA_Plate52y_annotated_minimized_2017-08-23_11-20-58_LVL4.gct\"}},\"config\":{\"panorama\":{\"method\":\"PUT\",\"url\":\"https://panoramaweb-dr.gs.washington.edu/_webdav/LINCS-DCIC/PSP/P100/%40files/GCT/LINCS_P100_DIA_Plate52y_annotated_minimized_2017-08-23_11-20-58.cfg\"}}}";
            org.json.simple.JSONObject jsonResponse = getJsonObject(response);
            parseResponseJson(jsonResponse, pspJob);
        }
        finally
        {
            if (conn != null)
            {
                conn.disconnect();
            }
        }
    }

    private static JSONObject getPostJson(String jobName, String assayName, ITargetedMSRun run)
    {
        /*
            {
              "name": "LINCS_GCP_Plate58_annotated_minimized_2018-01-02_14-26-56",
              "assay": "GCP",
              "level 2": {
                "panorama": {
                    "url": "https://panoramaweb.org/lincs/LINCS-DCIC/GCP/runGCTReportApi.view?runId=30974&reportName=GCT%20File%20GCP",
                    "http_method":"GET"
                }
              },
              "level 3": {
                "panorama": {
                    "url": "https://panoramaweb.org/_webdav/LINCS-DCIC/GCP/%40files/GCT/LINCS_GCP_Plate58_annotated_minimized_2018-01-02_14-26-56_LVL3.gct",
                    "http_method":"PUT"
                }
              },
              "level 4": {
                "panorama": {
                    "url":  "https://panoramaweb.org/_webdav/LINCS-DCIC/GCP/%40files/GCT/LINCS_GCP_Plate58_annotated_minimized_2018-01-02_14-26-56_LVL4.gct",
                    "http_method":"PUT"
                 }
              },
              "config": {
                "panorama": {
                    "url":  "https://panoramaweb.org/_webdav/LINCS-DCIC/GCP/%40files/GCT/LINCS_GCP_Plate58_annotated_minimized_2018-01-02_14-26-56.cfg",
                    "http_method":"PUT"
                 }
              }
            }
        */
        JSONObject json = new JSONObject();
        json.put("name", jobName);
        json.put("assay", assayName);
        json.put("level 2", getLevelJSON(run, LincsModule.LincsLevel.Two, assayName));
        json.put("level 3", getLevelJSON(run, LincsModule.LincsLevel.Three, assayName));
        json.put("level 4", getLevelJSON(run, LincsModule.LincsLevel.Four, assayName));
        json.put("config", getLevelJSON(run, LincsModule.LincsLevel.Config, assayName));
        return json;
    }

    private static JSONObject getLevelJSON(ITargetedMSRun run, LincsModule.LincsLevel level, String assayName)
    {
        JSONObject json = new JSONObject();
        JSONObject details = new JSONObject();
        String method = level == LincsModule.LincsLevel.Two ? "GET" : "PUT";
        String url = level == LincsModule.LincsLevel.Two ? getRunReportURL(run, assayName) : getWebDavUrl(run, level);
        details.put("url", url);
        details.put("method", method);
        json.put("panorama", details);
        return json;
    }

    private static String getAssayName(Container container)
    {
        if(container.isRoot())
        {
            return "";
        }
        switch (container.getName())
        {
            case "P100":
                return "P100";
            case "GCP":
                return "GCP";
            default:
                return getAssayName(container.getParent());
        }
    }

    private static String getWebDavUrl(ITargetedMSRun run, LincsModule.LincsLevel level)
    {
        String gctFile = run.getBaseName() + LincsModule.getExt(level);
        Path path = WebdavService.getPath().append(run.getContainer().getParsedPath()).append(FileContentService.FILES_LINK).append("GCT").append(gctFile);
        return ActionURL.getBaseServerURL() + path.encode();
    }

    private static String getRunReportURL(ITargetedMSRun run, String assayName)
    {
        ActionURL url = new ActionURL(LincsController.RunGCTReportApiAction.class, run.getContainer());
        url.addParameter("runId", run.getId());
        url.addParameter("remote", true);
        url.addParameter("reportName", assayName.equals("P100") ? "GCT File P100" : "GCT File GCP");
        return url.getURIString();
    }

    public static String getJobName(ITargetedMSRun run, PspEndpoint server, String jobNameSuffix, Logger log) throws LincsPspException
    {
        String baseName = run.getBaseName() + (!StringUtils.isBlank(jobNameSuffix) ? "_" + jobNameSuffix : "");
        String name = baseName;
        int i = 1;
        while(jobExists(name, server, log))
        {
            name = baseName + "_" + ++i;
        }

        return name;
    }

    private static boolean jobExists(String name, PspEndpoint server, Logger log) throws LincsPspException
    {
        log.info("Looking for existing job with name " + name);

        HttpURLConnection conn = null;
        URL url = null;
        try
        {
            // https://api.clue.io/api/psp?filter={"where":{"name":"LINCS_P100_DIA_Plate52x_annotated_minimized_2017-08-23_11-20-58"}}
            String filter = "{\"where\":{\"name\":\"" + name + "\"}}";
            url = new URL(server.getUrl() + "?filter=" + PageFlowUtil.encode(filter));

            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("user_key", server.getApiKey());
            conn.setRequestProperty("charset", "utf-8");
            int responseCode = conn.getResponseCode();
            String response;
            try (InputStream in = conn.getInputStream())
            {
                response = IOUtils.toString(in, StandardCharsets.UTF_8);
            }
            if(responseCode != 200)
            {
                log.error("Server returned response code " + responseCode);
                log.info("Response from server: " + response);
                throw new LincsPspException("Error looking for job with name " + name + ". URL: " + url);
            }
            else
            {
                JSONArray json = getJsonArray(response);
                if(!json.isEmpty())
                {
                    log.info("Job with name " + name + "exists.");
                    return true;
                }
            }
        }
        catch (IOException e)
        {
            throw new LincsPspException("Error looking for job with name " + name + ". URL: " + url, e);
        }
        finally
        {
            if(conn != null)
            {
                conn.disconnect();
            }
        }
        return false;
    }

    public static void updateJobStatus(PspEndpoint endPoint, LincsPspJob pspJob, User user) throws IOException, LincsPspException
    {
        org.json.simple.JSONObject json = getJobStatus(endPoint, pspJob);
        parseResponseJson(json, pspJob);
        LincsManager.get().updateLincsPspJob(pspJob, user);
    }

    /*
   {"name":"LINCS_P100_DIA_Plate57x_annotated_minimized_2018-05-10_10-51-47",
    "assay":"P100",
    "status":"created LVL 3 GCT",
    "id":"5c31318bb306063b135bf913",
    "created":"2019-01-05T22:36:59.924Z",
    "last_modified":"2019-01-05T22:37:40.333Z",

    "level 2":{
       "panorama":{
                   "method":"GET",
                   "url":"https://panoramaweb-dr.gs.washington.edu/lincs/LINCS%20-%20Jaffe%20Lab/P100/runGCTReportApi.view?runId=32358&remote=true&reportName=GCT%20File%20P100"
                   },
       "s3":{"url":"s3://proteomics.clue.io/psp/level2/LINCS_P100_DIA_Plate57x_annotated_minimized_2018-05-10_10-51-47_LVL2.gct"}
       },
    "level 3":{
       "panorama":{
                   "method":"PUT",
                   "url":"https://panoramaweb-dr.gs.washington.edu/_webdav/LINCS%20-%20Jaffe%20Lab/P100/%40files/GCT/LINCS_P100_DIA_Plate57x_annotated_minimized_2018-05-10_10-51-47_LVL3.gct"
                   },
        "s3":{"url":"s3://proteomics.clue.io/psp/level3/LINCS_P100_DIA_Plate57x_annotated_minimized_2018-05-10_10-51-47_LVL3.gct"}
        },
    "level 4":{
       "panorama":{
                  "method":"PUT",
                  "url":"https://panoramaweb-dr.gs.washington.edu/_webdav/LINCS%20-%20Jaffe%20Lab/P100/%40files/GCT/LINCS_P100_DIA_Plate57x_annotated_minimized_2018-05-10_10-51-47_LVL4.gct"
                  },
       "s3":{"message":"tear error: Length of sample group vector must equal number of unique probe groups. len(sample_grps_lists[0]): {} \n len(unique_probe_grps): 2"}
       },
    "config":{
       "panorama":{"method":"PUT","url":"https://panoramaweb-dr.gs.washington.edu/_webdav/LINCS%20-%20Jaffe%20Lab/P100/%40files/GCT/LINCS_P100_DIA_Plate57x_annotated_minimized_2018-05-10_10-51-47.cfg"},
       "s3":{"url":"s3://proteomics.clue.io/psp/config/LINCS_P100_DIA_Plate57x_annotated_minimized_2018-05-10_10-51-47.cfg"}
       }
    }
    */
    private static void parseResponseJson(org.json.simple.JSONObject json, LincsPspJob pspJob) throws LincsPspException
    {
        if(!json.isEmpty())
        {
            String id = (String)json.get("id");
            if(id == null)
            {
                throw new LincsPspException("Invalid JSON. No 'id' key found.", json.toJSONString());
            }
            String status = (String)json.get("status");
            if(status == null)
            {
                throw new LincsPspException("Invalid JSON. No 'status' key found.", json.toJSONString());
            }
            pspJob.setPspJobId(id);
            pspJob.setStatus(status);
            getLevelStatus(pspJob, json, LincsModule.LincsLevel.Two);
            getLevelStatus(pspJob, json, LincsModule.LincsLevel.Three);
            getLevelStatus(pspJob, json, LincsModule.LincsLevel.Four);
            pspJob.setJson(json.toString());
        }
    }

    private static org.json.simple.JSONObject getJsonObject(String response) throws LincsPspException
    {
        Object json = getJson(response);

        if(!(json instanceof org.json.simple.JSONObject))
        {
            throw new LincsPspException("Parsed JSON is not an instance of JSONObject.", response);
        }
        return (org.json.simple.JSONObject) json;
    }

    private static org.json.simple.JSONArray getJsonArray(String response) throws LincsPspException
    {
        Object json = getJson(response);

        if(!(json instanceof org.json.simple.JSONArray))
        {
            throw new LincsPspException("Parsed JSON is not an instance of JSONArray.", response);
        }
        return (org.json.simple.JSONArray) json;
    }

    private static Object getJson(String response) throws LincsPspException
    {
        Object json;
        try
        {
            JSONParser parser = new JSONParser();
            json = parser.parse(new StringReader(response));
        }
        catch (IOException | ParseException e)
        {
            throw new LincsPspException("Error parsing JSON", response, e);
        }
        return json;
    }

    private static void getLevelStatus(LincsPspJob pspJob, org.json.simple.JSONObject json, LincsModule.LincsLevel level) throws LincsPspException
    {
        int i = level == LincsModule.LincsLevel.Two ? 2 : (level == LincsModule.LincsLevel.Three ? 3 : (level == LincsModule.LincsLevel.Four ? 4 : 0));
        String levelKey = "level " + i;
        org.json.simple.JSONObject l2Status = (org.json.simple.JSONObject) json.get(levelKey);
        if(l2Status == null)
        {
            throw new LincsPspException("Invalid JSON. " + levelKey + " not found", json.toJSONString());
        }
        else
        {
            org.json.simple.JSONObject s3 = (org.json.simple.JSONObject) l2Status.get("s3");
            if(s3 == null)
            {
                // Processing has not started for this level.
                return;
            }
            else
            {
                String message = (String) s3.get("message");
                message = StringUtils.isBlank(message) ? null : levelKey + ": " + message;
                pspJob.updateLevelStatus(level, message);
            }
        }
    }

    public static String getJobStatusString(PspEndpoint server, LincsPspJob pspJob) throws IOException, LincsPspException
    {
        org.json.simple.JSONObject obj = getJobStatus(server, pspJob);
        JSONObject jsonObj = new JSONObject(obj.toString());
        return jsonObj.toString(2);
    }

    private static org.json.simple.JSONObject getJobStatus(PspEndpoint server, LincsPspJob pspJob) throws IOException, LincsPspException
    {
        // Make a GET request
        URL url = new URL(server.getUrl() + "/" + pspJob.getPspJobId());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try
        {
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("user_key", server.getApiKey());
            conn.setRequestProperty("charset", "utf-8");
            int responseCode = conn.getResponseCode();
            String response;
            try (InputStream in = conn.getInputStream())
            {
                response = IOUtils.toString(in, StandardCharsets.UTF_8);
            }
            if(responseCode != 200)
            {
                throw new LincsPspException("Server returned " + responseCode + " response code. Response was: " + response);
            }
            return getJsonObject(response);
        }
        finally
        {
            if(conn != null)
            {
                conn.disconnect();
            }
        }
    }
}

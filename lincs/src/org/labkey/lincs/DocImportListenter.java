package org.labkey.lincs;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentListener;
import org.labkey.api.files.FileContentService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.webdav.WebdavService;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class DocImportListenter implements ExperimentListener
{
    private static Logger _log = Logger.getLogger(DocImportListenter.class);

    @Override
    public void beforeRunCreated(Container container, User user, ExpProtocol protocol, ExpRun run)
    {
        // Check if the LINCS module is enabled.
        if (!container.getActiveModules().contains(ModuleLoader.getInstance().getModule(LincsModule.class)))
        {
            return;
        }

        try
        {
            PipelineJobService service = PipelineJobService.get();
            if(service == null)
            {
                _log.error("LINCS: Could not get PipelineJobService.");
                return;
            }
            PipelineStatusFile.JobStore jobStore = service.getJobStore();
            if(jobStore == null)
            {
                _log.error("LINCS: Could not get Pipeline JobStore.");
                return;
            }
            PipelineJob job = jobStore.getJob(run.getJobId());
            if (job == null)
            {
                _log.error("LINCS: Could not find a job with ID " + run.getJobId());
                return;
            }
            if (job.getLogger() == null)
            {
                _log.error("LINCS: Could not get logger from job: " + job.getJobGUID());
                return;
            }
            _log = job.getLogger();
        }
        catch(Exception e)
        {
            _log.error("LINCS: Error looking up job for jobId: " + run.getJobId(), e);
            return;
        }

        String clueServerUrl = null;
        String clueApiKey = null;
        try
        {
            // Only run if the clue server credentials are set in the folder
            LincsModule.ClueCredentials credentials = LincsModule.getClueCredentials(container);
            if (credentials == null)
            {
                _log.info("LINCS: Clue/PSP server credentials were not saved for this container. Skipping POST to Clue/PSP server.");
                return;
            }
            clueServerUrl = credentials.getServerUrl();
            clueApiKey = credentials.getApiKey();
        }
        catch(Exception e)
        {
            _log.error("LINCS: Error looking up Clue/PSP server credentials in container " + container.getPath(), e);
        }
        if(StringUtils.isBlank(clueServerUrl))
        {
            _log.error("LINCS: Could not find Clue/PSP server URL in saved properties.");
            return;
        }
        if(StringUtils.isBlank(clueApiKey))
        {
            _log.error("LINCS: Could not find Clue/PSP API Key in the saved properties.");
            return;
        }

        ITargetedMSRun skylineRun = null;
        try
        {
            skylineRun = TargetedMSService.get().getRunByFileName(run.getName(), run.getContainer());
            if(skylineRun == null)
            {
                _log.error("LINCS: Could not find a targetedms run with filename " + run.getName());
            }
            afterDocImport(skylineRun, clueServerUrl, clueApiKey, _log);
            _log.info("Sent POST request to " + clueServerUrl);
        }
        catch(Exception e)
        {
            _log.error("LINCS: Error sending POST request to " + clueServerUrl, e);
        }
        return;
    }

    //@Override
    public void afterDocImport(ITargetedMSRun run, String clueUrl, String apiKey, Logger log) throws IOException
    {
        // Make a POST request
        URL url = new URL(clueUrl);
        JSONObject json = getJSON(run);
        _log.info("Sending JSON:");
        _log.info(json.toString(2));
        byte[] postData = json.toString().getBytes(StandardCharsets.UTF_8);
        int postDataLength = postData.length;

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try
        {
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("user_key", apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("charset", "utf-8");
            conn.setRequestProperty("Content-Length", Integer.toString(postDataLength));
            conn.setUseCaches(false);
            try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream()))
            {
                wr.write(postData);
                wr.flush();
            }

            log.info("LINCS: Sending POST request to " + clueUrl);
            int responseCode = conn.getResponseCode();
            log.info("LINCS: Response code - " + responseCode);
            String response;
            try (InputStream in = conn.getInputStream())
            {
                response = IOUtils.toString(in, Charset.defaultCharset());
                log.info("LINCS: Response from server: " + response);
            }
        }
        finally
        {
            conn.disconnect();
        }
    }

    private JSONObject getJSON(ITargetedMSRun run)
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
        json.put("name", run.getBaseName());
        String assayName = getAssayName(run.getContainer());
        json.put("assay", assayName);
        json.put("level 2", getLevelJSON(run, 2, assayName));
        json.put("level 3", getLevelJSON(run, 3, assayName));
        json.put("level 4", getLevelJSON(run, 4, assayName));
        json.put("config", getConfigJSON(run, assayName));
        return json;
    }

    private JSONObject getLevelJSON(ITargetedMSRun run, int level, String assayName)
    {
        JSONObject json = new JSONObject();
        JSONObject details = new JSONObject();
        String method = level == 2 ? "GET" : "PUT";
        String url = level == 2 ? getRunReportURL(run, assayName) : getWebDavUrl(run, level);
        details.put("url", url);
        details.put("method", method);
        json.put("panorama", details);
        return json;
    }

    private JSONObject getConfigJSON(ITargetedMSRun run, String assayName)
    {
        JSONObject json = new JSONObject();
        JSONObject details = new JSONObject();
        String method = "PUT";
        String url = getConfigWebDavUrl(run);
        details.put("url", url);
        details.put("method", method);
        json.put("panorama", details);
        return json;
    }

    private String getWebDavUrl(ITargetedMSRun run, int level)
    {
        String gctFile = run.getBaseName() + "_LVL" + level + ".gct";
        Path path = WebdavService.getPath().append(run.getContainer().getParsedPath()).append(FileContentService.FILES_LINK).append("GCT").append(gctFile);
        return ActionURL.getBaseServerURL() + path.encode();
    }

    private String getConfigWebDavUrl(ITargetedMSRun run)
    {
        String gctFile = run.getBaseName() + ".cfg";
        Path path = WebdavService.getPath().append(run.getContainer().getParsedPath()).append(FileContentService.FILES_LINK).append("GCT").append(gctFile);
        return ActionURL.getBaseServerURL() + path.encode();
    }

    private String getRunReportURL(ITargetedMSRun run, String assayName)
    {
        ActionURL url = new ActionURL(LincsController.RunGCTReportApiAction.class, run.getContainer());
        url.addParameter("runId", run.getId());
        url.addParameter("remote", true);
        url.addParameter("reportName", assayName.equals("P100") ? "GCT File P100" : "GCT File GCP");
        return url.getURIString();
    }

    private String getAssayName(Container container)
    {
        if(container.isRoot())
        {
            return "";
        }
        if(container.getName().equals("P100"))
        {
            return "P100";
        }
        else if(container.getName().equals("GCP"))
        {
            return "GCP";
        }
        else
        {
            return getAssayName(container.getParent());
        }
    }

    @Override
    public void beforeRunDelete(ExpProtocol protocol, ExpRun run)
    {
        Container c = run.getContainer();
        // Check if the LINCS module is enabled.
        if (!c.getActiveModules().contains(ModuleLoader.getInstance().getModule(LincsModule.class)))
        {
            return;
        }
        // Get the file root for the container
        java.nio.file.Path gctDir = LincsController.getGCTDir(c);
        if(Files.exists(gctDir))
        {
            ITargetedMSRun tRun = TargetedMSService.get().getRunByFileName(run.getName(), run.getContainer());
            if(tRun != null)
            {
                String baseName = tRun.getBaseName().toLowerCase();
                List<java.nio.file.Path> toDelete = new ArrayList<>();
                try (Stream<java.nio.file.Path> paths = Files.list(gctDir))
                {
                    paths.forEach(path -> {
                        String filename = FileUtil.getFileName(path).toLowerCase();
                        int dots = (filename.endsWith(".processed.gct") || filename.endsWith(".console.txt") || filename.endsWith(".script.rout")) ? 2 : 1;
                        filename = FileUtil.getBaseName(filename, dots);
                        if (filename.equals(baseName))
                        {
                            toDelete.add(path);
                        }
                    });
                }
                catch (IOException e)
                {
                    _log.warn("LINCS: Error listing files in folder " + FileUtil.getAbsolutePath(gctDir), e);
                    return;
                }

                toDelete.forEach(path -> {
                    try
                    {
                        Files.delete(path);
                    }
                    catch (IOException e)
                    {
                        _log.warn("LINCS: Error deleting file " + FileUtil.getAbsolutePath(path), e);
                    }
                });
            }
        }
    }
}

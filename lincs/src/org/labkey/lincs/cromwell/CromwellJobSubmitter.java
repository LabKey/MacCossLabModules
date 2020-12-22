package org.labkey.lincs.cromwell;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.resource.FileResource;
import org.labkey.api.settings.AppProps;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.util.FileUtil;
import org.labkey.api.webdav.WebdavService;
import org.labkey.lincs.LincsController;
import org.labkey.lincs.LincsModule;
import org.labkey.lincs.cromwell.CromwellUtil.CromwellJobStatus;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class CromwellJobSubmitter
{
    public static final String WDL = "lincs_gct_workflow.wdl";

    private final CromwellConfig _cromwellConfig;
    private final LincsModule.LincsAssay _lincsAssay;

    public CromwellJobSubmitter(@NotNull CromwellConfig cromwellConfig, @NotNull LincsModule.LincsAssay lincsAssay)
    {
        _cromwellConfig = cromwellConfig;
        _lincsAssay = lincsAssay;
    }

    public CromwellJobStatus submitJob(Container container, ITargetedMSRun run, Logger log)
    {
        FileContentService fcs = FileContentService.get();
        if(fcs == null)
        {
            log.error("Could not get FileContentService");
            return null;
        }
        Path fileRootPath = fcs.getFileRootPath(container, FileContentService.ContentType.files);
        if(fileRootPath == null)
        {
            log.error("Cannot get a file root path for container " + container.getPath());
            return null;
        }

        // Check that .sky.zip file exists in the container
        var skyDocPath = fileRootPath.resolve(run.getFileName());
        if(!Files.isRegularFile(skyDocPath))
        {
            log.error("Skyline document file does not exist: " + skyDocPath);
            return null;
        }

        // Get the wdl file contents
        Module module = ModuleLoader.getInstance().getModule(LincsModule.class);
        String wdl = getWdl(module, log);

       if(wdl != null)
       {
           // Create the GCT directory if it does not exist
           var gctDir = createDirIfNotExists(fileRootPath, LincsController.GCT_DIR, log);
           if(gctDir == null)
           {
               return null;
           }

           // Create a GCT/Cromwell directory if it does not exist
           var cromwellDir = createDirIfNotExists(gctDir, "Cromwell", log);
           if(cromwellDir == null)
           {
               return null;
           }

           // Create a sub-directory under GCT/Cromwell with the same name as the base name of the .sky.zip
           // Log files etc. from running the Cromwell workflow will be uploaded to this directory
           var workDir = createDirIfNotExists(cromwellDir, run.getBaseName(), log);
           if(workDir == null)
           {
               return null;
           }

           // Copy the Skyline report template file to the work directory
           Path skyrFile = copyReportToWorkDir(module, workDir, _lincsAssay.getSkylineReport(), log);
           if(skyrFile == null)
           {
                return null;
           }

           // Create inputs
           JSONObject inputsJson = createInputs(fileRootPath, container, skyrFile, skyDocPath, gctDir, workDir);

           // Submit job
           return CromwellUtil.submitJob(_cromwellConfig, wdl, inputsJson.toString(), log);
       }
       return null;
    }

    private String getWdl(Module module, Logger log)
    {
        FileResource resource = (FileResource)module.getModuleResolver().lookup(org.labkey.api.util.Path.parse(WDL));
        if(resource == null)
        {
            log.error("Could not find WDL resource: lincs_gct_workflow.wdl");
            return null;
        }
        File wdlFile = resource.getFile();
        if(wdlFile == null)
        {
            log.error("File not found: lincs_gct_workflow.wdl");
            return null;
        }
        try
        {
            return Files.readString(wdlFile.toPath());
        }
        catch (IOException e)
        {
            log.error("Error reading WDL file", e);
        }
        return null;
    }

    private Path copyReportToWorkDir(Module module, Path workDir, String reportTemplate, Logger log)
    {
        FileResource reportTemplateResource = (FileResource)module.getModuleResolver().lookup(org.labkey.api.util.Path.parse(reportTemplate));
        if(reportTemplateResource == null)
        {
            log.error("Could not find report template resource: " + reportTemplate);
            return null;
        }
        File reportFile = reportTemplateResource.getFile();
        if(reportFile == null)
        {
            log.error("File not found: " + reportTemplate);
            return null;
        }
        var reportInWorkDir =  workDir.resolve(reportTemplate);
        try
        {
            Files.copy(reportFile.toPath(), reportInWorkDir, StandardCopyOption.REPLACE_EXISTING);
            return reportInWorkDir;
        }
        catch (IOException e)
        {
            log.error("Error copying report file " + reportFile + " to work directory: " + workDir.toAbsolutePath(), e);
            return null;
        }
    }

    private static java.nio.file.Path createDirIfNotExists(java.nio.file.Path parent, String dirName, Logger log)
    {
        java.nio.file.Path dir = parent.resolve(dirName);
        if(Files.exists(dir))
        {
            if(!Files.isDirectory(dir))
            {
                log.error("Path exists but is not a directory " + parent.toAbsolutePath());
                return null;
            }
            return dir;
        }
        else
        {
            try
            {
                return Files.createDirectory(dir);
            }
            catch (IOException e)
            {
                log.error("Could not create subdirectory " + dirName + " in " + parent.toAbsolutePath(), e);
            }
        }
        return null;
    }

    private JSONObject createInputs(Path fileRoot, Container container, Path skyrPath, Path skyDocPath, Path gctDir, Path workdir)
    {
        /* Example:
        "lincs_gct_workflow.panorama_apikey": "apikey|xxxx",
        "lincs_gct_workflow.url_webdav_skyr": "http://localhost:8080/_webdav/00Developer/vsharma/Workflows/LINCS/%40files/p100_comprehensive_report.skyr",
        "lincs_gct_workflow.url_webdav_skyline_zip": "http://localhost:8080/_webdav/00Developer/vsharma/Workflows/LINCS/%40files/LINCS_P100_DIA_Plate70_annotated_minimized_2019-10-11_15-25-03.sky.zip",
        "lincs_gct_workflow.url_webdav_gct_dir": "http://localhost:8080/_webdav/00Developer/vsharma/Workflows/LINCS/%40files/GCT/",
        "lincs_gct_workflow.url_webdav_cromwell_output_dir": "http://localhost:8080/_webdav/00Developer/vsharma/Workflows/LINCS/%40files/GCT/Cromwell/LINCS_P100_DIA_Plate70_annotated_minimized_2019-10-11_15-25-03"
         */
        JSONObject json = new JSONObject();
        json.put("lincs_gct_workflow.panorama_apikey", _cromwellConfig.getPanoramaApiKey());
        json.put("lincs_gct_workflow.url_webdav_skyr", getWebdavUrl(fileRoot, container, skyrPath));
        json.put("lincs_gct_workflow.url_webdav_skyline_zip", getWebdavUrl(fileRoot, container, skyDocPath));
        json.put("lincs_gct_workflow.url_webdav_gct_dir", getWebdavUrl(fileRoot, container, gctDir));
        json.put("lincs_gct_workflow.url_webdav_cromwell_output_dir", getWebdavUrl(fileRoot, container, workdir));

        return json;
    }

    private String getWebdavUrl(Path fileRoot, Container container, Path file)
    {
        Path relPath = fileRoot.relativize(file);
        org.labkey.api.util.Path fileLabKeyPath = new org.labkey.api.util.Path(relPath);

        String serverUrl = AppProps.getInstance().getBaseServerUrl();
        // serverUrl = "https://panoramaweb-dr.gs.washington.edu";
        org.labkey.api.util.Path path = WebdavService.getPath().append(container.getParsedPath()).append(FileContentService.FILES_LINK).append(fileLabKeyPath);
        return serverUrl + path.encode();
    }
}

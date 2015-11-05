/*
 * Copyright (c) 2015 LabKey Corporation
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

package org.labkey.lincs;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.action.LabkeyError;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.RedirectException;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

public class LincsController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(LincsController.class);
    public static final String NAME = "lincs";

    public LincsController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView("/org/labkey/lincs/view/hello.jsp");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    // ------------------------------------------------------------------------
    // BEGIN Actions to run an R script to create GCT files.
    // NOTE: This is used only in the LINCS project on daily.panoramaweb.org
    // https://panoramaweb.org/labkey/project/LINCS/begin.view?
    //
    // This module contains an R script that writes out two GCT format files.  The first file
    // is a straight-up GCT with no processing.  The second file is created after
    // processing the data through Jake Jaffe's processing code (p100_processing.r).
    // ------------------------------------------------------------------------
    @RequiresPermission(ReadPermission.class)
    public class RunGCTReportAction extends SimpleViewAction<GCTReportForm>
    {
        public ModelAndView getView(GCTReportForm form, BindException errors) throws Exception
        {
            if(form.getRunId() == 0)
            {
                errors.addError(new LabkeyError("No run Id found in the request."));
                return new SimpleErrorView(errors, true);
            }

            if (form.getReportId() == null && StringUtils.isBlank(form.getReportName()))
            {
                errors.addError(new LabkeyError("No report name or report Id found in the request."));
                return new SimpleErrorView(errors, true);
            }

            Report report = getReport(form);

            if(report == null)
            {
                errors.addError(new LabkeyError("Could not find report with name " + form.getReportName() +
                        (form.getReportId() != null ? "or Id: " + form.getReportId() : "")));
                return new SimpleErrorView(errors, true);
            }

            if (!(report instanceof RReport))
            {
                errors.addError(new LabkeyError("The specified report is not based upon an R script and therefore cannot be executed."));
                return new SimpleErrorView(errors, true);
            }

            // Ensure that the query.RunId param is set to the same value as the runId
            String queryRunIdParam = "query.RunId~eq";
            String param = getViewContext().getActionURL().getParameter(queryRunIdParam);
            int paramRunId = 0;
            if(param != null)
            {
                try {paramRunId = Integer.parseInt(param);} catch(NumberFormatException ignored){}
            }
            if(paramRunId != form.getRunId())
            {
                ActionURL redirectUrl = getViewContext().getActionURL().clone();
                redirectUrl.replaceParameter(queryRunIdParam, String.valueOf(form.getRunId()));

                throw new RedirectException(redirectUrl);
            }

            // Get the TargetedMS run.
            TargetedMSService service = ServiceRegistry.get().getService(TargetedMSService.class);

            if (service == null)
            {
                errors.addError(new LabkeyError("Run with ID " + form.getRunId() + " was not found in the folder."));
                return new SimpleErrorView(errors, false);
            }

            ITargetedMSRun run = service.getRun(form.getRunId(), getContainer());

            if(run == null)
            {
                errors.addError(new LabkeyError("Run with ID " + form.getRunId() + " was not found in the folder."));
                return new SimpleErrorView(errors, false);
            }

            // If a GCT folder does not exist in this folder, create one
            PipeRoot root = PipelineService.get().getPipelineRootSetting(getContainer());
            assert root != null;
            File gctDir = new File(root.getRootPath(), "GCT");
            if(!NetworkDrive.exists(gctDir))
            {
                if(!gctDir.mkdir())
                {
                    errors.addError(new LabkeyError("Failed to create GCT directory '" + gctDir + "'."));
                    return new SimpleErrorView(errors, true);
                }
            }

            // Get the name of the requested GCT file
            String outputFileBaseName = run.getBaseName();
            File gct = new File(gctDir, outputFileBaseName + ".gct");
            File processedGct = new File(gctDir, outputFileBaseName + ".processed.gct");
            File downloadFile;
            if(form.isProcessed())
            {
                downloadFile = processedGct;
            }
            else
            {
                downloadFile = gct;
            }

            // Get the last modified date on the report.
            Date reportModificationDate = report.getDescriptor().getModified();
            // TODO: Get the date when the run was imported


            // Check if the folder already contains the requested GCT file.
            NetworkDrive.ensureDrive(gctDir.getPath());
            if(form.getRunId() != 0)
            {
                File[] files = gctDir.listFiles();
                for(File file: files != null ? files : new File[0])
                {
                    if(file.getName().equals(gct.getName()) || file.getName().equals(processedGct.getName()))
                    {
                        if(form.isRerun() || FileUtils.isFileOlder(file, reportModificationDate))
                        {
                            // If this file was created before the last modified date on the R report, delete it
                            // so that we run the report again for this run.
                            file.delete();
                        }
                        else if(file.getName().equals(downloadFile.getName()))
                        {
                            if(!NetworkDrive.exists(downloadFile))
                            {
                                errors.addError(new LabkeyError("File " + downloadFile + " does not exist."));
                                return new SimpleErrorView(errors, true);
                            }
                            // We found the requested GCT file.
                            PageFlowUtil.streamFile(getViewContext().getResponse(), downloadFile, true);

                            return null;
                        }
                    }
                }
            }

            // Execute the script
            RReport rreport = (RReport)report;
            try
            {
                rreport.runScript(getViewContext(), new ArrayList<>(), rreport.createInputDataFile(getViewContext()), null);
            }
            catch(Exception e)
            {
                copyFiles(gctDir, outputFileBaseName, gct, processedGct, rreport.getReportDir(getContainer().getId()));
                errors.addError(new LabkeyError("There was an error running the GCT R script."));
                errors.addError(new LabkeyError(e));
                return new SimpleErrorView(errors, true);
            }

            copyFiles(gctDir, outputFileBaseName, gct, processedGct, rreport.getReportDir(getContainer().getId()));

            if(form.getRunId() != 0)
            {
                if(!NetworkDrive.exists(downloadFile))
                {
                    errors.addError(new LabkeyError("File " + downloadFile + " does not exist."));
                    return new SimpleErrorView(errors, true);
                }
                PageFlowUtil.streamFile(getViewContext().getResponse(), downloadFile, false);
            }

            return null;
        }

        private void copyFiles(File gctDir, String outputFileBaseName, File gct, File processedGct, File reportDir) throws IOException
        {
            File[] reportDirFiles = reportDir.listFiles(new FilenameFilter()
            {
                @Override
                public boolean accept(File dir, String name)
                {
                    return name.toLowerCase().endsWith(".txt") || name.toCharArray().equals("script.Rout");
                }
            });

            // The report should create two files: lincs.gct.txt and lincs.processed.gct.txt
            // Copy both to the GCT folder
            for(File file: reportDirFiles)
            {
                if(file.getName().toLowerCase().equals("lincs.gct.txt"))
                {
                    FileUtil.copyFile(file, gct);
                }
                else if(file.getName().toLowerCase().equals("lincs.processed.gct.txt"))
                {
                    FileUtil.copyFile(file, processedGct);
                }
                else if(file.getName().toLowerCase().equals("console.txt"))
                {
                    FileUtil.copyFile(file, new File(gctDir, outputFileBaseName + ".console.txt" ));
                }
                else if(file.getName().toLowerCase().equals("script.Rout"))
                {
                    FileUtil.copyFile(file, new File(gctDir, outputFileBaseName + "script.Rout" ));
                }
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }

        public Report getReport(GCTReportForm form)
        {
            Report report = null;
            String reportId = form.getReportId();
            String reportName = form.getReportName();

            if (reportId != null)
            {
                report = ReportUtil.getReportById(getViewContext(), reportId);
            }
            else if (reportName != null)
            {
                String key = ReportUtil.getReportKey("targetedms", "GCT_input_peptidearearatio");
                if (StringUtils.isBlank(key))
                    key = reportName;
                report = ReportUtil.getReportByName(getViewContext(), reportName, key);
            }

            return report;
        }
    }

    public static class GCTReportForm
    {
        private String _reportId;
        private String _reportName;
        private int _runId;
        private boolean _rerun;
        private boolean _processed;

        public String getReportId()
        {
            return _reportId;
        }

        public void setReportId(String reportId)
        {
            _reportId = reportId;
        }

        public String getReportName()
        {
            return _reportName;
        }

        public void setReportName(String reportName)
        {
            _reportName = reportName;
        }

        public int getRunId()
        {
            return _runId;
        }

        public void setRunId(int runId)
        {
            _runId = runId;
        }

        public boolean isRerun()
        {
            return _rerun;
        }

        public void setRerun(boolean rerun)
        {
            _rerun = rerun;
        }

        public boolean isProcessed()
        {
            return _processed;
        }

        public void setProcessed(boolean processed)
        {
            _processed = processed;
        }
    }
}
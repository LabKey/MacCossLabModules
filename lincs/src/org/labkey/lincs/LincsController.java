/*
 * Copyright (c) 2015-2018 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.files.FileContentService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.SkylineAnnotation;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DetailsView;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.lincs.psp.LincsPspException;
import org.labkey.lincs.psp.LincsPspJob;
import org.labkey.lincs.psp.LincsPspPipelineJob;
import org.labkey.lincs.psp.LincsPspUtil;
import org.labkey.lincs.psp.PspEndpoint;
import org.labkey.lincs.view.GctUtils;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LincsController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(LincsController.class);
    public static final String NAME = "lincs";
    public static final String GCT_DIR = "GCT";
    public static final String P100 = "P100";
    public static final String GCP = "GCP";

    public LincsController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors)
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
                errors.addError(new LabKeyError("No run Id found in the request."));
                return new SimpleErrorView(errors, true);
            }

            if (StringUtils.isBlank(form.getReportName()))
            {
                errors.addError(new LabKeyError("No report name found in the request."));
                return new SimpleErrorView(errors, true);
            }

            Report report = getReport(form);

            if(report == null)
            {
                errors.addError(new LabKeyError("Could not find report with name " + form.getReportName()));
                return new SimpleErrorView(errors, true);
            }

            if (!(report instanceof RReport))
            {
                errors.addError(new LabKeyError("The specified report is not based upon an R script and therefore cannot be executed."));
                return new SimpleErrorView(errors, true);
            }

            // Get the TargetedMS run.
            TargetedMSService service = TargetedMSService.get();

            if (service == null)
            {
                errors.addError(new LabKeyError("Could not get an instance of TargetedMSService."));
                return new SimpleErrorView(errors, false);
            }

            ITargetedMSRun run = service.getRun(form.getRunId(), getContainer());

            if(run == null)
            {
                errors.addError(new LabKeyError("Run with ID " + form.getRunId() + " was not found in the folder " + getContainer().getPath()));
                return new SimpleErrorView(errors, false);
            }

            // If a GCT folder does not exist in this folder, create one
            Path gctDir = getGCTDir(getContainer());
            if(!Files.exists(gctDir))
            {
                Files.createDirectory(gctDir);
                if(!Files.exists(gctDir))
                {
                    errors.addError(new LabKeyError("Failed to create GCT directory '" + gctDir + "'."));
                    return new SimpleErrorView(errors, true);
                }
            }

            // Get the name of the requested GCT file
            String outputFileBaseName = run.getBaseName();
            Path gct = gctDir.resolve(outputFileBaseName + ".gct");
            Path processedGct = gctDir.resolve(outputFileBaseName + ".processed.gct");
            Path downloadFile;
            if(form.isProcessed())
            {
                downloadFile = processedGct;
            }
            else
            {
                downloadFile = gct;
            }

            // Get the date when the run was imported
            Date runCreated = run.getCreated();

            // Check if the folder already contains the requested GCT file.
            boolean fileExits = false;
            try (Stream<Path> paths = Files.list(gctDir))
            {
                for (Path path : paths.collect(Collectors.toSet()))
                {
                    if (FileUtil.getFileName(downloadFile).equals(FileUtil.getFileName(path)))
                    {
                        fileExits = true;
                        break;
                    }
                }
            }
            if(fileExits)
            {
                Date lastModified = new Date(Files.getLastModifiedTime(downloadFile).toMillis());
                if(form.isRerun() || lastModified.before(runCreated))
                {
                    // Delete the file if:
                    // 1. This file was created before the last modified date on the R report
                    // 2. The Skyline document was re-uploaded after the last GCT file was created
                    // We run the report again for this run. Delete both the .gct and .processed.gct files.
                    if(Files.exists(gct))
                    {
                        Files.delete(gct);
                    }
                    if(Files.exists(processedGct))
                    {
                        Files.delete(processedGct);
                    }
                }
                else
                {
                    // We found the requested GCT file.
                    try (InputStream inputStream = Files.newInputStream(downloadFile))
                    {
                        PageFlowUtil.streamFile(getViewContext().getResponse(), Collections.emptyMap(), FileUtil.getFileName(downloadFile), inputStream, true);
                    }
                    return null;
                }
            }

            // Create a new ViewContext that will be used to initialize QuerySettings for getting input data file (labkey.data)
            ViewContext ctx = new ViewContext(getViewContext());
            ActionURL url = getViewContext().getActionURL();
            MutablePropertyValues propertyValues = new MutablePropertyValues();
            for (String key : url.getParameterMap().keySet())
            {
                propertyValues.addPropertyValue(key, url.getParameter(key));
            }
            // This is required so that the grid gets filtered by the given RunId.
            propertyValues.addPropertyValue("GCT_input_peptidearearatio.RunId~eq", form.getRunId());
            if(outputFileBaseName.contains("_QC_"))
            {
                // GCT_input_peptidearearatio is a parameterized query; parameter is "isotope".
                // For QC files we want the medium/heavy ratio.
                propertyValues.addPropertyValue("param.isotope", "medium");
            }
            ctx.setBindPropertyValues(propertyValues);
            RReport rreport = (RReport)report;
            try
            {
                // Execute the script
                rreport.runScript(getViewContext(), new ArrayList<>(), rreport.createInputDataFile(ctx), null);
            }
            catch(Exception e)
            {
                copyFiles(gctDir, outputFileBaseName, gct, processedGct, rreport.getReportDir(getContainer().getId()));
                errors.addError(new LabKeyError("There was an error running the GCT R script."));
                errors.addError(new LabKeyError(e));
                return new SimpleErrorView(errors, true);
            }

            copyFiles(gctDir, outputFileBaseName, gct, processedGct, rreport.getReportDir(getContainer().getId()));


            if(!Files.exists(downloadFile))
            {
                errors.addError(new LabKeyError("File " + downloadFile + " does not exist."));
                return new SimpleErrorView(errors, true);
            }
            try (InputStream inputStream = Files.newInputStream(downloadFile))
            {
                PageFlowUtil.streamFile(getViewContext().getResponse(), Collections.emptyMap(), FileUtil.getFileName(downloadFile), inputStream, true);
            }

            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    private void copyFiles(Path gctDir, String outputFileBaseName, Path gct, @Nullable Path processedGct, File reportDir) throws IOException
    {
        // reportDir is a local directory under tomcat's temp directory
        File[] reportDirFiles = reportDir.listFiles(new FilenameFilter()
        {
            @Override
            public boolean accept(File dir, String name)
            {
                return name.toLowerCase().endsWith(".txt")
                        || name.toLowerCase().endsWith(".gct")
                        || name.equals("script.Rout");
            }
        });

        // The report should create two files: lincs.gct and lincs.processed.gct
        // Copy both to the GCT folder
        for(File file: reportDirFiles)
        {
            if(file.getName().toLowerCase().equals("lincs.gct"))
            {
                Files.copy(file.toPath(), gct);
            }
            else if(file.getName().toLowerCase().equals("lincs.processed.gct") && processedGct != null)
            {
                Files.copy(file.toPath(), processedGct);
            }
            else if(file.getName().toLowerCase().equals("console.txt"))
            {
                Files.copy(file.toPath(), gctDir.resolve(outputFileBaseName + ".console.txt" ), StandardCopyOption.REPLACE_EXISTING);
            }
            else if(file.getName().equals("script.Rout"))
            {
                Files.copy(file.toPath(), gctDir.resolve(outputFileBaseName + ".script.Rout" ), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private Report getReport(GCTReportForm form)
    {
        Report report = null;
        String reportName = form.getReportName();

        if (reportName != null)
        {
            String key = ReportUtil.getReportKey("targetedms", "GCT_input_peptidearearatio");
            if (StringUtils.isBlank(key))
                key = reportName;
            report = ReportUtil.getReportByName(getViewContext(), reportName, key);
        }

        return report;
    }

    @RequiresPermission(ReadPermission.class)
    public class RunGCTReportApiAction extends ReadOnlyApiAction<GCTReportForm>
    {
        public ApiResponse execute(GCTReportForm form, BindException errors) throws Exception
        {
            if(form.getRunId() == 0)
            {
                throw new ApiUsageException("No run Id found in the request.");
            }

            if (StringUtils.isBlank(form.getReportName()))
            {
                throw new ApiUsageException("No report name found in the request.");
            }

            Report report = getReport(form);

            if(report == null)
            {
                throw new ApiUsageException("Could not find report with name " + form.getReportName());
            }

            if (!(report instanceof RReport))
            {
                throw new ApiUsageException("The specified report is not based upon an R script and therefore cannot be executed.");
            }

            // Get the TargetedMS run.
            TargetedMSService service = TargetedMSService.get();

            if (service == null)
            {
                throw new ApiUsageException("Could not get an instance of TargetedMSService.");
            }

            ITargetedMSRun run = service.getRun(form.getRunId(), getContainer());

            if(run == null)
            {
                throw new ApiUsageException("Run with ID " + form.getRunId() + " was not found in the folder " + getContainer().getPath());
            }

            // If a GCT folder does not exist in this folder, create one
            Path gctDir = getGCTDir(getContainer());
            if(!Files.exists(gctDir))
            {
                Files.createDirectory(gctDir);
                if(!Files.exists(gctDir))
                {
                    throw new ApiUsageException("Failed to create GCT directory '" + gctDir + "'.");
                }
            }

            // Get the name of the requested GCT file
            String outputFileBaseName = run.getBaseName();
            Path downloadFile = gctDir.resolve(outputFileBaseName + ".gct");

            // Check if the folder already contains the requested GCT file.
            boolean fileExits = false;
            try (Stream<Path> paths = Files.list(gctDir))
            {
                for (Path path : paths.collect(Collectors.toSet()))
                {
                    if (FileUtil.getFileName(downloadFile).equals(FileUtil.getFileName(path)))
                    {
                        fileExits = true;
                        break;
                    }
                }
            }
            if(fileExits)
            {
                if (form.isRerun())
                {
                    Files.delete(downloadFile);
                }
                else
                {
                    try (InputStream inputStream = Files.newInputStream(downloadFile))
                    {
                        PageFlowUtil.streamFile(getViewContext().getResponse(), Collections.emptyMap(), FileUtil.getFileName(downloadFile), inputStream, true);
                        return null;
                    }
                }
            }

            // Create a new ViewContext that will be used to initialize QuerySettings for getting input data file (labkey.data)
            ViewContext ctx = new ViewContext(getViewContext());
            ActionURL url = getViewContext().getActionURL();

            MutablePropertyValues propertyValues = new MutablePropertyValues();
            for (String key : url.getParameterMap().keySet())
            {
                propertyValues.addPropertyValue(key, url.getParameter(key));
            }

            // This is required so that the grid gets filtered by the given RunId.
            propertyValues.addPropertyValue("GCT_input_peptidearearatio.RunId~eq", form.getRunId());
            if(outputFileBaseName.contains("_QC_"))
            {
                // GCT_input_peptidearearatio is a parameterized query; parameter is "isotope".
                // For QC files we want the medium/heavy ratio.
                propertyValues.addPropertyValue("param.isotope", "medium");
            }
            ctx.setBindPropertyValues(propertyValues);
            RReport rreport = (RReport)report;
            try
            {
                // Execute the script
                rreport.runScript(getViewContext(), new ArrayList<>(), rreport.createInputDataFile(ctx), null);
            }
            catch(Exception e)
            {
                copyFiles(gctDir, outputFileBaseName, downloadFile, null, rreport.getReportDir(getContainer().getId()));
                throw new ApiUsageException("There was an error running the GCT R script.", e);
            }

            copyFiles(gctDir, outputFileBaseName, downloadFile, null, rreport.getReportDir(getContainer().getId()));

            if(!Files.exists(downloadFile))
            {
                throw new ApiUsageException("File " + downloadFile + " does not exist.");
            }
            try (InputStream inputStream = Files.newInputStream(downloadFile))
            {
                PageFlowUtil.streamFile(getViewContext().getResponse(), Collections.emptyMap(), FileUtil.getFileName(downloadFile), inputStream, true);
            }

            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    public static class GCTReportForm
    {
        private String _reportName;
        private int _runId;
        private boolean _rerun;
        private boolean _processed;

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

    @RequiresPermission(ReadPermission.class)
    public class CreateCustomGCTAction extends FormViewAction<CustomGCTForm>
    {
        @Override
        public void validateCommand(CustomGCTForm target, Errors errors)
        {

        }

        @Override
        public ModelAndView getView(CustomGCTForm customGCTForm, boolean reshow, BindException errors)
        {
            if(customGCTForm.getCustomGctBean() != null)
            {
                JspView view = new JspView("/org/labkey/lincs/view/downloadCustomGCT.jsp", customGCTForm, errors);
                view.setFrame(WebPartView.FrameType.PORTAL);
                view.setTitle("Download Custom GCT");
                return view;
            }
            else
            {
                CustomGCTBean bean = new CustomGCTBean();
                bean.setForm(customGCTForm);
                if(customGCTForm.getExperimentType() == null)
                {
                    customGCTForm.setExperimentType(CustomGCTForm.DEFAULT_EXPTYPE);
                }
                // Get a list of replicate annotations in this folder
                bean.setAnnotations(getReplicateAnnotationNameValues(getUser(), getContainer()));


                JspView view = new JspView("/org/labkey/lincs/view/customGCTForm.jsp", bean, errors);
                view.setFrame(WebPartView.FrameType.PORTAL);
                view.setTitle("Create Custom GCT");
                return view;
            }
        }

        @Override
        public boolean handlePost(CustomGCTForm customGCTForm, BindException errors)
        {
            // Get the replicate annotation values from the form
            List<SelectedAnnotation> annotations = customGCTForm.getSelectedAnnotationValues();
            // Get a list of GCT files that we want so use as input.
            String[] experimentTypes = customGCTForm.getExperimentTypes();
            List<Path> files = getGCTFiles(getContainer(), experimentTypes, errors);
            if(errors.hasErrors())
            {
                return false;
            }
            if(files.size() == 0)
            {
                errors.reject(ERROR_MSG, "No GCT files found in the folder for experiment type " + customGCTForm.getExperimentType());
                return false;
            }

            // Write out a custom GCT file

            GctBean customGctBean = writeCustomGCT(files, annotations,errors);
            if(errors.hasErrors())
            {
                return false;
            }
            customGCTForm.setCustomGctBean(customGctBean);

            return false;
        }

        private GctBean writeCustomGCT(List<Path> files, List<SelectedAnnotation> selectedAnnotations,
                                    BindException errors)
        {
            Path gctDir = getGCTDir(getContainer());
            if(!Files.exists(gctDir))
            {
                errors.reject(ERROR_MSG, "GCT directory does not exist: " + FileUtil.getAbsolutePath(gctDir));
                return null;
            }

            LincsManager manager = LincsManager.get();
            User user = getUser();
            Container container = getContainer();
            Set<String> ignoredProbeAnnotations = getIgnoredAnnotations(manager.getPeptideAnnotations(user, container));
            Set<String> ignoredReplicateAnnotations = getIgnoredAnnotations(manager.getReplicateAnnotations(user, container));
            Gct customGct;
            try
            {
                CustomGctBuilder builder = new CustomGctBuilder();
                customGct = builder.build(files, selectedAnnotations, ignoredProbeAnnotations, ignoredReplicateAnnotations);
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, "Error building custom GCT.");
                errors.addError(new LabKeyError(e));
                return null;
            }

            String gctFileName = FileUtil.makeFileNameWithTimestamp("CustomGCT", "gct");
            Path gctFile = gctDir.resolve(gctFileName);
            try
            {
                GctUtils.writeGct(customGct, gctFile);
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, "Error writing custom GCT file " + FileUtil.getAbsolutePath(gctFile));
                errors.addError(new LabKeyError(e));
                return null;
            }
            GctBean customGctBean = new GctBean();
            customGctBean.setGctFile(gctFile);
            customGctBean.setProbeCount(customGct.getProbeCount());
            customGctBean.setProbeAnnotationCount(customGct.getProbeAnnotationCount());
            customGctBean.setReplicateCount(customGct.getReplicateCount());
            customGctBean.setReplicateAnnotationCount(customGct.getReplicateAnnotationCount());
            return customGctBean;
        }



        private List<Path> getGCTFiles(Container container, String[] experimentTypes, BindException errors)
        {
            TargetedMSService service = TargetedMSService.get();

            if (service == null)
            {
                errors.reject(ERROR_MSG, "Could not get TargetedMSService from the ServiceRegistry");
                return Collections.emptyList();
            }

            List<ITargetedMSRun> runs = service.getRuns(container);
            List<Path> gctFiles = new ArrayList<>();

            Path gctDir = getGCTDir(getContainer());
            if(!Files.exists(gctDir))
            {
                errors.reject(ERROR_MSG, "GCT directory does not exist: " + FileUtil.getAbsolutePath(gctDir));
                return Collections.emptyList();
            }

            boolean processOnClue = LincsModule.processGctOnClueServer(container);

            for(ITargetedMSRun run: runs)
            {
                String outputFileBaseName = run.getBaseName();
                if(!fileMatchesExperimentType(outputFileBaseName, experimentTypes))
                {
                    continue;
                }
                String ext = processOnClue ? LincsModule.getExt(LincsModule.LincsLevel.Four) : ".processed.gct";
                Path processedGct = gctDir.resolve(outputFileBaseName + ext);
                if(Files.exists(processedGct))
                {
                    gctFiles.add(processedGct);
                }
                else
                {
                    // TODO: Try to generate the GCT?
                    errors.reject(ERROR_MSG, "GCT file does not exist: " + FileUtil.getAbsolutePath(processedGct));
                }
            }
            return gctFiles;
        }

        private boolean fileMatchesExperimentType(String outputFileBaseName, String[] experimentTypes)
        {
            outputFileBaseName = outputFileBaseName.toUpperCase();
            if(outputFileBaseName.contains("_GCP_") || outputFileBaseName.contains("_QC"))
            {
                return true; // TODO: GCP and QC files do not have experiment type in file names; include all
            }
            for(String expType: experimentTypes)
            {
               if(outputFileBaseName.contains("_" + expType.toUpperCase() + "_"))
               {
                   return true;
               }
            }
            return false;
        }

        @Override
        public URLHelper getSuccessURL(CustomGCTForm customGCTForm)
        {
            return null;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    private Set<String> getIgnoredAnnotations(List<LincsAnnotation> lincsAnnotations)
    {
        Set<String> ignored = new HashSet<>();
        for(LincsAnnotation annotation: lincsAnnotations)
        {
            if(annotation.isIgnored())
            {
                ignored.add(annotation.getName());
            }
        }
        return ignored;
    }

    public static class CustomGCTForm
    {
        private String _experimentType;
        private String _selectedAnnotations;
        private GctBean _customGctBean;

        public static final String DIA = "DIA";
        public static final String PRM = "PRM";
        public static final String DIA_OR_PRM = DIA + " or " + PRM;
        public static final String DEFAULT_EXPTYPE = DIA_OR_PRM;
        public static final String[] EXP_TYPES = new String[] {DIA_OR_PRM, DIA, PRM};

        public String getExperimentType()
        {
            return _experimentType;
        }

        public String[] getExperimentTypes()
        {
            if(StringUtils.isBlank(_experimentType))
            {
                return new String[0];
            }

            return _experimentType.split("\\sor\\s");
        }

        public void setExperimentType(String experimentType)
        {
            _experimentType = experimentType;
        }

        public String getSelectedAnnotations()
        {
            return _selectedAnnotations;
        }

        public void setSelectedAnnotations(String selectedAnnotations)
        {
            _selectedAnnotations = selectedAnnotations;
        }

        public GctBean getCustomGctBean()
        {
            return _customGctBean;
        }

        public void setCustomGctBean(GctBean customGctBean)
        {
            _customGctBean = customGctBean;
        }

        public List<SelectedAnnotation> getSelectedAnnotationValues()
        {
            String formString = StringUtils.trimToEmpty(getSelectedAnnotations());
            String[] annotationStrings = formString.split(";");

            List<SelectedAnnotation> annotations = new ArrayList<>();
            for(String s: annotationStrings)
            {
                int idx = s.indexOf(":");
                if(idx != -1 && s.length() > idx + 1)
                {
                    String name = s.substring(0, idx);
                    String[] values = s.substring(idx + 1).split(",");
                    SelectedAnnotation annotation = new SelectedAnnotation(new LincsAnnotation(name, name, false, false));
                    for(String value: values)
                    {
                        annotation.addValue(value);
                    }
                    annotations.add(annotation);
                }
            }
            return annotations;
        }
    }

    public static class GctBean
    {
        private Path _gctFile;
        private int _probeCount;
        private int _replicateCount;
        private int _probeAnnotationCount;
        private int _replicateAnnotationCount;

        public Path getGctFile()
        {
            return _gctFile;
        }

        public void setGctFile(Path gctFile)
        {
            _gctFile = gctFile;
        }

        public int getProbeCount()
        {
            return _probeCount;
        }

        public void setProbeCount(int probeCount)
        {
            _probeCount = probeCount;
        }

        public int getReplicateCount()
        {
            return _replicateCount;
        }

        public void setReplicateCount(int replicateCount)
        {
            _replicateCount = replicateCount;
        }

        public int getProbeAnnotationCount()
        {
            return _probeAnnotationCount;
        }

        public void setProbeAnnotationCount(int probeAnnotationCount)
        {
            _probeAnnotationCount = probeAnnotationCount;
        }

        public int getReplicateAnnotationCount()
        {
            return _replicateAnnotationCount;
        }

        public void setReplicateAnnotationCount(int replicateAnnotationCount)
        {
            _replicateAnnotationCount = replicateAnnotationCount;
        }
    }
    @RequiresPermission(ReadPermission.class)
    public class DownloadCustomGCTReportAction extends SimpleViewAction<DownloadCustomGCTReportForm>
    {
        public ModelAndView getView(DownloadCustomGCTReportForm form, BindException errors) throws Exception
        {
            if(form.getFileName() == null)
            {
                errors.reject(ERROR_MSG, "Request does not contain a fileName parameter");
                return new SimpleErrorView(errors, false);
            }

            Path gctDir = getGCTDir(getContainer());
            Path downloadFile = gctDir.resolve(form.getFileName());
            if(!Files.exists(downloadFile))
            {
                errors.reject(ERROR_MSG, "File does not exist '" + form.getFileName() + "'.");
                return new SimpleErrorView(errors, false);
            }
            try (InputStream inputStream = Files.newInputStream(downloadFile))
            {
                PageFlowUtil.streamFile(getViewContext().getResponse(), Collections.emptyMap(), FileUtil.getFileName(downloadFile), inputStream, true);
            }
            try
            {
                Files.delete(downloadFile);
            }
            catch(IOException ignored){}
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    public static class DownloadCustomGCTReportForm
    {
        private String _fileName;

        public String getFileName()
        {
            return _fileName;
        }

        public void setFileName(String fileName)
        {
            _fileName = fileName;
        }
    }
    public class CustomGCTBean
    {
        private List<SelectedAnnotation> _annotations;
        private CustomGCTForm _form;

        public List<SelectedAnnotation> getAnnotations()
        {
            return _annotations;
        }

        public void setAnnotations(List<SelectedAnnotation> annotations)
        {
            _annotations = annotations;
        }

        public CustomGCTForm getForm()
        {
            return _form;
        }

        public void setForm(CustomGCTForm form)
        {
            _form = form;
        }
    }

    private static List<SelectedAnnotation> getReplicateAnnotationNameValues(User user, Container container)
    {
        TargetedMSService service = TargetedMSService.get();

        if (service == null)
        {
            return Collections.emptyList();
        }

        List<? extends SkylineAnnotation> skyAnnotations = service.getReplicateAnnotations(container);

        List<LincsAnnotation> lincsAnnotations = LincsManager.get().getReplicateAnnotations(user, container);
        Map<String, LincsAnnotation> lincsAnnotationMap = new HashMap<>(lincsAnnotations.size());
        for(LincsAnnotation annotation: lincsAnnotations)
        {
            lincsAnnotationMap.put(annotation.getName(), annotation);
        }

        Map<String, SelectedAnnotation> annotationMap = new HashMap<>();
        for(SkylineAnnotation sAnnot : skyAnnotations)
        {
            String name = sAnnot.getName();
            SelectedAnnotation annot = annotationMap.get(name);
            if(annot == null)
            {
                LincsAnnotation lAnnot = lincsAnnotationMap.get(name);
                if (lAnnot == null)
                {
                    lAnnot = new LincsAnnotation(name, name, false, false);
                }
                else if (lAnnot.isIgnored())
                {
                    continue;
                }
                annot = new SelectedAnnotation(lAnnot);
                annotationMap.put(name, annot);
            }

            annot.addValue(sAnnot.getValue());
        }

        ArrayList<SelectedAnnotation> annotations = new ArrayList<>(annotationMap.values());
        annotations.sort(Comparator.comparing(SelectedAnnotation::getName));
        return annotations;
    }

    public static class SelectedAnnotation
    {
        private LincsAnnotation _lincsAnnotation;
        private Set<String> _values;

        private SelectedAnnotation(LincsAnnotation lincsAnnotation)
        {
            _lincsAnnotation = lincsAnnotation;
            _values = new HashSet<>();
        }

        public String getDisplayName()
        {
            return _lincsAnnotation.getDisplayName();
        }

        private void addValue(String value)
        {
            if(!StringUtils.isBlank(value))
            {
                _values.add(value);
            }
        }

        public String getName()
        {
            return _lincsAnnotation.getName();
        }

        public Set<String> getValues()
        {
            return _values;
        }

        public List<String> getSortedValues()
        {
            List vals = new ArrayList(_values);
            Collections.sort(vals);
            return vals;
        }

        public boolean isAdvanced()
        {
            return _lincsAnnotation.isAdvanced();
        }
    }

    static Path getGCTDir(Container container)
    {
        Path root = FileContentService.get().getFileRootPath(container, FileContentService.ContentType.files);
        assert root != null;
        return root.resolve(GCT_DIR);
    }

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

    @RequiresPermission(ReadPermission.class)
    public class GetLincsStatusAction extends ReadOnlyApiAction
    {
        @Override
        public Object execute(Object o, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            // Make sure the LINCS module is enabled in the folder
            if(!getContainer().getActiveModules().contains(ModuleLoader.getInstance().getModule(LincsModule.class)))
            {
                response.put("error", "Not a LINCS folder.");
                return response;
            }

            // Get a list of runs in the folder
            TargetedMSService service = TargetedMSService.get();
            List<ITargetedMSRun> runs = service.getRuns(getContainer());

            // Get the location of the GCT folder
            Path gctDir = getGCTDir(getContainer());


            // For each run get return the date it was imported, along with the names and create dates on the .gct
            // and .processed.gct files for the run
            List<Map<String, Object>> lincsData = new ArrayList<>();
            for(ITargetedMSRun run: runs)
            {
                Map<String, Object> data = new HashMap<>();
                data.put("skyline_doc", run.getBaseName());
                data.put("skyline_doc_date", dateFormat.format(run.getCreated()));

                if(Files.exists(gctDir))
                {
                    String outputFileBaseName = run.getBaseName();
                    Path gct = gctDir.resolve(outputFileBaseName + ".gct");
                    Path processedGct = gctDir.resolve(outputFileBaseName + ".processed.gct");

                    if(Files.exists(gct))
                    {
                        //data.put("gct", gct.getName());
                        try
                        {
                            Date lastMod = new Date(Files.getLastModifiedTime(gct).toMillis());
                            data.put("gct_date", dateFormat.format(lastMod));
                        }
                        catch (IOException e)
                        {
                            data.put("gct_date", "ERROR_GETTING_DATE");
                        }
                    }

                    if(Files.exists(processedGct))
                    {
                        //data.put("processed_gct", processedGct.getName());
                        try
                        {
                            Date lastMod = new Date(Files.getLastModifiedTime(gct).toMillis());
                            data.put("processed_gct_date", dateFormat.format(lastMod));
                        }
                        catch (IOException e)
                        {
                            data.put("processed_gct_date", "ERROR_GETTING_DATE");
                        }
                    }
                }

                lincsData.add(data);
            }

            response.put("lincs_data", lincsData);
            return response;
        }
    }

    public static LincsModule.LincsAssay getLincsAssayType(Container container)
    {
        if(isOrHasAncestor(container, LincsController.P100))
        {
            return LincsModule.LincsAssay.P100;
        }
        else if(isOrHasAncestor(container, LincsController.GCP))
        {
            return LincsModule.LincsAssay.GCP;
        }
        return null;
    }

    private static boolean isOrHasAncestor(Container container, String name)
    {
        if(container.isRoot())
        {
            return false;
        }
        if(container.getName().equals(name))
        {
            return true;
        }
        return isOrHasAncestor(container.getParent(), name);
    }

    public static String LINCS_CLUE_CREDENTIALS = "Lincs Clue Server Credentials";
    public static String CLUE_SERVER_URI = "serverUri";
    public static String CLUE_API_KEY = "apiKey";

    @RequiresPermission(AdminPermission.class)
    @ActionNames("pspConfig")
    public class ManageLincsClueCredentials extends FormViewAction<ClueCredentialsForm>
    {
        @Override
        public void validateCommand(ClueCredentialsForm target, Errors errors) {}

        @Override
        public boolean handlePost(ClueCredentialsForm form, BindException errors)
        {
            PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getWritableProperties(getContainer(), LINCS_CLUE_CREDENTIALS, true);
            map.put(CLUE_SERVER_URI, form.getServerUri());
            map.put(CLUE_API_KEY, form.getApiKey());
            map.save();
            return true;
        }

        @Override
        public URLHelper getSuccessURL(ClueCredentialsForm form)
        {
            return new ActionURL(ManageLincsClueCredentials.class, getContainer());
        }

        @Override
        public ModelAndView getView(ClueCredentialsForm form, boolean reshow, BindException errors)
        {
            PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getWritableProperties(getContainer(), LINCS_CLUE_CREDENTIALS, false);
            if(map != null)
            {
                form.setServerUri(map.get(CLUE_SERVER_URI));
                form.setApiKey(map.get(CLUE_API_KEY));
            }
            return new JspView<>("/org/labkey/lincs/view/manageClueCredentials.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    public static class ClueCredentialsForm
    {
        private String _serverUri;
        private String _apiKey;

        public String getServerUri()
        {
            return _serverUri;
        }

        public void setServerUri(String serverUri)
        {
            _serverUri = serverUri;
        }

        public String getApiKey()
        {
            return _apiKey;
        }

        public void setApiKey(String apiKey)
        {
            _apiKey = apiKey;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class LincsPspJobDetailsAction extends SimpleViewAction<LincsPspJobForm>
    {
        public ModelAndView getView(LincsPspJobForm form, BindException errors)
        {
            int runId = form.getRunId();

            LincsPspJob pspJob = LincsManager.get().getLincsPspJobForRun(runId);
            if(pspJob == null)
            {
                errors.addError(new LabKeyError("Could not find a PSP job for runId: " + runId));
                return new SimpleErrorView(errors);
            }

            VBox view = new VBox();

            DataRegion dr = getPspJobDetailsDataRegion();

            dr.addHiddenFormField("runId", String.valueOf(runId));
            dr.addHiddenFormField("jobId", String.valueOf(pspJob.getId()));

            ButtonBar buttonBar = new ButtonBar();
            buttonBar.setStyle(ButtonBar.Style.separateButtons);

            ActionURL statusUrl = new ActionURL(LincsPspJobStatusAction.class, getViewContext().getContainer());
            ActionButton statusButton = new ActionButton(statusUrl, "Get Status");
            statusButton.setActionType(ActionButton.Action.GET);
            buttonBar.add(statusButton);

            if (getUser().isInSiteAdminGroup())
            {
                ActionURL updateStatusUrl = new ActionURL(UpdatePspJobStatusAction.class, getViewContext().getContainer());
                ActionButton updateButton = new ActionButton(updateStatusUrl, "Update Status");
                updateButton.setActionType(ActionButton.Action.POST);
                buttonBar.add(updateButton);

                if(pspJob.canRetry() || pipelineJobError(pspJob))
                {
                    ActionURL url = new ActionURL(SubmitPspJobAction.class, getViewContext().getContainer());
                    ActionButton resubmitJobButton = new ActionButton(url, "Re-submit");
                    resubmitJobButton.setActionType(ActionButton.Action.POST);
                    buttonBar.add(resubmitJobButton);
                }
            }

            dr.setButtonBar(buttonBar);

            DetailsView detailsView = new DetailsView(dr, pspJob.getId());
            view.addView(detailsView);

            if(pspJob.getPipelineJobId() != null && getUser().isInSiteAdminGroup())
            {
                ActionURL pipelineJobUrl = PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlDetails(getContainer(), pspJob.getPipelineJobId());
                view.addView(new HtmlView(PageFlowUtil.textLink("View Pipeline Job. Status: " + PipelineService.get().getStatusFile(pspJob.getPipelineJobId()).getStatus(), pipelineJobUrl)));
            }

            view.setTitle("PSP Job Details");
            view.setFrame(WebPartView.FrameType.PORTAL);
            return view;
        }

        private boolean pipelineJobError(LincsPspJob pspJob)
        {
            if(pspJob.getPipelineJobId() != null)
            {
                PipelineStatusFile status = PipelineService.get().getStatusFile(pspJob.getPipelineJobId());
                return status != null && PipelineJob.TaskStatus.error.matches(status.getStatus());
            }
            return false;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            if(root != null)
            {
                root.addChild("PSP Job Details");
            }
            return root;
        }
    }

    @NotNull
    private static DataRegion getPspJobDetailsDataRegion()
    {
        DataRegion dr = new DataRegion();
        TableInfo tInfo = LincsManager.getTableInfoLincsPspJob();
        dr.setColumns(tInfo.getColumns("Id", "Created", "CreatedBy", "Modified", "ModifiedBy", "Container", "PspJobId", "PspJobName", "RunId", "Status", "Error", "Progress", "Json"));

        dr.getDisplayColumn("Progress").setVisible(false);
        dr.getDisplayColumn("Json").setVisible(false);

        DataColumn errCol = (DataColumn)dr.getDisplayColumn("Error");
        errCol.setPreserveNewlines(true);

        SimpleDisplayColumn jsonCol = new SimpleDisplayColumn(){

            @Override
            public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
            {
                String json = ctx.get(FieldKey.fromParts("Json"), String.class);
                JSONObject jsonObj = new JSONObject(json);
                out.write("<pre>" + PageFlowUtil.filter(jsonObj.toString(2)) + "</pre>");
            }
        };
        jsonCol.setCaption("JSON:");

        SimpleDisplayColumn progressCol = new SimpleDisplayColumn()
        {
            @Override
            public Object getValue(RenderContext ctx)
            {
                Integer progress = ctx.get(FieldKey.fromParts("Progress"), Integer.class);
                if(progress != null)
                {
                    String str = "";
                    int i = progress.intValue();
                    str = (i&1) == 1 ? "L2 done " : "";
                    str += (i&2) == 2 ? "L3 done " : "";
                    str += (i&4) == 4 ? "L4 done " : "";
                    return str;
                }
                else
                {
                    return super.getValue(ctx);
                }
            }
        };
        progressCol.setCaption("PSP Progress:");

        List<DisplayColumn> columns = dr.getDisplayColumns();
        dr.addDisplayColumn(columns.size() - 2, progressCol);
        dr.addDisplayColumn(jsonCol);
        return dr;
    }

    @RequiresPermission(AdminPermission.class)
    public class LincsPspJobStatusAction extends SimpleViewAction<LincsPspJobForm>
    {
        public ModelAndView getView(LincsPspJobForm form, BindException errors)
        {
            int jobId = form.getJobId();

            LincsPspJob pspJob = LincsManager.get().getLincsPspJob(jobId);
            if(pspJob == null)
            {
                errors.addError(new LabKeyError("Could not find a PSP job for id: " + jobId));
                return new SimpleErrorView(errors);
            }

            if(pspJob.getPspJobId() == null)
            {
                errors.addError(new LabKeyError("PSP job Id is null. Cannot get status"));
                return new SimpleErrorView(errors);
            }

            VBox view = new VBox();

            PspEndpoint endpoint;
            try
            {
                endpoint = LincsPspUtil.getPspEndpoint(getContainer());
            }
            catch (LincsPspException e)
            {
                errors.addError(new LabKeyError(e.getMessage()));
                return new SimpleErrorView(errors);
            }
            String jsonStatus;
            try
            {
                jsonStatus = LincsPspUtil.getJobStatusString(endpoint, pspJob);
            }
            catch (IOException e)
            {
                errors.addError(new LabKeyError("Error getting job status. Error was: " + e.getMessage()));
                return new SimpleErrorView(errors);
            }
            catch (LincsPspException e)
            {
                errors.addError(new LabKeyError(e.getMessage()));
                return new SimpleErrorView(errors);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("<p>").append("Status for job: " + pspJob.getId() +", PSP job Id: ").append(PageFlowUtil.filter(pspJob.getPspJobId()))
                    .append(", Run Id: ")
                    .append(pspJob.getRunId()).append("</p>");
            sb.append("<br>JSON Output:</br>");
            sb.append("<p><pre>").append(PageFlowUtil.filter(jsonStatus)).append("</pre></p>");
            view.addView(new HtmlView(sb.toString()));
            view.setTitle("PSP job status");
            view.setFrame(WebPartView.FrameType.PORTAL);
            return view;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            if(root != null)
            {
                root.addChild("PSP Job Status");
            }
            return root;
        }
    }

    @RequiresSiteAdmin
    public class UpdatePspJobStatusAction extends FormHandlerAction<LincsPspJobForm>
    {
        private int _runId;

        @Override
        public void validateCommand(LincsPspJobForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(LincsPspJobForm form, BindException errors)
        {
            int jobId = form.getJobId();
            _runId = form.getRunId();

            LincsPspJob pspJob = LincsManager.get().getLincsPspJob(jobId);
            if(pspJob == null)
            {
                errors.reject(ERROR_MSG, "Could not find a PSP job for id: " + jobId);
                return false;
            }

            if(pspJob.getPspJobId() == null)
            {
                errors.reject(ERROR_MSG, "PSP job Id is null. Cannot update status");
                return false;
            }

            PspEndpoint endpoint;
            try
            {
                endpoint = LincsPspUtil.getPspEndpoint(getContainer());
            }
            catch (LincsPspException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }

            try
            {
                LincsPspUtil.updateJobStatus(endpoint, pspJob, getUser());
            }
            catch (IOException e)
            {
                errors.reject(ERROR_MSG, "Error updating job status. Error was: " + e.getMessage());
                return false;
            }
            catch (LincsPspException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }
            return true;
        }

        @Override
        public URLHelper getSuccessURL(LincsPspJobForm lincsPspJobForm)
        {
            ActionURL url = new ActionURL(LincsPspJobDetailsAction.class, getContainer());
            url.addParameter("runId", _runId);
            return url;
        }
    }

    public static class LincsPspJobForm
    {
        private int _runId;
        private int _jobId; // id in the lincs.lincspspjob table

        public int getRunId()
        {
            return _runId;
        }

        public void setRunId(int runId)
        {
            _runId = runId;
        }

        public int getJobId()
        {
            return _jobId;
        }

        public void setJobId(int jobId)
        {
            _jobId = jobId;
        }
    }

    @RequiresSiteAdmin
    public class SubmitPspJobAction extends FormHandlerAction<LincsPspJobForm>
    {
        @Override
        public void validateCommand(LincsPspJobForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(LincsPspJobForm form, BindException errors)
        {
            int runId = form.getRunId();
            Container container = getContainer();

            ITargetedMSRun skylineRun = TargetedMSService.get().getRun(runId, getContainer());
            if(skylineRun == null)
            {
                errors.reject(ERROR_MSG, "Could not find a targetedms run with id " + runId + " in container " + getContainer().getPath());
                return false;
            }

            PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
            if (root == null || !root.isValid())
            {
                errors.reject(ERROR_MSG, "No valid pipeline root found for " + container.getPath());
                return false;
            }

            PspEndpoint pspEndpoint = null;
            try
            {
                pspEndpoint = LincsPspUtil.getPspEndpoint(container);
            }
            catch(LincsPspException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }

            LincsManager lincsManager = LincsManager.get();

            LincsPspJob oldPspJob = lincsManager.getLincsPspJobForRun(runId);
            LincsPspJob newPspJob = lincsManager.saveNewLincsPspJob(skylineRun, getUser());

            ViewBackgroundInfo info = new ViewBackgroundInfo(container, getUser(), null);
            LincsPspPipelineJob job = new LincsPspPipelineJob(info, root, skylineRun, newPspJob, oldPspJob, pspEndpoint);
            try
            {
                PipelineService.get().queueJob(job);
            }
            catch (PipelineValidationException e)
            {
                lincsManager.deleteLincsPspJob(newPspJob);
                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }

            int jobId = PipelineService.get().getJobId(getUser(), container, job.getJobGUID());
            newPspJob.setPipelineJobId(jobId);
            lincsManager.updatePipelineJobId(newPspJob);
            return true;
        }

        @Override
        public URLHelper getSuccessURL(LincsPspJobForm lincsPspJobForm)
        {
            return PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer());
        }
    }
}
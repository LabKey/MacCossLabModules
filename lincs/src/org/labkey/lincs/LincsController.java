/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ModuleReportDescriptor;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.SkylineAnnotation;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.lincs.view.GctUtils;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
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
            TargetedMSService service = ServiceRegistry.get().getService(TargetedMSService.class);

            if (service == null)
            {
                errors.addError(new LabKeyError("Run with ID " + form.getRunId() + " was not found in the folder."));
                return new SimpleErrorView(errors, false);
            }

            ITargetedMSRun run = service.getRun(form.getRunId(), getContainer());

            if(run == null)
            {
                errors.addError(new LabKeyError("Run with ID " + form.getRunId() + " was not found in the folder."));
                return new SimpleErrorView(errors, false);
            }

            // If a GCT folder does not exist in this folder, create one
            File gctDir = getGCTDir(getContainer());
            if(!NetworkDrive.exists(gctDir))
            {
                if(!gctDir.mkdir())
                {
                    errors.addError(new LabKeyError("Failed to create GCT directory '" + gctDir + "'."));
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
            Date reportModificationDate = new Date();
            ReportDescriptor descriptor = report.getDescriptor();
            if(descriptor instanceof ModuleReportDescriptor)
            {
                Resource resource = ((ModuleReportDescriptor) descriptor).getSourceFile();
                if(resource != null && resource.exists())
                {
                    reportModificationDate = new Date(resource.getLastModified());
                }
            }
            // Get the date when the run was imported
            Date runCreated = run.getCreated();


            // Check if the folder already contains the requested GCT file.
            NetworkDrive.ensureDrive(gctDir.getPath());
            if(form.getRunId() != 0)
            {
                File[] files = gctDir.listFiles();
                for(File file: files != null ? files : new File[0])
                {
                    if(file.getName().equals(gct.getName()) || file.getName().equals(processedGct.getName()))
                    {
                        if(form.isRerun() || FileUtils.isFileOlder(file, reportModificationDate)
                                          || FileUtils.isFileOlder(file, runCreated))
                        {
                            // Delete the file if:
                            // 1. This file was created before the last modified date on the R report
                            // 2. The Skyline document was re-uploaded after the last GCT file was created
                            // We run the report again for this run.
                            file.delete();
                        }
                        else if(file.getName().equals(downloadFile.getName()))
                        {
                            if(!NetworkDrive.exists(downloadFile))
                            {
                                errors.addError(new LabKeyError("File " + downloadFile + " does not exist."));
                                return new SimpleErrorView(errors, true);
                            }
                            // We found the requested GCT file.
                            PageFlowUtil.streamFile(getViewContext().getResponse(), downloadFile, true);

                            return null;
                        }
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
                copyFiles(gctDir, outputFileBaseName, gct, processedGct, rreport.getReportDir(getContainer().getId()));
                errors.addError(new LabKeyError("There was an error running the GCT R script."));
                errors.addError(new LabKeyError(e));
                return new SimpleErrorView(errors, true);
            }

            copyFiles(gctDir, outputFileBaseName, gct, processedGct, rreport.getReportDir(getContainer().getId()));

            if(form.getRunId() != 0)
            {
                if(!NetworkDrive.exists(downloadFile))
                {
                    errors.addError(new LabKeyError("File " + downloadFile + " does not exist."));
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
                    FileUtil.copyFile(file, gct);
                }
                else if(file.getName().toLowerCase().equals("lincs.processed.gct"))
                {
                    FileUtil.copyFile(file, processedGct);
                }
                else if(file.getName().toLowerCase().equals("console.txt"))
                {
                    FileUtil.copyFile(file, new File(gctDir, outputFileBaseName + ".console.txt" ));
                }
                else if(file.getName().equals("script.Rout"))
                {
                    FileUtil.copyFile(file, new File(gctDir, outputFileBaseName + ".script.Rout" ));
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
        public ModelAndView getView(CustomGCTForm customGCTForm, boolean reshow, BindException errors) throws Exception
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
        public boolean handlePost(CustomGCTForm customGCTForm, BindException errors) throws Exception
        {
            // Get the replicate annotation values from the form
            List<SelectedAnnotation> annotations = customGCTForm.getSelectedAnnotationValues();
            // Get a list of GCT files that we want so use as input.
            String[] experimentTypes = customGCTForm.getExperimentTypes();
            List<File> files = getGCTFiles(getContainer(), experimentTypes, errors);
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

        private GctBean writeCustomGCT(List<File> files, List<SelectedAnnotation> selectedAnnotations,
                                    BindException errors)
        {
            File gctDir = getGCTDir(getContainer());
            if(!NetworkDrive.exists(gctDir))
            {
                errors.reject(ERROR_MSG, "GCT directory does not exist: " + gctDir.getPath());
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
                errors.reject(ERROR_MSG, e.getMessage());
                errors.addError(new LabKeyError(e));
                return null;
            }

            String gctFileName = FileUtil.makeFileNameWithTimestamp("CustomGCT", "gct");
            File gctFile = new File(gctDir, gctFileName);
            try
            {
                GctUtils.writeGct(customGct, gctFile);
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, "Error writing custom GCT file " + gctFile.getPath());
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



        private List<File> getGCTFiles(Container container, String[] experimentTypes, BindException errors)
        {
            TargetedMSService service = ServiceRegistry.get().getService(TargetedMSService.class);

            if (service == null)
            {
                errors.reject(ERROR_MSG, "Could not get TargetedMSService from the ServiceRegistry");
                return Collections.emptyList();
            }

            List<ITargetedMSRun> runs = service.getRuns(container);
            List<File> gctFiles = new ArrayList<>();

            File gctDir = getGCTDir(getContainer());
            if(!NetworkDrive.exists(gctDir))
            {
                errors.reject(ERROR_MSG, "GCT directory does not exist: " + gctDir.getPath());
                return Collections.emptyList();
            }

            for(ITargetedMSRun run: runs)
            {
                String outputFileBaseName = run.getBaseName();
                if(!fileMatchesExperimentType(outputFileBaseName, experimentTypes))
                {
                    continue;
                }
                File processedGct = new File(gctDir, outputFileBaseName + ".processed.gct");
                if(NetworkDrive.exists(processedGct))
                {
                    gctFiles.add(processedGct);
                }
                else
                {
                    // TODO: Try to generate the GCT?
                    errors.reject(ERROR_MSG, "GCT file does not exist: " + processedGct.getPath());
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
        private File _gctFile;
        private int _probeCount;
        private int _replicateCount;
        private int _probeAnnotationCount;
        private int _replicateAnnotationCount;

        public File getGctFile()
        {
            return _gctFile;
        }

        public void setGctFile(File gctFile)
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
            PipeRoot root = PipelineService.get().getPipelineRootSetting(getContainer());
            assert root != null;
            File gctDir = new File(root.getRootPath(), "GCT");
            File downloadFile = new File(gctDir, form.getFileName());
            if(!NetworkDrive.exists(downloadFile))
            {
                errors.reject(ERROR_MSG, "File does not exist '" + form.getFileName() + "'.");
                return new SimpleErrorView(errors, false);
            }
            PageFlowUtil.streamFile(getViewContext().getResponse(), downloadFile, true);
            downloadFile.delete();
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
        TargetedMSService service = ServiceRegistry.get().getService(TargetedMSService.class);

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

        public boolean isAdvanced()
        {
            return _lincsAnnotation.isAdvanced();
        }
    }

    private static File getGCTDir(Container container)
    {
        PipeRoot root = PipelineService.get().getPipelineRootSetting(container);
        assert root != null;
        return new File(root.getRootPath(), GCT_DIR);
    }

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

    @RequiresPermission(ReadPermission.class)
    public class GetLincsStatusAction extends ApiAction
    {
        @Override
        public Object execute(Object o, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            // Make sure the LINCS module is enabled in the folder
            if(!getContainer().getActiveModules().contains(ModuleLoader.getInstance().getModule(LincsModule.class)))
            {
                response.put("error", "Not a LINCS folder.");
                return response;
            }

            // Get a list of runs in the folder
            TargetedMSService service = ServiceRegistry.get().getService(TargetedMSService.class);
            List<ITargetedMSRun> runs = service.getRuns(getContainer());

            // Get the location of the GCT folder
            File gctDir = getGCTDir(getContainer());


            // For each run get return the date it was imported, along with the names and create dates on the .gct
            // and .processed.gct files for the run
            List<Map<String, Object>> lincsData = new ArrayList<>();
            for(ITargetedMSRun run: runs)
            {
                Map<String, Object> data = new HashMap<>();
                data.put("skyline_doc", run.getBaseName());
                data.put("skyline_doc_date", dateFormat.format(run.getCreated()));

                if(gctDir.exists())
                {
                    String outputFileBaseName = run.getBaseName();
                    File gct = new File(gctDir, outputFileBaseName + ".gct");
                    File processedGct = new File(gctDir, outputFileBaseName + ".processed.gct");

                    if(gct.exists())
                    {
                        //data.put("gct", gct.getName());
                        data.put("gct_date", dateFormat.format(gct.lastModified()));
                    }

                    if(processedGct.exists())
                    {
                        //data.put("processed_gct", processedGct.getName());
                        data.put("processed_gct_date", dateFormat.format(processedGct.lastModified()));
                    }
                }

                lincsData.add(data);
            }

            response.put("lincs_data", lincsData);
            return response;
        }
    }

    public static LincsDataTable.LincsAssay getLincsAssayType(Container container)
    {
        if(isOrHasAncestor(container, LincsController.P100))
        {
            return LincsDataTable.LincsAssay.P100;
        }
        else if(isOrHasAncestor(container, LincsController.GCP))
        {
            return LincsDataTable.LincsAssay.GCP;
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
}
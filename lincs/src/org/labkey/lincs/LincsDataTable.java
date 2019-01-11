/*
 * Copyright (c) 2017 LabKey Corporation
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
import org.labkey.api.analytics.AnalyticsService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.UserSchema;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by vsharma on 8/21/2017.
 */
public class LincsDataTable extends FilteredTable
{
    public static final String PARENT_QUERY = "TargetedMSRunAndAnnotations";
    public static final String NAME = "LincsDataTable";
    public static final String PLATE_COL = "Plate";
    private static Pattern plateRegex = Pattern.compile("^LINCS.*_(Plate\\d+[a-zA-Z]*)_.*\\.sky\\.zip$");

    public LincsDataTable(@NotNull TableInfo table, @NotNull UserSchema userSchema)
    {
        super(table, userSchema);

        wrapAllColumns(true);

        PipeRoot root = PipelineService.get().getPipelineRootSetting(getContainer());
        assert root != null;

        ColumnInfo plateCol = wrapColumn(PLATE_COL, getRealTable().getColumn(FieldKey.fromParts("Token")));
        addColumn(plateCol);
        plateCol.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new PlateColumn(colInfo);
            }
        });

        ColumnInfo level1Col =  wrapColumn("Level 1", getRealTable().getColumn(FieldKey.fromParts("FileName")));
        addColumn(level1Col);
        level1Col.setDisplayColumnFactory(colInfo -> new DataColumn(colInfo){
            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                ActionURL downloadUrl = new ActionURL("targetedms", "DownloadDocument", getContainer());
                Integer runId = ctx.get(FieldKey.fromParts("Id"), Integer.class);
                downloadUrl.addParameter("runId", runId);
                out.write("<nobr>");
                out.write(PageFlowUtil.iconLink("fa fa-download", "Download", downloadUrl.getEncodedLocalURIString(), null, null, null));
                ActionURL docDetailsUrl = new ActionURL("targetedms", "ShowPrecursorList", getContainer());
                docDetailsUrl.addParameter("id", runId);
                out.write("&nbsp;<a href=\"" + docDetailsUrl.getEncodedLocalURIString() + "\">Skyline</a>");
                out.write("</nobr>");
            }

            @Override
            public void addQueryFieldKeys(Set<FieldKey> keys)
            {
                keys.add(FieldKey.fromParts("Id"));
                super.addQueryFieldKeys(keys);
            }

        });

        ColumnInfo cellLineCol = getColumn(FieldKey.fromParts("CellLine"));
        cellLineCol.setTextAlign("left");

        Path gctDir = root.getRootNioPath().resolve(LincsController.GCT_DIR);
        String davUrl = AppProps.getInstance().getBaseServerUrl() + root.getWebdavURL();
        LincsModule.LincsAssay assayType = LincsController.getLincsAssayType(getContainer());

        boolean processOnClue = LincsModule.processGctOnClueServer(getContainer());

        if(!processOnClue)
        {

            ColumnInfo level2Col = wrapColumn("Level 2", getRealTable().getColumn(FieldKey.fromParts("FileName")));
            addColumn(level2Col);
            level2Col.setDisplayColumnFactory(colInfo -> new GctColumn(colInfo, assayType, LincsModule.LincsLevel.Two, gctDir, davUrl));

            ColumnInfo level4Col = wrapColumn("Level 4", getRealTable().getColumn(FieldKey.fromParts("FileName")));
            addColumn(level4Col);
            level4Col.setDisplayColumnFactory(colInfo -> new GctColumn(colInfo, assayType, LincsModule.LincsLevel.Four, gctDir, davUrl));

        }
        else
        {
            ColumnInfo level2Col = wrapColumn("Level 2", getRealTable().getColumn(FieldKey.fromParts("FileName")));
            addColumn(level2Col);
            level2Col.setDisplayColumnFactory(colInfo -> new LincsDataTable.GctColumnPSP(colInfo, assayType, LincsModule.LincsLevel.Two, gctDir, davUrl));

            ColumnInfo level3Col = wrapColumn("Level 3", getRealTable().getColumn(FieldKey.fromParts("FileName")));
            addColumn(level3Col);
            level3Col.setDisplayColumnFactory(colInfo -> new LincsDataTable.GctColumnPSP(colInfo, assayType, LincsModule.LincsLevel.Three, gctDir, davUrl));

            ColumnInfo level4Col = wrapColumn("Level 4", getRealTable().getColumn(FieldKey.fromParts("FileName")));
            addColumn(level4Col);
            level4Col.setDisplayColumnFactory(colInfo -> new LincsDataTable.GctColumnPSP(colInfo, assayType, LincsModule.LincsLevel.Four, gctDir, davUrl));

            ColumnInfo cfgCol = wrapColumn("Config", getRealTable().getColumn(FieldKey.fromParts("FileName")));
            addColumn(cfgCol);
            cfgCol.setDisplayColumnFactory(colInfo -> new LincsDataTable.GctColumnPSP(colInfo, assayType, LincsModule.LincsLevel.Config, gctDir, davUrl));
        }

        List<FieldKey> visibleColumns = new ArrayList<>();
        visibleColumns.add(FieldKey.fromParts("Plate"));
        visibleColumns.add(FieldKey.fromParts("Label"));
        visibleColumns.add(FieldKey.fromParts("CellLine"));
        visibleColumns.add(FieldKey.fromParts("Level 1"));
        visibleColumns.add(FieldKey.fromParts("Level 2"));
        if(processOnClue)
        {
            visibleColumns.add(FieldKey.fromParts("Level 3"));
        }
        visibleColumns.add(FieldKey.fromParts("Level 4"));
        if(processOnClue)
        {
            visibleColumns.add(FieldKey.fromParts("Config"));
        }

        setDefaultVisibleColumns(visibleColumns);
    }

    private static final String EXT_SKY_ZIP_W_DOT = ".sky.zip";
    public static String getBaseName(String fileName)
    {
        if(fileName == null)
            return "";
        if(fileName.toLowerCase().endsWith(EXT_SKY_ZIP_W_DOT))
            return FileUtil.getBaseName(fileName, 2);
        else
            return FileUtil.getBaseName(fileName, 1);
    }

    public class GctColumn extends DataColumn
    {
        private LincsModule.LincsAssay assayType;
        private LincsModule.LincsLevel level;
        private Path gctDir;
        private String davUrl;

        public GctColumn(ColumnInfo col, LincsModule.LincsAssay assayType, LincsModule.LincsLevel level, Path gctDir, String davUrl)
        {
            super(col);
            this.assayType = assayType;
            this.level = level;
            this.gctDir = gctDir;
            this.davUrl = davUrl;
        }

        public LincsModule.LincsAssay getAssayType()
        {
            return assayType;
        }

        public LincsModule.LincsLevel getLevel()
        {
            return level;
        }

        public Path getGctDir()
        {
            return gctDir;
        }

        public String getDavUrl()
        {
            return davUrl;
        }

        @Override
        public void addQueryFieldKeys(Set<FieldKey> keys)
        {
            keys.add(FieldKey.fromParts("Id"));
            super.addQueryFieldKeys(keys);
        }

        @Override
        public boolean isFilterable()
        {
            return false;
        }

        @Override
        public boolean isSortable()
        {
            return false;
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            if(assayType == null)
            {
                out.write("Unknown assay type");
                return;
            }
            String fileName = ctx.get(getDisplayColumn().getFieldKey(), String.class);
            if(fileName == null)
            {
                out.write("&nbsp");
                return;
            }

            Integer runId = ctx.get(FieldKey.fromParts("Id"), Integer.class);
            if(runId == null)
            {
                out.write("<NO_RUN_ID>");
                return;
            }

            String baseName = getBaseName(fileName);
            if(level == LincsModule.LincsLevel.Four)
            {
                baseName += ".processed";
            }
            Path gct = gctDir.resolve(baseName + ".gct");
            boolean gctExists = Files.exists(gct);

            String analyticsScript = getAnalyticsScript("DownloadGCT", FileUtil.getFileName(gct), true);

            ActionURL downloadGctUrl = new ActionURL(LincsController.RunGCTReportAction.class, getContainer());
            downloadGctUrl.addParameter("runId", runId);
            downloadGctUrl.addParameter("reportName", getGctReportName(assayType));
            if(level == LincsModule.LincsLevel.Four)
            {
                downloadGctUrl.addParameter("processed", true);
            }

            String morpheusUrl = gctExists ? externalHeatmapViewerLink(FileUtil.getFileName(gct), assayType) : "";
            renderGridCell(out, analyticsScript, downloadGctUrl, morpheusUrl);
        }

        private void renderGridCell(Writer out, String analyticsScript, ActionURL downloadGctUrl, String morpheusUrl) throws IOException
        {
            renderGridCell(out, analyticsScript, downloadGctUrl.getLocalURIString(), downloadGctUrl.getEncodedLocalURIString(), "GCT", morpheusUrl);
        }

        void renderGridCell(Writer out, String analyticsScript, String downloadUrl, String downloadUrlEncoded, String downloadText, String morpheusUrl) throws IOException
        {
            out.write("<nobr>&nbsp;");
            // Do not HTML encode links given to PageFlowUtil.iconLink
            out.write(PageFlowUtil.iconLink("fa fa-download", "Download", downloadUrl, analyticsScript, null, null));
            out.write("&nbsp;");
            String onclickEvt = StringUtils.isBlank(analyticsScript) ? "" : "onclick=\"" + analyticsScript + "\"";
            out.write("<a " + onclickEvt + " href=\"" + downloadUrlEncoded + "\">" + downloadText + "</a>&nbsp;");
            if(morpheusUrl != null)
            {
                out.write("&nbsp;" + morpheusUrl + "&nbsp;");
            }
            out.write("</nobr>");
        }

        String getAnalyticsScript(String eventAction, String fileName, boolean addWaitTime)
        {
            if (!StringUtils.isBlank(AnalyticsService.getTrackingScript()))
            {
                // http://www.blastam.com/blog/how-to-track-downloads-in-google-analytics
                // Tell the browser to wait 400ms before going to the download.  This is to ensure
                // that the GA tracking request goes through. Some browsers will interrupt the tracking
                // request if the download opens on the same page.
                String timeout = addWaitTime ? "setTimeout(function(){location.href=that.href;},400);return false;" : "";
                String script = "if(_gaq) {that=this; _gaq.push(['_trackEvent', 'Lincs', '" + eventAction + "', '" + fileName + "']); " + timeout + "}";
                return script;
            }
            return null;
        }

        String getGctReportName(LincsModule.LincsAssay assayType)
        {
            switch (assayType)
            {
                case GCP: return "GCT File GCP";
                case P100: return "GCT File P100";
            }
            return null;
        }

        String externalHeatmapViewerLink(String fileName, LincsModule.LincsAssay assayType)
        {
            String gctFileUrl = davUrl + "GCT/" + PageFlowUtil.encodePath(fileName);
            String morpheusUrl = getMorpheusUrl(gctFileUrl, assayType);

            String analyticsScript = getAnalyticsScript("Morpheus", fileName, false);
            String onclickEvt = StringUtils.isBlank(analyticsScript) ? "" : "onclick=\"" + analyticsScript + "\"";

            String imgUrl = AppProps.getInstance().getContextPath() + "/lincs/GENE-E_icon.png";
            return "[&nbsp;<a target=\"_blank\" " + onclickEvt +  " href=\"" + morpheusUrl + "\">View in Morpheus</a> <img src=" + imgUrl + " width=\"13\", height=\"13\"/>&nbsp;]";
        }

        String getMorpheusUrl(String gctFileUrl, LincsModule.LincsAssay assayType)
        {
            String morpheusJson = "{\"dataset\":\"" + gctFileUrl + "\",";
            if(assayType == LincsModule.LincsAssay.P100)
            {
                morpheusJson += "\"rows\":[{\"field\":\"pr_p100_modified_peptide_code\",\"display\":\"Text\"},{\"field\":\"pr_gene_symbol\",\"display\":\"Text\"},{\"field\":\"pr_p100_phosphosite\",\"display\":\"Text\"},{\"field\":\"pr_uniprot_id\",\"display\":\"Text\"}],";
            }
            if(assayType == LincsModule.LincsAssay.GCP)
            {
                morpheusJson += "\"rows\":[{\"field\":\"pr_gcp_histone_mark\",\"display\":\"Text\"},{\"field\":\"pr_gcp_modified_peptide_code\",\"display\":\"Text\"}],";
            }
            morpheusJson += "\"columns\":[{\"field\":\"pert_iname\",\"display\":\"Text\"},{\"field\":\"det_well\",\"display\":\"Text\"}],";
            morpheusJson += "\"colorScheme\":{\"type\":\"fixed\",\"map\":[{\"value\":-3,\"color\":\"blue\"},{\"value\":0,\"color\":\"white\"},{\"value\":3,\"color\":\"red\"}]}";
            morpheusJson += "}";

            String morpheusUrl= "http://www.broadinstitute.org/cancer/software/morpheus/?json=";
            morpheusUrl += PageFlowUtil.encodeURIComponent(morpheusJson);
            return morpheusUrl;
        }
    }

    public class GctColumnPSP extends GctColumn
    {
        public GctColumnPSP(ColumnInfo col, LincsModule.LincsAssay assayType, LincsModule.LincsLevel level, Path gctDir, String davUrl)
        {
            super(col, assayType, level, gctDir, davUrl);
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            if(getAssayType() == null)
            {
                out.write("Unknown assay type");
                return;
            }
            String fileName = ctx.get(getDisplayColumn().getFieldKey(), String.class);
            if(fileName == null)
            {
                out.write("&nbsp");
                return;
            }

            Integer runId = ctx.get(FieldKey.fromParts("Id"), Integer.class);
            if(runId == null)
            {
                out.write("<NO_RUN_ID>");
                return;
            }

            String downloadFileName = getBaseName(fileName);
            if(getLevel() == LincsModule.LincsLevel.Three)
            {
                downloadFileName += "_LVL3";
            }
            else if(getLevel() == LincsModule.LincsLevel.Four)
            {
                downloadFileName += "_LVL4";
            }
            String extension = (getLevel() == LincsModule.LincsLevel.Config) ? ".cfg" : ".gct";
            downloadFileName = downloadFileName + extension;
            if(!Files.exists(getGctDir().resolve(downloadFileName)))
            {
                out.write("NOT AVAILABLE");
                return;
            }

            String actionName = (getLevel() == LincsModule.LincsLevel.Config) ? "DownloadConfig" : "DownloadGCT";
            String analyticsScript = getAnalyticsScript(actionName, downloadFileName, true);
            String morpheusUrl = externalHeatmapViewerLink(downloadFileName, getAssayType(), getLevel());
            String downloadText = (getLevel() == LincsModule.LincsLevel.Config) ? "CFG" : "GCT";
            renderGridCell(out, analyticsScript, getGctDavUrlUnencoded(downloadFileName), getGctDavUrl(downloadFileName), downloadText, morpheusUrl);
        }

        String externalHeatmapViewerLink(String fileName, LincsModule.LincsAssay assayType, LincsModule.LincsLevel level)
        {
            if(level == LincsModule.LincsLevel.Config)
            {
                return null;
            }
            return externalHeatmapViewerLink(fileName, assayType);
        }

        private String getGctDavUrl(String fileName)
        {
            return getDavUrl() + "GCT/" + PageFlowUtil.encodePath(fileName);
        }

        private String getGctDavUrlUnencoded(String fileName)
        {
            return getDavUrl() + "GCT/" + fileName;
        }
    }

    public static class PlateColumn extends DataColumn
    {
        public PlateColumn(ColumnInfo col)
        {
            super(col);
        }

        @Override
        public Object getValue(RenderContext ctx)
        {
            String value = ctx.get(getDisplayColumn().getFieldKey(), String.class);
            if(value != null)
            {
                value = value.replace("P100_PRM_", "");
                value = value.replace("P100_DIA_", "");
                value = value.replace("GCP_", "");
                return value;
            }
            else
            {
                // If the metadata table does not have an entry for this Skyline document get the plate number from the name of Skyline file.
                String fileName = ctx.get(FieldKey.fromParts("FileName"), String.class);
                if(fileName != null)
                {
                    Matcher match = plateRegex.matcher(fileName);
                    if(match.matches())
                    {
                        value = match.group(1);
                        return value;
                    }
                }
            }

            return super.getValue(ctx);
        }

        @Override
        public void addQueryFieldKeys(Set<FieldKey> keys)
        {
            keys.add(FieldKey.fromParts("FileName"));
            super.addQueryFieldKeys(keys);
        }

        @NotNull
        @Override
        public String getFormattedValue(RenderContext ctx)
        {
            return (String)getDisplayValue(ctx);
        }

        @Override
        public Object getDisplayValue(RenderContext ctx)
        {
            return getValue(ctx);
        }
    }
}

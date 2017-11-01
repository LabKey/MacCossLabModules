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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.UserSchema;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by vsharma on 8/21/2017.
 */
public class LincsDataTable extends FilteredTable
{
    public static final String PARENT_QUERY = "TargetedMSRunAndAnnotations";
    public static final String NAME = "LincsDataTable";
    public static final String PLATE_COL = "Plate";

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
                return new DataColumn(colInfo)
                {
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

                        return super.getValue(ctx);
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
                };
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

        File gctDir = new File(root.getRootPath(), LincsController.GCT_DIR);
        String davUrl = AppProps.getInstance().getBaseServerUrl() + root.getWebdavURL();
        LincsAssay assayType = LincsController.getLincsAssayType(getContainer());

        ColumnInfo level2Col = wrapColumn("Level 2", getRealTable().getColumn(FieldKey.fromParts("FileName")));
        addColumn(level2Col);
        level2Col.setDisplayColumnFactory(colInfo -> new GctColumn(colInfo, assayType, LincsLevel.Two, gctDir, davUrl));

        ColumnInfo level3Col = wrapColumn("Level 4", getRealTable().getColumn(FieldKey.fromParts("FileName")));
        addColumn(level3Col);
        level3Col.setDisplayColumnFactory(colInfo -> new GctColumn(colInfo, assayType, LincsLevel.Four, gctDir, davUrl));

        List<FieldKey> visibleColumns = new ArrayList<>();
        visibleColumns.add(FieldKey.fromParts("Plate"));
        visibleColumns.add(FieldKey.fromParts("Label"));
        visibleColumns.add(FieldKey.fromParts("CellLine"));
        visibleColumns.add(FieldKey.fromParts("Level 1"));
        visibleColumns.add(FieldKey.fromParts("Level 2"));
        visibleColumns.add(FieldKey.fromParts("Level 4"));

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

    public enum LincsAssay
    {
        P100,
        GCP;
    }

    private enum LincsLevel
    {
        One,Two,Three,Four
    }

    public class GctColumn extends DataColumn
    {
        private LincsAssay assayType;
        private LincsLevel level;
        private File gctDir;
        private String davUrl;

        public GctColumn(ColumnInfo col, LincsAssay assayType, LincsLevel level, File gctDir, String davUrl)
        {
            super(col);
            this.assayType = assayType;
            this.level = level;
            this.gctDir = gctDir;
            this.davUrl = davUrl;
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
            if(level == LincsLevel.Four)
            {
                baseName += ".processed";
            }
            File gct = new File(gctDir, baseName + ".gct");
            boolean gctExists = NetworkDrive.exists(gct);

            String timeout = "that = this; setTimeout(function(){location.href=that.href;},400);return false;";
            String gaEventPush = "_gaq.push(['_trackEvent', 'Lincs', 'DownloadGCT', ";
            String analyticsEvt= " onclick=\"" + gaEventPush + "'" + gct.getName() + "']); " + timeout + "\" ";

            ActionURL downloadGctUrl = new ActionURL(LincsController.RunGCTReportAction.class, getContainer());
            downloadGctUrl.addParameter("runId", runId);
            downloadGctUrl.addParameter("reportName", getGctReportName(assayType));
            if(level == LincsLevel.Four)
            {
                downloadGctUrl.addParameter("processed", true);
            }

            String morpheusUrl = gctExists ? externalHeatmapViewerLink(gct.getName(), assayType) : "";
            out.write("<nobr>&nbsp;");
            // Do not HTML encode links given to PageFlowUtil.iconLink
            out.write(PageFlowUtil.iconLink("fa fa-download", "Download", downloadGctUrl.getLocalURIString(), null, null, null));
            out.write("&nbsp;");
            out.write("<a " + analyticsEvt + "href=\"" + downloadGctUrl.getEncodedLocalURIString() + "\">GCT</a>&nbsp;&nbsp;" + morpheusUrl + "&nbsp;");
            out.write("</nobr>");
        }

        private String getGctReportName(LincsAssay assayType)
        {
            switch (assayType)
            {
                case GCP: return "GCT File GCP";
                case P100: return "GCT File P100";
            }
            return null;
        }

        private String externalHeatmapViewerLink(String fileName, LincsAssay assayType)
        {
            String gctFileUrl = davUrl + "GCT/" + PageFlowUtil.encodePath(fileName);
            String morpheusUrl = getMorpheusUrl(gctFileUrl, assayType);

            String analyticsEvt = " onclick=\"_gaq.push(['_trackEvent', 'Lincs', 'Morpheus', '" + fileName + "']);\" ";

            String imgUrl = AppProps.getInstance().getContextPath() + "/lincs/GENE-E_icon.png";
            return "[&nbsp;<a target=\"_blank\" " + analyticsEvt + "href=\"" + morpheusUrl + "\">View in Morpheus</a> <img src=" + imgUrl + " width=\"13\", height=\"13\"/>&nbsp;]";
        }

        private String getMorpheusUrl(String gctFileUrl, LincsAssay assayType)
        {
            String morpheusJson = "{\"dataset\":\"" + gctFileUrl + "\",";
            if(assayType == LincsAssay.P100)
            {
                morpheusJson += "\"rows\":[{\"field\":\"pr_p100_modified_peptide_code\",\"display\":\"Text\"},{\"field\":\"pr_gene_symbol\",\"display\":\"Text\"},{\"field\":\"pr_p100_phosphosite\",\"display\":\"Text\"},{\"field\":\"pr_uniprot_id\",\"display\":\"Text\"}],";
            }
            if(assayType == LincsAssay.GCP)
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
}

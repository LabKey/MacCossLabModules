/*
 * Copyright (c) 2017-2026 LabKey Corporation
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
import org.labkey.api.analytics.AnalyticsService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.UserSchema;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.DOM;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.HtmlWriter;
import org.labkey.lincs.psp.LincsPspJob;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.labkey.api.util.DOM.Attribute.height;
import static org.labkey.api.util.DOM.Attribute.src;
import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.Attribute.width;
import static org.labkey.api.util.DOM.IMG;
import static org.labkey.api.util.DOM.SPAN;
import static org.labkey.api.util.DOM.at;

/**
 * Created by vsharma on 8/21/2017.
 */
public class LincsDataTable extends FilteredTable
{
    public static final String PARENT_QUERY = "TargetedMSRunAndAnnotations";
    public static final String NAME = "LincsDataTable";
    public static final String PLATE_COL = "Plate";
    private static final Pattern plateRegex = Pattern.compile("^LINCS.*_(Plate[a-zA-Z0-9]*)_.*\\.sky\\.zip$");

    public LincsDataTable(@NotNull TableInfo table, @NotNull UserSchema userSchema)
    {
        super(table, userSchema);

        wrapAllColumns(true);

        PipeRoot root = PipelineService.get().getPipelineRootSetting(getContainer());
        assert root != null;

        var plateCol = wrapColumn(PLATE_COL, getRealTable().getColumn(FieldKey.fromParts("Token")));
        addColumn(plateCol);
        plateCol.setDisplayColumnFactory(PlateColumn::new);

        var level1Col =  wrapColumn("Level 1", getRealTable().getColumn(FieldKey.fromParts("FileName")));
        addColumn(level1Col);
        level1Col.setDisplayColumnFactory(colInfo -> new DataColumn(colInfo){
            @Override
            public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
            {
                ActionURL downloadUrl = new ActionURL("targetedms", "DownloadDocument", getContainer());
                Integer runId = ctx.get(FieldKey.fromParts("Id"), Integer.class);
                downloadUrl.addParameter("id", runId);
                ActionURL docDetailsUrl = new ActionURL("targetedms", "ShowPrecursorList", getContainer());
                docDetailsUrl.addParameter("id", runId);

                // <span style="white-space: nowrap;"> is recommended instead of deprecated <nobr></nobr>
                SPAN(
                    at(style, "white-space: nowrap;"),
                    LinkBuilder.labkeyLink("Download", downloadUrl).iconCls("fa fa-download"),
                    HtmlString.NBSP,
                    LinkBuilder.simpleLink("Skyline", docDetailsUrl)
                ).appendTo(out);
            }

            @Override
            public void addQueryFieldKeys(Set<FieldKey> keys)
            {
                keys.add(FieldKey.fromParts("Id"));
                super.addQueryFieldKeys(keys);
            }

            @Override
            public boolean isSortable()
            {
                return false;
            }

            @Override
            public boolean isFilterable()
            {
                return false;
            }
        });

        var cellLineCol = getMutableColumn(FieldKey.fromParts("CellLine"));
        cellLineCol.setTextAlign("left");

        Path gctDir = root.getRootNioPath().resolve(LincsController.GCT_DIR);
        String davUrl = AppProps.getInstance().getBaseServerUrl() + root.getWebdavURL();
        LincsModule.LincsAssay assayType = LincsController.getLincsAssayType(getContainer());

        var level2Col = wrapColumn("Level 2", getRealTable().getColumn(FieldKey.fromParts("FileName")));
        addColumn(level2Col);
        level2Col.setDisplayColumnFactory(colInfo -> new GctColumnPSP(colInfo, assayType, LincsModule.LincsLevel.Two, gctDir, davUrl));

        var level3Col = wrapColumn("Level 3", getRealTable().getColumn(FieldKey.fromParts("FileName")));
        addColumn(level3Col);
        level3Col.setDisplayColumnFactory(colInfo -> new GctColumnPSP(colInfo, assayType, LincsModule.LincsLevel.Three, gctDir, davUrl));

        var level4Col = wrapColumn("Level 4", getRealTable().getColumn(FieldKey.fromParts("FileName")));
        addColumn(level4Col);
        level4Col.setDisplayColumnFactory(colInfo -> new GctColumnPSP(colInfo, assayType, LincsModule.LincsLevel.Four, gctDir, davUrl));

        var cfgCol = wrapColumn("Config", getRealTable().getColumn(FieldKey.fromParts("FileName")));
        addColumn(cfgCol);
        cfgCol.setDisplayColumnFactory(colInfo -> new GctColumnPSP(colInfo, assayType, LincsModule.LincsLevel.Config, gctDir, davUrl));

        var pspJobCol = wrapColumn("PSP Job Status", getRealTable().getColumn(FieldKey.fromParts("FileName")));
        addColumn(pspJobCol);
        pspJobCol.setDisplayColumnFactory(colInfo -> new DataColumn(colInfo)
        {
            @Override
            public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
            {
                Integer runId = ctx.get(FieldKey.fromParts("Id"), Integer.class);
                if(runId == null)
                {
                    out.write("NO_RUN_ID");
                    return;
                }
                LincsPspJob pspJob = LincsManager.get().getLincsPspJobForRun(runId, getContainer());
                if(pspJob == null)
                {
                    out.write("PSP job not found for runId: " + runId);
                    if(userSchema.getUser().hasSiteAdminPermission())
                    {
                        ActionURL url = new ActionURL(LincsController.SubmitPspJobAction.class, getContainer());
                        url.addParameter("runId", runId);

                        out.write(LinkBuilder.labkeyLink(" [Submit Job]", url).usePost());
                    }
                    return;
                }
                String text = pspJob.getStatus();
                if(StringUtils.isBlank(text))
                {
                    if(pspJob.getPipelineJobId() != null)
                    {
                        text = "Pipeline Status: " + PipelineService.get().getStatusFile(pspJob.getPipelineJobId()).getStatus();
                    }
                    else
                    {
                        text = "Job Details";
                    }
                }
                ActionURL url = new ActionURL(LincsController.LincsPspJobDetailsAction.class, getContainer());
                url.addParameter("runId", pspJob.getRunId());
                out.write(LinkBuilder.labkeyLink(text, url));
            }

            @Override
            public boolean isSortable()
            {
                return false;
            }

            @Override
            public boolean isFilterable()
            {
                return false;
            }

            @Override
            public boolean isEditable()
            {
                return false;
            }
        });


        List<FieldKey> visibleColumns = new ArrayList<>();
        visibleColumns.add(FieldKey.fromParts("Plate"));
        visibleColumns.add(FieldKey.fromParts("Label"));
        visibleColumns.add(FieldKey.fromParts("CellLine"));
        visibleColumns.add(FieldKey.fromParts("Level 1"));
        visibleColumns.add(FieldKey.fromParts("Level 2"));
        visibleColumns.add(FieldKey.fromParts("Level 3"));
        visibleColumns.add(FieldKey.fromParts("Level 4"));
        visibleColumns.add(FieldKey.fromParts("Config"));

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

    public static class GctColumnPSP extends DataColumn
    {
        private final LincsModule.LincsAssay assayType;
        private final LincsModule.LincsLevel level;
        private final Path gctDir;
        private final String davUrl;

        public GctColumnPSP(ColumnInfo col, LincsModule.LincsAssay assayType, LincsModule.LincsLevel level, Path gctDir, String davUrl)
        {
            super(col);
            this.assayType = assayType;
            this.level = level;
            this.gctDir = gctDir;
            this.davUrl = davUrl;
        }

        private LincsModule.LincsAssay getAssayType()
        {
            return assayType;
        }

        private LincsModule.LincsLevel getLevel()
        {
            return level;
        }

        private Path getGctDir()
        {
            return gctDir;
        }

        private String getDavUrl()
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

        private HtmlString externalHeatmapViewerLink(String fileName, LincsModule.LincsAssay assayType)
        {
            return externalHeatmapViewerLink(fileName, assayType, getGctDavUrl(fileName));
        }

        private static String getAnalyticsScript(String eventAction, String fileName, boolean addWaitTime)
        {
            if (!StringUtils.isBlank(AnalyticsService.getTrackingScript()))
            {
                // http://www.blastam.com/blog/how-to-track-downloads-in-google-analytics
                // Tell the browser to wait 400ms before going to the download.  This is to ensure
                // that the GA tracking request goes through. Some browsers will interrupt the tracking
                // request if the download opens on the same page.
                String timeout = addWaitTime ? "that=this; setTimeout(function(){location.href=that.href;},400);return false;" : "";

                // GA4 variant
                String onClickScript = "try {gtag('event', 'Lincs', {eventAction: " + PageFlowUtil.qh(eventAction) + ", fileName: " + PageFlowUtil.qh(fileName) + "}); } catch(err) {}";
                onClickScript += timeout;
                return onClickScript;
            }
            return null;
        }

        public static HtmlString externalHeatmapViewerLink(String fileName, LincsModule.LincsAssay assayType, String gctFileDavUrl)
        {
            String morpheusUrl = getMorpheusUrl(gctFileDavUrl, assayType);

            String analyticsScript = getAnalyticsScript("Morpheus", fileName, false);

            String imgUrl = AppProps.getInstance().getContextPath() + "/lincs/GENE-E_icon.png";

            LinkBuilder viewInMorpheusLink = LinkBuilder.simpleLink("View in Morpheus", morpheusUrl).target("_blank");
            if (analyticsScript != null)
            {
                viewInMorpheusLink.onClick(analyticsScript);
            }

            return HtmlString.of(DOM.SPAN("[",
                    HtmlString.NBSP,
                    viewInMorpheusLink,
                    HtmlString.NBSP,
                    IMG(at(src, imgUrl).at(width, 13).at(height, 13)),
                    HtmlString.NBSP,
                    "]"));
        }

        private static String getMorpheusUrl(String gctFileUrl, LincsModule.LincsAssay assayType)
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

        @Override
        public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
        {
            if(getAssayType() == null)
            {
                out.write("Unknown assay type");
                return;
            }
            String fileName = ctx.get(getDisplayColumn().getFieldKey(), String.class);
            if(fileName == null)
            {
                out.write(HtmlString.NBSP);
                return;
            }

            Integer runId = ctx.get(FieldKey.fromParts("Id"), Integer.class);
            if(runId == null)
            {
                out.write("<NO_RUN_ID>");
                return;
            }

            String downloadFileName = getBaseName(fileName);
            String extension = LincsModule.getExt(getLevel());
            downloadFileName = downloadFileName + extension;
            if(!fileAvailable(runId, downloadFileName, ctx.getContainer()))
            {
                out.write("NOT AVAILABLE");
                return;
            }

            String actionName = (getLevel() == LincsModule.LincsLevel.Config) ? "DownloadConfig" : "DownloadGCT";
            String analyticsScript = getAnalyticsScript(actionName, downloadFileName, true);
            HtmlString morpheusUrl = externalHeatmapViewerLink(downloadFileName, getAssayType(), getLevel());
            String downloadText = (getLevel() == LincsModule.LincsLevel.Config) ? "CFG" : "GCT";
            renderGridCell(out, analyticsScript, getGctDavUrlUnencoded(downloadFileName), downloadText, morpheusUrl);
        }

        private void renderGridCell(HtmlWriter out, String analyticsScript, String downloadUrl, String downloadText, HtmlString morpheusUrl)
        {
            // <span style="white-space: nowrap;"> is recommended instead of deprecated <nobr></nobr>
            SPAN(
                at(style, "white-space: nowrap;"),
                HtmlString.NBSP,
                LinkBuilder.labkeyLink("Download", downloadUrl).iconCls("fa fa-download").onClick(analyticsScript),
                HtmlString.NBSP,
                LinkBuilder.simpleLink(downloadText, downloadUrl).onClick(analyticsScript),
                HtmlString.NBSP,
                morpheusUrl != null ? HtmlString.NBSP : null,
                morpheusUrl,
                morpheusUrl != null ? HtmlString.NBSP : null
            ).appendTo(out);
        }

        private boolean fileAvailable(Integer runId, String downloadFileName, Container container)
        {
            LincsPspJob job = LincsManager.get().getLincsPspJobForRun(runId, container);
            if(job != null)
            {
                if(getLevel() == LincsModule.LincsLevel.Two && job.isLevel2Done())
                {
                    return true; // L2 GCT files are generated on PanoramaWeb.
                }
                if(job.isJobCheckTimeoutError())
                {
                    return Files.exists(getGctDir().resolve(downloadFileName));
                }
                // L3 and L4 files are generated on PSP and are not uploaded to the server unless both steps complete without errors.
                return job.isSuccess();
            }
            else
            {
                return Files.exists(getGctDir().resolve(downloadFileName));
            }
        }

        @Nullable HtmlString externalHeatmapViewerLink(String fileName, LincsModule.LincsAssay assayType, LincsModule.LincsLevel level)
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
        public HtmlString getFormattedHtml(RenderContext ctx)
        {
            return HtmlString.of((String)getDisplayValue(ctx));
        }

        @Override
        public Object getDisplayValue(RenderContext ctx)
        {
            return getValue(ctx);
        }
    }
}

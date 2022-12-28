package org.labkey.panoramapublic.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.util.Button;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.panoramapublic.PanoramaPublicController;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.PanoramaPublicSchema;
import org.labkey.panoramapublic.model.CatalogEntry;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.view.publish.CatalogEntryWebPart;
import org.labkey.panoramapublic.view.publish.ShortUrlDisplayColumnFactory;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.SPAN;
import static org.labkey.api.util.DOM.at;

public class CatalogEntryTableInfo extends PanoramaPublicTable
{
    public CatalogEntryTableInfo(@NotNull PanoramaPublicSchema userSchema, ContainerFilter cf)
    {
        super(PanoramaPublicManager.getTableInfoCatalogEntry(), userSchema, cf,
                new ContainerJoin("ShortUrl", PanoramaPublicManager.getTableInfoExperimentAnnotations(), "ShortUrl"));

        var viewCol = wrapColumn("View", getRealTable().getColumn("Id"));
        viewCol.setJdbcType(JdbcType.VARCHAR);
        viewCol.setDisplayColumnFactory(colInfo -> new DataColumn(colInfo)
        {
            @Override
            public Object getValue(RenderContext ctx)
            {
                Integer id = ctx.get(getColumnInfo().getFieldKey(), Integer.class);
                CatalogEntry entry = id != null ? CatalogEntryManager.get(id) : null;
                ExperimentAnnotations expAnnotations = entry != null ? ExperimentAnnotationsManager.getExperimentForShortUrl(entry.getShortUrl()) : null;
                if (entry != null && expAnnotations != null)
                {
                    return new ActionURL(PanoramaPublicController.ViewCatalogEntryAction.class, expAnnotations.getContainer())
                                    .addParameter("id", entry.getId());
                }
                return super.getValue(ctx);
            }

            @Override
            public Object getDisplayValue(RenderContext ctx)
            {
                return getValue(ctx);
            }

            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                Object viewCatalogEntryUrl = getValue(ctx);
                if (viewCatalogEntryUrl instanceof ActionURL url)
                {
                    url.addReturnURL(ctx.getViewContext().getActionURL());
                    var viewButton = new Button.ButtonBuilder("View").href(url).build();
                    viewButton.appendTo(out);
                    return;
                }
                super.renderGridCellContents(ctx, out);
            }
            @Override
            public void addQueryFieldKeys(Set<FieldKey> keys)
            {
                super.addQueryFieldKeys(keys);
                keys.add(FieldKey.fromParts("Id"));
            }
        });
        addColumn(viewCol);

        var accessUrlCol = wrapColumn("Link", getRealTable().getColumn("ShortUrl"));
        accessUrlCol.setDisplayColumnFactory(new ShortUrlDisplayColumnFactory());
        addColumn(accessUrlCol);

        SQLFragment expColSql = new SQLFragment(" (SELECT Id FROM ")
                .append(PanoramaPublicManager.getTableInfoExperimentAnnotations(), "exp")
                .append(" WHERE exp.shortUrl = ").append(ExprColumn.STR_TABLE_ALIAS).append(".shortUrl")
                .append(") ");
        var experimentTitleCol = new ExprColumn(this, "Title", expColSql, JdbcType.VARCHAR);
        experimentTitleCol.setFk(QueryForeignKey.from(getUserSchema(), cf).schema(getUserSchema()).to(PanoramaPublicSchema.TABLE_EXPERIMENT_ANNOTATIONS, "Id", null));
        addColumn(experimentTitleCol);

        var imageFileNameCol = wrapColumn("ImageFile", getRealTable().getColumn("ImageFileName"));
        imageFileNameCol.setDisplayColumnFactory(colInfo -> new DataColumn(colInfo)
        {
            @Override
            public Object getValue(RenderContext ctx)
            {
                return ctx.get(getColumnInfo().getFieldKey(), String.class);
            }

            @Override
            public Object getDisplayValue(RenderContext ctx)
            {
                return getValue(ctx);
            }

            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                String fileName = (String) getValue(ctx);
                ShortURLRecord shortUrl = ctx.get(FieldKey.fromParts("ShortUrl"), ShortURLRecord.class);
                ExperimentAnnotations expAnnotations = shortUrl != null ? ExperimentAnnotationsManager.getExperimentForShortUrl(shortUrl) : null;
                if (fileName != null && expAnnotations != null)
                {
                    ActionURL downloadLink = PanoramaPublicController.getCatalogImageDownloadUrl(expAnnotations, fileName);
                    SPAN(at(style, "white-space: nowrap;"),
                            fileName,
                            PageFlowUtil.iconLink("fa fa-download", null).href(downloadLink).style("margin-left:10px;").build())
                            .appendTo(out);
                    return;
                }
                super.renderGridCellContents(ctx, out);
            }

            @Override
            public void addQueryFieldKeys(Set<FieldKey> keys)
            {
                super.addQueryFieldKeys(keys);
                keys.add(FieldKey.fromParts("ShortUrl"));
            }
        });
        addColumn(imageFileNameCol);

        var entryStatusCol = wrapColumn("Status", getRealTable().getColumn("Approved"));
        entryStatusCol.setDisplayColumnFactory(colInfo -> new DataColumn(colInfo)
        {
            @Override
            public Object getValue(RenderContext ctx)
            {
                Boolean approved = ctx.get(getColumnInfo().getFieldKey(), Boolean.class);
                return CatalogEntry.getStatusText(approved);
            }

            @Override
            public Object getDisplayValue(RenderContext ctx)
            {
                return getValue(ctx);
            }
        });
        entryStatusCol.setTextAlign("center");
        addColumn(entryStatusCol);

        var reviewCol = wrapColumn("Review", getRealTable().getColumn("Id"));
        reviewCol.setDisplayColumnFactory(colInfo -> new DataColumn(colInfo)
        {
            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out)
            {
                Integer catalogEntryId = ctx.get(getColumnInfo().getFieldKey(), Integer.class);
                if (catalogEntryId != null && ctx.getViewContext().getUser().hasSiteAdminPermission())
                {
                    CatalogEntry entry = CatalogEntryManager.get(catalogEntryId);
                    ExperimentAnnotations expAnnotations = ExperimentAnnotationsManager.getExperimentForShortUrl(entry.getShortUrl());
                    if (expAnnotations != null)
                    {
                        DIV(CatalogEntryWebPart.changeStatusButtonBuilder(entry.getApproved(), expAnnotations.getId(), catalogEntryId, expAnnotations.getContainer())
                                .build())
                                .appendTo(out);
                    }
                }
            }
        });
        reviewCol.setTextAlign("center");
        addColumn(reviewCol);

        List<FieldKey> visibleColumns = new ArrayList<>();
        visibleColumns.add(FieldKey.fromParts("Created"));
        visibleColumns.add(FieldKey.fromParts("CreatedBy"));
        visibleColumns.add(FieldKey.fromParts("View"));
        visibleColumns.add(FieldKey.fromParts("Status"));
        visibleColumns.add(FieldKey.fromParts("Review"));
        visibleColumns.add(FieldKey.fromParts("Link"));
        visibleColumns.add(FieldKey.fromParts("Title"));
        visibleColumns.add(FieldKey.fromParts("ImageFile"));
        visibleColumns.add(FieldKey.fromParts("Description"));
        setDefaultVisibleColumns(visibleColumns);
    }
}

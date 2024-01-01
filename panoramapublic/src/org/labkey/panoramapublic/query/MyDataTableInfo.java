package org.labkey.panoramapublic.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.DOM;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.panoramapublic.PanoramaPublicController;
import org.labkey.panoramapublic.PanoramaPublicSchema;
import org.labkey.panoramapublic.model.CatalogEntry;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.view.publish.CatalogEntryWebPart;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import static org.labkey.api.util.DOM.Attribute.height;
import static org.labkey.api.util.DOM.Attribute.href;
import static org.labkey.api.util.DOM.Attribute.src;
import static org.labkey.api.util.DOM.Attribute.title;
import static org.labkey.api.util.DOM.Attribute.width;
import static org.labkey.api.util.DOM.at;

public class MyDataTableInfo extends ExperimentAnnotationsTableInfo
{

    public MyDataTableInfo(PanoramaPublicSchema schema, ContainerFilter cf, User user)
    {
        super(schema, cf, user);

        if (user != null && !user.isGuest())
        {
            var catalogEntryCol = wrapColumn("CatalogEntry", getRealTable().getColumn("Id"));
            catalogEntryCol.setLabel("CatalogEntry");
            catalogEntryCol.setDescription("Link to add or view the catalog entry for the experiment");
            catalogEntryCol.setDisplayColumnFactory(CatalogEntryIconColumn::new);
            addColumn(catalogEntryCol);
        }

        List<FieldKey> visibleColumns = new ArrayList<>();
        visibleColumns.add(FieldKey.fromParts("Share"));
        visibleColumns.add(FieldKey.fromParts("Title"));
        visibleColumns.add(FieldKey.fromParts("Organism"));
        visibleColumns.add(FieldKey.fromParts("Instrument"));
        visibleColumns.add(FieldKey.fromParts("Runs"));
        visibleColumns.add(FieldKey.fromParts("Keywords"));
        visibleColumns.add(FieldKey.fromParts("Citation"));
        visibleColumns.add(FieldKey.fromParts("pxid"));
        visibleColumns.add(FieldKey.fromParts("Public"));
        if (user != null && !user.isGuest())
        {
            visibleColumns.add(FieldKey.fromParts("CatalogEntry"));
        }
        setDefaultVisibleColumns(visibleColumns);
    }

    public static class CatalogEntryIconColumn extends DataColumn
    {
        private String _imageURL;
        private String _imageTitle;

        public CatalogEntryIconColumn(ColumnInfo col)
        {
            super(col);
            super.setCaption("Catalog Entry");
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
        public ActionURL getValue(RenderContext ctx)
        {
            Integer experimentId = ctx.get(getColumnInfo().getFieldKey(), Integer.class);
            if (experimentId != null)
            {
                ExperimentAnnotations expAnnot = ExperimentAnnotationsManager.get(experimentId);
                if (expAnnot != null && CatalogEntryWebPart.canBeDisplayed(expAnnot, ctx.getViewContext().getUser()))
                {
                    CatalogEntry entry = CatalogEntryManager.getEntryForExperiment(expAnnot);
                    if (entry != null)
                    {
                        _imageURL = AppProps.getInstance().getContextPath() + "/PanoramaPublic/images/slideshow-icon-green.png";
                        _imageTitle = "View catalog entry";
                        return PanoramaPublicController.getViewCatalogEntryUrl(expAnnot, entry).addReturnURL(ctx.getViewContext().getActionURL().clone());
                    }
                    else
                    {
                        _imageURL = AppProps.getInstance().getContextPath() + "/PanoramaPublic/images/slideshow-icon.png";
                        _imageTitle = "Add catalog entry";
                        return PanoramaPublicController.getAddCatalogEntryUrl(expAnnot).addReturnURL(ctx.getViewContext().getActionURL().clone());
                    }
                }
            }
            return null;
        }

        @Override
        public Object getDisplayValue(RenderContext ctx)
        {
            return getValue(ctx);
        }

        @Override
        public @NotNull HtmlString getFormattedHtml(RenderContext ctx)
        {
            ActionURL catalogEntryLink = getValue(ctx);
            return catalogEntryLink != null ? HtmlString.of(catalogEntryLink.getLocalURIString()) : HtmlString.EMPTY_STRING;
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            ActionURL catalogEntryLink = getValue(ctx);
            if (catalogEntryLink != null)
            {
                DOM.A(at(href, catalogEntryLink.getLocalURIString(), title, PageFlowUtil.filter(_imageTitle)),
                        DOM.IMG(at(src, _imageURL, height, 22, width, 22)))
                .appendTo(out);
            }

            HtmlString.EMPTY_STRING.appendTo(out);
        }
    }
}

package org.labkey.panoramapublic.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.Link;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.io.Writer;

public class ProteinMatchesDisplayColumnFactory implements DisplayColumnFactory
{
    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new DataColumn(colInfo)
        {
            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                Integer matches = ctx.get(FieldKey.fromParts("Matches"), Integer.class);
                String proteinLabel = ctx.get(FieldKey.fromParts("proteinLabel"), String.class);
                String container = ctx.get(FieldKey.fromParts("container"), String.class);
                Container c = ContainerManager.getForId(container);
                ActionURL searchUrl = new ActionURL("panoramapublic", "proteinSearchResults", c);
                searchUrl.addParameter("proteinLabel", proteinLabel);

                out.write(new Link.LinkBuilder(String.valueOf(matches)).href(searchUrl).toString());
                out.write(PageFlowUtil.button("View").href(searchUrl).toString());
            }
        };
    }
}

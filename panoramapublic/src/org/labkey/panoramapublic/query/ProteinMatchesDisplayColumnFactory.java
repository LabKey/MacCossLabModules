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
import java.util.Set;

public class ProteinMatchesDisplayColumnFactory implements DisplayColumnFactory
{

    private static final FieldKey PROTEIN_LABEL = FieldKey.fromParts("proteinLabel");
    private static final FieldKey EXACT_MATCH = FieldKey.fromParts("exactMatch");
    private static final FieldKey CONTAINER = FieldKey.fromParts("container");

    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new DataColumn(colInfo)
        {
            @Override
            public void addQueryFieldKeys(Set<FieldKey> keys)
            {
                super.addQueryFieldKeys(keys);
                keys.add(PROTEIN_LABEL);
                keys.add(EXACT_MATCH);
                keys.add(CONTAINER);
            }

            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                Integer matches = ctx.get(FieldKey.fromParts("Matches"), Integer.class);
                String proteinLabel = ctx.get(PROTEIN_LABEL, String.class);
                String exactMatch = ctx.get(EXACT_MATCH, String.class);
                String container = ctx.get(CONTAINER, String.class);
                Container c = ContainerManager.getForId(container);
                ActionURL searchUrl = new ActionURL("panoramapublic", "proteinSearchResults", c);
                searchUrl.addParameter("proteinLabel", proteinLabel);
                searchUrl.addParameter("exactMatch", exactMatch);

                out.write(new Link.LinkBuilder(String.valueOf(matches)).href(searchUrl).toString());
                out.write(PageFlowUtil.button("View").href(searchUrl).toString());
            }
        };
    }
}

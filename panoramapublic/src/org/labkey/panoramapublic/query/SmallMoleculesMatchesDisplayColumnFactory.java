/*
 * Copyright (c) 2023-2026 LabKey Corporation
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
package org.labkey.panoramapublic.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.HtmlWriter;

import java.util.Set;

public class SmallMoleculesMatchesDisplayColumnFactory implements DisplayColumnFactory
{
    private static final FieldKey SMALL_MOLECULE = FieldKey.fromParts("smallMolecule");
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
                keys.add(SMALL_MOLECULE);
                keys.add(EXACT_MATCH);
                keys.add(CONTAINER);
            }

            @Override
            public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
            {
                String smallMol= ctx.get(SMALL_MOLECULE, String.class);
                String exactMatch = ctx.get(EXACT_MATCH, String.class);
                String container = ctx.get(CONTAINER, String.class);
                Container c = ContainerManager.getForId(container);
                ActionURL searchUrl = new ActionURL("panoramapublic", "smallMoleculeSearchResults", c);
                Integer matches = ctx.get(FieldKey.fromParts("Matches"), Integer.class);
                searchUrl.addParameter("smallMolecule", smallMol);
                searchUrl.addParameter("exactMatch", exactMatch);

                out.write(LinkBuilder.labkeyLink(String.valueOf(matches), searchUrl));
                out.write(PageFlowUtil.button("View").href(searchUrl));
            }
        };
    }
}

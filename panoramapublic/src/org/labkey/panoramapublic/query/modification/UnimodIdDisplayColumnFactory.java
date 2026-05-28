/*
 * Copyright (c) 2022-2026 LabKey Corporation
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
package org.labkey.panoramapublic.query.modification;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.writer.HtmlWriter;
import org.labkey.panoramapublic.proteomexchange.UnimodModification;

/**
 * Displays a link to the Unimod page for a modification with the given Unimod Id.
 * Example: https://www.unimod.org/modifications_view.php?editid1=4
 */
public class UnimodIdDisplayColumnFactory implements DisplayColumnFactory
{
    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new DataColumn(colInfo)
        {
            @Override
            public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
            {
                Integer unimodId = ctx.get(colInfo.getFieldKey(), Integer.class);
                if (unimodId != null)
                {
                    UnimodModification.getLink(unimodId).appendTo(out);
                }
            }
        };
    }
}

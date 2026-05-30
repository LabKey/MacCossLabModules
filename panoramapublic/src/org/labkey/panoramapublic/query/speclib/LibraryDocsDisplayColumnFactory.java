/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.panoramapublic.query.speclib;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.DOM;
import org.labkey.api.writer.HtmlWriter;
import org.labkey.panoramapublic.model.speclib.SpectralLibrary;
import org.labkey.panoramapublic.query.SpecLibInfoManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.TD;
import static org.labkey.api.util.DOM.TR;
import static org.labkey.api.util.DOM.at;

public class LibraryDocsDisplayColumnFactory implements DisplayColumnFactory
{
    private static final FieldKey SPECLIB_INFO_ID = FieldKey.fromParts("specLibInfoId");

    public LibraryDocsDisplayColumnFactory() {}

    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new DataColumn(colInfo)
        {
            @Override
            public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
            {
                String specLibIds = ctx.get(colInfo.getFieldKey(), String.class);
                if (!StringUtils.isBlank(specLibIds))
                {
                    User user = ctx.getViewContext().getUser();
                    Set<Long> ids = Arrays.stream(specLibIds.split(","))
                            .map(s -> NumberUtils.toLong(s, 0))
                            .filter(l -> l != 0)
                            .collect(Collectors.toSet());
                    List<SpectralLibrary> libraries = SpecLibInfoManager.getLibraries(ids, user);
                    if (!libraries.isEmpty())
                    {
                        Integer specLibInfoId = ctx.get(SPECLIB_INFO_ID, Integer.class);
                        List<DOM.Renderable> runLibraryLinks = new ArrayList<>();
                        for (SpectralLibrary library: libraries)
                        {
                              runLibraryLinks.add(TR(
                                      TD(at(style, "padding:2px 2px 2px 5px;"), library.getRunLink(user)),
                                      TD(at(style, "padding:2px; vertical-align:top;"),
                                              library.getViewLibInfoAndDownloadLink(user, Map.of(
                                                      "allSpecLibIds", StringUtils.join(ids, ","),
                                                      "specLibInfoId", String.valueOf(specLibInfoId)))))
                              );
                        }
                        DOM.TABLE(runLibraryLinks).appendTo(out);
                    }
                    else
                    {
                        out.write("No libraries found for Ids: " + specLibIds);
                    }
                }
            }

            @Override
            public void addQueryFieldKeys(Set<FieldKey> keys)
            {
                super.addQueryFieldKeys(keys);
                keys.add(SPECLIB_INFO_ID);
            }
        };
    }
}

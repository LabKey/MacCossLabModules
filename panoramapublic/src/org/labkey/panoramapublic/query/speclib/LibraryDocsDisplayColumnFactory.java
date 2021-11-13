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
import org.labkey.api.util.PageFlowUtil;
import org.labkey.panoramapublic.model.speclib.SpectrumLibrary;
import org.labkey.panoramapublic.query.SpecLibInfoManager;

import java.io.IOException;
import java.io.Writer;
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
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                String specLibIds = ctx.get(colInfo.getFieldKey(), String.class);
                if (!StringUtils.isBlank(specLibIds))
                {
                    User user = ctx.getViewContext().getUser();
                    Set<Long> ids = Arrays.stream(specLibIds.split(","))
                            .map(s -> NumberUtils.toLong(s, 0))
                            .filter(l -> l != 0)
                            .collect(Collectors.toSet());
                    List<SpectrumLibrary> libraries = SpecLibInfoManager.getLibraries(ids, user);
                    if (libraries.size() > 0)
                    {
                        Integer specLibInfoId = ctx.get(SPECLIB_INFO_ID, Integer.class);
                        List<DOM.Renderable> runLibraryLinks = new ArrayList<>();
                        for (SpectrumLibrary library: libraries)
                        {
                              runLibraryLinks.add(TR(TD(at(style, "padding:2px 2px 2px 5px;"), library.getRunLink(user)),
                                      TD(at(style, "padding:2px;"),
                                              library.getViewLibInfoAndDownloadLink(user, Map.of("allSpecLibIds", specLibIds, "specLibInfoId", String.valueOf(specLibInfoId)))))
                              );
                        }
                        DOM.TABLE(runLibraryLinks).appendTo(out);
                    }
                    else
                    {
                        out.write("No libraries found for Ids: " + PageFlowUtil.filter(specLibIds));
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

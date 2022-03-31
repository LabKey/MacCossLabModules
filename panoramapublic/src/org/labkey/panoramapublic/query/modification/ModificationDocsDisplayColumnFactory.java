package org.labkey.panoramapublic.query.modification;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.targetedms.TargetedMSUrls;
import org.labkey.api.util.DOM;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Link;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.TD;
import static org.labkey.api.util.DOM.TR;
import static org.labkey.api.util.DOM.at;

/**
 * Used with the "RunIds" column of the custom queries StructuralModifications.sql and IsotopeModifications.sql
 * Displays links to the Skyline documents represented by "RunIds" along with a link to the peptides that have
 * the modification in each document.
 */
public abstract class ModificationDocsDisplayColumnFactory implements DisplayColumnFactory
{
    private static final FieldKey MOD_ID = FieldKey.fromParts("ModId");

    protected abstract String getPeptideModTableName();
    protected abstract String getModIdQueryParam();

    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new DataColumn(colInfo)
        {
            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                String runIds = ctx.get(colInfo.getFieldKey(), String.class);
                if (!StringUtils.isBlank(runIds))
                {
                    User user = ctx.getViewContext().getUser();
                    Set<Long> ids = Arrays.stream(runIds.split(","))
                            .map(r -> NumberUtils.toLong(r, 0))
                            .filter(l -> l != 0)
                            .collect(Collectors.toSet());

                    List<ITargetedMSRun> runs = getRuns(ids, user);
                    if (runs.size() > 0)
                    {
                        Long modId = ctx.get(MOD_ID, Long.class);
                        List<DOM.Renderable> runPeptideLinks = new ArrayList<>();
                        for (ITargetedMSRun run: runs)
                        {
                            runPeptideLinks.add(TR(
                                    TD(at(style, "padding:2px 2px 2px 5px;"), runLink(run)),
                                    modId != null ? TD(at(style, "padding:2px; vertical-align:top;"), peptidesLink(run, modId)) : HtmlString.EMPTY_STRING)
                            );
                        }
                        DOM.TABLE(runPeptideLinks).appendTo(out);
                    }
                    else
                    {
                        out.write("No runs found for Ids: " + PageFlowUtil.filter(runIds));
                    }
                }
            }

            @Override
            public void addQueryFieldKeys(Set<FieldKey> keys)
            {
                super.addQueryFieldKeys(keys);
                keys.add(MOD_ID);
            }
        };
    }

    private List<ITargetedMSRun> getRuns(Collection<Long> runIds, User user)
    {
        var runs = new ArrayList<ITargetedMSRun>();
        var svc = TargetedMSService.get();
        runIds.forEach(id -> runs.add(svc.getRun(id, user)));
        return runs.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }

    private @NotNull DOM.Renderable runLink(@NotNull ITargetedMSRun run)
    {
        var runUrl = PageFlowUtil.urlProvider(TargetedMSUrls.class).getShowRunUrl(run.getContainer(), run.getId());
        return new Link.LinkBuilder(run.getFileName()).href(runUrl).clearClasses().build();
    }

    private @NotNull DOM.Renderable peptidesLink(ITargetedMSRun run, Long modId)
    {
        String query = getPeptideModTableName();
        var peptidesLink = PageFlowUtil.urlProvider(QueryUrls.class).urlExecuteQuery(run.getContainer(), "targetedms", query);
        peptidesLink.addParameter("query.PeptideId/PeptideGroupId/RunId~eq", run.getId());
        peptidesLink.addParameter(getModIdQueryParam(), modId);
        return new Link.LinkBuilder("[PEPTIDES]").href(peptidesLink).build();
    }

    public static class StructuralModDocsColumn extends ModificationDocsDisplayColumnFactory
    {
        @Override
        protected String getPeptideModTableName()
        {
            return "PeptideStructuralModification";
        }

        @Override
        protected String getModIdQueryParam()
        {
            return "query.StructuralModId/Id~eq";
        }
    }

    public static class IsotopicModDocsColumn extends ModificationDocsDisplayColumnFactory
    {
        @Override
        protected String getPeptideModTableName()
        {
            return "PeptideIsotopeModification";
        }

        @Override
        protected String getModIdQueryParam()
        {
            return "query.IsotopeModId/Id~eq";
        }
    }
}

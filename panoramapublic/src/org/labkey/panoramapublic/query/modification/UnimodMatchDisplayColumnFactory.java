package org.labkey.panoramapublic.query.modification;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.targetedms.IModification;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.DOM;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Link;
import org.labkey.api.view.ActionURL;
import org.labkey.panoramapublic.PanoramaPublicController;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.proteomexchange.UnimodModification;
import org.labkey.panoramapublic.query.ExperimentAnnotationsManager;
import org.labkey.panoramapublic.query.ModificationInfoManager;

import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.B;
import static org.labkey.api.util.DOM.BR;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.SPAN;
import static org.labkey.api.util.DOM.at;
import static org.labkey.api.util.DOM.cl;

public abstract class UnimodMatchDisplayColumnFactory<T extends ExperimentModInfo> implements DisplayColumnFactory
{
    private static final FieldKey MOD_ID = FieldKey.fromParts("ModId");
    private static final FieldKey MOD_INFO_ID = FieldKey.fromParts("ModInfoId");

    abstract ActionURL getMatchToUnimodAction(RenderContext ctx);
    abstract ActionURL getDeleteAction(RenderContext ctx);
    abstract T getModInfo(int modInfoId);
    abstract IModification getModification(long dbModId);

    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new DataColumn(colInfo)
        {
            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out)
            {
                Integer unimodId = ctx.get(colInfo.getFieldKey(), Integer.class);

                if (unimodId != null)
                {
                    Integer modInfoId = ctx.get(MOD_INFO_ID, Integer.class);
                    if (modInfoId == null)
                    {
                        // This is the Unimod Id from the Skyline document
                        UnimodModification.getLink(unimodId).appendTo(out);
                    }
                    else
                    {
                        var modInfo = getModInfo(modInfoId);
                        DIV(getAssignedUnimodDetails(modInfo)).appendTo(out);

                        int exptId = modInfo.getExperimentAnnotationsId();
                        var dbMod = getModification(modInfo.getModId());
                        var deleteUrl = getDeleteAction(ctx).addParameter("id", exptId).addParameter("modInfoId", modInfoId);
                        deleteUrl.addReturnURL(ctx.getViewContext().getActionURL());
                        DIV(at(style, "margin-top:5px;"), new Link.LinkBuilder("[Delete]")
                                .href(deleteUrl)
                                .clearClasses().addClass("labkey-error")
                                .usePost(String.format("Are you sure you want to delete the saved Unimod information for modification '%s'?",
                                        dbMod != null ? dbMod.getName() : ""))
                                .build())
                                .appendTo(out);
                    }
                }
                else
                {
                    // If there is an experiment in the container then display a link to find a Unimod match
                    ExperimentAnnotations exptAnnotations = ExperimentAnnotationsManager.getExperimentInContainer(ctx.getContainer());
                    Integer exptId = exptAnnotations != null ? exptAnnotations.getId() : null;

                    Long modId = ctx.get(MOD_ID, Long.class);

                    if (modId != null && exptId != null &&
                            // Show the find match link only if user has the right permissions
                            exptAnnotations.getContainer().hasPermission(ctx.getViewContext().getUser(), UpdatePermission.class))
                    {
                        var url = getMatchToUnimodAction(ctx).addParameter("id", exptId).addParameter("modificationId", modId);
                        url.addReturnURL(ctx.getViewContext().getActionURL());
                        var findMatchLink = new Link.LinkBuilder("Find Match").href(url);
                        DIV(cl("alert-warning"), findMatchLink).appendTo(out);
                    }
                }
            }

            @Override
            public void addQueryFieldKeys(Set<FieldKey> keys)
            {
                super.addQueryFieldKeys(keys);
                keys.add(MOD_ID);
                keys.add(MOD_INFO_ID);
            }
        };
    }

    protected List<DOM.Renderable> getAssignedUnimodDetails(T modInfo)
    {
        return renderUnimod(modInfo.getUnimodId(), modInfo.getUnimodName(), true);
    }

    @NotNull
    List<DOM.Renderable> renderUnimod(int unimodId, String unimodName, boolean addAsterisk)
    {
        return List.of(
                addAsterisk ? SPAN("**") : HtmlString.EMPTY_STRING,
                UnimodModification.getLink(unimodId, true),
                HtmlString.NBSP,
                SPAN("(" + unimodName + ")")
        );
    }

    public static class StructuralUnimodMatch extends UnimodMatchDisplayColumnFactory<ExperimentStructuralModInfo>
    {
        @Override
        ActionURL getMatchToUnimodAction(RenderContext ctx)
        {
            return new ActionURL(PanoramaPublicController.StructuralModToUnimodOptionsAction.class, ctx.getContainer());
        }

        @Override
        ActionURL getDeleteAction(RenderContext ctx)
        {
            return new ActionURL(PanoramaPublicController.DeleteStructuralModInfoAction.class, ctx.getContainer());
        }

        @Override
        ExperimentStructuralModInfo getModInfo(int modInfoId)
        {
            return ModificationInfoManager.getStructuralModInfo(modInfoId);
        }

        @Override
        IModification getModification(long dbModId)
        {
            return TargetedMSService.get().getStructuralModification(dbModId);
        }

        @Override
        protected List<DOM.Renderable> getAssignedUnimodDetails(ExperimentStructuralModInfo modInfo)
        {
            List<DOM.Renderable> list = new ArrayList<>(super.getAssignedUnimodDetails(modInfo));
            if (modInfo.isCombinationMod())
            {
                list.add(SPAN(at(style, "margin:0 10px 0 10px;"), B("+")));
                list.addAll(super.renderUnimod(modInfo.getUnimodId2(), modInfo.getUnimodName2(), false));
            }
            return list;
        }
    }

    public static class IsotopeUnimodMatch extends UnimodMatchDisplayColumnFactory<ExperimentIsotopeModInfo>
    {
        @Override
        ActionURL getMatchToUnimodAction(RenderContext ctx)
        {
            return new ActionURL(PanoramaPublicController.MatchToUnimodIsotopeAction.class, ctx.getContainer());
        }

        @Override
        ActionURL getDeleteAction(RenderContext ctx)
        {
            return new ActionURL(PanoramaPublicController.DeleteIsotopeModInfoAction.class, ctx.getContainer());
        }

        @Override
        ExperimentIsotopeModInfo getModInfo(int modInfoId)
        {
            return ModificationInfoManager.getIsotopeModInfo(modInfoId);
        }

        @Override
        IModification getModification(long dbModId)
        {
            return TargetedMSService.get().getIsotopeModification(dbModId);
        }

        @Override
        protected List<DOM.Renderable> getAssignedUnimodDetails(ExperimentIsotopeModInfo modInfo)
        {
            List<DOM.Renderable> list = new ArrayList<>();
            for (ExperimentModInfo.UnimodInfo unimodInfo: modInfo.getUnimodInfos())
            {
                list.addAll(super.renderUnimod(unimodInfo.getUnimodId(), unimodInfo.getUnimodName(), true));
                list.add(BR());
            }
            if (list.size() > 1)
            {
                list.remove(list.size() - 1); // remove the last <BR>
            }
            return list;
        }
    }
}

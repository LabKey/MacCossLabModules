package org.labkey.panoramapublic.query.modification;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataRegion;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.query.QueryView;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.panoramapublic.PanoramaPublicSchema;
import org.labkey.panoramapublic.model.ExperimentAnnotations;

public class ModificationsView extends QueryView
{
    protected final ExperimentAnnotations _exptAnnotations;
    private final String _title;
    private final String _queryName;
    private final String _customViewName;

    public ModificationsView(ViewContext portalCtx, String queryName, String title, String customViewName, @Nullable ExperimentAnnotations exptAnnotations)
    {
        super(new PanoramaPublicSchema(portalCtx.getUser(), portalCtx.getContainer()));
        _exptAnnotations = exptAnnotations;
        _title = title;
        _queryName = queryName;
        _customViewName = customViewName;

        setTitle(title);
        setSettings(createQuerySettings(portalCtx));
        setShowDetailsColumn(false);
        setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);
        setShowExportButtons(false);
        setShowBorders(true);
        setShadeAlternatingRows(true);
        setAllowableContainerFilterTypes(ContainerFilter.Type.Current, ContainerFilter.Type.CurrentAndSubfolders);
        setFrame(FrameType.PORTAL);
    }

    protected QuerySettings createQuerySettings(ViewContext portalCtx)
    {
        QuerySettings settings = getSchema().getSettings(portalCtx, _title, _queryName, null);
        if (_exptAnnotations != null)
        {
            // If we are given an ExperimentAnnotations it means that this view is being displayed on the experiment details page.
            // We will display a non-customizable view in this case.  The user can click the grid title to view the customizable query.
            settings.setContainerFilterName(_exptAnnotations.isIncludeSubfolders() ?
                    ContainerFilter.Type.CurrentAndSubfolders.name() : ContainerFilter.Type.Current.name());
            settings.setViewName(_customViewName);
            settings.setAllowChooseView(false);
        }

        setTitleHref(PageFlowUtil.urlProvider(QueryUrls.class).urlExecuteQuery(portalCtx.getContainer(), PanoramaPublicSchema.SCHEMA_NAME, _queryName));

        return settings;
    }

    public static class StructuralModsView extends ModificationsView
    {
        public StructuralModsView(ViewContext portalCtx)
        {
            this(portalCtx, null);
        }

        public StructuralModsView(ViewContext portalCtx, @Nullable ExperimentAnnotations exptAnnotations)
        {
            super(portalCtx, "StructuralModifications", "Structural Modifications", "ExperimentStructuralModInfo", exptAnnotations);
        }
    }

    public static class IsotopeModsView extends ModificationsView
    {
        public IsotopeModsView(ViewContext portalCtx)
        {
            this(portalCtx, null);
        }

        public IsotopeModsView(ViewContext portalCtx, @Nullable ExperimentAnnotations exptAnnotations)
        {
            super(portalCtx, "IsotopeModifications", "Isotope Modifications", "ExperimentIsotopeModInfo", exptAnnotations);
        }
    }
}

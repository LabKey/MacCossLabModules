package org.labkey.panoramapublic.query.speclib;

import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataRegion;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.query.QueryView;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.panoramapublic.PanoramaPublicSchema;
import org.labkey.panoramapublic.model.ExperimentAnnotations;

public class SpecLibView extends QueryView
{
    public static final String NAME = "Spectral Libraries";
    public static final String QUERY_NAME = "SpectralLibraries";

    private final ExperimentAnnotations _exptAnnotations;

    public SpecLibView(ViewContext portalCtx)
    {
        this(portalCtx, null);
    }

    public SpecLibView(ViewContext portalCtx, ExperimentAnnotations exptAnnotations)
    {
        super(new PanoramaPublicSchema(portalCtx.getUser(), portalCtx.getContainer()));
        _exptAnnotations = exptAnnotations;
        setTitle(NAME);
        setSettings(createQuerySettings(portalCtx));
        setShowDetailsColumn(false);
        setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);
        setShowExportButtons(false);
        setShowBorders(true);
        setShadeAlternatingRows(true);
        setAllowableContainerFilterTypes(ContainerFilter.Type.Current, ContainerFilter.Type.CurrentAndSubfolders);
        setFrame(FrameType.PORTAL);
    }

    private QuerySettings createQuerySettings(ViewContext portalCtx)
    {
        QuerySettings settings = getSchema().getSettings(portalCtx, NAME, QUERY_NAME, null);

        if(_exptAnnotations != null)
        {
            settings.setContainerFilterName(_exptAnnotations.isIncludeSubfolders() ?
                    ContainerFilter.Type.CurrentAndSubfolders.name() : ContainerFilter.Type.Current.name());
            settings.setViewName("SpectralLibrariesInfo");
            settings.setAllowChooseView(false);
        }
        setTitleHref(PageFlowUtil.urlProvider(QueryUrls.class).urlExecuteQuery(portalCtx.getContainer(), PanoramaPublicSchema.SCHEMA_NAME, QUERY_NAME));
        return settings;
    }
}

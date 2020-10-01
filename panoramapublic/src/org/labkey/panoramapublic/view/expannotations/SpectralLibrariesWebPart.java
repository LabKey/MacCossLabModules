package org.labkey.panoramapublic.view.expannotations;

import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.UrlColumn;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.panoramapublic.PanoramaPublicController;
import org.labkey.panoramapublic.PanoramaPublicSchema;

public class SpectralLibrariesWebPart extends QueryView
{
    public static final String WEB_PART_NAME = "Spectral Library Files";

    public SpectralLibrariesWebPart(ViewContext portalCtx)
    {
        super(new PanoramaPublicSchema(portalCtx.getUser(), portalCtx.getContainer()));

        setTitle(WEB_PART_NAME);

        QuerySettings settings = getSchema().getSettings(portalCtx, WEB_PART_NAME, PanoramaPublicSchema.TABLE_SPEC_LIB_INFO_RUN);
        if (settings.getContainerFilterName() == null)
        {
            settings.setContainerFilterName(ContainerFilter.Type.CurrentAndSubfolders.name());
        }
        setSettings(settings);

        setShowDetailsColumn(false);
        setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);
        setShowExportButtons(false);
        setShowBorders(true);
        setShadeAlternatingRows(true);

        setAllowableContainerFilterTypes(ContainerFilter.Type.Current, ContainerFilter.Type.CurrentAndSubfolders);

        setFrame(FrameType.PORTAL);
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);
    }

    @Override
    protected void setupDataView(DataView ret)
    {
        super.setupDataView(ret);
        ActionURL editUrl = new ActionURL(PanoramaPublicController.EditSpecLibInfoAction.class, getContainer());
        editUrl.addParameter("id", "${SpecLibInfoId}");
        SimpleDisplayColumn editCol = new UrlColumn(editUrl.toString(), "Edit");
        ret.getDataRegion().addDisplayColumn(editCol);
    }
}

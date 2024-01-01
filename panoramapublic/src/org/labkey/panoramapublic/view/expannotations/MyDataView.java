package org.labkey.panoramapublic.view.expannotations;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.ViewContext;
import org.labkey.panoramapublic.PanoramaPublicSchema;

public class MyDataView extends QueryView
{
    private final Integer _userId;

    public MyDataView(ViewContext portalCtx, @NotNull Integer userId)
    {
        super(new PanoramaPublicSchema(portalCtx.getUser(), portalCtx.getContainer()));
        setTitle("Panorama Public Experiments");
        _userId = userId;

        setSettings(createQuerySettings(portalCtx));

        setShowRecordSelectors(false);
        setShowExportButtons(false);
        setShowDetailsColumn(false);
        setShowDeleteButton(false);
        setShowUpdateColumn(false);
        setShowInsertNewButton(false);
        disableContainerFilterSelection();
    }

    private QuerySettings createQuerySettings(ViewContext portalCtx)
    {
        QuerySettings settings = new QuerySettings(portalCtx,  "MyPanoramaPublicData", "MyPanoramaPublicData");
        settings.setContainerFilterName(ContainerFilter.Type.CurrentAndSubfolders.name());
        settings.setFieldKeys();

        if (_userId != null)
        {
            SimpleFilter.OrClause or = new SimpleFilter.OrClause();
            or.addClause(new CompareType.EqualsCompareClause(FieldKey.fromParts("submitter"), CompareType.EQUAL, _userId));
            or.addClause(new CompareType.EqualsCompareClause(FieldKey.fromParts("labhead"), CompareType.EQUAL, _userId));
            SimpleFilter filter = new SimpleFilter();
            filter.addClause(or);
            settings.setBaseFilter(filter);
        }
        return settings;
    }
}

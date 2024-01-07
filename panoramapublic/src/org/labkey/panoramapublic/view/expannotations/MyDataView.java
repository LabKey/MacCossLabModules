package org.labkey.panoramapublic.view.expannotations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.ViewContext;
import org.labkey.panoramapublic.PanoramaPublicSchema;
import org.springframework.validation.Errors;

import java.util.ArrayList;
import java.util.List;

public class MyDataView extends QueryView
{
    public MyDataView(ViewContext portalCtx, @NotNull Integer userId, @Nullable Errors errors)
    {
        super(new PanoramaPublicSchema(portalCtx.getUser(), portalCtx.getContainer()), createQuerySettings(portalCtx, userId), errors);

        setTitle("Panorama Public Experiments");

        setShowRecordSelectors(false);
        setShowExportButtons(false);
        setShowDetailsColumn(false);
        setShowDeleteButton(false);
        setShowUpdateColumn(false);
        setShowInsertNewButton(false);
        disableContainerFilterSelection();
    }

    private static QuerySettings createQuerySettings(ViewContext portalCtx, Integer userId)
    {
        QuerySettings settings = new QuerySettings(portalCtx,  "MyPanoramaPublicData", "MyPanoramaPublicData");
        // QuerySettings settings = new QuerySettings(portalCtx,  "MyPanoramaPublicData", "ExperimentAnnotations");
        settings.setContainerFilterName(ContainerFilter.Type.CurrentAndSubfolders.name());
//        List<FieldKey> visibleColumns = new ArrayList<>();
//        visibleColumns.add(FieldKey.fromParts("Share"));
//        visibleColumns.add(FieldKey.fromParts("Title"));
//        visibleColumns.add(FieldKey.fromParts("Organism"));
//        visibleColumns.add(FieldKey.fromParts("Instrument"));
//        visibleColumns.add(FieldKey.fromParts("Runs"));
//        visibleColumns.add(FieldKey.fromParts("Keywords"));
//        visibleColumns.add(FieldKey.fromParts("Citation"));
//        visibleColumns.add(FieldKey.fromParts("pxid"));
//        visibleColumns.add(FieldKey.fromParts("Public"));
//        visibleColumns.add(FieldKey.fromParts("CatalogEntry"));
//        settings.setFieldKeys(visibleColumns);
//        settings.setAllowCustomizeView(false); // Disable customize view.  It does not work when using setFieldKeys.
//        settings.setAllowChooseView(false);

        // Display rows where the given user is either the submitter or the lab head.
//        SimpleFilter.OrClause or = new SimpleFilter.OrClause();
//        or.addClause(new CompareType.EqualsCompareClause(FieldKey.fromParts("submitter"), CompareType.EQUAL, userId));
//        or.addClause(new CompareType.EqualsCompareClause(FieldKey.fromParts("labhead"), CompareType.EQUAL, userId));
//        SimpleFilter filter = new SimpleFilter();
//        filter.addClause(or);
//        settings.setBaseFilter(filter);

        return settings;
    }
}

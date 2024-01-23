package org.labkey.panoramapublic.query;

import org.labkey.api.data.CompareType;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.panoramapublic.PanoramaPublicSchema;

import java.util.ArrayList;
import java.util.List;

public class MyDataTableInfo extends ExperimentAnnotationsTableInfo
{
    public static final String NAME = "MyPanoramaPublicData";

    public MyDataTableInfo(PanoramaPublicSchema schema, ContainerFilter cf, User user)
    {
        super(schema, cf);

        if (user != null)
        {
            // Filter to rows where the given user is either the submitter or the lab head.
            SimpleFilter.OrClause or = new SimpleFilter.OrClause();
            or.addClause(new CompareType.EqualsCompareClause(FieldKey.fromParts("submitter"), CompareType.EQUAL, user.getUserId()));
            or.addClause(new CompareType.EqualsCompareClause(FieldKey.fromParts("labhead"), CompareType.EQUAL, user.getUserId()));
            SimpleFilter filter = new SimpleFilter();
            filter.addClause(or);
            addCondition(filter);
        }

        List<FieldKey> visibleColumns = new ArrayList<>();
        visibleColumns.add(FieldKey.fromParts("Share"));
        visibleColumns.add(FieldKey.fromParts("Title"));
        visibleColumns.add(FieldKey.fromParts("Organism"));
        visibleColumns.add(FieldKey.fromParts("Instrument"));
        visibleColumns.add(FieldKey.fromParts("Runs"));
        visibleColumns.add(FieldKey.fromParts("Keywords"));
        visibleColumns.add(FieldKey.fromParts("Citation"));
        visibleColumns.add(FieldKey.fromParts("pxid"));
        visibleColumns.add(FieldKey.fromParts("Public"));
        visibleColumns.add(FieldKey.fromParts("CatalogEntry"));
        setDefaultVisibleColumns(visibleColumns);
    }
}

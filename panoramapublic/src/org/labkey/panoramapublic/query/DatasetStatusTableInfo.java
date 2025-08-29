package org.labkey.panoramapublic.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.PanoramaPublicSchema;
import org.labkey.panoramapublic.view.publish.ShortUrlDisplayColumnFactory;

import java.util.ArrayList;
import java.util.List;

public class DatasetStatusTableInfo extends PanoramaPublicTable
{
    public DatasetStatusTableInfo(@NotNull PanoramaPublicSchema userSchema, ContainerFilter cf)
    {
        super(PanoramaPublicManager.getTableInfoDatasetStatus(), userSchema, cf,
                new ContainerJoin("ExperimentAnnotationsId", PanoramaPublicManager.getTableInfoExperimentAnnotations(), "Id"));

        var shortUrlCol = wrapColumn("ShortURL", getRealTable().getColumn("ExperimentAnnotationsId"));
        shortUrlCol.setDisplayColumnFactory(new ShortUrlDisplayColumnFactory(FieldKey.fromParts("ShortUrl")));
        addColumn(shortUrlCol);

        var experimentTitleCol = wrapColumn("Title", getRealTable().getColumn(FieldKey.fromParts("ExperimentAnnotationsId")));
        experimentTitleCol.setFk(QueryForeignKey.from(getUserSchema(), cf).schema(getUserSchema()).to(PanoramaPublicSchema.TABLE_EXPERIMENT_ANNOTATIONS, "Id", null));
        addColumn(experimentTitleCol);

        List<FieldKey> visibleColumns = new ArrayList<>();
        visibleColumns.add(FieldKey.fromParts("Created"));
        visibleColumns.add(FieldKey.fromParts("CreatedBy"));
        visibleColumns.add(FieldKey.fromParts("Modified"));
        visibleColumns.add(FieldKey.fromParts("ModifiedBy"));
        visibleColumns.add(FieldKey.fromParts("ShortUrl"));
        visibleColumns.add(FieldKey.fromParts("Title"));
        visibleColumns.add(FieldKey.fromParts("LastReminderDate"));
        visibleColumns.add(FieldKey.fromParts("ExtensionRequestedDate"));
        visibleColumns.add(FieldKey.fromParts("DeletionRequestedDate"));
        setDefaultVisibleColumns(visibleColumns);
    }
}

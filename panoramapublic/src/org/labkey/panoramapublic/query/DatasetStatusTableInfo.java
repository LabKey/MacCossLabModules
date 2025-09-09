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

        List<FieldKey> visibleColumns = new ArrayList<>();
        visibleColumns.add(FieldKey.fromParts("Created"));
        visibleColumns.add(FieldKey.fromParts("CreatedBy"));
        visibleColumns.add(FieldKey.fromParts("Modified"));
        visibleColumns.add(FieldKey.fromParts("ModifiedBy"));
        visibleColumns.add(FieldKey.fromParts("ExperimentAnnotationsId", "Link"));
        visibleColumns.add(FieldKey.fromParts("ExperimentAnnotationsId", "Title"));
        visibleColumns.add(FieldKey.fromParts("LastReminderDate"));
        visibleColumns.add(FieldKey.fromParts("ExtensionRequestedDate"));
        visibleColumns.add(FieldKey.fromParts("DeletionRequestedDate"));
        setDefaultVisibleColumns(visibleColumns);
    }
}

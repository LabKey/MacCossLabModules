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
                new ContainerJoin("ShortUrl", PanoramaPublicManager.getTableInfoExperimentAnnotations(), "ShortUrl"));


        var accessUrlCol = wrapColumn("ShortUrl", getRealTable().getColumn("ShortUrl"));
        accessUrlCol.setDisplayColumnFactory(new ShortUrlDisplayColumnFactory());
        addColumn(accessUrlCol);

        SQLFragment expColSql = new SQLFragment(" (SELECT Id FROM ")
                .append(PanoramaPublicManager.getTableInfoExperimentAnnotations(), "exp")
                .append(" WHERE exp.shortUrl = ").append(ExprColumn.STR_TABLE_ALIAS).append(".shortUrl")
                .append(") ");
        var experimentTitleCol = new ExprColumn(this, "Title", expColSql, JdbcType.VARCHAR);
        experimentTitleCol.setFk(QueryForeignKey.from(getUserSchema(), cf).schema(getUserSchema()).to(PanoramaPublicSchema.TABLE_EXPERIMENT_ANNOTATIONS, "Id", null));
        addColumn(experimentTitleCol);

        List<FieldKey> visibleColumns = new ArrayList<>();
        visibleColumns.add(FieldKey.fromParts("Created"));
        visibleColumns.add(FieldKey.fromParts("CreatedBy"));
        visibleColumns.add(FieldKey.fromParts("Modified"));
        visibleColumns.add(FieldKey.fromParts("ModifiedBy"));
        visibleColumns.add(FieldKey.fromParts("ShortUrl"));
        visibleColumns.add(FieldKey.fromParts("Title"));
        visibleColumns.add(FieldKey.fromParts("ReminderDate"));
        visibleColumns.add(FieldKey.fromParts("ExtensionRequestedDate"));
        visibleColumns.add(FieldKey.fromParts("DeletionRequestedDate"));
        setDefaultVisibleColumns(visibleColumns);
    }
}

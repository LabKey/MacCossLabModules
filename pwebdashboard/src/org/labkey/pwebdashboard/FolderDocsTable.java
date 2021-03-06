package org.labkey.pwebdashboard;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerTable;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.WrappedColumn;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.targetedms.TargetedMSService;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by vsharma on 12/8/2016.
 */
public class FolderDocsTable extends ContainerTable
{
    public static final String NAME = "FolderDocs";

    public FolderDocsTable(@NotNull PwebDashboardSchema userSchema, ContainerFilter cf)
    {
        super(userSchema, cf);

        setName(NAME);

        BaseColumnInfo folderCol = new WrappedColumn(getColumn("EntityId"), "Folder");
        ContainerForeignKey.initColumn(folderCol, userSchema);
        addColumn(folderCol);

        /*
        WITH RECURSIVE project(parent, entityId, name) AS (
        SELECT parent, entityId, name FROM core.containers WHERE name='QC Subfolder 2a'
        UNION
        SELECT a.parent AS parent, p.parent AS entityId, a.name AS name FROM project p, core.containers a
        WHERE a.entityId = p.parent
        ) SELECT * FROM project WHERE parent = (SELECT entityId FROM core.containers WHERE parent IS NULL)
         */
        // Get the project for the folder. Traverse up the folder tree.
        SQLFragment projectColSql = new SQLFragment("(WITH RECURSIVE project(parent, entityId, name) AS ( ");
        projectColSql.append(" SELECT parent, entityId, name FROM ").append(CoreSchema.getInstance().getTableInfoContainers(), "c");
        projectColSql.append(" WHERE entityId=").append(ExprColumn.STR_TABLE_ALIAS).append(".EntityId");
        projectColSql.append(" UNION ");
        projectColSql.append(" SELECT a.parent AS parent, p.parent AS entityId, a.name AS name FROM project p, core.containers a WHERE a.entityId = p.parent ");
        projectColSql.append(" ) SELECT entityId FROM project WHERE parent = ?)");
        projectColSql.add(ContainerManager.getRoot().getEntityId());
        ExprColumn projectCol = new ExprColumn(this, "Project", projectColSql, JdbcType.VARCHAR);
        projectCol.setReadOnly(true);
        ContainerForeignKey.initColumn(projectCol, userSchema);
        addColumn(projectCol);

        TargetedMSService tmsService = TargetedMSService.get();

        SQLFragment docCountSql = new SQLFragment(" (SELECT COUNT(*) FROM ");
        docCountSql.append(tmsService.getTableInfoRuns(), "r");
        docCountSql.append(" WHERE container=").append(ExprColumn.STR_TABLE_ALIAS).append(".EntityId");
        docCountSql.append(" AND deleted = FALSE AND statusId=1) ");
        ExprColumn documentCountCol = new ExprColumn(this, "Documents", docCountSql, JdbcType.INTEGER);
        addColumn(documentCountCol);

        SQLFragment lastUploadSql = new SQLFragment(" (SELECT MAX(Created) FROM ");
        lastUploadSql.append(tmsService.getTableInfoRuns(), "r");
        lastUploadSql.append(" WHERE container=").append(ExprColumn.STR_TABLE_ALIAS).append(".EntityId");
        lastUploadSql.append(" AND deleted = FALSE AND statusId=1) ");
        ExprColumn lastUploadCol = new ExprColumn(this, "LastUpload", lastUploadSql, JdbcType.DATE);
        addColumn(lastUploadCol);

        SQLFragment folderTypeColSql = new SQLFragment(" (SELECT p.value FROM prop.properties AS p INNER JOIN prop.propertySets AS ps ON p.set = ps.set ");
        folderTypeColSql.append(" WHERE p.Name=?");
        folderTypeColSql.add(tmsService.FOLDER_TYPE_PROP_NAME);
        folderTypeColSql.append(" AND ps.category=?");
        folderTypeColSql.add("moduleProperties." + tmsService.MODULE_NAME);
        folderTypeColSql.append(" AND ps.ObjectId=").append(ExprColumn.STR_TABLE_ALIAS).append(".EntityId");
        folderTypeColSql.append(") ");
        ExprColumn folderTypeCol = new ExprColumn(this, "TargetedMSFolderType", folderTypeColSql, JdbcType.VARCHAR);
        addColumn(folderTypeCol);


        List<FieldKey> visibleColumns = new ArrayList<>();
        visibleColumns.add(FieldKey.fromParts("Project"));
        visibleColumns.add(FieldKey.fromParts("Created"));
        visibleColumns.add(FieldKey.fromParts("CreatedBy"));
        visibleColumns.add(FieldKey.fromParts("Folder"));
        visibleColumns.add(FieldKey.fromParts("TargetedMSFolderType"));
        visibleColumns.add(FieldKey.fromParts("Documents"));
        visibleColumns.add(FieldKey.fromParts("LastUpload"));
        setDefaultVisibleColumns(visibleColumns);

        SQLFragment sql = new SQLFragment("(CreatedBy IS NOT NULL)");
        addCondition(sql);
    }
}

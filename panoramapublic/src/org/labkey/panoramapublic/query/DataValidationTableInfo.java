package org.labkey.panoramapublic.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.RowIdQueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.HtmlString;
import org.labkey.api.view.ActionURL;
import org.labkey.panoramapublic.PanoramaPublicController;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.PanoramaPublicSchema;
import org.labkey.panoramapublic.model.validation.DataValidation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataValidationTableInfo extends PanoramaPublicTable
{
    public DataValidationTableInfo(@NotNull PanoramaPublicSchema userSchema, ContainerFilter cf)
    {
        super(PanoramaPublicManager.getTableInfoDataValidation(), userSchema, cf, ContainerJoin.ExpAnnotJoin);

        var statusCol = getMutableColumn("Status");
        if (statusCol != null)
        {
            statusCol.setFk(QueryForeignKey.from(userSchema, cf).to(PanoramaPublicSchema.TABLE_PX_STATUS, "RowId", null));
        }
        Map<String, Object> params = new HashMap<>();
        params.put("validationId", FieldKey.fromParts("Id"));
        params.put("id", FieldKey.fromParts("ExperimentAnnotationsId"));
        var validationStatusUrl = new ActionURL(PanoramaPublicController.PxValidationStatusAction.class, null);
        statusCol.setURL(new DetailsURL(validationStatusUrl, params).setContainerContext(getContainerContext()));

        ExprColumn docCountCol = createCountsColumn(PanoramaPublicManager.getTableInfoSkylineDocValidation(),
                PanoramaPublicSchema.TABLE_SKYLINE_DOC_VALIDATION, "Documents");
        addColumn(docCountCol);
        ExprColumn libCountCol = createCountsColumn(PanoramaPublicManager.getTableInfoSpecLibValidation(),
                PanoramaPublicSchema.TABLE_SPEC_LIB_VALIDATION, "Libraries");
        addColumn(libCountCol);
        ExprColumn modCountCol = createCountsColumn(PanoramaPublicManager.getTableInfoModificationValidation(),
                PanoramaPublicSchema.TABLE_MODIFICATION_VALIDATION, "Modifications");
        addColumn(modCountCol);

        MutableColumnInfo logCol = addWrapColumn("Log", getRealTable().getColumn("JobId"));
        logCol.setDisplayColumnFactory(colInfo -> new DataColumn(colInfo)
        {
            @Override
            public @NotNull HtmlString getFormattedHtml(RenderContext ctx)
            {
                return renderURLorValueURL(ctx) != null ? HtmlString.of("View Log") : super.getFormattedHtml(ctx);
            }

            @Override
            public String getLinkCls()
            {
                return "labkey-text-link";
            }
        });

        List<FieldKey> visibleColumns = new ArrayList<>();
        visibleColumns.add(FieldKey.fromParts("Id"));
        visibleColumns.add(FieldKey.fromParts("Created"));
        visibleColumns.add(FieldKey.fromParts("CreatedBy"));
        visibleColumns.add(FieldKey.fromParts("Status"));
        visibleColumns.add(FieldKey.fromParts("Log"));
        visibleColumns.add(FieldKey.fromParts(docCountCol.getName()));
        visibleColumns.add(FieldKey.fromParts(libCountCol.getName()));
        visibleColumns.add(FieldKey.fromParts(modCountCol.getName()));
        setDefaultVisibleColumns(visibleColumns);
    }

    private ExprColumn createCountsColumn(TableInfo table, String queryName, String name)
    {
        return createCountsColumn(this, table, "validationId", queryName, name, getContainerContext());
    }
    @NotNull
    public static ExprColumn createCountsColumn(TableInfo parentTable, TableInfo table, String fkColName, String queryName, String name, ContainerContext containerContext)
    {
        SQLFragment countSql = new SQLFragment(" (SELECT COUNT(*) FROM ").append(table, "t")
                .append(" WHERE t.").append(fkColName).append("=").append(ExprColumn.STR_TABLE_ALIAS).append(".id) ");
        ExprColumn countsCol = new ExprColumn(parentTable, name, countSql, JdbcType.INTEGER);

        ActionURL url = new ActionURL("query", "executeQuery", null)
                .addParameter("schemaName", PanoramaPublicSchema.SCHEMA_NAME)
                .addParameter("query.queryName", queryName)
                .addParameter("query." + fkColName + "~eq", "${id}");

        countsCol.setURL(new DetailsURL(url).setContainerContext(containerContext));
        return countsCol;
    }


    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new DataValidationQueryUpdateService(this);
    }

    // Update service allows row deletion but not insert or edit
    public static class DataValidationQueryUpdateService extends RowIdQueryUpdateService<DataValidation>
    {
        public DataValidationQueryUpdateService(DataValidationTableInfo tableInfo)
        {
            super(tableInfo);
        }

        @Override
        protected DataValidation createNewBean()
        {
            return new DataValidation();
        }

        @Override
        public DataValidation get(User user, Container container, int key)
        {
            return new TableSelector(PanoramaPublicManager.getTableInfoDataValidation()).getObject(key, DataValidation.class);
        }

        @Override
        public void delete(User user, Container container, int key)
        {
            DataValidationManager.deleteValidation(key, container);
        }

        @Override
        protected DataValidation insert(User user, Container container, DataValidation bean) throws ValidationException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected DataValidation update(User user, Container container, DataValidation bean, Integer oldKey) throws ValidationException
        {
            throw new UnsupportedOperationException();
        }
    }
}

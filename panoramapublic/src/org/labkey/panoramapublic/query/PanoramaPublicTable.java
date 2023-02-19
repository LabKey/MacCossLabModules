package org.labkey.panoramapublic.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.panoramapublic.PanoramaPublicSchema;

public class PanoramaPublicTable extends FilteredTable<PanoramaPublicSchema>
{
    private final SQLFragment _joinSql;
    private final SQLFragment _containerSql;
    private final FieldKey _containerFieldKey;
    private final boolean _noContainerFilter;

    public static final String TABLE_ALIAS = "X";

    public PanoramaPublicTable(TableInfo table, PanoramaPublicSchema schema, ContainerFilter cf, @NotNull ContainerJoin joinType)
    {
        this(table, schema, cf, joinType, false);
    }

    public PanoramaPublicTable(TableInfo table, PanoramaPublicSchema schema, ContainerFilter cf, @NotNull ContainerJoin joinType, boolean noContainerFilter)
    {
        super(table, schema, noContainerFilter ? null : cf);
        _joinSql = joinType.getJoinSql();
        _containerSql = joinType.getContainerSql();
        _containerFieldKey = joinType.getContainerFieldKey() != null ? joinType.getContainerFieldKey() : getContainerFieldKey();
        wrapAllColumns(true);
        addQueryFKs();
        _noContainerFilter = noContainerFilter;
    }

    @Override
    public boolean supportsContainerFilter()
    {
        return _noContainerFilter ? false : super.supportsContainerFilter();
    }

    @Override
    protected void applyContainerFilter(ContainerFilter filter)
    {
        // Don't apply the container filter normally, let us apply it in our wrapper around the normally generated SQL
    }

    @Override
    @NotNull
    public SQLFragment getFromSQL(String alias)
    {
        SQLFragment sql = new SQLFragment("(SELECT " + TABLE_ALIAS + ".* FROM ");
        sql.append(super.getFromSQL(TABLE_ALIAS));
        sql.append(" ");

        if (getContainerFilter() != ContainerFilter.EVERYTHING)
        {
            sql.append(_joinSql.getSQL());
            sql.append(" WHERE ");
            sql.append(getContainerFilter().getSQLFragment(getSchema(), _containerSql));
        }
        sql.append(") ");
        sql.append(alias);

        return sql;
    }

    @Override
    public FieldKey getContainerFieldKey()
    {
        return _containerFieldKey;
    }

    private void addQueryFKs()
    {
        for (var columnInfo : getMutableColumns())
        {
            // Add lookups to user schema tables (exposed through the query schema browser) so that we get any extra columns added to those tables.
            // If we don't add these here then the lookups in the schema browser will show null schema values: e.g. null.ExperimentAnnotations.Id
            // See Issue 40229: targetedms lookups target DB schema TableInfo instead of UserSchema version
            ForeignKey fk = columnInfo.getFk();
            if (fk != null && PanoramaPublicSchema.SCHEMA_NAME.equalsIgnoreCase(fk.getLookupSchemaKey().toString()))
            {
                columnInfo.setFk(new QueryForeignKey(getUserSchema(), getContainerFilter(), getUserSchema(), null,
                        fk.getLookupTableName(), fk.getLookupColumnName(), fk.getLookupDisplayName()));
            }
            else
            {
                String name = columnInfo.getName();
                if ("Container".equalsIgnoreCase(name))
                {
                    columnInfo.setFk(new ContainerForeignKey(getUserSchema()));
                }
                else if ("CreatedBy".equalsIgnoreCase(columnInfo.getName()) || "ModifiedBy".equalsIgnoreCase(columnInfo.getName()))
                {
                    columnInfo.setFk(new UserIdQueryForeignKey(getUserSchema(), true));
                }
            }
        }
    }
}

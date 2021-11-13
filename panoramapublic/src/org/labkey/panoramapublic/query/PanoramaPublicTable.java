package org.labkey.panoramapublic.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FilteredTable;
import org.labkey.panoramapublic.PanoramaPublicSchema;

public class PanoramaPublicTable extends FilteredTable<PanoramaPublicSchema>
{
    private final SQLFragment _joinSql;
    private final SQLFragment _containerSql;

    public PanoramaPublicTable(TableInfo table, PanoramaPublicSchema schema, ContainerFilter cf, SQLFragment joinSql,  SQLFragment containerSql)
    {
        super(table, schema, cf);
        _joinSql = joinSql;
        _containerSql = containerSql;
        wrapAllColumns(true);
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
        SQLFragment sql = new SQLFragment("(SELECT X.* FROM ");
        sql.append(super.getFromSQL("X"));
        sql.append(" ");

        if (getContainerFilter() != ContainerFilter.EVERYTHING)
        {
            sql.append(_joinSql != null ? _joinSql.getSQL() : "");

            sql.append(" WHERE ");
            sql.append(getContainerFilter().getSQLFragment(getSchema(), _containerSql));
        }
        sql.append(") ");
        sql.append(alias);

        return sql;
    }
}

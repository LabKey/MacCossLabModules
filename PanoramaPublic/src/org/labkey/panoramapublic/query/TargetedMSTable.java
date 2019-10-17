/*
 * Copyright (c) 2012-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.panoramapublic.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.view.ActionURL;
import org.labkey.panoramapublic.PanoramaPublicSchema;
import org.springframework.web.servlet.mvc.Controller;

import java.util.Collections;
import java.util.Map;

/**
 * User: jeckels
 * Date: Apr 19, 2012
 */
public class TargetedMSTable extends FilteredTable<PanoramaPublicSchema>
{
    public static final String CONTAINER_COL_TABLE_ALIAS = "r";
    private static final SQLFragment _defaultContainerSQL = new SQLFragment(CONTAINER_COL_TABLE_ALIAS).append(".Container");

    private final PanoramaPublicSchema.ContainerJoinType _joinType;
    private final SQLFragment _containerSQL;

    private CompareType.EqualsCompareClause _containerTableFilter;

    /** Assumes that the table has its own container column, instead of needing to join to another table for container info */
    public TargetedMSTable(TableInfo table, PanoramaPublicSchema schema, ContainerFilter cf, PanoramaPublicSchema.ContainerJoinType joinType)
    {
        this(table, schema, cf, joinType, _defaultContainerSQL);
    }

    public TargetedMSTable(TableInfo table, PanoramaPublicSchema schema, ContainerFilter cf, PanoramaPublicSchema.ContainerJoinType joinType, SQLFragment containerSQL)
    {
        super(table, schema, cf);
        _joinType = joinType;
        _containerSQL = containerSQL;
        wrapAllColumns(true);

        // Swap out DbSchema FKs with Query FKs so that we get all the extra calculated columns and such
        for (var columnInfo : getMutableColumns())
        {
            ForeignKey fk = columnInfo.getFk();
            if (fk != null && PanoramaPublicSchema.SCHEMA_NAME.equalsIgnoreCase(fk.getLookupSchemaName()))
            {
                columnInfo.setFk(new QueryForeignKey(schema, cf, schema, null, fk.getLookupTableName(), fk.getLookupColumnName(), fk.getLookupDisplayName()));
            }
        }

        if(getDetailsActionClass() != null && getContainerFieldKey() != null)
        {
            DetailsURL url = new DetailsURL(new ActionURL(getDetailsActionClass(), getContainer()),getColumnParams());
            url.setContainerContext(new ContainerContext.FieldKeyContext(getContainerFieldKey()));
            setDetailsURL(url);
        }
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

        // See issue 38134 - don't filter if we don't need to because we're on the other side of a FK/lookup
        if ((getContainerFilter() != ContainerFilter.EVERYTHING) || _containerTableFilter != null)
        {
            sql.append(_joinType != null ? _joinType.getSQL() : "");

            sql.append(" WHERE ");
            sql.append(getContainerFilter().getSQLFragment(getSchema(), _containerSQL, getContainer()));

            if(_containerTableFilter != null)
            {
                // Add another filter on the table that has the container column
                sql.append(" AND ");
                SQLFragment fragment = new SQLFragment(CONTAINER_COL_TABLE_ALIAS).append(".")
                                      .append(_containerTableFilter.toSQLFragment(Collections.emptyMap(),getSqlDialect()));
                sql.append(fragment);
            }
        }
        sql.append(") ");
        sql.append(alias);

        return sql;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return ReadPermission.class.equals(perm) && getContainer().hasPermission(user, perm);
    }

    /*
    This is an additional filter that is applied to the table that has the container column. This can be used, for example,
    to filter the results of the Precursor table to a single run in a container (Id column in the targetedms.runs table).
    Tables in the targetedms schema that have a container column are:
     - runs, autoqcping, irtscale, experimentannotations, guideset, qcannotation, qcannotationtype
     */
    public void addContainerTableFilter(CompareType.EqualsCompareClause filterClause)
    {
       _containerTableFilter = filterClause;
    }

    @Override
    public FieldKey getContainerFieldKey()
    {
        return _joinType != null ? _joinType.getContainerFieldKey() : super.getContainerFieldKey();
    }

    protected Class<? extends Controller> getDetailsActionClass()
    {
        return null;
    }

    protected Map<String, ?> getColumnParams()
    {
        return Collections.singletonMap("id", "Id");
    }

}

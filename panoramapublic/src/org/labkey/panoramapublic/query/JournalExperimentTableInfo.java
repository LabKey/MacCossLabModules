/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.PanoramaPublicSchema;
import org.labkey.panoramapublic.view.publish.ShortUrlDisplayColumnFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * User: vsharma
 * Date: 9/12/2014
 * Time: 4:07 PM
 */
public class JournalExperimentTableInfo extends FilteredTable<PanoramaPublicSchema>
{
    public JournalExperimentTableInfo(final PanoramaPublicSchema schema, ContainerFilter cf)
    {
        super(PanoramaPublicManager.getTableInfoJournalExperiment(), schema, cf);

        wrapAllColumns(true);

        var accessUrlCol = getMutableColumn(FieldKey.fromParts("ShortAccessUrl"));
        if (accessUrlCol != null)
        {
            accessUrlCol.setDisplayColumnFactory(new ShortUrlDisplayColumnFactory());
        }
        var copyUrlCol = getMutableColumn(FieldKey.fromParts("ShortCopyUrl"));
        if (copyUrlCol != null)
        {
            copyUrlCol.setDisplayColumnFactory(new ShortUrlDisplayColumnFactory());
        }

        List<FieldKey> columns = new ArrayList<>();
        columns.add(FieldKey.fromParts("Id"));
        columns.add(FieldKey.fromParts("CreatedBy"));
        columns.add(FieldKey.fromParts("Created"));
        columns.add(FieldKey.fromParts("JournalId"));
        columns.add(FieldKey.fromParts("ShortAccessURL"));
        setDefaultVisibleColumns(columns);
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
            SQLFragment joinToExpAnnotSql = new SQLFragment("INNER JOIN ");
            joinToExpAnnotSql.append(PanoramaPublicManager.getTableInfoExperimentAnnotations(), "exp");
            joinToExpAnnotSql.append(" ON ( ");
            joinToExpAnnotSql.append("exp.id = ").append("ExperimentAnnotationsId");
            joinToExpAnnotSql.append(" ) ");

            sql.append(joinToExpAnnotSql);

            sql.append(" WHERE ");
            sql.append(getContainerFilter().getSQLFragment(getSchema(), new SQLFragment("exp.Container")));
        }
        sql.append(") ");
        sql.append(alias);

        return sql;
    }
}

/*
 * Copyright (c) 2015 LabKey Corporation
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

package org.labkey.testresults;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.util.Set;

/**
 * User: Yuval Boss, yuval(at)uw.edu
 * Date: 1/14/2015
 */
public class TestResultsSchema extends UserSchema
{
    public static final String SCHEMA_NAME = "testresults";
    private static final String SCHEMA_DESCRIPTION = "TestResults nightly run data";

    public static final String TABLE_TEST_RUNS      = "testruns";
    public static final String TABLE_USER           = "user";
    public static final String TABLE_USER_DATA      = "userdata";
    public static final String TABLE_TRAIN_RUNS     = "trainruns";
    public static final String TABLE_HANGS          = "hangs";
    public static final String TABLE_MEMORY_LEAKS   = "memoryleaks";
    public static final String TABLE_HANDLE_LEAKS   = "handleleaks";
    public static final String TABLE_TEST_PASSES    = "testpasses";
    public static final String TABLE_TEST_FAILS     = "testfails";
    public static final String TABLE_GLOBAL_SETTINGS = "globalsettings";

    public TestResultsSchema(User user, Container container)
    {
        super(SCHEMA_NAME, SCHEMA_DESCRIPTION, user, container, getSchema());
    }

    public static void register(Module module)
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider(module)
        {
            @Override
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new TestResultsSchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public static DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME, DbSchemaType.Module);
    }

    public static SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    @Override
    public @Nullable TableInfo createTable(@NotNull String name, @NotNull ContainerFilter cf)
    {
        TableInfo dbTable = switch (name.toLowerCase())
        {
            case TABLE_TEST_RUNS       -> getTableInfoTestRuns();
            case TABLE_USER            -> getTableInfoUser();
            case TABLE_USER_DATA       -> getTableInfoUserData();
            case TABLE_TRAIN_RUNS      -> getTableInfoTrain();
            case TABLE_HANGS           -> getTableInfoHangs();
            case TABLE_MEMORY_LEAKS    -> getTableInfoMemoryLeaks();
            case TABLE_HANDLE_LEAKS    -> getTableInfoHandleLeaks();
            case TABLE_TEST_PASSES     -> getTableInfoTestPasses();
            case TABLE_TEST_FAILS      -> getTableInfoTestFails();
            case TABLE_GLOBAL_SETTINGS -> getTableInfoGlobalSettings();
            default                    -> null;
        };
        if (dbTable == null)
            return null;
        FilteredTable<TestResultsSchema> table = new FilteredTable<>(dbTable, this, cf);
        table.wrapAllColumns(true);
        resolveSchemaForeignKeys(table, cf);
        return table;
    }

    /**
     * Converts DbSchema-level FKs (propagated by wrapAllColumns()) into UserSchema-level FKs
     * so the Query Schema Browser renders hyperlinks and can navigate to target query grids.
     * After wrapAllColumns(), columns whose FK targets are within this schema show up with an
     * "undefined" schema name because the DbSchema FK has no UserSchema context. This method
     * walks all columns and replaces any such FK with a proper QueryForeignKey.
     */
    private void resolveSchemaForeignKeys(FilteredTable<TestResultsSchema> table, ContainerFilter cf)
    {
        for (ColumnInfo col : table.getColumns())
        {
            ForeignKey fk = col.getFk();
            if (fk == null)
                continue;
            String fkTable = fk.getLookupTableName();
            String fkCol   = fk.getLookupColumnName();
            // Only replace FKs that target this schema (schema name comes through as null or
            // SCHEMA_NAME from the DbSchema XML; skip FKs targeting other schemas like "core").
            String fkSchema = fk.getLookupSchemaName();
            if (fkTable != null && (fkSchema == null || SCHEMA_NAME.equalsIgnoreCase(fkSchema)))
            {
                var mutableCol = table.getMutableColumn(col.getName());
                if (mutableCol != null)
                    mutableCol.setFk(QueryForeignKey.from(this, cf).to(fkTable, fkCol, null));
            }
        }
    }

    @Override
    public @NotNull Set<String> getTableNames()
    {
        return Set.of(
                TABLE_TEST_RUNS,
                TABLE_USER,
                TABLE_USER_DATA,
                TABLE_TRAIN_RUNS,
                TABLE_HANGS,
                TABLE_MEMORY_LEAKS,
                TABLE_HANDLE_LEAKS,
                TABLE_TEST_PASSES,
                TABLE_TEST_FAILS,
                TABLE_GLOBAL_SETTINGS);
    }

    // ---------------------------------------------------------------------------
    // Static table accessors — used throughout TestResultsController
    // ---------------------------------------------------------------------------

    public static TableInfo getTableInfoTestRuns()       { return getSchema().getTable(TABLE_TEST_RUNS); }
    public static TableInfo getTableInfoUser()           { return getSchema().getTable(TABLE_USER); }
    public static TableInfo getTableInfoUserData()       { return getSchema().getTable(TABLE_USER_DATA); }
    public static TableInfo getTableInfoTrain()          { return getSchema().getTable(TABLE_TRAIN_RUNS); }
    public static TableInfo getTableInfoHangs()          { return getSchema().getTable(TABLE_HANGS); }
    public static TableInfo getTableInfoMemoryLeaks()    { return getSchema().getTable(TABLE_MEMORY_LEAKS); }
    public static TableInfo getTableInfoHandleLeaks()    { return getSchema().getTable(TABLE_HANDLE_LEAKS); }
    public static TableInfo getTableInfoTestPasses()     { return getSchema().getTable(TABLE_TEST_PASSES); }
    public static TableInfo getTableInfoTestFails()      { return getSchema().getTable(TABLE_TEST_FAILS); }
    public static TableInfo getTableInfoGlobalSettings() { return getSchema().getTable(TABLE_GLOBAL_SETTINGS); }
}

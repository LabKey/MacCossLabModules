/*
 * Copyright (c) 2015-2017 LabKey Corporation
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

package org.labkey.lincs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateColumn;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Set;

public class LincsSchema extends UserSchema
{
    public static final String SCHEMA_NAME = "lincs";

    public static void register(Module module)
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider(module)
        {
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new LincsSchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public LincsSchema(User user, Container container)
    {
        super(SCHEMA_NAME, "Schema for the LINCS module", user, container, getSchema());
    }

    @Nullable
    @Override
    public TableInfo createTable(String name)
    {
        if (LincsDataTable.NAME.equalsIgnoreCase(name))
        {
            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), "targetedms");
            List<String> names = schema.getTableAndQueryNames(false);
            TableInfo tInfo = schema.getTable(LincsDataTable.PARENT_QUERY);
            return new LincsDataTable(tInfo, schema);
        }
        if (getTableNames().contains(name))
        {
            SimpleUserSchema.SimpleTable<LincsSchema> result = new SimpleUserSchema.SimpleTable<LincsSchema>(this, getSchema().getTable(name))
            {
                @Override
                public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
                {
                    return getContainer().hasPermission(user, perm);
                }

                @Override
                public QueryUpdateService getUpdateService()
                {
                    return new DefaultQueryUpdateService(this, getRealTable());
                }
            };
            // result.setReadOnly(false);
            result.wrapAllColumns(true);
            return result;
        }
        return null;
    }

    @Override
    public Set<String> getTableNames()
    {
        CaseInsensitiveHashSet hs = new CaseInsensitiveHashSet();
        hs.add("LincsMetadata");
        return hs;
    }

    public static DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME, DbSchemaType.Module);
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    @Override
    public QueryView createView(ViewContext context, @NotNull QuerySettings settings, BindException errors)
    {
        if(LincsDataTable.NAME.equalsIgnoreCase(settings.getQueryName()))
        {
            QueryView view = new QueryView(this, settings, errors)
            {
                @Override
                protected void addDetailsAndUpdateColumns(List<DisplayColumn> ret, TableInfo table)
                {
                    super.addDetailsAndUpdateColumns(ret, table);
                }
            };
            return view;
        }

        return super.createView(context, settings, errors);
    }

}

/*
 * Copyright (c) 2013 LabKey Corporation
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

package org.labkey.signup;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.Group;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

public class SignUpSchema extends UserSchema
{
    public static String SCHEMA_NAME = "signup";

    public static final String TABLE_TEMP_USERS = "temporaryuser";
    public static final String TABLE_MOVED_USERS = "movedusers";

    public SignUpSchema(User user, Container container)
    {
        super(SCHEMA_NAME, "Schema for the SignUp module", user, container, DbSchema.get(SCHEMA_NAME));
    }

    public static DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME);
    }

    public static TableInfo getTableInfoMovedUsers() {
        return getSchema().getTable(TABLE_MOVED_USERS);
    }

    @Nullable
    @Override
    public TableInfo createTable(String name, ContainerFilter containerFilter)
    {
        if (getTableNames().contains(name))
        {
            if(!getContainer().hasPermission(getUser(), AdminPermission.class))
            {
                return null;
            }
            FilteredTable<SignUpSchema> result = new FilteredTable<>(getSchema().getTable(name), this, containerFilter);
            result.wrapAllColumns(true);
            if(name.equals(TABLE_TEMP_USERS))
                ContainerForeignKey.initColumn(result.getMutableColumn(FieldKey.fromParts("Container")), this);

            var userIdCol = result.getMutableColumn(FieldKey.fromParts("labkeyUserId"));
            userIdCol.setFk(new UserIdQueryForeignKey(this, true));

            if(name.equals(TABLE_MOVED_USERS))
            {
                var oldgroup = result.getMutableColumn(FieldKey.fromParts("oldgroup"));
                var newgroup = result.getMutableColumn(FieldKey.fromParts("newgroup"));
                oldgroup.setDisplayColumnFactory(colInfo -> new DataColumn(colInfo)
                {
                    @Override
                    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                    {
                        Group old = org.labkey.api.security.SecurityManager.getGroup(Integer.parseInt(String.valueOf(ctx.get(FieldKey.fromParts("oldgroup")))));
                        out.write(old.getName());
                    }
                });
                newgroup.setDisplayColumnFactory(colInfo -> new DataColumn(colInfo)
                {
                    @Override
                    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                    {
                        Group old = org.labkey.api.security.SecurityManager.getGroup(Integer.parseInt(String.valueOf(ctx.get(FieldKey.fromParts("newgroup")))));
                        out.write(old.getName());
                    }
                });
            }

            if(name.equals(TABLE_TEMP_USERS)) {
                var confirmationUrlColumn = result.wrapColumn("ConfirmationURL", result.getRealTable().getColumn(FieldKey.fromParts("key")));
                // confirmationUrlColumn.setHidden(true);
                result.addColumn(confirmationUrlColumn);
                confirmationUrlColumn.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    @Override
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new DataColumn(colInfo)
                        {
                            @Override
                            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                            {
                                Container container = ContainerManager.getForId(String.valueOf(ctx.get(FieldKey.fromParts("Container"))));
                                String email = String.valueOf(ctx.get(FieldKey.fromParts("email")));
                                String key = String.valueOf(ctx.get(getColumnInfo().getFieldKey()));

                                ActionURL url = new ActionURL(SignUpController.ConfirmAction.class, container);
                                url.addParameter("email", email);
                                url.addParameter("key", key);
                                out.write(url.getLocalURIString());
                                // out.write("<a href=\"" + url + "\">Confirmation URL</a>");
                            }

                            @Override
                            public void addQueryFieldKeys(Set<FieldKey> keys)
                            {
                                super.addQueryFieldKeys(keys);
                                keys.add(FieldKey.fromParts("Container"));
                                keys.add(FieldKey.fromParts("email"));
                            }
                        };
                    }
                });
            }
            return result;
        }

       return null;
    }

    @Override
    public Set<String> getTableNames()
    {
        CaseInsensitiveHashSet hs = new CaseInsensitiveHashSet();
        hs.add(TABLE_TEMP_USERS);
        hs.add(TABLE_MOVED_USERS);
        return hs;
    }

    static public void register(Module module)
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider(module)
        {
            @Override
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new SignUpSchema(schema.getUser(), schema.getContainer());
            }
        });
    }
}

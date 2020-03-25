/*
 * Copyright (c) 2016 LabKey Corporation
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

package org.labkey.dashboard;

import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.util.Set;

public class DashboardSchema extends UserSchema
{
    public static final String NAME = "dashboard";

    public DashboardSchema(User user, Container container)
    {
        super(NAME, "Queries for PanoramaWeb usage dashboard", user, container, getSchema());
    }

    public static DbSchema getSchema()
    {
        return DbSchema.get(NAME, DbSchemaType.Module);
    }

    static public void register(Module module)
    {
        DefaultSchema.registerProvider(NAME, new DefaultSchema.SchemaProvider(module)
        {
            @Override
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new DashboardSchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    @Override
    public TableInfo createTable(String name, ContainerFilter cf)
    {
        if ("ProjectAdmins".equalsIgnoreCase(name))
        {
            return new ProjectAdminsTable(this, cf);
        }
        else if("FolderDocs".equalsIgnoreCase(name))
        {
            return new FolderDocsTable(this, cf);
        }
        return null;
    }

    @Override
    public Set<String> getTableNames()
    {
        CaseInsensitiveHashSet hs = new CaseInsensitiveHashSet();
        hs.add("ProjectAdmins");
        hs.add("FolderDocs");
        return hs;
    }
}

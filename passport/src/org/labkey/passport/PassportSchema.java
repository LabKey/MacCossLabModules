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

package org.labkey.passport;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.util.List;
import java.util.Set;

public class PassportSchema extends UserSchema
{

    public PassportSchema(User user, Container container)
    {
        super("passport", null, user, container, PassportManager.getSchema());
    }

    @Override
    public @Nullable TableInfo createTable(String name)
    {
//        if (name.equals("ProteinList"))
//        {
//            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), "targetedms");
//            List<String> names = schema.getTableAndQueryNames(false);
//            TableInfo tInfo = schema.getTable("ProteinList");
//            return new Query;
//        }

        return null;
    }

    @Override
    public Set<String> getTableNames()
    {
        return null;
    }


//
//    public TableInfo createTable(String name)
//    {
//        if ("PassportProteins".equalsIgnoreCase(name))
//        {
//            return getTargetedMSRunsTable();
//        }
//        return null;
//    }

    public DbSchema getSchema()
    {
        return DbSchema.get("passport");
    }
}

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

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;

/**
 * User: Yuval Boss, yuval(at)uw.edu
 * Date: 1/14/2015
 */
public class TestResultsSchema
{
    private static final TestResultsSchema _instance = new TestResultsSchema();

    public static TestResultsSchema getInstance()
    {
        return _instance;
    }

    private TestResultsSchema()
    {
        // private constructor to prevent instantiation from
        // outside this class: this singleton should only be
        // accessed via org.labkey.testresults.TestResultsSchema.getInstance()
    }

    public static DbSchema getSchema()
    {
        return DbSchema.get("testresults");
    }

    public static TableInfo getTableInfoTestRuns() { return getSchema().getTable("testruns"); }

    public static TableInfo getTableInfoUser()
    {
        return getSchema().getTable("user");
    }

    public static TableInfo getTableInfoUserData() { return getSchema().getTable("userdata"); }

    public static TableInfo getTableInfoTrain() { return getSchema().getTable("trainruns"); }

    public static TableInfo getTableInfoHangs() { return getSchema().getTable("hangs"); }

    public static TableInfo getTableInfoMemoryLeaks() { return getSchema().getTable("memoryleaks"); }

    public static TableInfo getTableInfoHandleLeaks() { return getSchema().getTable("handleleaks"); }

    public static TableInfo getTableInfoTestPasses()
    {
        return getSchema().getTable("testpasses");
    }

    public static TableInfo getTableInfoTestFails()
    {
        return getSchema().getTable("testfails");
    }

    public static TableInfo getTableInfoGlobalSettings()
    {
        return getSchema().getTable("globalsettings");
    }

    public static SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }
}

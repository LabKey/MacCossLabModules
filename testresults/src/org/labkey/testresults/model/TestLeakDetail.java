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
package org.labkey.testresults.model;

/**
 * User: Yuval Boss, yuval(at)uw.edu
 * Date: 1/14/2015
 */
public class TestLeakDetail
{
    private int testRunId;
    private String testName;
    private String type;

    public TestLeakDetail() {

    }

    public TestLeakDetail(int testRunId, String name, String type) {
        this.testRunId = testRunId;
        this.testName = name;
        this.type = type;
    }

    public int getTestRunId()
    {
        return testRunId;
    }

    public void setTestRunId(int testRunId)
    {
        this.testRunId = testRunId;
    }

    public String getTestName()
    {
        return testName;
    }

    public void setTestName(String testname)
    {
        this.testName = testname;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }
}

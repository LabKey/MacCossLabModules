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

/**
 * User: Yuval Boss, yuval(at)uw.edu
 * Date: 1/14/2015
 */
public class TestLeakDetail
{
    private int testRunId;
    private String testname;
    private int bytes; // bytes leaked


    public TestLeakDetail() {

    }
    public TestLeakDetail(int testRunId, String name, int bytes) {
        this.testRunId = testRunId;
        this.testname = name;
        this.bytes = bytes;
    }

    public int getTestRunId()
    {
        return testRunId;
    }

    public void setTestRunId(int testRunId)
    {
        this.testRunId = testRunId;
    }

    public int getBytes()
    {
        return bytes;
    }

    public void setBytes(int bytes)
    {
        this.bytes = bytes;
    }

    public String getTestName()
    {
        return testname;
    }

    public void setTestName(String testname)
    {
        this.testname = testname;
    }
}

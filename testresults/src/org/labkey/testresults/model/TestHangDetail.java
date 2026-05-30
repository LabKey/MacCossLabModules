/*
 * Copyright (c) 2020-2026 LabKey Corporation
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

import java.util.Date;

public class TestHangDetail
{
    private int testRunId;
    private int pass;
    private Date timestamp;
    private String testName;
    private String language;

    public TestHangDetail() {
    }

    public TestHangDetail(int testRunId, int pass, Date timestamp, String testName, String language) {
        this.testRunId = testRunId;
        this.pass = pass;
        this.timestamp = timestamp;
        this.testName = testName;
        this.language = language;
    }

    public int getTestRunId()
    {
        return testRunId;
    }
    public void setTestRunId(int testRunId)
    {
        this.testRunId = testRunId;
    }

    public int getPass() { return pass; }
    public void setPass(int pass) { this.pass = pass; }

    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }

    public String getTestName() { return testName; }
    public void setTestName(String testname)
    {
        this.testName = testname;
    }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
}

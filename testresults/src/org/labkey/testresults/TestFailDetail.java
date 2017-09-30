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

import java.util.Date;

/**
 * User: Yuval Boss, yuval(at)uw.edu
 * Date: 1/14/2015
 */
public class TestFailDetail implements Comparable<TestFailDetail>
{
    private int testRunId;
    private String testName;
    private int pass; // pass during run of the failure
    private int testId;
    private String language;
    private String stacktrace; // stack trace at failure
    private Date timestamp;
    public TestFailDetail() {

    }

    public TestFailDetail(int testRunId, String testName, int pass, int testId, String language, String stacktrace, Date timestamp) {
        setTestRunId(testRunId);
        setTestName(testName);
        setPass(pass);
        setTestId(testId);
        setLanguage(language);
        setStacktrace(stacktrace);
        setTimestamp(timestamp);
    }

    public String getStacktrace()
    {
        return stacktrace;
    }

    public void setStacktrace(String stacktrace)
    {
        this.stacktrace = stacktrace;
    }

    public int getTestRunId()
    {
        return testRunId;
    }

    public void setTestRunId(int testRunId)
    {
        this.testRunId = testRunId;
    }

    public String getLanguage()
    {
        if(language == null)
            return "unknown";  // standard display value so that null values aren't ignored as that would skew data..
        return language;
    }

    public void setLanguage(String language)
    {
        this.language = language;
    }

    public int getTestId()
    {
        return testId;
    }

    public void setTestId(int testId)
    {
        this.testId = testId;
    }

    public int getPass()
    {
        return pass;
    }

    public void setPass(int pass)
    {
        this.pass = pass;
    }

    public String getTestName()
    {
        return testName;
    }

    public void setTestName(String testName)
    {
        this.testName = testName;
    }

    public Date getTimestamp()
    {
        return timestamp;
    }

    public void setTimestamp(Date timestamp)
    {
        this.timestamp = timestamp;
    }

    // comparable by date and then id if dates are the same
    public int compareTo(TestFailDetail o)
    {
        int diff = 0;
        if(this.timestamp == null) {
            diff = Integer.compare(this.testId, o.testId);
        } else {
            diff = this.timestamp.compareTo(o.timestamp);
        }
        return diff;
    }
}

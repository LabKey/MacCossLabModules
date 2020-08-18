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

import java.util.Date;

/**
 * User: Yuval Boss, yuval(at)uw.edu
 * Date: 1/14/2015
 */
public class TestPassDetail implements Comparable<TestPassDetail>
{
    private int id;
    private int testRunId;
    private int pass;
    private int testId;
    private String testName;
    private String language;
    private int duration;
    private double managedMemory;
    private double totalMemory;
    private double committedMemory;
    private int userAndGDIHandles;
    private int handles;
    private Date timestamp;

    public TestPassDetail() {

    }

    public TestPassDetail(int testRunId, int pass, int testId, String testName, String language, int duration,
                          double managedMemory, double totalMemory, double committedMemory, int userGdiHandles, int handles, Date timestamp) {
        this.testRunId = testRunId;
        this.pass = pass;
        this.testId = testId;
        this.testName = testName;
        this.language = language;
        this.duration = duration;
        this.managedMemory = managedMemory;
        this.totalMemory = totalMemory;
        this.committedMemory = committedMemory;
        this.userAndGDIHandles = userGdiHandles;
        this.handles = handles;
        this.timestamp = timestamp;
    }

    public int getTestRunId()
    {
        return testRunId;
    }

    public void setTestRunId(int testRunId) { this.testRunId = testRunId; }

    public int getPass() { return pass; }

    public void setPass(int pass)
    {
        this.pass = pass;
    }

    public int getTestId()
    {
        return testId;
    }

    public void setTestId(int testId)
    {
        this.testId = testId;
    }

    public String getTestName()
    {
        return testName;
    }

    public void setTestName(String testName)
    {
        this.testName = testName;
    }

    public String getLanguage() {
        return language != null
            ? language
            : "unknown";  // standard display value so that null values aren't ignored as that would skew data..
    }

    public void setLanguage(String language)
    {
        this.language = language;
    }

    public int getDuration()
    {
        return duration;
    }

    public void setDuration(int duration)
    {
        this.duration = duration;
    }

    public double getManagedMemory()
    {
        return managedMemory;
    }

    public void setManagedMemory(double managedMemory)
    {
        this.managedMemory = managedMemory;
    }

    public double getTotalMemory() { return totalMemory; }

    public void setTotalMemory(double totalMemory)
    {
        this.totalMemory = totalMemory;
    }

    public int getId()
    {
        return id;
    }

    public void setId(int id)
    {
        this.id = id;
    }

    public Date getTimestamp()
    {
        return timestamp;
    }

    public void setTimestamp(Date timestamp)
    {
        this.timestamp = timestamp;
    }

    public double getCommittedMemory()
    {
        return committedMemory;
    }

    public void setCommittedMemory(double committedMemory)
    {
        this.committedMemory = committedMemory;
    }

    public int getUserAndGDIHandles()
    {
        return userAndGDIHandles;
    }

    public void setUserAndGDIHandles(int userAndGDIHandles)
    {
        this.userAndGDIHandles = userAndGDIHandles;
    }

    public int getHandles()
    {
        return handles;
    }

    public void setHandles(int handles)
    {
        this.handles = handles;
    }

    @Override
    public int compareTo(TestPassDetail o2) {
        return this.getId() - o2.getId();
    }
}

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

import org.labkey.api.data.Container;
import org.labkey.api.reader.Readers;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * User: Yuval Boss, yuval(at)uw.edu
 * Date: 1/14/2015
 */
public class RunDetail implements Comparable<RunDetail>
{
    private int id;
    private int userid;
    private String username;
    private int duration; // duration of run
    private Date posttime;
    private Date timestamp;
    private int revision; // revision of skyline SVN at time of post
    private boolean flagged; // if true then is not displayed in charts
    private String os; // operating system
    private Container container;
    private boolean isTrainRun;
    private TestFailDetail[] failures; // all failures which resulted in this run
    private TestPassDetail[] passes; // all passes(successful tests runs) in this run
    private TestLeakDetail[] leaks; // all leaks detected during this run

    private byte[] xml; // compressed xml

    private byte[] log;
    private byte[] pointsummary;
    private int passedtests;
    private int failedtests;
    private int leakedtests;
    private int averagemem;

    public RunDetail()
    {

    }

    public RunDetail(int userid, int duration, Date posttime, Date timestamp, String os, int revision, Container container, boolean flagged, byte[] xml,
                     byte[] pointsummary, int passedtests, int failedtests, int leakedtests, int averagemem, byte[] log) {
        this.userid = userid;
        this.username = null;
        this.duration = duration;
        this.posttime = posttime;
        this.os = os;
        this.revision = revision;
        this.container = container;
        this.failures = new TestFailDetail[0];
        this.passes = new TestPassDetail[0];
        this.leaks = new TestLeakDetail[0];
        this.flagged = flagged;
        this.timestamp = timestamp;
        this.xml = xml;
        this.pointsummary = pointsummary;
        this.passedtests = passedtests;
        this.failedtests = failedtests;
        this.leakedtests = leakedtests;
        this.averagemem = averagemem;
        this.log = log;
    }
    public RunDetail(int userid, String username, int duration, Date posttime, Date timestamp, String os, int revision, Container container, boolean flagged, byte[] xml,
                     byte[] pointsummary, int passedtests, int failedtests, int leakedtests, int averagemem) {
        this(userid, duration, posttime, timestamp, os, revision, container, flagged, xml, pointsummary, passedtests, failedtests, leakedtests, averagemem, new byte[0]);
        this.username = username;
    }

    public Container getContainer()
    {
        return container;
    }

    public void setContainer(Container container)
    {
        this.container = container;
    }

    public int getRevision()
    {
        return revision;
    }

    public void setRevision(int revision)
    {
        this.revision = revision;
    }

    public String getOs()
    {
        return os;
    }

    public void setOs(String os)
    {
        this.os = os;
    }

    public TestLeakDetail[] getLeaks()
    {
        return leaks;
    }

    public void setLeaks(TestLeakDetail[] leaks)
    {
        this.leaks = leaks;
    }

    public TestFailDetail[] getFailures()
    {
        return failures;
    }

    public void setFailures(TestFailDetail[] failures)
    {
        this.failures = failures;
    }

    public TestPassDetail[] getPasses()
    {
        return passes;
    }

    public void setPasses(TestPassDetail[] passes)
    {
        this.passes = passes;
    }
    public void setPasses(List<TestPassDetail> passes)
    {
        this.passes = passes.toArray(new TestPassDetail[passes.size()]);
    }

    public int getId()
    {
        return id;
    }

    public void setId(int id)
    {
        this.id = id;
    }

    public Date getPostTime() { return posttime; }

    public void setPosttime(Date posttime)
    {
        this.posttime = posttime;
    }

    public int getDuration()
    {
        return duration;
    }

    public void setDuration(int duration)
    {
        this.duration = duration;
    }

    public int getUserid()
    {
        return userid;
    }

    public void setUserid(int userid)
    {
        this.userid = userid;
    }

    public String getUserName()
    {
        return username;
    }

    public void setUserName(String userName)
    {
        this.username = userName;
    }

    public boolean isTrainRun()
    {
        return isTrainRun;
    }

    public void setTrainRun(boolean trainRun)
    {
        isTrainRun = trainRun;
    }

    public boolean isFlagged() // if true run is not considered in stats & generated charts
    {
        return flagged;
    }

    public void setFlagged(boolean flagged)
    {
        this.flagged = flagged;
    }

    public Date getTimestamp() { return timestamp;  }

    public void setTimestamp(Date timestamp)
    {
        this.timestamp = timestamp;
    }

    public byte[] getXml()
    {
        return xml;
    }

    public void setXml(byte[] xml)
    {
        this.xml = xml;
    }

    public byte[] getPointsummary() { return pointsummary; }

    public void setPointsummary(byte[] pointsummary) { this.pointsummary = pointsummary; }
    public byte[] getLog() { return log; }

    public void setLog(byte[] log) { this.log = log; }

    public String getDecodedLog() {
        byte[] bytes = getLog();
        if(bytes == null)
            return "";

        ByteArrayInputStream baos = new ByteArrayInputStream(bytes);
        StringBuilder out = new StringBuilder();
        try(GZIPInputStream s = new GZIPInputStream(baos)){
            BufferedReader reader = Readers.getReader(s);
            BufferedReader in = new BufferedReader(reader);
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                out.append(line);
                out.append("\r\n");
            }

        }
        catch (IOException e1)
        {
            e1.printStackTrace();
        }

        return out.toString();
    }


    // decodes point pass summary data points from the byte array
    public Double[] getPoints() throws IOException {
        if(pointsummary == null)
            return new Double[0];

        ByteArrayInputStream bais = new ByteArrayInputStream(pointsummary);
        DataInputStream in = new DataInputStream(bais);
        String str = "";
        while (in.available() > 0) {
            String element = in.readUTF();
            str+=element;
        }
        String[] strValues = str.split(",");
        List<Double> values = new ArrayList<>();
        try {

            for(String val: strValues) {
                double d = Double.parseDouble(val);
                values.add(d);

            }
        } catch (Exception e) {
            e.getStackTrace();
        }

        in.close();
        bais.close();
        return values.toArray(new Double[values.size()]);
    }

    public int getPassedtests() { return passedtests; }

    public void setPassedtests(int passedtests) { this.passedtests = passedtests; }

    public int getFailedtests() { return failedtests; }

    public void setFailedtests(int failedtests) { this.failedtests = failedtests; }

    public int getLeakedtests() { return leakedtests; }

    public void setLeakedtests(int leakedtests) { this.leakedtests = leakedtests; }

    public void setAveragemem(int averagemem)  { this.averagemem = averagemem; }

    public int getAveragemem() { return averagemem; }

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public double getAverageMemory() {
        if(averagemem != 0) {
            return averagemem;
        } else if(passes == null || (passes.length > 0 && passes[0] == null))
            return 0d;
        double total = 0;
        for(TestPassDetail pass : passes) {
            total += pass.getTotalMemory();
        }
        return total/passes.length;
    }

    public boolean hasHang() {
        if(passes != null && passes.length > 0 && getPostTime() != null && passes[passes.length-1].getTimestamp() != null) {
            /* If posttime is over 30 minutes(1800000ms) from last test run then assume there was a hang */
            return getPostTime().getTime() - passes[passes.length-1].getTimestamp().getTime() > 1800000;
        }
        return false;
    }

    @Override
    public int compareTo(RunDetail other) {
        if(this.posttime != null && other.posttime!= null)
            return this.posttime.compareTo(other.posttime);
        if (this.id  > other.id)
            return 1;
        else if (this.id < other.id)
            return -1;
        else
            return 0;
    }
}

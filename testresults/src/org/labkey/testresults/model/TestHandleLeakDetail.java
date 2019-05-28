package org.labkey.testresults.model;

public class TestHandleLeakDetail extends TestLeakDetail
{
    double handles;

    public TestHandleLeakDetail() {

    }

    public TestHandleLeakDetail(int testRunId, String name, String type, double handles) {
        setTestRunId(testRunId);
        setTestName(name);
        setType(type);
        this.handles = handles;
    }

    public double getHandles()
    {
        return handles;
    }

    public void setHandles(float handles)
    {
        this.handles = handles;
    }
}

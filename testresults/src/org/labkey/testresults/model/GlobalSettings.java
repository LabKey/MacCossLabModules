package org.labkey.testresults.model;

import org.labkey.api.data.Container;

import java.util.Date;

public class GlobalSettings
{

    private int warningB;
    private int errorB;


    public GlobalSettings()
    {
        this.warningB = 3;
        this.errorB = 2;
    }

    public GlobalSettings(int warningB, int errorB) {
        this.warningB = warningB;
        this.errorB = errorB;
    }

    public int getWarningB()
    {
        return warningB;
    }

    public int getErrorB()
    {
        return errorB;
    }

    public void setWarningB(int warningB)
    {
        this.warningB = warningB;
    }

    public void setErrorB(int errorB)
    {
        this.errorB = errorB;
    }
}

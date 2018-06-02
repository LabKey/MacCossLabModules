package org.labkey.passport.model;

public class IPeptide
{
    private String sequence;
    private int startIndex;
    private int endIndex;
    private int proteinId;
    private int panoramaPeptideId;
    private double beforeIntensity;
    private double beforeGlobalStandardArea;
    private double afterIntensity;
    private double afterGlobalStandardArea;
    private int precursorbeforeid;

    public double getBeforeGlobalStandardArea()
    {
        return beforeGlobalStandardArea;
    }

    public void setBeforeGlobalStandardArea(double beforeGlobalStandardArea) {
        this.beforeGlobalStandardArea = beforeGlobalStandardArea;
    }

    public double getAfterGlobalStandardArea()
    {
        return afterGlobalStandardArea;
    }

    public void setAfterGlobalStandardArea(double afterGlobalStandardArea) {
        this.afterGlobalStandardArea = afterGlobalStandardArea;
    }

    public int getPrecursorbeforeid()
    {
        return precursorbeforeid;
    }

    public void setPrecursorbeforeid(int precursorbeforeid)
    {
        this.precursorbeforeid = precursorbeforeid;
    }

    public String getSequence()
    {
        return sequence;
    }

    public void setSequence(String sequence)
    {
        this.sequence = sequence;
    }

    public int getStartIndex()
    {
        return startIndex;
    }

    public void setStartIndex(int startIndex)
    {
        this.startIndex = startIndex;
    }

    public int getEndIndex()
    {
        return endIndex;
    }

    public void setEndIndex(int endIndex)
    {
        this.endIndex = endIndex;
    }

    public int getProteinId()
    {
        return proteinId;
    }

    public void setProteinId(int proteinId)
    {
        this.proteinId = proteinId;
    }

    public int getPanoramaPeptideId()
    {
        return panoramaPeptideId;
    }

    public void setPanoramaPeptideId(int panoramaPeptideId)
    {
        this.panoramaPeptideId = panoramaPeptideId;
    }

    public double getBeforeIntensity()
    {
        return beforeIntensity;
    }

    public void setBeforeIntensity(double beforeIntensity)
    {
        this.beforeIntensity = beforeIntensity;
    }

    public double getAfterIntensity()
    {
        return afterIntensity;
    }

    public void setAfterIntensity(double afterIntensity)
    {
        this.afterIntensity = afterIntensity;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (!IPeptide.class.isAssignableFrom(o.getClass())) {
            return false;
        }
        final IPeptide other = (IPeptide) o;
        return this.getPanoramaPeptideId() == other.getPanoramaPeptideId();
    }

    public int hashCode() {
        return getPanoramaPeptideId();
    }
}

package org.labkey.passport.model;

public class IFeature
{
    int StartIndex;
    int EndIndex;
    String type;
    String description;
    String original;
    String variation;

    public IFeature() {

    }

    public IFeature(int startIndex, int endIndex, String type, String original, String variation, String description)
    {
        StartIndex = startIndex;
        EndIndex = endIndex;
        this.type = type;
        this.original = original;
        this.variation = variation;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public int getStartIndex()
    {
        return StartIndex;
    }

    public void setStartIndex(int startIndex)
    {
        StartIndex = startIndex;
    }

    public int getEndIndex()
    {
        return EndIndex;
    }

    public void setEndIndex(int endIndex)
    {
        EndIndex = endIndex;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public String getOriginal()
    {
        return original;
    }

    public void setOriginal(String original)
    {
        this.original = original;
    }

    public String getVariation()
    {
        return variation;
    }

    public void setVariation(String variation)
    {
        this.variation = variation;
    }
    public boolean isVariation() {
        return type.equals("sequence variant");
    }
}

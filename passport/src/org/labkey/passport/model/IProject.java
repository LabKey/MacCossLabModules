package org.labkey.passport.model;

import org.labkey.api.data.Container;

import java.util.ArrayList;
import java.util.List;

// Class for a Panorama Public Project
public class IProject
{
    int runId;
    int peptideGroupId;
    String fileName;
    Container container;
    List<IPeptide> peptides;

    public int getPeptideGroupId()
    {
        return peptideGroupId;
    }

    public void setPeptideGroupId(int id)
    {
        this.peptideGroupId = id;
    }

    public int getRunId()
    {
        return runId;
    }

    public void setRunId(int runId)
    {
        this.runId = runId;
    }

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    public Container getContainer()
    {
        return container;
    }

    public void setContainer(Container container)
    {
        this.container = container;
    }

    public List<IPeptide> getPeptides()
    {
        return peptides;
    }

    public void setPeptides(List<IPeptide> peptides)
    {
        this.peptides = peptides;
    }

    public void addPeptide(IPeptide peptide) {
        if(peptides == null)
            peptides = new ArrayList<>();
        peptides.add(peptide);
    }
}

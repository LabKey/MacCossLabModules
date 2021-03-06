package org.labkey.testresults.model;

import org.labkey.api.data.Container;

/**
 * Created by Yuval on 6/10/2016.
 */
public class User implements Comparable<User>
{
    private int id;
    private String username;
    private double meantestsrun;
    private double meanmemory;
    private double stddevtestsrun;
    private double stddevmemory;
    private Container container;
    private boolean active;

    public User()
    {
    }

    public User(int id, String username, double meantestsrun, double meanmemory, double stddevtestsrun, double stddevmemory, boolean active) {
        this.id = id;
        this.username = username;
        this.meantestsrun = meantestsrun;
        this.meanmemory = meanmemory;
        this.stddevtestsrun = stddevtestsrun;
        this.stddevmemory = stddevmemory;
        this.active = active;
    }

    public User(int id, String username) {
        this.id = id;
        this.username = username;
    }

    public int getId() { return id; }

    public void setId(int id)
    {
        this.id = id;
    }

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public double getStddevmemory() { return stddevmemory; }

    public void setStddevmemory(double stddevmemory)
    {
        this.stddevmemory = stddevmemory;
    }

    public double getStddevtestsrun() { return stddevtestsrun; }

    public void setStddevtestsrun(double stddevtestsrun) { this.stddevtestsrun = stddevtestsrun; }

    public double getMeanmemory() { return meanmemory; }

    public void setMeanmemory(double meanmemory)
    {
        this.meanmemory = meanmemory;
    }

    public double getMeantestsrun()
    {
        return meantestsrun;
    }

    public void setMeantestsrun(double meantestsrun)
    {
        this.meantestsrun = meantestsrun;
    }

    public Container getContainer()
    {
        return container;
    }

    public void setContainer(Container container)
    {
        this.container = container;
    }

    public boolean isActive() { return active; }

    public void setActive(boolean active) { this.active = active; }

    public boolean fitsRunCountTrainingData(int testsRunCount, int stdDeviations) {
        return getMeantestsrun() == 0.0 || getMeanmemory() == 0.0 ||  // if no training data for user
            lowerBound(getMeantestsrun(), getStddevtestsrun(), stdDeviations) <= testsRunCount;
    }

    public boolean fitsMemoryTrainingData(double avgMemory, int stdDeviations) {
        return getMeantestsrun() == 0.0 || getMeanmemory() == 0.0 || // if no training data for user
            avgMemory <= upperBound(getMeanmemory(), getStddevmemory(), stdDeviations);
    }

    public static double lowerBound(double mean, double stdDev, int stdDevs) { return Math.max(mean - stdDev * stdDevs, 0); }
    public static double upperBound(double mean, double stdDev, int stdDevs) { return mean + stdDev * stdDevs; }

    public String runBoundHtmlString(int warningBoundary, int errorBoundary) {
        StringBuilder sb = new StringBuilder();
        sb.append("Good: ");
        sb.append("> ");
        sb.append((int) Math.round(lowerBound(getMeantestsrun(), getStddevtestsrun(), warningBoundary)) - 1);
        sb.append("\n");
        sb.append("Warn: ");
        sb.append((int) Math.round(lowerBound(getMeantestsrun(), getStddevtestsrun(), errorBoundary)));
        sb.append(" - ");
        sb.append((int) Math.round(lowerBound(getMeantestsrun(), getStddevtestsrun(), warningBoundary)) - 1);
        sb.append("\n");
        sb.append("Error: ");
        sb.append("< ");
        sb.append((int) Math.round(lowerBound(getMeantestsrun(), getStddevtestsrun(), errorBoundary)));
        return sb.toString();
    }

    public String memBoundHtmlString(int warningBoundary, int errorBoundary) {
        StringBuilder sb = new StringBuilder();
        sb.append("Good: ");
        sb.append("< ");
        sb.append((int) Math.round(upperBound(getMeanmemory(), getStddevmemory(), warningBoundary)) + 1);
        sb.append("\n");
        sb.append("Warn: ");
        sb.append((int) Math.round(upperBound(getMeanmemory(), getStddevmemory(), warningBoundary)) + 1);
        sb.append(" - ");
        sb.append((int) Math.round(upperBound(getMeanmemory(), getStddevmemory(), errorBoundary)));
        sb.append("\n");
        sb.append("Error: ");
        sb.append("> ");
        sb.append((int) Math.round(upperBound(getMeanmemory(), getStddevmemory(), errorBoundary)));
        return sb.toString();
    }

    @Override
    public int compareTo(User o)
    {
        return Integer.compare(this.id, o.id);
    }
}

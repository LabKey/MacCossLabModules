package org.labkey.testresults;

import java.util.Arrays;
import java.util.List;

// stats class to calculate stddev, mean, and median
public class TRStats
{
    double[] data;
    int size;

    public TRStats(List<Double> data)
    {
        double[] target = new double[data.size()];
        for (int i = 0; i < target.length; i++) {
            target[i] = data.get(i).doubleValue();
        }
        this.data = target;
        size = target.length;
    }

    public double getMean()
    {
        double sum = 0.0;
        for(double a : data)
            sum += a;
        return sum/size;
    }

    double getVariance()
    {
        double mean = getMean();
        double temp = 0;
        for(double a :data)
            temp += (mean-a)*(mean-a);
        return temp/size;
    }

    public double getStdDev()
    {
        return Math.sqrt(getVariance());
    }

    public double median()
    {
        Arrays.sort(data);

        if (data.length % 2 == 0)
        {
            return (data[(data.length / 2) - 1] + data[data.length / 2]) / 2.0;
        }
        else
        {
            return data[data.length / 2];
        }
    }
}
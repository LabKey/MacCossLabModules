package org.labkey.lincs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * User: vsharma
 * Date: 12/3/2015
 * Time: 9:45 AM
 */
public class Gct
{
    private List<GctEntity> _probes;
    private Map<String, Integer> _probeIndexMap;
    private List<GctEntity> _replicates;
    private Map<String, Integer> _replicateIndexMap;
    private HashMap<GctKey, String> _areaRatios;

    private List<String> _probeAnnotationNames;
    private List<String> _replicateAnnotationNames;

    public Gct()
    {
        _probes = new ArrayList<>();
        _probeIndexMap = new HashMap<>();
        _replicates = new ArrayList<>();
        _replicateIndexMap = new HashMap<>();
        _areaRatios = new HashMap<>();
    }

    public void addProbe(GctEntity probe)
    {
        _probes.add(probe);
        _probeIndexMap.put(probe.getName(), _probes.size() - 1);
    }

    public GctEntity getProbeByName(String name)
    {
        Integer idx = _probeIndexMap.get(name);
        return idx != null ? _probes.get(idx) : null;
    }

    public void addReplicate(GctEntity replicate)
    {
        _replicates.add(replicate);
        _replicateIndexMap.put(replicate.getName(), _replicates.size() - 1);
    }

    public GctEntity getReplicateByName(String name)
    {
        Integer idx = _replicateIndexMap.get(name);
        return idx != null ? _replicates.get(idx) : null;
    }

    public GctEntity getReplicateAtIndex(int index)
    {
        return index < _replicates.size() ? _replicates.get(index) : null;
    }

    public void addAreaRatio(String probe, String replicate, String ratio)
    {
        GctKey key = new GctKey(probe, replicate);
        if(_areaRatios.containsKey(key))
        {
            throw new GctFileException("Area ratio has already been added for probe " + probe + " and replicate " + replicate);
        }
        _areaRatios.put(key, ratio);
    }

    public Map<GctKey, String> getAreaRatios()
    {
        return _areaRatios;
    }

    public List<GctEntity> getProbes()
    {
        return _probes;
    }

    public List<GctEntity> getReplicates()
    {
        return _replicates;
    }

    private int getProbeCount()
    {
        return _probes.size();
    }

    private int getProbeAnnotationCount()
    {
        if(_probeAnnotationNames == null)
        {
            LinkedHashSet<String> annotations = new LinkedHashSet<>();
            for(GctEntity probe: _probes)
            {
                annotations.addAll(probe.getAnnotations().keySet());
            }
            _probeAnnotationNames = new ArrayList<>(annotations);
            Collections.sort(_probeAnnotationNames);
        }
        return _probeAnnotationNames.size();
    }

    private int getReplicateCount()
    {
        return _replicates.size();
    }

    private int getReplicateAnnotationCount()
    {
        if(_replicateAnnotationNames == null)
        {
            LinkedHashSet<String> annotations = new LinkedHashSet<>();
            for(GctEntity replicate: _replicates)
            {
                annotations.addAll(replicate.getAnnotations().keySet());
            }
            _replicateAnnotationNames = new ArrayList<>(annotations);
            Collections.sort(_replicateAnnotationNames);
        }
        return _replicateAnnotationNames.size();
    }

    public void writeGct(File outFile) throws IOException
    {
        BufferedWriter writer = null;
        try
        {
            writer = new BufferedWriter(new FileWriter(outFile));
            writer.write("#1.3");
            writer.newLine();
            int probeAnnotationCount = getProbeAnnotationCount();
            writer.write(getProbeCount() + "\t" +
                         getReplicateCount() + "\t" +
                         probeAnnotationCount + "\t" +
                         getReplicateAnnotationCount());
            writer.newLine();
            writer.write("id"); // first column, first row in GCT table
            // FIRST ROW: Write probe annotation names
            for(String probeAnnotation: _probeAnnotationNames)
            {
                writer.write("\t");
                writer.write(probeAnnotation);
            }
            // FIRST ROW: Write replicate names
            for(GctEntity replicate: _replicates)
            {
                writer.write("\t");
                writer.write(replicate.getName());
            }
            writer.newLine();

            // REPLICATE ANNOTATION ROWS: Write replicate annotation values
            for(String repAnnotationName: _replicateAnnotationNames)
            {
                writer.write(repAnnotationName);
                for(int i = 0; i < probeAnnotationCount; i++)
                {
                    writer.write("\tNA");
                }

                for(GctEntity replicate: _replicates)
                {
                    writer.write("\t");
                    String annotationValue = replicate.getAnnotationValue(repAnnotationName);
                    writer.write(annotationValue == null ? "NA" : annotationValue);
                }
                writer.newLine();
            }

            // PROBE ROWS: Write probe names, probe annotation values and area ratios
            List<String> probeNames = new ArrayList<>(_probeIndexMap.keySet());
            Collections.sort(probeNames);
            for(String probeName: probeNames)
            {
                GctEntity probe = _probes.get(_probeIndexMap.get(probeName));
                writer.write(probe.getName());
                for(String probeAnnotationName: _probeAnnotationNames)
                {
                    writer.write("\t");
                    String annotationValue = probe.getAnnotationValue(probeAnnotationName);
                    writer.write(annotationValue == null ? "NA" : annotationValue);
                }

                // Write the area ratios for this probe in all the replicates
                for(GctEntity replicate: _replicates)
                {
                    String value = _areaRatios.get(new GctKey(probe.getName(), replicate.getName()));
                    writer.write("\t");
                    writer.write(value == null ? "NA" : value);
                }
                writer.newLine();
            }
        }
        finally
        {
            if(writer != null) try {writer.close();} catch(IOException ignored) {}
        }
    }


    public static Gct readGct(File inFile) throws IOException
    {
        BufferedReader reader = null;
        try
        {
            Gct gct = new Gct();
            int probeCount, replicateCount, probeAnnotationCount, replicateAnnotationCount;

            reader = new BufferedReader(new FileReader(inFile));
            reader.readLine();  // GCT version number

            // Header with number of probes, replicates, replicate annotations, probe annotations
            String[] tokens = readNextLine(reader, 4);
            probeCount = Integer.parseInt(tokens[0]);
            replicateCount = Integer.parseInt(tokens[1]);
            probeAnnotationCount = Integer.parseInt(tokens[2]);
            replicateAnnotationCount = Integer.parseInt(tokens[3]);


            // Probe annotation names and replicate names
            int numColumns = 1 + probeAnnotationCount + replicateCount;
            tokens = readNextLine(reader, numColumns);

            List<String> probeAnnotationNames = new ArrayList<>(probeAnnotationCount);
            probeAnnotationNames.addAll(Arrays.asList(tokens).subList(1, probeAnnotationCount + 1));

            for(int i = probeAnnotationCount + 1; i < tokens.length; i++)
            {
                gct.addReplicate(new GctEntity(tokens[i]));
            }

            // Read the replicate annotation values for all replicates
            for(int i = 0; i < replicateAnnotationCount; i++)
            {
                tokens = readNextLine(reader, numColumns);
                String replAnnotationName = tokens[0];
                for(int j = probeAnnotationCount + 1; j < tokens.length; j++)
                {
                    GctEntity replicate = gct.getReplicateAtIndex(j - (probeAnnotationCount + 1));
                    replicate.addAnnotation(replAnnotationName, tokens[j]);
                }
            }

            // Read the probe annotation values and the area ratios
            for(int i = 0; i < probeCount; i++)
            {
                tokens = readNextLine(reader, numColumns);
                GctEntity probe = new GctEntity(tokens[0]);
                gct.addProbe(probe);

                for(int j = 1; j <= probeAnnotationCount; j++)
                {
                    probe.addAnnotation(probeAnnotationNames.get(j - 1), tokens[j]);
                }

                for(int j = probeAnnotationCount + 1; j < tokens.length; j++)
                {
                    String replicateName = gct.getReplicateAtIndex(j - (probeAnnotationCount + 1)).getName();
                    gct.addAreaRatio(probe.getName(), replicateName, tokens[j]);
                }
            }

            return gct;
        }
        finally
        {
            if(reader != null) try {reader.close();} catch(IOException ignored){}
        }
    }

    private static String[] readNextLine(BufferedReader reader, int expectedColumns) throws IOException
    {
        String line = reader.readLine();
        if(line == null)
        {
            throw new GctFileException("Could not read next line in file.");
        }
        String[] tokens = line.split("\\t");
        if(tokens.length != expectedColumns)
        {
            throw new GctFileException("Expecting line with " + expectedColumns + " columns. Found " + tokens.length + ":\n " + line);
        }
        return tokens;
    }

    public static class GctEntity
    {
        private final String _name;
        private Map<String, String> _annotations;

        public GctEntity(String name)
        {
            _name = name;
            _annotations = new HashMap<>();
        }
        public String getName()
        {
            return _name;
        }

        public void addAnnotation(String name, String value)
        {
            if(_annotations.containsKey(name))
            {
                throw new IllegalArgumentException(getName() + " already contains a value for annotation " + name);
            }
            _annotations.put(name, value);
        }

        public boolean hasAnnotationValues(List<LincsController.SelectedAnnotation> selectedAnnotations)
        {
            for(LincsController.SelectedAnnotation annotation: selectedAnnotations)
            {
                String myValue = _annotations.get(annotation.getName());
                if(myValue == null || !annotation.getValues().contains(myValue))
                {
                    return false;
                }
            }
            return true;
        }

        public String getAnnotationValue(String name)
        {
            String value = _annotations.get(name);
            return value == null ? "NA" : value;
        }

        public Map<String, String> getAnnotations()
        {
            return _annotations;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            GctEntity gctEntity = (GctEntity) o;

            return _name.equals(gctEntity._name);

        }

        @Override
        public int hashCode()
        {
            return _name.hashCode();
        }
    }

    public static class GctKey
    {
        private final String _probe;
        private final String _replicate;

        public GctKey(String probe, String replicate)
        {
            _probe = probe;
            _replicate = replicate;
        }

        public String getProbe()
        {
            return _probe;
        }

        public String getReplicate()
        {
            return _replicate;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            GctKey gctKey = (GctKey) o;

            return _probe.equals(gctKey._probe) && _replicate.equals(gctKey._replicate);

        }

        @Override
        public int hashCode()
        {
            int result = _probe.hashCode();
            result = 31 * result + _replicate.hashCode();
            return result;
        }
    }

    public static class GctFileException extends RuntimeException
    {
        public GctFileException(String message)
        {
            super(message);
        }
    }
}


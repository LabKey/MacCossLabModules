/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.lincs.view;

import org.labkey.lincs.Gct;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: vsharma
 * Date: 12/9/2015
 * Time: 2:13 PM
 */
public class GctUtils
{
    private GctUtils() {}

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
                gct.addReplicate(new Gct.GctEntity(tokens[i]));
            }

            // Read the replicate annotation values for all replicates
            for(int i = 0; i < replicateAnnotationCount; i++)
            {
                tokens = readNextLine(reader, numColumns);
                String replAnnotationName = tokens[0];
                for(int j = probeAnnotationCount + 1; j < tokens.length; j++)
                {
                    Gct.GctEntity replicate = gct.getReplicateAtIndex(j - (probeAnnotationCount + 1));
                    replicate.addAnnotation(replAnnotationName, tokens[j]);
                }
            }

            // Read the probe annotation values and the area ratios
            for(int i = 0; i < probeCount; i++)
            {
                tokens = readNextLine(reader, numColumns);
                Gct.GctEntity probe = new Gct.GctEntity(tokens[0]);
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
            throw new Gct.GctFileException("Could not read next line in file.");
        }
        String[] tokens = line.split("\\t");
        if(tokens.length != expectedColumns)
        {
            throw new Gct.GctFileException("Expecting line with " + expectedColumns + " columns. Found " + tokens.length + ":\n " + line);
        }
        return tokens;
    }

    public static void writeGct(Gct gct, File outFile) throws IOException
    {
        BufferedWriter writer = null;
        try
        {
            writer = new BufferedWriter(new FileWriter(outFile));
            writer.write("#1.3");
            writer.newLine();
            int probeAnnotationCount = gct.getProbeAnnotationCount();
            writer.write(gct.getProbeCount() + "\t" +
                    gct.getReplicateCount() + "\t" +
                    probeAnnotationCount + "\t" +
                    gct.getReplicateAnnotationCount());
            writer.newLine();
            writer.write("id"); // first column, first row in GCT table
            // FIRST ROW: Write probe annotation names
            for (String probeAnnotation : gct.getProbeAnnotationNames())
            {
                writer.write("\t");
                writer.write(probeAnnotation);
            }
            // FIRST ROW: Write replicate names (sorted by det_plate, and then by replicate name)
            List<Gct.GctEntity> sortedReplicates = gct.getSortedReplicates();
            for (Gct.GctEntity replicate : sortedReplicates)
            {
                writer.write("\t");
                writer.write(replicate.getName());
            }
            writer.newLine();

            // REPLICATE ANNOTATION ROWS: Write replicate annotation values
            for (String repAnnotationName : gct.getReplicateAnnotationNames())
            {
                writer.write(repAnnotationName);
                for (int i = 0; i < probeAnnotationCount; i++)
                {
                    writer.write("\tNA");
                }

                for (Gct.GctEntity replicate : sortedReplicates)
                {
                    writer.write("\t");
                    String annotationValue = replicate.getAnnotationValue(repAnnotationName);
                    writer.write(annotationValue == null ? "NA" : annotationValue);
                }
                writer.newLine();
            }

            // PROBE ROWS: Write probe names, probe annotation values and area ratios
            List<String> probeNames = gct.getSortedProbeNames();
            Gct.GctKeyBuilder<Gct.ProbeExpTypePlate> keyBuilder = new Gct.ProbePlateKeyBuilder();
            for (String probeName : probeNames)
            {
                Gct.GctEntity probe = gct.getProbeByName(probeName);
                writer.write(probe.getName());
                for (String probeAnnotationName : gct.getProbeAnnotationNames())
                {
                    writer.write("\t");
                    String annotationValue;
                    Gct.GctTable<Gct.ProbeExpTypePlate> valuesTable = gct.getMultiValueProbeAnnotation(probeAnnotationName);
                    if (valuesTable != null)
                    {
                        // pr_probe_normalization_group and pr_probe_suitability_manual probe annotation can have different values
                        // in the various processed GCT files. Combine them as a vector (e.g. [1,2,1]) sorted by the plate number.
                        // For example: if the value of pr_probe_normalization_group is 1 in plate 17 and 2 in plate 18, the combined value
                        // will be [1,2]
                        // 05/03/16 - Sorting will be on both experiment type (DIA/PRM) and plate number since the key (key2 = expType_plate)
                        // has both experiment type and plate.
                        annotationValue = valuesTable.getSortedValuesForKey1(probe.getName(), keyBuilder);
                    }
                    else
                    {
                        annotationValue = probe.getAnnotationValue(probeAnnotationName);
                    }

                    writer.write(annotationValue == null ? "NA" : annotationValue);
                }

                // Write the area ratios for this probe in all the replicates
                Gct.GctTable<Gct.ProbeReplicate> areaRatios = gct.getAreaRatios();
                for (Gct.GctEntity replicate : sortedReplicates)
                {
                    String value = areaRatios.getValue(new Gct.ProbeReplicate(probe.getName(), replicate.getName()));
                    writer.write("\t");
                    writer.write(value == null ? "NA" : value);
                }
                writer.newLine();
            }
        }
        finally
        {
            if (writer != null) try
            {
                writer.close();
            }
            catch (IOException ignored)
            {
            }
        }
    }
}

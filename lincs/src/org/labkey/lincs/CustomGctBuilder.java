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
package org.labkey.lincs;

import org.labkey.lincs.view.GctUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: vsharma
 * Date: 12/9/2015
 * Time: 2:10 PM
 */
public class CustomGctBuilder
{
    private final String[] multiValueProbeAnnotations = new String[] {"pr_probe_normalization_group", "pr_probe_suitability_manual"};

    public Gct build(List<File> files, List<LincsController.SelectedAnnotation> selectedAnnotations,
                     Set<String> ignoredProbeAnnotations, Set<String> ignoredReplicateAnnotations) throws Gct.GctFileException
    {
        Gct customGct = new Gct();

        for(File file: files)
        {
            Gct gct;
            try
            {
                gct = GctUtils.readGct(file);
            }
            catch (IOException e)
            {
                throw new Gct.GctFileException( "Error reading GCT file " + file.getName(), e);
            }

            // Add the replicates.
            int addedReplicates = addReplicates(gct, customGct, selectedAnnotations);

            if(addedReplicates == 0)
            {
                // If no replicate columns were added from this GCT file move on to the next one.
                continue;
            }

            // Add the probes.
            addProbes(gct, customGct, file.getName());

            // Add the area ratios.
            addAreaRatios(gct, customGct);

            // Record the values of multi-value probe annotations (pr_probe_normalization_group & pr_probe_suitability_manual)
            updateMultiValueProbeAnnotations(gct, customGct);

        }

        customGct.setIgnoredProbeAnnotations(ignoredProbeAnnotations);
        customGct.setIgnoredReplicateAnnotations(ignoredReplicateAnnotations);
        return customGct;
    }

    private void updateMultiValueProbeAnnotations(Gct gct, Gct customGct)
    {
        // All replicates in a single GCT file should have the same value for the "det_plate" replicate annotation.
        String detPlateAnnotationVal = gct.getReplicates().get(0).getAnnotationValue("det_plate");

        // pr_probe_normalization_group and pr_probe_suitability_manual probe annotation can have different values
        // in the various processed GCT files. Combine them as a vector (e.g. [1,2,1]) sorted by the plate number.
        // For example: if the value of pr_probe_normalization_group is 1 in plate 17 and 2 in plate 18, the combined value
        // will be [1,2]
        for(Gct.GctEntity probe: gct.getProbes())
        {
            for (String annotationName : multiValueProbeAnnotations)
            {
                customGct.addMultiValueProbeAnnotation(annotationName,
                        new Gct.ProbePlate(probe.getName(), detPlateAnnotationVal),
                        probe.getAnnotationValue(annotationName));
            }
        }
    }

    private void addAreaRatios(Gct gct, Gct customGct)
    {
        Gct.GctTable<Gct.ProbeReplicate> areaRatioTable = gct.getAreaRatios();
        for(Gct.ProbeReplicate key: areaRatioTable.getKeys())
        {
            if(customGct.getReplicateByName(key.getReplicate()) == null)
            {
                continue; // This replicate may have been filtered out
            }
            if(customGct.getProbeByName(key.getProbe()) == null)
            {
                throw new Gct.GctFileException("Probe " + key.getProbe() + " not found in the list of probes.");
            }
            customGct.addAreaRatio(key.getProbe(), key.getReplicate(), areaRatioTable.getValue(key));
        }
    }

    private void addProbes(Gct gct, Gct customGct, String fileName)
    {
        for(Gct.GctEntity probe: gct.getProbes())
        {
            Gct.GctEntity savedProbe = customGct.getProbeByName(probe.getName());
            if (savedProbe == null)
            {
                customGct.addProbe(probe);
            }

            else
            {
                // Look at annotation values; Should be same across all GCT files.
                for (Map.Entry<String, String> annotation : probe.getAnnotations().entrySet())
                {
                    String annotationName = annotation.getKey();
                    boolean multiValueAnnotation = false;
                    for(String name: multiValueProbeAnnotations)
                    {
                        if(name.equals(annotationName))
                        {
                            multiValueAnnotation = true;
                            break;
                        }
                    }
                    if (!multiValueAnnotation)
                    {
                        String newValue = annotation.getValue();
                        String oldValue = savedProbe.getAnnotationValue(annotationName);
                        if (!oldValue.equals(newValue))
                        {
                            throw new Gct.GctFileException("Value of probe annotation " + annotationName + " is different in file " + fileName +
                                    ". Saved value is " + oldValue + ". Value in file is " + newValue);
                        }
                    }
                }
            }
        }
    }

    private int addReplicates(Gct gct, Gct customGct, List<LincsController.SelectedAnnotation> selectedAnnotations)
    {
        int addedReplicates = 0;
        for(Gct.GctEntity replicate: gct.getReplicates())
        {
            if(customGct.getReplicateByName(replicate.getName()) != null)
            {
                throw new Gct.GctFileException("Replicate name " + replicate.getName() + " has already been seen in another GCT file.");
            }
            if(replicate.hasAnnotationValues(selectedAnnotations))
            {
                // Add this replicate column if this replicate has all the selected annotation values
                // OR if no annotation values were selected.
                customGct.addReplicate(replicate);
                addedReplicates++;
            }
        }
        return addedReplicates;
    }
}

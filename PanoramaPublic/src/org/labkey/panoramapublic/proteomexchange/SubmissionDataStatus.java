/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.targetedms.proteomexchange;

import org.apache.commons.io.FilenameUtils;
import org.labkey.targetedms.model.ExperimentAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SubmissionDataStatus
{
    private final ExperimentAnnotations _expAnnot;
    private List<String> _missingMetadata;
    private Map<String, Set<String>> _missingRawFiles = new HashMap<>();
    private List<ExperimentModificationGetter.PxModification> _noUnimodMods;

    public SubmissionDataStatus(ExperimentAnnotations expAnnot)
    {
        _expAnnot = expAnnot;
    }

    public ExperimentAnnotations getExperimentAnnotations()
    {
        return _expAnnot;
    }

    public List<String> getMissingMetadata()
    {
        return _missingMetadata == null ? Collections.emptyList() : _missingMetadata;
    }

    public void setMissingMetadata(List<String> missingMetadata)
    {
        _missingMetadata = missingMetadata;
    }

    public List<MissingRawData> getMissingRawData()
    {
        if(_missingRawFiles.size() == 0)
        {
            return Collections.emptyList();
        }

        List<String> rawFiles = new ArrayList<>(_missingRawFiles.keySet());
        Set<String> mergedRawFiles = new HashSet<>();
        List<MissingRawData> missingData = new ArrayList<>();
        for(int i = 0; i < rawFiles.size(); i++)
        {
            String rawFile1 = rawFiles.get(i);
            if(mergedRawFiles.contains(rawFile1))
            {
                continue;
            }
            Set<String> skydocs = _missingRawFiles.get(rawFile1);

            Set<String> pathsFromSameSkyDocs = new HashSet<>();
            pathsFromSameSkyDocs.add(rawFile1);
            for(int j = i+1; j < rawFiles.size(); j++)
            {
                String rawFile2 = rawFiles.get(j);

                Set<String> skydocs2 = _missingRawFiles.get(rawFile2);

                if(skydocs.size() == skydocs2.size() && skydocs.containsAll(skydocs2))
                {
                    pathsFromSameSkyDocs.add(rawFile2);
                    mergedRawFiles.add(rawFile2);
                }
            }
            Set<String> fileNames = new HashSet<>(pathsFromSameSkyDocs.size());
            for(String filePath: pathsFromSameSkyDocs)
            {
                fileNames.add(SubmissionDataValidator.getSampleFileName(filePath));
            }
            MissingRawData missing = new MissingRawData(skydocs, fileNames);
            missingData.add(missing);
        }

        return missingData;
    }

    public void addMissingRawPath(String filePath, String skylineDoc)
    {
        Set<String> skyDocs = _missingRawFiles.get(filePath);
        if(skyDocs == null)
        {
            skyDocs = new HashSet<>();
            _missingRawFiles.put(filePath, skyDocs);
        }
        skyDocs.add(skylineDoc);
    }

    public List<ExperimentModificationGetter.PxModification> getInvalidMods()
    {
        return _noUnimodMods == null ? Collections.emptyList() : _noUnimodMods;
    }

    public void addInvalidMod(ExperimentModificationGetter.PxModification modName)
    {
        if(_noUnimodMods == null)
        {
            _noUnimodMods = new ArrayList<>();
        }

        _noUnimodMods.add(modName);
    }

    public boolean isValid()
    {
        return (!hasMissingMetadata() && !hasMissingRawFiles() && !hasInvalidModifications());
    }

    public boolean hasMissingMetadata()
    {
        return _missingMetadata != null && _missingMetadata.size() > 0;
    }

    public boolean hasMissingRawFiles()
    {
        return _missingRawFiles != null && _missingRawFiles.size() > 0;
    }

    public boolean hasInvalidModifications()
    {
        return _noUnimodMods != null && _noUnimodMods.size() > 0;
    }

    public class MissingRawData
    {
        private final Set<String> _skyDocs;
        private final Set<String> _rawData;

        public MissingRawData(Set<String> skyDocs, Set<String> rawData)
        {
            _skyDocs = skyDocs;
            _rawData = rawData;
        }

        public Set<String> getSkyDocs()
        {
            return _skyDocs;
        }

        public Set<String> getRawData()
        {
            return _rawData;
        }
    }
}

/*
 * Copyright (c) 2015-2017 LabKey Corporation
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private GctTable<ProbeReplicate> _areaRatios;

    private List<String> _probeAnnotationNames;
    private List<String> _replicateAnnotationNames;

    // pr_probe_normalization_group and pr_probe_suitability_manual probe annotation can have different values
    // in the various processed GCT files.
    // GctTable<ProbeExpTypePlate> : table with probes as rows and <expType>_<plateNumber> (e.g. DIA_P0018) as columns.
    private Map<String, GctTable<ProbeExpTypePlate>> _multiValueProbeAnnotations;

    public Gct()
    {
        _probes = new ArrayList<>();
        _probeIndexMap = new HashMap<>();
        _replicates = new ArrayList<>();
        _replicateIndexMap = new HashMap<>();
        _areaRatios = new GctTable<>();
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

    public List<String> getSortedProbeNames()
    {
        List<String> probeNames = new ArrayList<>(_probeIndexMap.keySet());
        probeNames.sort(String.CASE_INSENSITIVE_ORDER);
        return probeNames;
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

    public String uniquifyReplicateName(GctEntity replicate)
    {
        // Append the value of the det_plate annotation AND experiment type (DIA or PRM) to the replicate name.
        String plateNumber = replicate.getAnnotationValue(LincsAnnotation.PLATE_ANNOTATION);

        return getExperimentType(replicate) + "_" + plateNumber + "_" + replicate.getName();
    }

    public String getExperimentType(GctEntity replicate)
    {
        String provenanceCode = replicate.getAnnotationValue(LincsAnnotation.PROVENANCE_CODE);
        return getExperimentType(provenanceCode);
    }

    public static String getExperimentType(String provenanceCode)
    {
        String exptType = "";
        if(provenanceCode != null)
        {
            exptType = provenanceCode.startsWith("DIA1+") ? "DIA" : (provenanceCode.startsWith("PR1") ? "PRM" : "");
        }
        return exptType;
    }

    public void addAreaRatio(String probe, String replicate, String ratio)
    {
        _areaRatios.addValue(new ProbeReplicate(probe, replicate), ratio);
    }

    public void addMultiValueProbeAnnotation(String annotationName, ProbeExpTypePlate key, String value)
    {
        if(_multiValueProbeAnnotations == null)
        {
            _multiValueProbeAnnotations = new HashMap<>();
        }
        GctTable<ProbeExpTypePlate> probePlateValues = _multiValueProbeAnnotations.get(annotationName);
        if(probePlateValues == null)
        {
            probePlateValues = new GctTable<>();
            _multiValueProbeAnnotations.put(annotationName, probePlateValues);
        }
        probePlateValues.addValue(key, value);
    }

    public Gct.GctTable<ProbeExpTypePlate>  getMultiValueProbeAnnotation(String annotationName)
    {
        if(_multiValueProbeAnnotations != null)
        {
            return _multiValueProbeAnnotations.get(annotationName);
        }
        return null;
    }

    public GctTable<ProbeReplicate> getAreaRatios()
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

    public List<GctEntity> getSortedReplicates()
    {
        // Sort replicates by the value of the det_plate annotation, and then
        // by replicate name. For custom GCT, the replicate name is:
        // <exp_type>_<plate_number>_<original_replicate_name>
        List<GctEntity> sortedReplicates = new ArrayList<GctEntity>(_replicates.size());
        sortedReplicates.addAll(_replicates);
        sortedReplicates.sort((rep1, rep2) ->
        {
            String annot1 = rep1.getAnnotationValue(LincsAnnotation.PLATE_ANNOTATION);
            String annot2 = rep2.getAnnotationValue(LincsAnnotation.PLATE_ANNOTATION);
            if (annot1 == null) return 1;
            if (annot2 == null) return -1;
            int val = annot1.compareTo(annot2);
            return val != 0 ? val : rep1.getName().compareTo(rep2.getName());
        });
        return sortedReplicates;
    }

    public int getProbeCount()
    {
        return _probes.size();
    }

    public int getProbeAnnotationCount()
    {
        if(_probeAnnotationNames == null)
        {
            filterProbeAnnotations(Collections.emptySet());
        }

        return _probeAnnotationNames.size();
    }

    public List<String> getProbeAnnotationNames()
    {
        return _probeAnnotationNames == null ? Collections.emptyList() : _probeAnnotationNames;
    }

    private void filterProbeAnnotations(Set<String> toIgnore)
    {
        _probeAnnotationNames = filterAnnotations(getProbes(), toIgnore);
    }

    public int getReplicateCount()
    {
        return _replicates.size();
    }

    public int getReplicateAnnotationCount()
    {
        if(_replicateAnnotationNames == null)
        {
            filterProbeAnnotations(Collections.emptySet());
        }

        return _replicateAnnotationNames.size();
    }

    public List<String> getReplicateAnnotationNames()
    {
        return _replicateAnnotationNames == null ? Collections.emptyList() : _replicateAnnotationNames;
    }

    private void filterReplicateAnnotations(Set<String> toIgnore)
    {
        _replicateAnnotationNames = filterAnnotations(getReplicates(), toIgnore);
    }

    private List<String> filterAnnotations(List<GctEntity> gctEntities, Set<String> toIgnore)
    {
        LinkedHashSet<String> annotations = new LinkedHashSet<>();
        for(GctEntity gctEntity: gctEntities)
        {
            annotations.addAll(gctEntity.getAnnotations().keySet());
        }
        List<String> annotationNames = new ArrayList<>(annotations);
        Iterator<String> iterator = annotationNames.iterator();
        while(iterator.hasNext())
        {
            String annotationName = iterator.next();
            if(toIgnore.contains(annotationName))
            {
                iterator.remove();
            }
        }
        Collections.sort(annotationNames);
        return annotationNames;
    }

    public void setIgnoredReplicateAnnotations(Set<String> ignored)
    {
        filterReplicateAnnotations(ignored);
    }

    public void setIgnoredProbeAnnotations(Set<String> ignored)
    {
        filterProbeAnnotations(ignored);
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

        public GctEntity copy(String newName)
        {
            GctEntity newReplicate = new GctEntity(newName);
            for(String key: _annotations.keySet())
            {
                newReplicate.addAnnotation(key, _annotations.get(key));
            }
            return newReplicate;
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

    public static class ProbeReplicate extends GctKey
    {
        public ProbeReplicate (String probe, String replicate)
        {
            super(probe, replicate);
        }

        @Override
        public String getKey1Name()
        {
            return "probe";
        }

        @Override
        public String getKey2Name()
        {
            return "replicate";
        }

        public String getProbe()
        {
            return super.getKey1();
        }

        public String getReplicate()
        {
            return super.getKey2();
        }
    }

    private abstract static  class GctKey
    {
        private final String _key1;
        private final String _key2;

        public GctKey(String key1, String key2)
        {
            _key1 = key1;
            _key2 = key2;
        }

        public String getKey1()
        {
            return _key1;
        }

        public String getKey2()
        {
            return _key2;
        }

        public abstract String getKey1Name();
        public abstract String getKey2Name();

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            GctKey gctKey = (GctKey) o;

            return _key1.equals(gctKey._key1) && _key2.equals(gctKey._key2);
        }

        @Override
        public int hashCode()
        {
            int result = _key1.hashCode();
            result = 31 * result + _key2.hashCode();
            return result;
        }

        @Override
        public String toString()
        {
            return "Key{" + getKey1Name() + ": " + _key1 + ", " + getKey2Name() + ": " + _key2 + "}";
        }
    }

    public interface GctKeyBuilder <T extends GctKey>
    {
        public T build(String key1, String key2);
    }

    public static class ProbePlateKeyBuilder implements GctKeyBuilder<ProbeExpTypePlate>
    {
        // @Override
        public ProbeExpTypePlate build(String probe, String expTypeAndPlate)
        {
            return new ProbeExpTypePlate(probe, expTypeAndPlate);
        }
    }

    public static class GctFileException extends RuntimeException
    {
        public GctFileException(String message)
        {
            super(message);
        }

        public GctFileException(String message, Throwable cause)
        {
            super(message, cause);
        }
    }

    public static class GctTable <T extends GctKey>
    {
        private Map<T, String> _map;
        private List<String> _sortedKey2;

        public GctTable()
        {
            _map = new HashMap<>();
        }

        public void addValue(T key, String value)
        {
            if(_map.containsKey(key))
            {
                throw new GctFileException("Value has already been added for " + key.toString());
            }
            _map.put(key, value);
        }

        public String getValue(T key)
        {
            String value = _map.get(key);
            return value == null ? "NA" : value;
        }

        public Set<T> getKeys()
        {
            return _map.keySet();
        }

        public String getSortedValuesForKey1(String key1, GctKeyBuilder<T> gctKeyBuilder)
        {
            if(_sortedKey2 == null)
            {
                Set<String> uniqKey2Set = new HashSet<>();
                for(T key: _map.keySet())
                {
                    uniqKey2Set.add(key.getKey2());
                }
                _sortedKey2 = new ArrayList<>(uniqKey2Set);
                Collections.sort(_sortedKey2);
            }

            StringBuilder values = new StringBuilder();
            if(_sortedKey2.size() > 1)
            {
                values.append("[");
            }

            String comma = "";
            for(String key2: _sortedKey2)
            {
                String value = getValue(gctKeyBuilder.build(key1, key2));
                values.append(comma).append(value);
                comma = ",";
            }

            if(_sortedKey2.size() > 1)
            {
                values.append("]");
            }

            return values.toString();
        }
    }

    public static class ProbeExpTypePlate extends GctKey
    {
        public ProbeExpTypePlate(String probe, String expTypeAndPlate)
        {
            super(probe, expTypeAndPlate);
        }

        @Override
        public String getKey1Name()
        {
            return "probe";
        }

        @Override
        public String getKey2Name()
        {
            return "expType_plate";
        }
    }
}


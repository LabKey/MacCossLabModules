package org.labkey.lincs;

import java.util.ArrayList;
import java.util.Collections;
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
    private Map<String, GctTable<ProbePlate>> _multiValueProbeAnnotations;

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
        Collections.sort(probeNames);
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

    public void addAreaRatio(String probe, String replicate, String ratio)
    {
        _areaRatios.addValue(new ProbeReplicate(probe, replicate), ratio);
    }

    public void addMultiValueProbeAnnotation(String annotationName, ProbePlate key, String value)
    {
        if(_multiValueProbeAnnotations == null)
        {
            _multiValueProbeAnnotations = new HashMap<>();
        }
        GctTable<ProbePlate> probePlateValues = _multiValueProbeAnnotations.get(annotationName);
        if(probePlateValues == null)
        {
            probePlateValues = new GctTable<>();
            _multiValueProbeAnnotations.put(annotationName, probePlateValues);
        }
        probePlateValues.addValue(key, value);
    }

    public Gct.GctTable<Gct.ProbePlate>  getMultiValueProbeAnnotation(String annotationName)
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

        public GctKey(String probe, String replicate)
        {
            _key1 = probe;
            _key2 = replicate;
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
    }

    public interface GctKeyBuilder <T extends GctKey>
    {
        public T build(String key1, String key2);
    }

    public static class ProbePlateKeyBuilder implements GctKeyBuilder<ProbePlate>
    {
        @Override
        public ProbePlate build(String key1, String key2)
        {
            return new ProbePlate(key1, key2);
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
        private Map<T, String> _tableValues;
        private List<String> _sortedKey2;

        public GctTable()
        {
            _tableValues = new HashMap<>();
        }

        public void addValue(T key, String value)
        {
            if(_tableValues.containsKey(key))
            {
                throw new GctFileException("Value has already been added for " + key.getKey1Name() + " " + key.getKey1() +
                                           " and " + key.getKey2Name() + " " + key.getKey2());
            }
            _tableValues.put(key, value);
        }

        public String getValue(T key)
        {
            String value = _tableValues.get(key);
            return value == null ? "NA" : value;
        }

        public Set<T> getKeys()
        {
            return _tableValues.keySet();
        }

        public String getSortedValuesForKey1(String key1, GctKeyBuilder<T> gctKeyBuilder)
        {
            if(_sortedKey2 == null)
            {
                Set<String> uniqKey2Set = new HashSet<>();
                for(T key: _tableValues.keySet())
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

    public static class ProbePlate extends GctKey
    {
        public ProbePlate(String probe, String plate)
        {
            super(probe, plate);
        }

        @Override
        public String getKey1Name()
        {
            return "probe";
        }

        @Override
        public String getKey2Name()
        {
            return "plate";
        }
    }
}


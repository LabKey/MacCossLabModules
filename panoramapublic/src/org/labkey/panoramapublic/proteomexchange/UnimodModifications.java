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
package org.labkey.panoramapublic.proteomexchange;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.panoramapublic.proteomexchange.UnimodParser.Position;
import static org.labkey.panoramapublic.proteomexchange.UnimodParser.Specificity;
import static org.labkey.panoramapublic.proteomexchange.UnimodParser.Terminus;

public class UnimodModifications
{
    private final List<UnimodModification> _modifications;
    private final Map<Integer, Integer> _modIdModIdxMap; // Index in the _modifications array of a Unimod Id
    private final Map<String, List<Integer>> _formulaModIdxMap; // Indices in the _modifications array for modifications with a given formula
    private final Map<String, List<Integer>> _isotopicDiffFormulaModIdxMap;
    private final Map<String, Integer> _modNameModIdxMap; // Index in the _modifications array for modifications with a given name
    private final Exception _error;

    private final Map<Character, Map<String, Integer>> _aminoAcids;

    public UnimodModifications(Exception error)
    {
        _modifications = new ArrayList<>();
        _modIdModIdxMap = new HashMap<>();
        _formulaModIdxMap = new HashMap<>();
        _isotopicDiffFormulaModIdxMap = new HashMap<>();
        _modNameModIdxMap = new HashMap<>();
        _aminoAcids = new HashMap<>();
        _error = error;
    }

    public UnimodModifications()
    {
        this(null);
    }

    public List<UnimodModification> getModifications()
    {
        return Collections.unmodifiableList(_modifications);
    }

    public List<UnimodModification> getStructuralModifications()
    {
        return _modifications.stream().filter(UnimodModification::isStructural).toList();
    }

    public boolean hasModifications()
    {
        return _modifications.size() > 0;
    }

    public boolean hasParseError()
    {
        return _error != null;
    }

    public Exception getParseError()
    {
        return _error;
    }

    public void add(UnimodModification uMod) throws PxException
    {
        _modifications.add(uMod);
        Integer idx = _modIdModIdxMap.get(uMod.getId());
        if(idx != null)
        {
            throw new PxException("Multiple modifications with UNIMOD ID " + uMod.getId());
        }
        _modIdModIdxMap.put(uMod.getId(), (_modifications.size() - 1));

        List<Integer> indexes = _formulaModIdxMap.computeIfAbsent(uMod.getNormalizedFormula(), k -> new ArrayList<>());
        indexes.add(_modifications.size() - 1);

        _modNameModIdxMap.put(uMod.getName(), _modifications.size() - 1);
        if (uMod.getDiffIsotopicFormula() != null)
        {
            List<Integer> diffIsotopeModIndexes = _isotopicDiffFormulaModIdxMap.computeIfAbsent(uMod.getDiffIsotopicFormula().getFormula(), k -> new ArrayList<>());
            diffIsotopeModIndexes.add(_modifications.size() - 1);
        }
    }

    public void updateDiffIsotopeMod(@NotNull UnimodModification uMod, @NotNull Formula diffFormula, @NotNull UnimodModification parentStructuralMod)
    {
        uMod.setDiffIsotopicFormulaAndParent(diffFormula, parentStructuralMod);
        List<Integer> diffIsotopeModIndexes = _isotopicDiffFormulaModIdxMap.computeIfAbsent(diffFormula.getFormula(), k -> new ArrayList<>());
        diffIsotopeModIndexes.add(_modIdModIdxMap.get(uMod.getId()));
    }

    public void addAminoAcid(char aa, Map<String, Integer> composition) throws PxException
    {
        if(_aminoAcids.containsKey(aa))
        {
            throw new PxException("Entry for amino acid " + aa + " already exists.");
        }

        _aminoAcids.put(aa, composition);
    }

    public @Nullable UnimodModification getById(int unimodId)
    {
        Integer idx = _modIdModIdxMap.get(unimodId);
        if(idx != null)
        {
            return _modifications.get(idx);
        }
        return null;
    }

    public @Nullable UnimodModification getByName(String name)
    {
        Integer idx = _modNameModIdxMap.get(name);
        return idx != null ? _modifications.get(idx) : null;
    }

    public List<UnimodModification> getByFormula(String formula)
    {
        return getFromMap(formula, _formulaModIdxMap);
    }

    public List<UnimodModification> getByIsotopeDiffFormula(String formula)
    {
        return getFromMap(formula, _isotopicDiffFormulaModIdxMap);
    }

    public List<UnimodModification> getFromMap(String formula, Map<String, List<Integer>> map)
    {
        List<Integer> indexes = map.get(formula);
        if(indexes == null)
        {
            return Collections.emptyList();
        }
        List<UnimodModification> list = new ArrayList<>();
        for(Integer idx: indexes)
        {
            list.add(_modifications.get(idx));
        }
        return list;
    }

    List<UnimodModification> getMatches(String normalizedFormula, String[] sites, Terminus terminus, boolean structural)
    {
        var matches = getUnimodMatches(normalizedFormula, sites, terminus, structural, getByFormula(normalizedFormula));
        if (!structural)
        {
            var isotopeDiffMatches = getUnimodMatches(normalizedFormula, sites, terminus, false, getByIsotopeDiffFormula(normalizedFormula));
            matches.addAll(isotopeDiffMatches);
        }
        return matches;
    }

    @NotNull
    private List<UnimodModification> getUnimodMatches(String normalizedFormula, String[] sites, Terminus terminus, boolean structural, List<UnimodModification> uModCandidates)
    {
        List<UnimodModification> matches = new ArrayList<>();
        for(UnimodModification uMod: uModCandidates)
        {
            if (structural && !uMod.isStructural())
            {
                continue;
            }
            else if (!structural && !uMod.isIsotopic())
            {
                continue;
            }

            Set<Specificity> specificities = new HashSet<>();
            for (String site: sites)
            {
                // If a terminus is given, the modification occurs on the specified amino acids at the specified terminus.
                // If no sites are given then the modification can occur on any amino acid at the specified terminus.
                Position pos = Terminus.N == terminus ? Position.AnyNterm : Terminus.C == terminus ? Position.AnyCterm : Position.Anywhere;
                specificities.add(new Specificity(site, pos));
            }
            if(uMod.matches(normalizedFormula, specificities, terminus))
            {
                matches.add(uMod);
            }
        }
        return matches;
    }

    public Map<String, Integer> getAminoAcidComposition(char aminoAcid)
    {
        return _aminoAcids.get(aminoAcid);
    }

    public List<Character> getAminoAcids()
    {
        return _aminoAcids.keySet().stream().toList();
    }
}

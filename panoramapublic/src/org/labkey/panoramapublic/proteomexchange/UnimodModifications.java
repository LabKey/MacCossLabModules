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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UnimodModifications
{
    private List<UnimodModification> _modifications;
    private Map<Integer, Integer> _modIdModIdxMap;
    private Map<String, List<Integer>> _formulaModIdxMap;

    private Map<Character, Map<String, Integer>> _aminoAcids;

    public UnimodModifications()
    {
        _modifications = new ArrayList<>();
        _modIdModIdxMap = new HashMap<>();
        _formulaModIdxMap = new HashMap<>();
        _aminoAcids = new HashMap<>();
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

        List<Integer> indexes = _formulaModIdxMap.get(uMod.getNormalizedFormula());
        if(indexes == null)
        {
            indexes = new ArrayList<>();
            _formulaModIdxMap.put(uMod.getNormalizedFormula(), indexes);
        }
        indexes.add(_modifications.size() - 1);
    }

    public void addAminoAcid(char aa, Map<String, Integer> composition) throws PxException
    {
        if(_aminoAcids.containsKey(aa))
        {
            throw new PxException("Entry for amino acid " + aa + " already exists.");
        }

        _aminoAcids.put(aa, composition);
    }

    public UnimodModification getById(int unimodId)
    {
        Integer idx = _modIdModIdxMap.get(unimodId);
        if(idx != null)
        {
            return _modifications.get(idx);
        }
        return null;
    }

    public List<UnimodModification> getByFormula(String formula)
    {
        List<Integer> indexes = _formulaModIdxMap.get(formula);
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

    public UnimodModification getMatch(String normalizedFormula, String[] sites, boolean structural)
    {
        List<UnimodModification> uMods = getByFormula(normalizedFormula);
        for(UnimodModification uMod: uMods)
        {
            if (structural && !uMod.isStructural())
            {
                continue;
            }
            else if (!structural && !uMod.isIsotopic())
            {
                continue;
            }

            if(uMod.matches(normalizedFormula, sites, structural))
            {
                return uMod;
            }
        }
        return null;
    }

    public String buildIsotopicModFormula(char aminoAcid, boolean label2h, boolean label13c, boolean label15n, boolean label18o)
    {
        StringBuilder formulaPos = new StringBuilder();
        StringBuilder formulaNeg = new StringBuilder(" - ");
        Map<String, Integer> composition = _aminoAcids.get(aminoAcid);
        if(composition != null)
        {
            for(String element: composition.keySet())
            {
                int count = composition.get(element);
                if(element.equals("H") && label2h)
                {
                    addElementAndCount(element, count, formulaPos, formulaNeg);
                }
                else if(element.equals("C") && label13c)
                {
                    addElementAndCount(element, count, formulaPos, formulaNeg);
                }
                else if(element.equals("N") && label15n)
                {
                    addElementAndCount(element, count, formulaPos, formulaNeg);
                }
                else if(element.equals("O") && label18o)
                {
                    addElementAndCount(element, count, formulaPos, formulaNeg);
                }
            }
        }
        return UnimodModification.normalizeFormula(formulaPos.append(formulaNeg.length() > 3 ? formulaNeg : "").toString());
    }

    private void addElementAndCount(String element, int count, StringBuilder formulaPos, StringBuilder formulaNeg)
    {
        formulaPos.append(element).append("'").append(count);
        formulaNeg.append(element).append(count);
    }
}

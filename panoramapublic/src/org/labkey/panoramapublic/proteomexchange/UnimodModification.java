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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.panoramapublic.proteomexchange.UnimodParser.*;

public class UnimodModification
{
    private final int _id;
    private final String _name;
    private final String _normFormula;
    private final Set<Specificity> _modSites;
    private TermSpecificity _nTerm;
    private TermSpecificity _cTerm;
    private boolean _isIsotopic;

    public UnimodModification(int id, String name, String normalizedFormula)
    {
        _id = id;
        _name = name;
        _normFormula = normalizedFormula;
        _modSites = new HashSet<>();
    }

    public int getId()
    {
        return _id;
    }

    public String getName()
    {
        return _name;
    }

    public String getNormalizedFormula()
    {
        return _normFormula;
    }

    public void setNterm(@NotNull Position position)
    {
        if (_nTerm != null)
        {
            // Some Unimod modifications have terminus specificity on both Any N-term and Protein N-term. Keep the less restrictive one.
            position = position.ordinal() < _nTerm.getPosition().ordinal() ? position : _nTerm.getPosition();
        }
        _nTerm = new TermSpecificity(Terminus.N, position);
    }

    public void setCterm(@NotNull Position position)
    {
        if (_cTerm != null)
        {
            // Some Unimod modifications have terminus specificity on both Any C-term and Protein C-term. Keep the less restrictive one.
            // Example: Unimod:2, Amidation https://www.unimod.org/modifications_view.php?editid1=2
            position = position.ordinal() < _cTerm.getPosition().ordinal() ? position : _cTerm.getPosition();
        }
        _cTerm = new TermSpecificity(Terminus.C, position);
    }

    public void addSite(@NotNull String site, @NotNull Position position)
    {
        _modSites.add(new Specificity(site, position));
    }

    public boolean isStructural()
    {
        return !_isIsotopic;
    }

    public boolean isIsotopic()
    {
        return _isIsotopic;
    }

    public void setIsotopic(boolean isotopic)
    {
        _isIsotopic = isotopic;
    }

    /**
     * @param normFormula normalized formula for the modification {@link UnimodModification#normalizeFormula(String)}
     * @param sites sites (amino acids + terminus) where this modification occurs
     * @param terminus terminus (N-term / C-term) where this modification occurs if no sites are specified.
     * @return true if the given normalized formula matches this Unimod modification's composition, and the given sites are in the
     * allowed sites for this modification. If no sites are given then the given terminus must match. If both the given
     * sites and terminus are null or empty then return false.
     */
    public boolean matches(String normFormula, @NotNull Set<Specificity> sites, Terminus terminus)
    {
        if(!formulaMatches(normFormula))
        {
            return false;
        }
        if (sites.size() == 0 && terminus == null)
        {
            // Cannot find an exact match based on just the formula
            return false;
        }
        if (sites.size() > 0)
        {
            for (Specificity site: sites)
            {
                // If this Unimod modification has a C/N-term position restriction then we need to match that, but if the Skyline definition
                // has a terminus restriction we can ignore it.
                // Example Skyline modification: Label:13C(6)15N(4) (C-term R) has a C-term restriction.
                // This one should match Unimod:267, Label:13C(6)15N(4).
                // In Unimod, however, the specificity on 'R' does not have a position restriction on the C-term.
                // https://www.unimod.org/modifications_view.php?editid1=267
                if (!_modSites.contains(site)  // Try match with the given site + terminus definition
                        && !_modSites.contains(new Specificity(site.getSite(), Position.Anywhere))) // Try match with no terminus restriction
                {
                    return false;
                }
            }
            return true;
        }
        else
        {
            // If there are no amino acid sites given, match on the terminus
            TermSpecificity termSpecificity = Terminus.N == terminus ? _nTerm : Terminus.C == terminus ? _cTerm : null;
            return termSpecificity != null
                    // Do not match if the position for the term specificity for this Unimod modification is Protein C-term or Protein N-term.
                    // In Skyline we cannot define a modification on Protein C/N-term.
                    // Some Unimod modifications have terminus specificity on both Any *-term and Protein *-term. We keep the less restrictive one.
                    /**{@link UnimodModification#setNterm(Position)} and {@link UnimodModification#setCterm(Position)} */
                    && termSpecificity.getPosition().isAnywhere();
        }
    }

    public boolean formulaMatches(String normFormula)
    {
        return _normFormula.equals(normFormula);
    }

    public static String normalizeFormula(String formula)
    {
        if(StringUtils.isBlank(formula))
        {
            return formula;
        }

        // Assume formulas are of the form H'6C'8N'4 - H2C6N4.
        // The part of the formula following ' - ' are the element masses that will be subtracted
        // from the total mass.  Only one negative part is allowed. We will parse the positive and negative parts separately.
        String[] parts = formula.split("-");
        if(parts.length > 2)
        {
            throw new IllegalArgumentException("Formula inconsistent with required form: " + formula);
        }

        Map<String, Integer> composition = getComposition(parts[0]);
        if(parts.length > 1)
        {
            Map<String, Integer> negComposition = getComposition(parts[1]);
            for(String element: negComposition.keySet())
            {
                int posCount = composition.get(element) == null ? 0 : composition.get(element);
                int totalCount = posCount - negComposition.get(element);
                if(totalCount != 0)
                {
                    composition.put(element, totalCount);
                }
                else
                {
                    composition.remove(element);
                }
            }
        }

        List<String> sortedElements = new ArrayList<>(composition.keySet());
        Collections.sort(sortedElements);
        StringBuilder posForm = new StringBuilder();
        StringBuilder negForm = new StringBuilder();
        for(String element: sortedElements)
        {
            Integer count = composition.get(element);
            if(count > 0)
            {
                posForm.append(element).append(composition.get(element));
            }
            else
            {
                negForm.append(element).append(-(composition.get(element)));
            }
        }
        String totalFormula = posForm.toString();
        if(negForm.length() > 0)
        {
            totalFormula = totalFormula + (totalFormula.length() > 0 ? " - " : "-") + negForm.toString();
        }
        return totalFormula;
    }

    private static Map<String, Integer> getComposition(String formula)
    {
        Map<String, Integer> composition = new HashMap<>();

        String currElem = null;
        Integer currCount = null;
        char[] chars = formula.toCharArray();
        for (char c : chars)
        {
            if (Character.isDigit(c))
            {
                currCount = ((currCount == null ? 0 : currCount) * 10 + (c - '0'));
            }
            else if (Character.isUpperCase(c))
            {
                if (currElem != null)
                {
                    updateElementCount(composition, currElem, currCount);
                }
                currElem = "" + c;
                currCount = null;
            }
            else if (!Character.isWhitespace(c)) // e.g. Na, C'
            {
                currElem += c;
            }
        }

        // last one
        if(currElem != null)
        {
            updateElementCount(composition, currElem, currCount);
        }

        return composition;
    }

    private static void updateElementCount(Map<String, Integer> composition, String currElem, Integer currCount)
    {
        int oldCount = composition.get(currElem) == null ? 0 : composition.get(currElem);
        Integer newCount = oldCount + (currCount == null ? 1 : currCount);
        if(newCount == 0)
        {
            composition.remove(currElem);
        }
        else
        {
            composition.put(currElem, newCount);
        }
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("UNIMOD:").append(getId());
        sb.append(", ").append(getName());
        sb.append(", ").append(getNormalizedFormula());
        if(_modSites.size() > 0)
        {
            sb.append(", Sites: ").append(StringUtils.join(_modSites, ":"));
        }
        if(_cTerm != null)
        {
            sb.append(", C-term");
        }
        if(_nTerm != null)
        {
            sb.append(", N-term");
        }
        sb.append(", Isotopic: " + _isIsotopic);
        return sb.toString();
    }

    public String getModSites()
    {
        if(_modSites.size() > 0)
        {
            return StringUtils.join(_modSites.stream().map(s -> s.getSite()).collect(Collectors.toSet()), ":");
        }
        return "";
    }
}

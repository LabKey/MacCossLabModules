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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.Link;
import org.w3c.dom.NodeList;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.panoramapublic.proteomexchange.ChemElement.HEAVY_LABELS;
import static org.labkey.panoramapublic.proteomexchange.UnimodParser.Position;
import static org.labkey.panoramapublic.proteomexchange.UnimodParser.Specificity;
import static org.labkey.panoramapublic.proteomexchange.UnimodParser.TermSpecificity;
import static org.labkey.panoramapublic.proteomexchange.UnimodParser.Terminus;

public class UnimodModification
{
    private final int _id;
    private final String _name;
    private final Formula _formula;
    private final Set<Specificity> _modSites;
    private TermSpecificity _nTerm;
    private TermSpecificity _cTerm;
    private final boolean _isIsotopic;
    private boolean _isTrueIsotopic;
    private Formula _diffFormula;
    private UnimodModification _parentStructuralMod;


    public UnimodModification(int id, String name, Formula formula)
    {
        _id = id;
        _name = name;
        _formula = formula;
        _modSites = new HashSet<>();
        _isIsotopic = _formula.getElementCounts().keySet().stream().anyMatch(el -> HEAVY_LABELS.containsKey(el));
    }

    public int getId()
    {
        return _id;
    }

    public String getName()
    {
        return _name;
    }

    public Formula getFormula()
    {
        return _formula;
    }

    public String getNormalizedFormula()
    {
        return _formula.getFormula();
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

    /**
     * @return true if the modification formula changes the elemental composition of the residue or terminus.
     * This will include modifications such as
     * Methyl:2H(3); Unimod:298; Composition: H(-1) 2H(3) C
     * and 	Dimethyl:2H(6); Unimod:1291; Composition: H(-2) 2H(6) C(2)
     * These are isotope labels that are also chemical modifications.
     * This is based on the PanoramaWeb support board request: https://panoramaweb.org/home/support/announcements-thread.view?rowId=4105
     * The user added Methyl:2H(3) and Dimethyl:2H(6) as structural modifications rather than isotopic because he needed them to be variable.
     * Data is available on Panorama Public at: https://panoramaweb.org/PRMT6motif.url
     */
    public boolean isStructural()
    {
        return !isTrueIsotopic();
    }

    /**
     * @return true if the element composition contains at least one heavy element.
     */
    public boolean isIsotopic()
    {
        return _isIsotopic;
    }

    /**
     * @return true if the formula has heavy labeled elements, and the labels do not change the elemental composition of
     * the residue or terminus. Example: C(-6)13C(6). This is determined by {@link UnimodParser#checkTrueIsotopeMod(NodeList)}.
     */
    @SuppressWarnings("JavadocReference")
    public boolean isTrueIsotopic()
    {
        return _isTrueIsotopic;
    }

    public void setTrueIsotopic(boolean trueIsotopic)
    {
        _isTrueIsotopic = trueIsotopic;
    }

    // https://www.unimod.org/names.html
    public boolean isIsotopeLabel()
    {
        return _name.contains(":") && !_name.startsWith("Delta:");
    }

    public @Nullable String getIsotopeLabelName()
    {
        return isIsotopeLabel() ? _name.substring(0, _name.indexOf(":")) : null;
    }

    public @Nullable Formula getDiffIsotopicFormula()
    {
        return _diffFormula;
    }

    public void setDiffIsotopicFormulaAndParent(@NotNull Formula diffFormula, @NotNull UnimodModification parentStructuralMod)
    {
        _diffFormula = diffFormula;
        _parentStructuralMod = parentStructuralMod;
    }

    public UnimodModification getParentStructuralMod()
    {
        return _parentStructuralMod;
    }

    public String getParentStructuralModFormula()
    {
        return _parentStructuralMod != null ? _parentStructuralMod.getFormula().getFormula() : "";
    }

    /**
     * @param normFormula normalized formula for the modification {@link Formula#normalizeFormula(String)}
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

    private boolean formulaMatches(String normFormula)
    {
        return _formula.getFormula().equals(normFormula) || (isIsotopic() && _diffFormula != null && _diffFormula.getFormula().equals(normFormula));
    }

    public static Formula getCombinedFormula(UnimodModification mod1, UnimodModification mod2)
    {
        return mod1.getFormula().addFormula(mod2.getFormula());
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
        sb.append(", Isotopic: ").append(isIsotopic());
        sb.append(", Structural: ").append(isStructural());

        if (_diffFormula != null)
        {
            sb.append(", Diff isotope formula: ").append(_diffFormula.getFormula())
                    .append("; Parent mod: ").append(_parentStructuralMod.getName()).append(" Unimod:").append(_parentStructuralMod.getId());
        }
        return sb.toString();
    }

    public TermSpecificity getNterm()
    {
        return _nTerm;
    }

    public TermSpecificity getcTerm()
    {
        return _cTerm;
    }

    public String getModSitesWithPosition()
    {
        if(_modSites.size() > 0)
        {
            return StringUtils.join(_modSites.stream().map(Specificity::toString).collect(Collectors.toSet()), ":");
        }
        return "";
    }

    public Set<Specificity> getModSpecificities()
    {
        return Collections.unmodifiableSet(_modSites);
    }

    public String getTerminus()
    {
        String terminus = "";
        if (_nTerm != null)
        {
            terminus += _nTerm.toString();
        }
        if (_cTerm != null)
        {
            terminus = terminus + (terminus.length() > 0 ? ", " : "") + _cTerm;
        }
        return terminus;
    }

    public Link getLink()
    {
       return getLink(_id);
    }

    public static Link getLink(int unimodId)
    {
        return getLink(unimodId, false);
    }

    public static Link getLink(int unimodId, boolean clearClasses)
    {
        var link = new Link.LinkBuilder("UNIMOD:" + unimodId)
                .href("https://www.unimod.org/modifications_view.php?editid1=" + unimodId)
                .target("_blank")
                .rel("noopener noreferrer");
        if (clearClasses)
        {
            link = link.clearClasses();
        }
        return link.build();
    }
}

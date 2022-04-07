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
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.targetedms.IModification;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.query.ExperimentAnnotationsManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.panoramapublic.proteomexchange.UnimodParser.Terminus;

public class ExperimentModificationGetter
{
    private static final Logger LOG = LogHelper.getLogger(ExperimentModificationGetter.class, "Looks up the structural and isotopic modifications for documents associated with an experiment");

    /**
     * @return a list of modifications for the runs in the given experiment.  If the Skyline modifications did not have a Unimod Id,
     * an attempt is made to infer the Unimod Id based on the modification formula, modified sites and terminus.
     */
    public static List<PxModification> getModifications(ExperimentAnnotations expAnnot, boolean lookupUnimod)
    {
        List<ITargetedMSRun> runs = ExperimentAnnotationsManager.getTargetedMSRuns(expAnnot);

        Map<Long, PxModification> strModMap = new HashMap<>();
        Map<Long, PxModification> isoModMap = new HashMap<>();

        UnimodModifications uMods = UnimodUtil.getUnimod(); // Read the UNIMOD modifications

        for(ITargetedMSRun run: runs)
        {
            List<? extends IModification.IStructuralModification> smods = TargetedMSService.get().getStructuralModificationsUsedInRun(run.getId());
            for(IModification.IStructuralModification mod: smods)
            {
                PxModification pxMod = strModMap.get(mod.getId());
                if(pxMod == null)
                {
                    pxMod = getStructuralUnimodMod(mod, uMods, lookupUnimod);
                    strModMap.put(mod.getId(), pxMod);
                }
                pxMod.addSkylineDoc(run);
            }

            List<? extends IModification.IIsotopeModification> iMods = TargetedMSService.get().getIsotopeModificationsUsedInRun(run.getId());
            for(IModification.IIsotopeModification mod: iMods)
            {
                PxModification pxMod = isoModMap.get(mod.getId());
                if(pxMod == null)
                {
                    pxMod = getIsotopicUnimodMod(mod, uMods, lookupUnimod);
                    isoModMap.put(mod.getId(), pxMod);
                }
                pxMod.addSkylineDoc(run);
            }
        }

        List<PxModification> allMods = new ArrayList<>();
        allMods.addAll(strModMap.values());
        allMods.addAll(isoModMap.values());
        return allMods;
    }

    private static @NotNull String[] modSites(IModification mod)
    {
        if(StringUtils.isBlank(mod.getAminoAcid()))
        {
            return new String[0];
        }

        return mod.getAminoAcid().replaceAll("\\s", "").split(",");
    }

    public static PxModification getStructuralUnimodMod(IModification mod, UnimodModifications uMods)
    {
        return getStructuralUnimodMod(mod, uMods, true);
    }

    public static PxModification getStructuralUnimodMod(IModification mod, UnimodModifications uMods, boolean lookupUnimod)
    {
        if(mod.getUnimodId() != null)
        {
            UnimodModification uMod = uMods.getById(mod.getUnimodId());
            return uMod != null ? new PxStructuralMod(mod.getName(), mod.getId(), uMod, false) : new PxStructuralMod(mod.getName(), mod.getId());
        }
        else
        {
            PxStructuralMod pxMod = new PxStructuralMod(mod.getName(), mod.getId());
            String normFormula = Formula.normalizeIfValid(mod.getFormula());
            if(normFormula != null && lookupUnimod)
            {
                addMatches(mod, uMods, pxMod, normFormula, true);
            }
            return pxMod;
        }
    }

    private static void addMatches(IModification mod, UnimodModifications uMods, PxModification pxMod, String normFormula, boolean structural)
    {
        String[] sites = modSites(mod);
        Terminus term = "C".equalsIgnoreCase(mod.getTerminus()) ? Terminus.C : "N".equalsIgnoreCase(mod.getTerminus()) ? Terminus.N : null;
        // Find possible matches based on formula and modification sites (aa or term)
        List<UnimodModification> uModList = uMods.getMatches(normFormula, sites, term, structural);
        if ((sites.length > 0 || term != null) && uModList.size() == 1)
        {
            // If there was only one match for the modification formula and modification sites / terminus then assume
            // that this is the right match.
            pxMod.setUnimodMatch(uModList.get(0), true);
        }
        else
        {
            uModList.forEach(pxMod::addPossibleUnimod);
        }
    }

    public static PxModification getIsotopicUnimodMod(IModification.IIsotopeModification mod, UnimodModifications uMods)
    {
        return getIsotopicUnimodMod(mod, uMods, true);
    }

    public static PxModification getIsotopicUnimodMod(IModification.IIsotopeModification mod, UnimodModifications uMods, boolean lookupUnimod)
    {
        if(mod.getUnimodId() != null)
        {
            UnimodModification uMod = uMods.getById(mod.getUnimodId());
            return uMod != null ? new PxIsotopicMod(mod.getName(), mod.getId(), uMod, false) : new PxIsotopicMod(mod.getName(), mod.getId());
        }
        else
        {
            String normFormula = null;
            if(StringUtils.isBlank(mod.getFormula()))
            {
                try
                {
                    var formula = buildIsotopeModFormula(mod);
                    normFormula = formula != null ? formula.getFormula() : null;
                }
                catch (PxException e)
                {
                    LOG.error("Error building formula for isotopic mod (Id: " + mod.getId() + ", " + mod.getName() + ")", e);
                }
            }
            else
            {
                normFormula = Formula.normalizeIfValid(mod.getFormula());
            }
            PxIsotopicMod pxMod = new PxIsotopicMod(mod.getName(), mod.getId());
            if(normFormula != null && lookupUnimod)
            {
                addMatches(mod, uMods, pxMod, normFormula, false);
            }
            return pxMod;
        }
    }

    private static Formula buildIsotopeModFormula(IModification.IIsotopeModification mod) throws PxException
    {
        String aminoAcids = mod.getAminoAcid();
        if (StringUtils.isBlank(aminoAcids))
        {
            return null;
        }

        // On PanoramaWeb we do not have any isotopic modifications with multiple amino acids as targets.  But Skyline allows it
        String[] sites = modSites(mod);
        Formula formula = null;
        for (String site : sites)
        {
            Formula f = UnimodUtil.buildIsotopicModFormula(site.charAt(0),
                    Boolean.TRUE.equals(mod.getLabel2H()),
                    Boolean.TRUE.equals(mod.getLabel13C()),
                    Boolean.TRUE.equals(mod.getLabel15N()),
                    Boolean.TRUE.equals(mod.getLabel18O()));
            if (formula == null)
            {
                formula = f;
            }
            else if (!formula.getFormula().equals(f.getFormula()))
            {
                throw new PxException("Multiple amino acids found for isotopic modification (" + mod.getName() + "). Formulae do not match.");
            }
        }
        return formula;
    }

    public static abstract class PxModification
    {
        private final String _skylineName;
        private final Set<Long> _runIds;
        private final long _dbModId; // database id from the IsotopeModification table if _isotopicMod is true, StructuralModification otherwise
        private final boolean _isotopicMod;
        private UnimodModification _match;
        private boolean _matchInferred;
        private final List<UnimodModification> _unimodModifications; // List of possible Unimod modifications

        PxModification(String skylineName, boolean isIsotopic, long dbModId)
        {
            _skylineName = skylineName;

            _runIds = new HashSet<>();
            _isotopicMod = isIsotopic;
            _dbModId = dbModId;

            _unimodModifications = new ArrayList<>();
        }

        public void addSkylineDoc(ITargetedMSRun run)
        {
            if(run != null)
            {
                _runIds.add(run.getId());
            }
        }

        public Set<Long> getRunIds()
        {
            return _runIds;
        }

        public String getName()
        {
            return _match != null ? _match.getName() : null;
        }

        public String getSkylineName()
        {
            return _skylineName;
        }

        public long getDbModId()
        {
            return _dbModId;
        }

        public String getUnimodId()
        {
            return _match == null ? null : "UNIMOD:" + _match.getId();
        }

        public Integer getUnimodIdInt()
        {
            return _match == null ? null : _match.getId();
        }

        public boolean hasUnimodId()
        {
            return _match != null;
        }

        public UnimodModification getUnimodMatch()
        {
            return _match;
        }

        public boolean isIsotopicMod()
        {
            return _isotopicMod;
        }

        public void setUnimodMatch(UnimodModification uMod, boolean inferred)
        {
            _match = uMod;
            _matchInferred = inferred;
        }

        public boolean isMatchInferred()
        {
            return _matchInferred;
        }

        public void addPossibleUnimod(UnimodModification uMod)
        {
            _unimodModifications.add(uMod);
        }

        public boolean hasPossibleUnimods()
        {
            return _unimodModifications.size() > 0;
        }

        public @NotNull List<UnimodModification> getPossibleUnimodMatches()
        {
            return Collections.unmodifiableList(_unimodModifications);
        }
    }

    public static class PxStructuralMod extends PxModification
    {
        public PxStructuralMod(String skylineName, long dbModId)
        {
            super(skylineName, false, dbModId);
        }

        public PxStructuralMod(String skylineName, long dbModId, UnimodModification uMod, boolean inferred)
        {
            super(skylineName, false, dbModId);
            if (uMod != null) setUnimodMatch(uMod, inferred);
        }
    }

    public static class PxIsotopicMod extends PxModification
    {
        public PxIsotopicMod(String skylineName, long dbModId)
        {
            super(skylineName, true, dbModId);
        }

        public PxIsotopicMod(String skylineName, long dbModId, UnimodModification uMod, boolean inferred)
        {
            super(skylineName, true, dbModId);
            if (uMod != null) setUnimodMatch(uMod, inferred);
        }
    }

    public static class TestCase extends Assert
    {
        private static final boolean debug = false;

        @Test
        public void testStructuralMods() throws IOException
        {
            File unimodXml = getUnimodFile();
            UnimodModifications uMods = null;
            try
            {
                uMods = new UnimodParser().parse(unimodXml);
            }
            catch (Exception e)
            {
                fail("Failed to parse UNIMOD modifications. " + e.getMessage());
            }

            assertNotNull(uMods);

            List<Modification> mods = new ArrayList<>();
            Map<String, List<UnimodModification>> unimodMatches = new HashMap<>();
            int idx = 0;

            // Some modifications in PanoramaWeb that do not have a UNIMOD ID.
            // Oxidations
            var oxidation = uMods.getById(35);
            mods.add(createMod("oxidation (H)", "O", "H", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(oxidation));
            mods.add(createMod("Oxidation (T)", "O", "T", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(oxidation));
            mods.add(createMod("try->monooxidation (W)", "O", "W", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(oxidation));
            mods.add(createMod("Gln Oxidation (Q)", "O", "Q", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(oxidation));
            mods.add(createMod("Ser oxidation", "O", "S", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(oxidation));
            mods.add(createMod("Leu/Ile oxidation", "O", "L, I", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(oxidation));
            mods.add(createMod("Methionine_sulfoxide", "O", "M", null));
            // Comment in Unimod for 'M' specificity: "methionine sulfoxide"
            unimodMatches.put(mods.get(idx++).getName(), List.of(oxidation));
            mods.add(createMod("Glu oxidation (E)", "O", "E", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(oxidation));
            mods.add(createMod("V oxidation", "O", "V", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(oxidation));
            mods.add(createMod("mono-oxidation", "O", "M, W, H, C, F, Y", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(oxidation));

            // Acetylation
            var acetylation = uMods.getById(1);
            mods.add(createMod("acetylation", "C2H2O1", "K", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(acetylation));
            mods.add(createMod("Acetyl-T (N-term)", "C2H2O", "T", "N"));
            unimodMatches.put(mods.get(idx++).getName(), List.of(acetylation));
            mods.add(createMod("Acetyl (K)", "C2 H3 O -H", "K", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(acetylation));
            mods.add(createMod("Acetyl (N-term)", "C2H2O", null, "N"));
            unimodMatches.put(mods.get(idx++).getName(), List.of(acetylation));
            mods.add(createMod("Acetyl-M (N-term)", "C2H2O", "M", "N"));
            // Formula matches UNIMOD:1, Acetyl and UNIMOD:1197, "Ser->Glu" but neither has 'M' as a site specificity option
            unimodMatches.put(mods.get(idx++).getName(), Collections.emptyList());
            mods.add(createMod("Acetyl-A (N-term)", "C2H2O", "A", "N"));
            // Formula matches UNIMOD:1, Acetyl and UNIMOD:1197, "Ser->Glu" but neither has 'A' as a site specificity option
            unimodMatches.put(mods.get(idx++).getName(), Collections.emptyList());
            mods.add(createMod("Acetyl-S (N-term)", "C2H2O", "S", "N"));
            // Formula matches UNIMOD:1, Acetyl and UNIMOD:1197, "Ser->Glu" both have 'S' as a site specificity option
            unimodMatches.put(mods.get(idx++).getName(), List.of(
                    acetylation,
                    uMods.getById(1197) /* Ser->Glu */));


            // Decarboxylation
            // 4 Unimod matches based on formula (https://www.unimod.org/modifications_list.php?a=search&value=1&SearchFor=H%28-2%29+C%28-1%29+O%28-1%29&SearchOption=Equals&SearchField=)
            // Only one had 'D' and 'E' as site specificity options
            var decarboxylation = uMods.getById(1915);
            mods.add(createMod("Asp decarboxylation (D)", "-COHH", "D", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(decarboxylation));
            mods.add(createMod("Glu decarboxylation (E)", "-COHH", "E", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(decarboxylation));


            // Carbonyl
            // Not in UniModData.cs
            // 4 matchs based on formula: https://www.unimod.org/modifications_list.php?a=search&value=1&SearchFor=H%28-2%29+O&SearchOption=Equals&SearchField=
            // Only one has 'R', 'A', 'Q', 'L', 'E' as site specificity options
            var carbonyl = uMods.getById(1918);
            mods.add(createMod("carbonyl (R)", "O-HH", "R", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(carbonyl));
            mods.add(createMod("carbonyl (A)", "O-HH", "A", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(carbonyl));
            mods.add(createMod("Gln Carbonyl (Q)", "O-HH", "Q", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(carbonyl));
            mods.add(createMod("carbonyl (L/I)", "O-HH", "L, I", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(carbonyl));
            mods.add(createMod("Glu carbonyl (E)", "O-HH", "E", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(carbonyl));

            // Nitro
            // Only one match for H(-1) N O(2)
            var nitro = uMods.getById(354);
            mods.add(createMod("NitroY", "NO2 -H", "Y", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(nitro));
            mods.add(createMod("NitroY", "N O2 -H", "Y", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(nitro));

            // Phospho
            var phospho = uMods.getById(21);
            mods.add(createMod("Phospho(S)", "HO3P", "S", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(phospho));
            mods.add(createMod("Phosho (Y)", "HPO3", "Y", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(phospho));
            mods.add(createMod("Phospho (S,T)", "HPO3", "S, T", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(phospho));
            mods.add(createMod("Phospho", "HO3P", "T", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(phospho));

            // Chlorination
            // UnimodData.cs has the formula for UnimodId 936 as just 'Cl':  AAs = "Y", LabelAtoms = LabelAtoms.None, Formula = "Cl", ID = 936,
            // But the Unimod composition for this modification is H(-1) Cl https://www.unimod.org/modifications_view.php?editid1=936
            mods.add(createMod("Chlorination (Y)", "Cl -H", "Y", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(uMods.getById(936) /* "Chlorination" */));

            // Unimod description: Tryptophan oxidation to dihydroxy-N-formaylkynurenine
            // Not in UniModData.cs
            mods.add(createMod("Dihydroxyformylkynurenine (W)", "OOOO", "W", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(uMods.getById(1925) /* "Delta:O(4)" */));

            // Unimod description: Tryptophan oxidation to beta-unsaturated-2,4-bis-tryptophandione
            // Not in UniModData.cs
            mods.add(createMod("unsaturated tryptophandione (W)", "OO-HHHH", "W", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(uMods.getById(1923) /* "Delta:H(-4)O(2)" */));

            // Formula matches 5 Unimod mods: https://www.unimod.org/modifications_list.php?a=search&value=1&SearchFor=H%284%29+C%282%29&SearchOption=Equals&SearchField=composition
            // Only Unimod:36 has K, R and N as site specificity options
            mods.add(createMod("Dimethylation (KRN)", "H4C2", "K, R, N", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(uMods.getById(36) /* "Dimethyl" */));

            // Formula matches 6 Unimod modifications: https://www.unimod.org/modifications_list.php?a=search&value=1&SearchFor=H%282%29+C&SearchOption=Equals&SearchField=composition
            // Only Unimod:34, Methyl has 'E' as a site specificity option
            mods.add(createMod("Methyl-ester (E)", "CH2", "E", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(uMods.getById(34) /* "Methyl" */));

            // Only one match. Description in Unimod: Tryptophan oxidation to hydroxy-bis-tryptophandione
            mods.add(createMod("hydroxy tryptophandione (W)", "OOO-HHHH", "W", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(uMods.getById(1924) /* Delta:H(-4)O(3) */));

            // Formula matches 2 Unimod modifications https://www.unimod.org/modifications_list.php?a=search&value=1&SearchFor=H%285%29+C%283%29+N+O&SearchOption=Equals&SearchField=composition
            // Unimod:24, Propionamide has 'C' as site specificity option
            mods.add(createMod("Propionamide(C)", "H5C3NO", "C", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(uMods.getById(24) /* Propionamide */));

            mods.add(createMod("ICAT-C (C)", "C10H17N3O3", "C", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(uMods.getById(105) /* ICAT-C */));

            // Formula matches 3: https://www.unimod.org/modifications_list.php?a=search&value=1&SearchFor=H%282%29+C%282%29+O%282%29&SearchOption=Equals&SearchField=composition
            // Only Unimod:6, Carboxymethyl has 'C' as the site specificity option
            mods.add(createMod("Carboxymethylcysteine", "CH2COO", "C", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(uMods.getById(6) /* Carboxymethyl */));

            // Formula matches 4: https://www.unimod.org/modifications_list.php?a=search&value=1&SearchFor=H%283%29+C%282%29+N+O&SearchOption=Equals&SearchField=composition
            // Only Unimod:4, Carbamidomethyl has 'C' as the site specificity option
            mods.add(createMod("Carbamidomethyl Cysteine", "C2H3ON", "C", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(uMods.getById(4) /* Carbamidomethyl */));

            // Composition in Unimod is "dHex HexNAc", not H(23) C(14) N O(9)
            mods.add(createMod("HexNAc(1)dHex(1) (N)", "H23C14NO9", "N", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(uMods.getById(142) /* HexNAc(1)dHex(1) */));

            // Formula matches two mods: https://www.unimod.org/modifications_list.php?a=search&value=1&SearchFor=H%2812%29+C%287%29+N%282%29+O&SearchOption=Equals&SearchField=composition
            // Only Unimod:888, mTRAQ has a specificity on N-term (Any N-term)
            // Unimod:1027,Xlink:DMP[140] has a specificity on N-term (Protein N-term) so that will not match
            mods.add(createMod("mTRAQ +0 (N-term)", "H12C7N2O", null, "N"));
            unimodMatches.put(mods.get(idx++).getName(), List.of(uMods.getById(888) /* mTRAQ */));

            mods.add(createMod("Kinome-ATP-K", "C10H16N2O2", "K", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(uMods.getById(1031) /* Biotin:Thermo-88310 */));


            // ---------------------------------------------------------------------------
            // ------- Review these again? -----------------------------------------------
            // User entered an incorrect name? Tryptoline and Thiazolidine are not the same according to Wikipedia.
            // Unimod:1009 exists in UniModData.cs only as Thiazolidine (N-term C). In Unimod however, there is no
            // position restriction on 'C', and there are other specificities
            mods.add(createMod("Tryptoline (W)", "C", "W", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(uMods.getById(1009) /* Thiazolidine */));

            // Matches 3 Unimod modifications: https://www.unimod.org/modifications_list.php?a=search&value=1&SearchFor=H+N+O%28-1%29&SearchOption=Equals&SearchField=composition
            // Unimod:2, Amidation is the only one with C-term specificity, both on "Any C-Term" and "Protein C-term".  This should match.
            // Why is it named "C-term deamidation"? Deamidation has a different formula: H(-1) N(-1) O
            mods.add(createMod("C-term deamidation", "HN-O", null, "C"));
            unimodMatches.put(mods.get(idx++).getName(), List.of(uMods.getById(2) /* Amidated */));

            // More modifications to look at.
            // Not in UniModData.cs
            mods.add(createMod("ring open1 (H)", "O-C2NH", "H", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(uMods.getById(348) /* His->Asn */));
            mods.add(createMod("ring open 2(H)", "OO-C2NNHH", "H", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(uMods.getById(349) /* His->Asp */));
            mods.add(createMod("ring open 4 (H)", "OO-NNCHH", "H", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(uMods.getById(1916) /* Aspartylurea */));
            mods.add(createMod("ring open 3 (H)", "OO-NHC", "H", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(uMods.getById(1917) /* Formylasparagine */));
            // ---------------------------------------------------------------------------



            // Modifications with no matches
            // ---------------------------------------------------------------------------------------------------------
            mods.add(createMod("GlcNAc-Fuc", null, "N", null)); // No formula. We need a formula to find a match
            unimodMatches.put(mods.get(idx++).getName(), Collections.emptyList());
            mods.add(createMod("pyro-glu", null, "Q", "N")); // No formula. We need a formula to find a match
            unimodMatches.put(mods.get(idx++).getName(), Collections.emptyList());
            mods.add(createMod("GlcNAc", null, "N", null)); // No formula. We need a formula to find a match
            unimodMatches.put(mods.get(idx++).getName(), Collections.emptyList());
            mods.add(createMod("Pyroglutamic acid (Q)", null, "Q", "N")); // No formula. We need a formula to find a match
            unimodMatches.put(mods.get(idx++).getName(), Collections.emptyList());
            mods.add(createMod("Ubiquitin", null, "K", null)); // No formula. We need a formula to find a match
            unimodMatches.put(mods.get(idx++).getName(), Collections.emptyList());

            // Could be this https://www.unimod.org/modifications_view.php?editid1=676 but formula is different.
            // Unimod:676 - Trp->Gly substitution, H(-7) C(-9) N(-1), on W.
            // UniModData.cs does not have mod 676
            mods.add(createMod("Try->glycine hydroperoxide (W)", "OO-C9H7N", "W", null));
            unimodMatches.put(mods.get(idx++).getName(), Collections.emptyList());


            // Formula matches composition of Hex, Unimod:42.  But that does not have 'V' as a site specificity
            mods.add(createMod("Glycation(V)", "C6H12O6-H2O", "V", null));
            unimodMatches.put(mods.get(idx++).getName(), Collections.emptyList());

            // Formula matches Unimod:345, Trioxidation. But that only has C, W, Y, F as the site specificity options
            mods.add(createMod("trioxidation (MHWFY)", "OOO", "M, H, W, F, Y", null));
            unimodMatches.put(mods.get(idx++).getName(), Collections.emptyList());

            // Formula matches Unimod mods: https://www.unimod.org/modifications_list.php?a=search&value=1&SearchFor=H%28-2%29+O%28-1%29&SearchOption=Equals&SearchField=composition
            // Only Unimod:23, Dehydration has 'Q' as a site specificity option, but it specifies the position as 'Protein C-term'.
            // Terminus is missing in the modification definition below
            mods.add(createMod("Pyro Glu", "-OH2", "Q", null));
            unimodMatches.put(mods.get(idx++).getName(), Collections.emptyList());

            // No carbonyl on Lysine
            mods.add(createMod("Lys carbonyl", "O-HH", "K", null));
            unimodMatches.put(mods.get(idx++).getName(), Collections.emptyList());

            // Formula matches Unimod:425, Dioxidation. But 'H' is not one of the site specificities
            mods.add(createMod("dioxidation (MHWFY)", "OO", "M, H, W, F, Y", null));
            unimodMatches.put(mods.get(idx++).getName(), Collections.emptyList());



            // Modifications with multiple potential Unimod matches based on formula, modification site and terminus
            // ---------------------------------------------------------------------------------------------------------
            // Both Unimod:888, mTRAQ and Unimod:1027,Xlink:DMP[140] have a 'K' as a site specificity option
            mods.add(createMod("mTRAQ +0 (K)", "H12C7N2O", "K", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(
                    uMods.getById(888) /* mTRAQ */,
                    uMods.getById(1027) /* DMP[140] */));

            // Formula has 5 matches: https://www.unimod.org/modifications_list.php?a=search&value=1&SearchFor=H%282%29+C&SearchOption=Equals&SearchField=composition
            // Both Unimod:34, Methyl and Unimod:558, Asp->Glu have 'D' as a site specificity option
            mods.add(createMod("Methyl-ester (D)", "CH2", "D", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(
                    uMods.getById(34) /* Methyl */,
                    uMods.getById(558) /* Asp->Glu */));

            // Formula has 3 matches: https://www.unimod.org/modifications_list.php?a=search&value=1&SearchFor=H%28-1%29+N%28-1%29+O&SearchOption=Equals&SearchField=composition
            // Both Unimod:7, Deamidated and Unimod:621, Asn->Asp have 'N' as a site specificity option
            mods.add(createMod("deamidate (N)", "O-NH", "N", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(
                    uMods.getById(7) /* Deamidated */,
                    uMods.getById(621) /* Asn->Asp */));

            // Formula has 5 matches: https://www.unimod.org/modifications_list.php?a=search&value=1&SearchFor=H%284%29+C%282%29&SearchOption=Equals&SearchField=composition
            // 3 of them have a specificity on N-term (Any N-term)
            mods.add(createMod("Dimethylation (N-term)", "H4C2", null, "N"));
            unimodMatches.put(mods.get(idx++).getName(), List.of(
                    uMods.getById(36) /* Dimethyl */,
                    uMods.getById(255) /* Delta:H(4)C(2) */,
                    uMods.getById(280) /* Ethyl */));

            // Formula has 2 matches: https://www.unimod.org/modifications_list.php?a=search&value=1&SearchFor=H%284%29+C%283%29+O&SearchOption=Equals&SearchField=composition
            // Both have 'K' as a site specificity option
            mods.add(createMod("Propionylation", "C3H4O1", "K", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(
                    uMods.getById(58) /* Propionyl */,
                    uMods.getById(206) /* Delta:H(4)C(3)O(1) */));
            mods.add(createMod("PropionylNT", "C3H4O1", "", "N"));
            unimodMatches.put(mods.get(idx++).getName(), List.of(
                    uMods.getById(58) /* Propionyl */));



            // Combination modifications
            // ---------------------------------------------------------------------------------------------------------
            // Skyline does not allow multiple modifications on a residue. So users create custom "combination" modifications.
            // Some of them actually match a Unimod modification. We need to provide an interface for users to give us
            // information for combination modifications.

            // This is a combo modification but gets a single match: UNIMOD:209, Delta:H(8)C(6)O(2)
            mods.add(createMod("DoublePropionyl(NTK)", "C6H8O2", "K", "N"));
            // !!! WRONG MATCH
            unimodMatches.put(mods.get(idx++).getName(), List.of(uMods.getById(209) /* Delta:H(8)C(6)O(2) */));

            // Propionylation (H4C3O) + Trimethylation (H6C3)
            mods.add(createMod("PropionylNY_LysMe3", "C6H10O", "K", "N"));
            // !!! WRONG MATCH
            unimodMatches.put(mods.get(idx++).getName(), List.of(uMods.getById(1873) /* MesitylOxide */));
            // Propionylation (H4C3O) + Acetylation (H2C2O)
            mods.add(createMod("PropionylNT_LysAc1", "C5H6O2", "K", "N"));
            unimodMatches.put(mods.get(idx++).getName(), Collections.emptyList());
            // Propionylation (H4C3O) + ?? (H6C4O)
            mods.add(createMod("PropionylNT_LysMe1", "C7O2H10", "K", "N"));
            unimodMatches.put(mods.get(idx++).getName(), Collections.emptyList());
            // Propionylation (H4C3O) + Dimethylation (H4C2)
            mods.add(createMod("PropionylNT_LysMe2", "C5H8O", "K", null));
            unimodMatches.put(mods.get(idx++).getName(), Collections.emptyList());

            // Methylation + Propionylation
            // Formula has two matches: https://www.unimod.org/modifications_list.php?a=search&value=1&SearchFor=H%286%29+C%284%29+O&SearchOption=Equals&SearchField=composition
            // Both have have'K' as a site specificity option
            mods.add(createMod("MethylPropionyl", "C4H6O", "K", null));
            unimodMatches.put(mods.get(idx++).getName(), List.of(
                    uMods.getById(253) /* Crotonaldehyde  */,
                    uMods.getById(1289) /* Butyryl  */));

            // Looks like a combination of Acetly (Unimod:1, H(2) C(2) O) and Phopho (Unimod:21, H O(3) P)
            mods.add(createMod("N-Acetyl-Phospho-T", "C2H3O4P", "T", "N"));
            unimodMatches.put(mods.get(idx++).getName(), Collections.emptyList());

            // Combination of Oxidation and Acetyl
            mods.add(createMod("MetOxid_NtermAcetyl", "C2H2O2", "M", "N"));
            unimodMatches.put(mods.get(idx++).getName(), Collections.emptyList());

            for(Modification mod: mods)
            {
                PxModification pxMod = getStructuralUnimodMod(mod, uMods);

                printStructuralMod(mod, pxMod);

                List<UnimodModification> matches = unimodMatches.get(pxMod.getSkylineName());
                if (matches.size() == 0)
                {
                    assertFalse("Unexpected Unimod match for modification " + pxMod.getSkylineName(), pxMod.hasUnimodId());
                    assertTrue("Unexpected possible mods for modification " + pxMod.getSkylineName(), pxMod.getPossibleUnimodMatches().size() == 0);
                }
                else if (matches.size() == 1)
                {
                    assertTrue("Expected a Unimod Id for modification " + pxMod.getSkylineName(), pxMod.hasUnimodId());
                    assertEquals("Unexpected Unimod match Id for modification " + pxMod.getSkylineName(), matches.get(0).getId(), pxMod.getUnimodIdInt().intValue());
                    assertEquals("Unexpected Unimod match name for modification " + pxMod.getSkylineName(), matches.get(0).getName(), pxMod.getName());
                    assertFalse("modification " + pxMod.getSkylineName() + " has a Unimod Id."
                            + " Unexpected " + pxMod.getPossibleUnimodMatches().size() + " possible matches", pxMod.hasPossibleUnimods());
                }
                else if (matches.size() > 1)
                {
                    List<UnimodModification> possibleMods = pxMod.getPossibleUnimodMatches();
                    assertFalse("Unexpected Unimod Id for modification " + pxMod.getSkylineName(), pxMod.hasUnimodId());
                    assertEquals("Expected " + matches.size() + " possible matches for modification " + pxMod.getSkylineName(), matches.size(), possibleMods.size());
                    assertEquals(matches.stream().map(m -> m.getId()).collect(Collectors.toSet()), possibleMods.stream().map(m -> m.getId()).collect(Collectors.toSet()));
                }
            }
        }

        private void printStructuralMod(Modification mod, PxModification pxMod)
        {
            if (!debug)
            {
                return;
            }

            if (pxMod.hasUnimodId())
            {
                String term = mod.getTerminus() == null ? "" : (mod.getTerminus().equals("N") ? "N-term" : "C-term");
                String modInfo = pxMod.getSkylineName() + ", " + Formula.normalizeFormula(mod.getFormula()) + ", " + mod.getAminoAcid() + ", TERM: " + term;
                System.out.print("Skyline: " + modInfo);
                System.out.println(" --- " + pxMod.getUnimodId() + ", " + pxMod.getName());
            }
            if (pxMod.hasPossibleUnimods())
            {
                String term = mod.getTerminus() == null ? "" : (mod.getTerminus().equals("N") ? "N-term" : "C-term");
                String modInfo = pxMod.getSkylineName() + ", " + Formula.normalizeFormula(mod.getFormula()) + ", " + mod.getAminoAcid() + ", TERM: " + term;
                System.out.println("Skyline: " + modInfo);
                for (UnimodModification umod: pxMod.getPossibleUnimodMatches())
                {
                    System.out.println(" --- List.of(new UnimodModification(" + umod.getId() + ", \"" + umod.getName() + "\", null))");
                }
            }
        }

        private File getUnimodFile() throws IOException
        {
            File root = JunitUtil.getSampleData(null, "../../../server");
            if(root == null)
            {
                root = new File(System.getProperty("user.dir"));
            }
            // /modules/MacCossLabModules/PanoramaPublic/resources/unimod_NO_NAMESPACE.xml
            return new File(root, "/modules/MacCossLabModules/PanoramaPublic/resources/unimod_NO_NAMESPACE.xml");
        }

        @Test
        public void testIsotopicMods() throws IOException
        {
            File unimodXml = getUnimodFile();
            UnimodModifications uMods = null;
            try
            {
                uMods = new UnimodParser().parse(unimodXml);
            }
            catch (Exception e)
            {
                fail("Failed to parse UNIMOD modifications. " + e.getMessage());
            }

            assertNotNull(uMods);

            List<IsotopeModification> mods = new ArrayList<>();
            Map<String, List<UnimodModification>> matches = new HashMap<>();
            int idx = 0;

            // Some modifications on PanoramaWeb that do not have a UNIMOD ID.

            var label13C6 = uMods.getById(188); // Label:13C(6)
            mods.add(createisotopicMod("R-6",null,"R",null,false, LABEL13C,false,false));
            matches.put(mods.get(idx++).getName(), List.of(label13C6));
            mods.add(createisotopicMod("Label:13C(6) (C-term R)",null,"R","C",false, LABEL13C,false,false));
            matches.put(mods.get(idx++).getName(), List.of(label13C6));
            mods.add(createisotopicMod("K-6",null,"K",null,false, LABEL13C,false,false));
            matches.put(mods.get(idx++).getName(), List.of(
                    label13C6, // This is the real match. Others below will match because the isotope formula diff (C'6 - C6) matches
                    uMods.getById(364), /* ICPL:13C(6) */
                    uMods.getById(464), /* SPITC:13C(6) */
                    uMods.getById(1398) /* Iodoacetanilide:13C(6) */));
            mods.add(createisotopicMod("Label:13C(6) (C-term K)",null,"K","C",false, LABEL13C,false,false));
            matches.put(mods.get(idx++).getName(), List.of(
                    label13C6, // This is the real match. Others below will match because the isotope formula diff (C'6 - C6) matches
                    uMods.getById(364), /* ICPL:13C(6) */
                    uMods.getById(464), /* SPITC:13C(6) */
                    uMods.getById(1398) /* Iodoacetanilide:13C(6) */));
            mods.add(createisotopicMod("L-6",null,"L",null,false, LABEL13C,false,false));
            matches.put(mods.get(idx++).getName(), List.of(label13C6));

            // Other user given names for this modification in Skyline: heavy K, K-8, K(+08)
            var label13C615N2 = uMods.getById(259);
            mods.add(createisotopicMod("Label:13C(6)15N(2) (K)",null,"K",null,false, LABEL13C, LABEL15N,false));
            matches.put(mods.get(idx++).getName(), List.of(
                    label13C615N2, // This is the real match. Others below will match because the isotope formula diff (C'6N'2 - C6N2) matches
                    uMods.getById(1302) /* mTRAQ:13C(6)15N(2) */));
            mods.add(createisotopicMod("Label:13C(6)15N(2) (C-term K)",null,"K","C",false, LABEL13C, LABEL15N,false));
            matches.put(mods.get(idx++).getName(), List.of(
                    label13C615N2, // This is the real match. Others below will match because the isotope formula diff (C'6N'2 - C6N2) matches
                    uMods.getById(1302) /* mTRAQ:13C(6)15N(2) */));

            // Other user given names for this modification in Skyline: HeavyR, heavy R, R10, R-10, R(+10), R 13C 15N,
            var label13C615N4 = uMods.getById(267);
            mods.add(createisotopicMod("Label:13C(6)15N(4) (C-term R)",null,"R","C",false, LABEL13C, LABEL15N,false));
            matches.put(mods.get(idx++).getName(), List.of(label13C615N4));
            mods.add(createisotopicMod("Label:13C(6)15N(4) (C-term R)",null,"R",null,false, LABEL13C, LABEL15N,false));
            matches.put(mods.get(idx++).getName(), List.of(label13C615N4));


            var label13C515N1 = uMods.getById(268);
            // Modification name is incorrect! It should be Label:13C(5)15N(1) (C-term E) Formula for Glutamic Acid (E) is C5H9NO4
            mods.add(createisotopicMod("Label:13C(4)15N(2) (C-term E)",null,"E","C",false, LABEL13C, LABEL15N,false));
            matches.put(mods.get(idx++).getName(), List.of(label13C515N1));


            // The formula could have been simplified to "H'4 - H4".  This is probably a heavy version of a unlabeled structural modification.
            // Assuming that the user entered "- C6H12ON2" as the formula of the unlabeled mod, Unimod:1301, Lys matches.  But there is no
            // labeled form for Lys:2H(4) in Unimod.
            mods.add(createisotopicMod("heavyK","C6H8H'4ON2 - C6H12ON2","K",null,false,false,false,false));
            matches.put(mods.get(idx++).getName(), List.of(
                    uMods.getById(481), /* Label:2H(4) */
                    uMods.getById(65), /* Succinyl:2H(4) */
                    uMods.getById(95), /* IMID:2H(4) */
                    uMods.getById(199), /* Dimethyl:2H(4) */
                    uMods.getById(687) /* ICPL:2H(4) */));

            mods.add(createisotopicMod("Leu6C13N15","C'6N' -C6N","L",null,false,false,false,false));
            matches.put(mods.get(idx++).getName(), List.of(uMods.getById(695) /* Label:13C(6)15N(1) */));


            // These are wildcard modifications. No formula or labeled amino acids are given so we cannot get a specific match
            // Label:15N, for example corresponds to the following Unimod modifications:
            // http://www.unimod.org/modifications_view.php?editid1=994 15N(1)
            // http://www.unimod.org/modifications_view.php?editid1=995 15N(2)
            // http://www.unimod.org/modifications_view.php?editid1=996 15N(3)
            // http://www.unimod.org/modifications_view.php?editid1=897 SILAC 15N(4)
            mods.add(createisotopicMod("Label:15N",null,null,null,false,false,true,false));
            matches.put(mods.get(idx++).getName(), Collections.emptyList());
            mods.add(createisotopicMod("Label:13C",null,null,null,false,true,false,false));
            matches.put(mods.get(idx++).getName(), Collections.emptyList());
            mods.add(createisotopicMod("Label:13C15N",null,null,null,false,true,true,false));
            matches.put(mods.get(idx++).getName(), Collections.emptyList());
            mods.add(createisotopicMod("all N15",null,null,null,false,false,true,false));
            matches.put(mods.get(idx++).getName(), Collections.emptyList());
            mods.add(createisotopicMod("13C V",null,"V",null,false,true,false,false));
            matches.put(mods.get(idx++).getName(), Collections.emptyList());
            mods.add(createisotopicMod("15N",null,null,null,false,false,true,false));
            matches.put(mods.get(idx++).getName(), Collections.emptyList());
            mods.add(createisotopicMod("all 15N",null,null,null,false,false,true,false));
            matches.put(mods.get(idx++).getName(), Collections.emptyList());
            mods.add(createisotopicMod("N15",null,null,null,false,false,true,false));
            matches.put(mods.get(idx++).getName(), Collections.emptyList());


            // User defined mTRAQ heavy labels.
            // Skyline's formula in UniModData.cs for the heavy label does not match the composition in Unimod.
            // For example: mTRAQ:13C(6)15N(2) (K) has formula "C'6N'2 - C6N2" in Skyline.
            // The corresponding Unimod entry, Unimod:1302 mTRAQ heavy has composition H(12) C 13C(6) 15N(2) O
            // The difference is because Skyline's formula for the heavy label is the difference of mTRAQ(heavy) - mTRAQ(light).
            // The light version, on 'K', in Skyline is mTRAQ (K), with formula C7H12N2O. This is the same as Unimod:888, mTRAQ light (H(12) C(7) N(2) O).
            // Unimod mTRAQ heavy (H(12) C 13C(6) 15N(2) O) minus Unimod mTRAQ light (H(12) C(7) N(2) O) equals C(-6) 13C(6) N(-2) 15N(2).
            // C(-6) 13C(6) N(-2) 15N(2) is also the formula for Unimod:259, Label:13C(6)15N(2), and applies on 'K'.
            // We will find two matches for this modification. The correct one is from matching to the isotope formula diff.
            mods.add(createisotopicMod("mTRAQ +8 (K)","C'6N'2 - C6N2","K",null,false, LABEL13C, LABEL15N,false));
            matches.put(mods.get(idx++).getName(), List.of(
                    uMods.getById(1302), /* mTRAQ:13C(6)15N(2) */
                    uMods.getById(259) /* Label:13C(6)15N(2) */));
            // Unimod:259, Label:13C(6)15N(2) does not have a N-term specificity so it will not match the modification below. Only one match will be found
            // based on the isotope formula diff.
            mods.add(createisotopicMod("mTRAQ +8 (N-term)","C'6N'2 - C6N2",null,"N",false,false,false,false));
            matches.put(mods.get(idx++).getName(), List.of(uMods.getById(1302) /* mTRAQ:13C(6)15N(2) */));
            mods.add(createisotopicMod("mTRAQ +4 (N-term)","C'3N'1 - C3N1",null,"N",false,false,false,false));
            matches.put(mods.get(idx++).getName(), List.of(uMods.getById(889) /* mTRAQ:13C(3)15N(1) */));
            mods.add(createisotopicMod("mTRAQ +4 (K)","C'3N'1 - C3N1","K",null,false,false,false,false));
            matches.put(mods.get(idx++).getName(), List.of(uMods.getById(889) /* mTRAQ:13C(3)15N(1) */));

            for(int i = 0; i < mods.size(); i++)
            {
                IsotopeModification mod = mods.get(i);
                PxModification pxMod = getIsotopicUnimodMod(mod, uMods);

                printIsotopicMod(mod, pxMod);
                
                List<UnimodModification> expectedMatches = matches.get(pxMod.getSkylineName());

                if (expectedMatches.size() == 0)
                {
                    assertFalse("Unexpected Unimod match for isotopic modification " + pxMod.getSkylineName(), pxMod.hasUnimodId());
                    assertTrue("Unexpected possible mods for isotopic modification " + pxMod.getSkylineName(), pxMod.getPossibleUnimodMatches().size() == 0);
                }
                else if (expectedMatches.size() == 1)
                {
                    assertTrue("Expected a Unimod Id for isotopic modification " + pxMod.getSkylineName(), pxMod.hasUnimodId());
                    assertEquals("Unexpected Unimod match Id for isotopic modification " + pxMod.getSkylineName(), expectedMatches.get(0).getId(), pxMod.getUnimodIdInt().intValue());
                    assertEquals("Unexpected Unimod match name for isotopic modification " + pxMod.getSkylineName(), expectedMatches.get(0).getName(), pxMod.getName());
                    assertFalse("Isotopic modification " + pxMod.getSkylineName() + " has a Unimod Id."
                            + " Unexpected " + pxMod.getPossibleUnimodMatches().size() + " possible matches", pxMod.hasPossibleUnimods());
                }
                else if (expectedMatches.size() > 1)
                {
                    List<UnimodModification> possibleMods = pxMod.getPossibleUnimodMatches();
                    assertFalse("Unexpected Unimod Id for isotopic modification " + pxMod.getSkylineName(), pxMod.hasUnimodId());
                    assertEquals("Expected " + expectedMatches.size() + " possible matches for isotopic modification " + pxMod.getSkylineName(), expectedMatches.size(), possibleMods.size());
                    assertEquals(expectedMatches.stream().map(m -> m.getId()).collect(Collectors.toSet()), possibleMods.stream().map(m -> m.getId()).collect(Collectors.toSet()));
                }
            }
        }

        private void printIsotopicMod(IsotopeModification mod, PxModification pxMod)
        {
            if (!debug)
            {
                return;
            }
            if (pxMod.hasUnimodId())
            {
                String term = mod.getTerminus() == null ? "" : (mod.getTerminus().equals("N") ? "N-term" : "C-term");
                String modInfo = pxMod.getSkylineName() + ", " + Formula.normalizeFormula(mod.getFormula()) + ", " + mod.getAminoAcid() + ", TERM: " + term;
                System.out.print("Skyline: " + modInfo);
                System.out.println(" --- " + pxMod.getUnimodId() + ", " + pxMod.getName());
            }
            if (pxMod.hasPossibleUnimods())
            {
                String term = mod.getTerminus() == null ? "" : (mod.getTerminus().equals("N") ? "N-term" : "C-term");
                String modInfo = pxMod.getSkylineName() + ", " + Formula.normalizeFormula(mod.getFormula()) + ", " + mod.getAminoAcid() + ", TERM: " + term;
                System.out.println("Skyline: " + modInfo);
                for (UnimodModification umod: pxMod.getPossibleUnimodMatches())
                {
                    System.out.println(" --- List.of(new UnimodModification(" + umod.getId() + ", \"" + umod.getName() + "\", null))");
                }
            }
        }

        private Modification createMod(String name, String formula, String sites, String terminus)
        {
            Modification mod = new Modification();
            mod.setFormula(formula);
            mod.setTerminus(terminus);
            mod.setAminoAcid(sites);
            mod.setName(name);
            return mod;
        }

        private static final boolean LABEL2H = true;
        private static final boolean LABEL13C = true;
        private static final boolean LABEL15N = true;
        private static final boolean LABEL18O = true;
        private static IsotopeModification createisotopicMod(String name, String formula, String sites, String terminus,
                                                                      boolean label2h, boolean label13c, boolean label15n, boolean label18o)
        {
            IsotopeModification mod = new IsotopeModification();
            mod.setFormula(formula);
            mod.setTerminus(terminus);
            mod.setAminoAcid(sites);
            mod.setName(name);
            mod.setLabel2H(label2h);
            mod.setLabel13C(label13c);
            mod.setLabel15N(label15n);
            mod.setLabel18O(label18o);
            return mod;
        }
    }

    static class Modification implements IModification
    {
        private long _id;
        private String _name;
        private String _aminoAcid;
        private String _terminus;
        private String _formula;
        private Double _massDiffMono;
        private Double _massDiffAvg;
        private Integer _unimodId;

        @Override
        public long getId()
        {
            return _id;
        }

        public void setId(long id)
        {
            _id = id;
        }

        @Override
        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        @Override
        public String getAminoAcid()
        {
            return _aminoAcid;
        }

        public void setAminoAcid(String aminoAcid)
        {
            _aminoAcid = aminoAcid;
        }

        @Override
        public String getTerminus()
        {
            return _terminus;
        }

        public void setTerminus(String terminus)
        {
            _terminus = terminus;
        }

        @Override
        public String getFormula()
        {
            return _formula;
        }

        public void setFormula(String formula)
        {
            _formula = formula;
        }

        @Override
        public Double getMassDiffMono()
        {
            return _massDiffMono;
        }

        public void setMassDiffMono(Double massDiffMono)
        {
            _massDiffMono = massDiffMono;
        }

        @Override
        public Double getMassDiffAvg()
        {
            return _massDiffAvg;
        }

        public void setMassDiffAvg(Double massDiffAvg)
        {
            _massDiffAvg = massDiffAvg;
        }

        @Override
        public Integer getUnimodId()
        {
            return _unimodId;
        }

        public void setUnimodId(Integer unimodId)
        {
            _unimodId = unimodId;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Modification that = (Modification) o;
            return getId() == that.getId() && getName().equals(that.getName()) && Objects.equals(getAminoAcid(), that.getAminoAcid()) && Objects.equals(getTerminus(), that.getTerminus()) && Objects.equals(getFormula(), that.getFormula()) && Objects.equals(getMassDiffMono(), that.getMassDiffMono()) && Objects.equals(getMassDiffAvg(), that.getMassDiffAvg()) && Objects.equals(getUnimodId(), that.getUnimodId());
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(getId(), getName(), getAminoAcid(), getTerminus(), getFormula(), getMassDiffMono(), getMassDiffAvg(), getUnimodId());
        }
    }

    static class IsotopeModification extends Modification implements IModification.IIsotopeModification
    {
        private Boolean _label13C;
        private Boolean _label15N;
        private Boolean _label18O;
        private Boolean _label2H;

        @Override
        public Boolean getLabel13C()
        {
            return _label13C;
        }

        public void setLabel13C(Boolean label13C)
        {
            _label13C = label13C;
        }

        @Override
        public Boolean getLabel15N()
        {
            return _label15N;
        }

        public void setLabel15N(Boolean label15N)
        {
            _label15N = label15N;
        }

        @Override
        public Boolean getLabel18O()
        {
            return _label18O;
        }

        public void setLabel18O(Boolean label18O)
        {
            _label18O = label18O;
        }

        @Override
        public Boolean getLabel2H()
        {
            return _label2H;
        }

        public void setLabel2H(Boolean label2H)
        {
            _label2H = label2H;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            IsotopeModification that = (IsotopeModification) o;
            return Objects.equals(getLabel13C(), that.getLabel13C())
                    && Objects.equals(getLabel15N(), that.getLabel15N())
                    && Objects.equals(getLabel18O(), that.getLabel18O())
                    && Objects.equals(getLabel2H(), that.getLabel2H());
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(super.hashCode(), getLabel13C(), getLabel15N(), getLabel18O(), getLabel2H());
        }

        private static final boolean LABEL2H = true;
        private static final boolean LABEL13C = true;
        private static final boolean LABEL15N = true;
        private static final boolean LABEL18O = true;

        public static IsotopeModification createisotopicMod(String name, String formula, String sites, String terminus,
                                                             boolean label2h, boolean label13c, boolean label15n, boolean label18o)
        {
            IsotopeModification mod = new IsotopeModification();
            mod.setFormula(formula);
            mod.setTerminus(terminus);
            mod.setAminoAcid(sites);
            mod.setName(name);
            mod.setLabel2H(label2h);
            mod.setLabel13C(label13c);
            mod.setLabel15N(label15n);
            mod.setLabel18O(label18o);
            return mod;
        }

        public static IsotopeModification create(IModification.IIsotopeModification iMod)
        {
            return iMod != null ?
                    createisotopicMod(iMod.getName(), iMod.getFormula(), iMod.getAminoAcid(), iMod.getTerminus(),
                    iMod.getLabel2H() != null ? iMod.getLabel2H().booleanValue() : false,
                    iMod.getLabel13C() != null ? iMod.getLabel13C().booleanValue() : false,
                    iMod.getLabel15N() != null ? iMod.getLabel15N().booleanValue() : false,
                    iMod.getLabel18O() != null ? iMod.getLabel18O().booleanValue() : false)
                    : null;
        }
    }
}

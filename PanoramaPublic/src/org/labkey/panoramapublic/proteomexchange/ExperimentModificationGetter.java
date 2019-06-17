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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.util.JunitUtil;
import org.labkey.targetedms.TargetedMSRun;
import org.labkey.targetedms.model.ExperimentAnnotations;
import org.labkey.targetedms.parser.PeptideSettings;
import org.labkey.targetedms.query.ExperimentAnnotationsManager;
import org.labkey.targetedms.query.ModificationManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExperimentModificationGetter
{
    private static final Logger LOG = Logger.getLogger(ExperimentModificationGetter.class);

    public static List<PxModification> getModifications(ExperimentAnnotations expAnnot)
    {
        List<TargetedMSRun> runs = ExperimentAnnotationsManager.getTargetedMSRuns(expAnnot);

        Map<Integer, PxModification> strModMap = new HashMap<>();
        Map<Integer, PxModification> isoModMap = new HashMap<>();

        UnimodModifications uMods = getUnimodMods(); // Read the UNIMOD modifications

        for(TargetedMSRun run: runs)
        {
            List<PeptideSettings.RunStructuralModification> smods = ModificationManager.getStructuralModificationsForRun(run.getId());
            for(PeptideSettings.RunStructuralModification mod: smods)
            {
                PxModification pxMod = strModMap.get(mod.getStructuralModId());
                if(pxMod == null)
                {
                    pxMod = getStructuralUnimodMod(mod, uMods);
                    strModMap.put(mod.getStructuralModId(), pxMod);
                }
                pxMod.addSkylineDoc(run.getFileName());
            }

            List<PeptideSettings.RunIsotopeModification> iMods = ModificationManager.getIsotopeModificationsForRun(run.getId());
            for(PeptideSettings.RunIsotopeModification mod: iMods)
            {
                PxModification pxMod = isoModMap.get(mod.getIsotopeModId());
                if(pxMod == null)
                {
                    pxMod = getIsotopicUnimodMod(mod, uMods, expAnnot.getContainer());
                    isoModMap.put(mod.getIsotopeModId(), pxMod);
                }
                pxMod.addSkylineDoc(run.getFileName());
            }
        }
        List<PxModification> allMods = new ArrayList<>();
        allMods.addAll(strModMap.values());
        allMods.addAll(isoModMap.values());
        return allMods;
    }

    private static String[] modSites(PeptideSettings.Modification mod)
    {
        if(mod.getAminoAcid() == null)
        {
            return new String[0];
        }

        return mod.getAminoAcid().replaceAll("\\s", "").split(",");
    }

    public static PxModification getStructuralUnimodMod(PeptideSettings.Modification mod, UnimodModifications uMods)
    {
        UnimodModification uMod = null;
        if(mod.getUnimodId() != null)
        {
            uMod = uMods.getById(mod.getUnimodId());
        }
        else
        {
            String normFormula = UnimodModification.normalizeFormula(mod.getFormula());
            if(normFormula != null)
            {
                String[] sites = modSites(mod);
                // Find a match based on formula and modification sites (aa or term)
                uMod = uMods.getMatch(normFormula, sites, true);
            }
        }

        return uMod == null ? new PxModification(null, mod.getName(), mod.getName()) : new PxModification(uMod.getId(), uMod.getName(), mod.getName());
    }

    private static PxModification getIsotopicUnimodMod(PeptideSettings.IsotopeModification mod, UnimodModifications uMods, Container container)
    {
        UnimodModification uMod = null;
        if(mod.getUnimodId() != null)
        {
            uMod = uMods.getById(mod.getUnimodId());
        }
        else
        {
            if(StringUtils.isBlank(mod.getFormula()))
            {
                try
                {
                    buildIsotopeModFormula(mod, uMods);
                }
                catch (PxException e)
                {
                    LOG.error("Error building formula for isotopic mod (" + mod.getName() + ") in container " + container, e);
                }
            }
            String normFormula = UnimodModification.normalizeFormula(mod.getFormula());
            if(normFormula != null)
            {
                String[] sites = modSites(mod);
                // Find a match based on formula and modification sites (aa or term)
                uMod = uMods.getMatch(normFormula, sites, false);
            }
        }

        return uMod == null ? new PxModification(null, mod.getName(), mod.getName()) : new PxModification(uMod.getId(), uMod.getName(), mod.getName());
    }

    private static void buildIsotopeModFormula(PeptideSettings.IsotopeModification mod, UnimodModifications uMods) throws PxException
    {
        String aminoAcids = mod.getAminoAcid();
        if(StringUtils.isBlank(aminoAcids))
        {
            return;
        }

        // On PanoramaWeb we do not have any isotopic modifications with multiple amino amods as targets.  But Skyline allows it
        String[] sites = modSites(mod);
        String formula = null;
        for(String site: sites)
        {
            String f = uMods.buildIsotopicModFormula(site.charAt(0),
                    Boolean.TRUE.equals(mod.getLabel2H()),
                    Boolean.TRUE.equals(mod.getLabel13C()),
                    Boolean.TRUE.equals(mod.getLabel15N()),
                    Boolean.TRUE.equals(mod.getLabel18O()));
            if(formula == null)
            {
                formula = f;
            }
            else if(!formula.equals(f))
            {
                throw new PxException("Multiple amino acids found for isotopic modification (" + mod.getName() +"). Formulae do not match.");
            }
        }
        mod.setFormula(formula);
    }

    public static UnimodModifications getUnimodMods()
    {
        try
        {
            return (new UnimodParser().parse());
        }
        catch (Exception e)
        {
            LOG.error("There was an error reading UNIMOD modifications.", e);
            return new UnimodModifications();
        }
    }

    public static class PxModification
    {
        private final String _name;
        private final String _skylineName;
        private final Integer _unimodId;
        private Set<String> _skylineDocs;

        public PxModification(Integer id, String name, String skylineName)
        {
            _name = name;
            _unimodId = id;
            _skylineName = skylineName;

            _skylineDocs = new HashSet<>();
        }

        public void addSkylineDoc(String skyDocName)
        {
            if(skyDocName != null)
            {
                _skylineDocs.add(skyDocName);
            }
        }

        public Set<String> getSkylineDocs()
        {
            return _skylineDocs;
        }

        public String getName()
        {
            return _name;
        }

        public String getSkylineName()
        {
            return _skylineName;
        }

        public String getUnimodId()
        {
            return _unimodId == null ? null : "UNIMOD:" + String.valueOf(_unimodId);
        }

        public boolean hasUnimodId()
        {
            return _unimodId != null;
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testStructuralMods() throws IOException
        {
            // Modifications in Panorama Public that do not have a UNIMOD ID.
            List<PeptideSettings.Modification> mods = new ArrayList<>();
            mods.add(createMod("unsaturated tryptophandione (W)", "OO-HHHH", "W", null));
            mods.add(createMod("oxidation (H)", "O", "H", null));
            mods.add(createMod("acetylation", "C2H2O1", "K", null));
            mods.add(createMod("Dimethylation (KRN)", "H4C2", "K, R, N", null));
            mods.add(createMod("GlcNAc-Fuc", null, "N", null));
            mods.add(createMod("Propionamide(C)", "H5C3NO", "C", null));
            mods.add(createMod("Lipoyl NEM (K)", "H14C8OS2 H7C6NO2 H7C6NO2", "K", null));
            mods.add(createMod("Acetyl-T (N-term)", "C2H2O", "T", "N"));
            mods.add(createMod("Met Ox", "O", "M", null));
            mods.add(createMod("Oxidation (T)", "O", "T", null));
            mods.add(createMod("Carbamidomethyl Cysteine", "C2H3ON", "C", null));
            mods.add(createMod("ICAT-C (C)", "C10H17N3O3", "C", null));
            mods.add(createMod("ring open1 (H)", "O-C2NH", "H", null));
            mods.add(createMod("Chlorination (Y)", "Cl -H", "Y", null));
            mods.add(createMod("Oxidation (M)", "O", "M", null));
            mods.add(createMod("Dihydroxyformylkynurenine (W)", "OOOO", "W", null));
            mods.add(createMod("Asp decarboxylation (D)", "-COHH", "D", null));
            mods.add(createMod("Tryptoline (W)", "C", "W", null));
            mods.add(createMod("try->monooxidation (W)", "O", "W", null));
            mods.add(createMod("Lys carbonyl", "O-HH", "K", null));
            mods.add(createMod("Acetyl (K)", "C2 H3 O -H", "K", null));
            mods.add(createMod("Methyl-ester (E)", "CH2", "E", null));
            mods.add(createMod("Gln Oxidation (Q)", "O", "Q", null));
            mods.add(createMod("Pyro Glu", "-OH2", "Q", null));
            mods.add(createMod("Try->glycine hydroperoxide (W)", "OO-C9H7N", "W", null));
            mods.add(createMod("carbonyl (R)", "O-HH", "R", null));
            mods.add(createMod("pyro-glu", null, "Q", "N"));
            mods.add(createMod("mTRAQ +0 (N-term)", "H12C7N2O", null, "N"));
            mods.add(createMod("Acetyl-V (N-term)", "C2H2O", "V", "N"));
            mods.add(createMod("Ser oxidation", "O", "S", null));
            mods.add(createMod("Nacetyl_phospho(T)", "C2H3O4P", "T", "N"));
            mods.add(createMod("carbonyl (A)", "O-HH", "A", null));
            mods.add(createMod("NitroY", "NO2 -H", "Y", null));
            mods.add(createMod("Leu/Ile oxidation", "O", "L, I", null));
            mods.add(createMod("hydroxy tryptophandione (W)", "OOO-HHHH", "W", null));
            mods.add(createMod("Methyl-ester (D)", "CH2", "D", null));
            mods.add(createMod("Methionine_sulfoxide", "O", "M", null));
            mods.add(createMod("ring open 3 (H)", "OO-NHC", "H", null));
            mods.add(createMod("NitroY", "N O2 -H", "Y", null));
            mods.add(createMod("N-Acetyl-Phospho-T", "C2H3O4P", "T", "N"));
            mods.add(createMod("Acetyl-A (N-term)", "C2H2O", "A", "N"));
            mods.add(createMod("GlcNAc", null, "N", null));
            mods.add(createMod("Phospho(S)", "HO3P", "S", null));
            mods.add(createMod("Glycation(V)", "C6H12O6-H2O", "V", null));
            mods.add(createMod("Phosho (Y)", "HPO3", "Y", null));
            mods.add(createMod("Met Sulfoxide", "O", "M", null));
            mods.add(createMod("Acetyl-M (N-term)", "C2H2O", "M", "N"));
            mods.add(createMod("Dimethylation (N-term)", "H4C2", null, "N"));
            mods.add(createMod("Methyl (TSCKRH)", "H4C2", "T, S, C, K, R, H", null));
            mods.add(createMod("dopa-derived quinone (Y+O-2H)", "O-HH", "Y", null));
            mods.add(createMod("HexNAc(1)dHex(1) (N)", "H23C14NO9", "N", null));
            mods.add(createMod("trioxidation (MHWFY)", "OOO", "M, H, W, F, Y", null));
            mods.add(createMod("C-term deamidation", "HN-O", null, "C"));
            mods.add(createMod("His-Thiolatp", "PO2", "S", null));
            mods.add(createMod("Pyroglutamic acid (Q)", null, "Q", "N"));
            mods.add(createMod("Phospho (S,T)", "HPO3", "S, T", null));
            mods.add(createMod("Ubiquitin", null, "K", null));
            mods.add(createMod("deamidate (N)", "O-NH", "N", null));
            mods.add(createMod("H->hydroxy-dioxidation", "H2O2", "H", null));
            mods.add(createMod("Glu oxidation (E)", "O", "E", null));
            mods.add(createMod("dioxidation (MHWFY)", "OO", "M, H, W, F, Y", null));
            mods.add(createMod("Gln Carbonyl (Q)", "O-HH", "Q", null));
            mods.add(createMod("ring open 4 (H)", "OO-NNCHH", "H", null));
            mods.add(createMod("MetOxid_NtermAcetyl", "C2H2O2", "M", "N"));
            mods.add(createMod("OOO-HH (C)", "OOO-HH", "C", null));
            mods.add(createMod("ring open 2(H)", "OO-C2NNHH", "H", null));
            mods.add(createMod("Acetyl-S (N-term)", "C2H2O", "S", "N"));
            mods.add(createMod("Kinome-ATP-K", "C10H16N2O2", "K", null));
            mods.add(createMod("Phospho", "HO3P", "T", null));
            mods.add(createMod("carbonyl (L/I)", "O-HH", "L, I", null));
            mods.add(createMod("Met ox", "O", "M", null));
            mods.add(createMod("V oxidation", "O", "V", null));
            mods.add(createMod("mTRAQ +0 (K)", "H12C7N2O", "K", null));
            mods.add(createMod("Glu decarboxylation (E)", "-COHH", "E", null));
            mods.add(createMod("Glu carbonyl (E)", "O-HH", "E", null));
            mods.add(createMod("N-term Met loss+ acetylation (S)", "-H6C3NS", "S", "N"));
            mods.add(createMod("Carboxymethylcysteine", "CH2COO", "C", null));
            mods.add(createMod("mono-oxidation", "O", "M, W, H, C, F, Y", null));

            // These modifications do not match with a modification in unimod.xml
            Set<String> unknown = new HashSet();
            unknown.add("GlcNAc-Fuc");
            unknown.add("Lipoyl NEM (K)");
            unknown.add("ICAT-C (C)");
            unknown.add("Lys carbonyl");
            unknown.add("Try->glycine hydroperoxide (W)");
            unknown.add("pyro-glu");
            unknown.add("Acetyl-V (N-term)");
            unknown.add("Nacetyl_phospho(T)");
            unknown.add("N-Acetyl-Phospho-T");
            unknown.add("Acetyl-A (N-term)");
            unknown.add("GlcNAc");
            unknown.add("Glycation(V)");
            unknown.add("Acetyl-M (N-term)");
            unknown.add("Methyl (TSCKRH)");
            unknown.add("dopa-derived quinone (Y+O-2H)");
            unknown.add("trioxidation (MHWFY)");
            unknown.add("His-Thiolatp");
            unknown.add("Pyroglutamic acid (Q)");
            unknown.add("Ubiquitin");
            unknown.add("H->hydroxy-dioxidation");
            unknown.add("dioxidation (MHWFY)");
            unknown.add("MetOxid_NtermAcetyl");
            unknown.add("OOO-HH (C)");
            unknown.add("N-term Met loss+ acetylation (S)");

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

            assertTrue(uMods != null);

            int notFound = 0;
            int total = 0;
            for(PeptideSettings.Modification mod: mods)
            {
                PxModification pxMod = getStructuralUnimodMod(mod, uMods);
//                if(!pxMod.hasUnimodId())
//                {
//                    String term = mod.getTerminus() == null ? "" : (mod.getTerminus().equals("N") ? "N-term" : "C-term");
//                    System.out.println(pxMod.getUnimodId() + ", " + pxMod.getName() + "(" + pxMod.getSkylineName() + ")"
//                            + ", " + UnimodModification.normalizeFormula(mod.getFormula()) + ", " + mod.getAminoAcid() + ", TERM: " + term);
//                }

                if(!unknown.contains(pxMod.getName()))
                {
                    assertTrue(pxMod.hasUnimodId());
                }

                notFound = notFound + (pxMod.hasUnimodId() ? 0 : 1);
                total++;
            }

//            System.out.println("TOTAL " + total + ", NOT FOUND: " + notFound);
        }

        private File getUnimodFile() throws IOException
        {
            File root = JunitUtil.getSampleData(null, "../server");
            if(root == null)
            {
                root = new File(System.getProperty("user.dir"));
            }
            // return new File(root, "/customModules/targetedms/webapp/TargetedMS/unimod/unimod_NO_NAMESPACE.xml");
            return new File(root, "/customModules/targetedms/resources/unimod_NO_NAMESPACE.xml");
        }

        @Test
        public void testIsotopicMods() throws IOException
        {
            // Modifications in Panorama Public that do not have a UNIMOD ID.
            List<PeptideSettings.IsotopeModification> mods = new ArrayList<>();
            mods.add(createisotopicMod("all N15",null,null,null,false,false,true,false));
            mods.add(createisotopicMod("13C V",null,"V",null,false,true,false,false));
            mods.add(createisotopicMod("Label:13C(6)15N(2) (K)",null,"K",null,false,true,true,false));
            mods.add(createisotopicMod("heavy K",null,"K","C",false,true,true,false));
            mods.add(createisotopicMod("K-8",null,"K",null,false,true,true,false));
            mods.add(createisotopicMod("HeavyK",null,"K","C",false,true,true,false));
            mods.add(createisotopicMod("Label:13C15N",null,null,null,false,true,true,false));
            mods.add(createisotopicMod("Label:13C(6)15N(4) (C-term R)",null,"R","C",false,true,true,false));
            mods.add(createisotopicMod("Label:13C(6)15N(2) (C-term K)",null,"K",null,false,true,true,false));
            mods.add(createisotopicMod("mTRAQ +8 (N-term)","C'6N'2 - C6N2",null,"N",false,false,false,false));
            mods.add(createisotopicMod("HeavyR",null,"R","C",false,true,true,false));
            mods.add(createisotopicMod("R-6",null,"R",null,false,true,false,false));
            mods.add(createisotopicMod("Leu6C13N15","C'6N' -C6N","L",null,false,false,false,false));
            mods.add(createisotopicMod("R-10",null,"R",null,false,true,true,false));
            mods.add(createisotopicMod("Label:13C(6)15N(4) (C-term R)",null,"R",null,false,true,true,false));
            mods.add(createisotopicMod("15N",null,null,null,false,false,true,false));
            mods.add(createisotopicMod("all 15N",null,null,null,false,false,true,false));
            mods.add(createisotopicMod("13C R",null,"R","C",false,true,false,false));
            mods.add(createisotopicMod("R 13C 15N",null,"R","C",false,true,true,false));
            mods.add(createisotopicMod("K-6",null,"K",null,false,true,false,false));
            mods.add(createisotopicMod("Label:15N",null,null,null,false,false,true,false));
            mods.add(createisotopicMod("mTRAQ +8 (K)","C'6N'2 - C6N2","K",null,false,false,false,false));
            mods.add(createisotopicMod("Label:13C15N",null,"V",null,false,true,true,false));
            mods.add(createisotopicMod("Label:13C",null,null,null,false,true,false,false));
            mods.add(createisotopicMod("heavyK","C6H8H'4ON2 - C6H12ON2","K",null,false,false,false,false));
            mods.add(createisotopicMod("R(+10)",null,"R",null,false,true,true,false));
            mods.add(createisotopicMod("R10",null,"R","C",false,true,true,false));
            mods.add(createisotopicMod("heavy R",null,"R","C",false,true,true,false));
            mods.add(createisotopicMod("N15",null,null,null,false,false,true,false));
            mods.add(createisotopicMod("Label:13C(6) (C-term K)",null,"K","C",false,true,false,false));
            mods.add(createisotopicMod("L-6",null,"L",null,false,true,false,false));
            mods.add(createisotopicMod("Label:13C(4)15N(2) (C-term E)",null,"E","C",false,true,true,false));
            mods.add(createisotopicMod("Label:13C(6)15N(2) (C-term K)",null,"K","C",false,true,true,false));
            mods.add(createisotopicMod("mTRAQ +4 (N-term)","C'3N'1 - C3N1",null,"N",false,false,false,false));
            mods.add(createisotopicMod("K(+08)",null,"K",null,false,true,true,false));
            mods.add(createisotopicMod("Label:13C(6) (C-term R)",null,"R","C",false,true,false,false));
            mods.add(createisotopicMod("mTRAQ +4 (K)","C'3N'1 - C3N1","K",null,false,false,false,false));

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

            assertTrue(uMods != null);

            // These modifications do not match with a modification in unimod.xml
            Set<String> unknown = new HashSet<>();
            unknown.add("all N15");
            unknown.add("13C V");
            unknown.add("Label:13C15N");
            unknown.add("15N");
            unknown.add("all 15N");
            unknown.add("Label:15N");
            unknown.add("Label:13C");
            unknown.add("N15");
            unknown.add("mTRAQ +4 (K)");

            int notFound = 0;
            int total = 0;
            for(PeptideSettings.IsotopeModification mod: mods)
            {
                PxModification pxMod = getIsotopicUnimodMod(mod, uMods, null);
//                if(!pxMod.hasUnimodId())
//                {
//                    String term = mod.getTerminus() == null ? "" : (mod.getTerminus().equals("N") ? "N-term" : "C-term");
//                    String labels = mod.getLabel2H() ? "2H" : "-";
//                    labels = labels + (mod.getLabel13C() ? "13C" : "-");
//                    labels = labels + (mod.getLabel15N() ? "15N" : "-");
//                    labels = labels + (mod.getLabel18O() ? "18N" : "-");
//
//                    System.out.println(pxMod.getUnimodId() + ", " + pxMod.getSkylineName() + "(" + pxMod.getName() + ")"
//                            + ", " + UnimodModification.normalizeFormula(mod.getFormula()) + ", " + mod.getAminoAcid() + ", TERM: " + term
//                            +", " + labels);
//                }
                if(!unknown.contains(pxMod.getName()))
                {
                    assertTrue(pxMod.hasUnimodId());
                }

                notFound = notFound + (pxMod.hasUnimodId() ? 0 : 1);
                total++;
            }

//            System.out.println("TOTAL " + total + ", NOT FOUND: " + notFound);
        }

        private PeptideSettings.Modification createMod(String name, String formula, String sites, String terminus)
        {
            PeptideSettings.Modification mod = new PeptideSettings.Modification();
            mod.setFormula(formula);
            mod.setTerminus(terminus);
            mod.setAminoAcid(sites);
            mod.setName(name);
            return mod;
        }

        private PeptideSettings.IsotopeModification createisotopicMod(String name, String formula, String sites, String terminus,
                                                                      boolean label2h, boolean label13c, boolean label15n, boolean label18o)
        {
            PeptideSettings.IsotopeModification mod = new PeptideSettings.IsotopeModification();
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
}

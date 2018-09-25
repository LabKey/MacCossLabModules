/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.targetedms.TargetedMSRun;
import org.labkey.targetedms.model.ExperimentAnnotations;
import org.labkey.targetedms.parser.PeptideSettings;
import org.labkey.targetedms.query.ExperimentAnnotationsManager;
import org.labkey.targetedms.query.ModificationManager;

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
                    Boolean.TRUE.equals(mod.getLabel2H()) ? true : false,
                    Boolean.TRUE.equals(mod.getLabel13C()) ? true : false,
                    Boolean.TRUE.equals(mod.getLabel15N()) ? true : false,
                    Boolean.TRUE.equals(mod.getLabel18O()) ? true : false);
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
            return "UNIMOD:" + (_unimodId == null ? "0" : String.valueOf(_unimodId));
        }

        public boolean hasUnimodId()
        {
            return _unimodId != null;
        }
    }
}

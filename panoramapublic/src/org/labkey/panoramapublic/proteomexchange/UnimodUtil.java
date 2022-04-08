package org.labkey.panoramapublic.proteomexchange;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.logging.LogHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.panoramapublic.proteomexchange.ExperimentModificationGetter.*;

public class UnimodUtil
{
    private static final String UNIMOD = "unimod";
    private static final BlockingCache<String, UnimodModifications> _unimodCache =
            CacheManager.getBlockingCache(1, CacheManager.DAY, "Unimod Modifications", (key, argument) -> readUnimodMods());

    private static final Logger LOG = LogHelper.getLogger(ExperimentModificationGetter.class, "Reads and caches Unimod modifications");

    public static UnimodModifications getUnimod()
    {
        return _unimodCache.get(UNIMOD);
    }

    private static UnimodModifications readUnimodMods()
    {
        try
        {
            return (new UnimodParser().parse());
        }
        catch (Exception e)
        {
            LOG.error("There was an error parsing UNIMOD modifications.", e);
            throw UnexpectedException.wrap(e,
                    "There was an error parsing Unimod modifications. The error was: " + e.getMessage()
                            + " Please try again. If you continue to see this error please contact the server administrator.");
        }
    }

    // --------------------------------------------------------------------
    // Hard-coded modifications in Skyline that have a Unimod Id
    // --------------------------------------------------------------------
    /*
    new UniModModificationData
    {
        Name = "Label:13C(6)15N(2) (C-term K)",
        AAs = "K", Terminus = ModTerminus.C, LabelAtoms = LabelAtoms.N15 | LabelAtoms.C13,
        Structural = false, Hidden = false,
    }
     */
    private static final IsotopeModification label13C615N2_CtermK = IsotopeModification.createisotopicMod("Label:13C(6)15N(2) (C-term K)", null, "K", "C",
            false, true, true, false);

    /*
     new UniModModificationData
    {
         Name = "Label:13C(6)15N(4) (C-term R)",
         AAs = "R", Terminus = ModTerminus.C, LabelAtoms = LabelAtoms.N15 | LabelAtoms.C13,
         Structural = false, Hidden = false,
    }
    */
    private static final IsotopeModification label13C615N4_CtermR = IsotopeModification.createisotopicMod("Label:13C(6)15N(4) (C-term R)", null, "R", "C",
            false, true, true, false);

    /*
    new UniModModificationData
    {
         Name = "Label:13C(6) (C-term K)",
         AAs = "K", Terminus = ModTerminus.C, LabelAtoms = LabelAtoms.C13,
         Structural = false, Hidden = false,
    }
     */
    private static final IsotopeModification label13C6_CtermK = IsotopeModification.createisotopicMod("Label:13C(6) (C-term K)", null, "K", "C",
            false, true, false, false);

    /*
    new UniModModificationData
    {
         Name = "Label:13C(6) (C-term R)",
         AAs = "R", Terminus = ModTerminus.C, LabelAtoms = LabelAtoms.C13,
         Structural = false, Hidden = false,
    }
     */
    private static final IsotopeModification label13C6_CtermR = IsotopeModification.createisotopicMod("Label:13C(6) (C-term R)", null, "R", "C",
            false, true, false, false);


    public static @Nullable UnimodModification getMatchIfHardCodedSkylineMod(PxModification pxMod)
    {
        if (pxMod != null && pxMod.isIsotopicMod())
        {
            IsotopeModification skylineMod = IsotopeModification.create(TargetedMSService.get().getIsotopeModification(pxMod.getDbModId()));
            if (skylineMod != null)
            {
                return getMatchIfHardCodedMod(skylineMod);
            }
        }
        return null;
    }

    private static @Nullable UnimodModification getMatchIfHardCodedMod(IsotopeModification skylineMod)
    {
        if (label13C615N2_CtermK.equals(skylineMod) // Unimod:259
        || label13C615N4_CtermR.equals(skylineMod)  // Unimod:267
        || label13C6_CtermK.equals(skylineMod)      // Unimod:188
        || label13C6_CtermR.equals(skylineMod))     // Unimod:188
        {
            var modsList = getUnimod().getByFormula(buildIsotopeModFormula(skylineMod));
            if (modsList.size() == 1)
            {
                return modsList.get(0);
            }
        }
        return null;
    }

    private static String buildIsotopeModFormula(IsotopeModification mod)
    {
        return buildIsotopeModFormula(mod, mod.getAminoAcid().charAt(0));
    }

    private static String buildIsotopeModFormula(IsotopeModification mod, char aaSite)
    {
        return buildIsotopicModFormula(aaSite,
                Boolean.TRUE.equals(mod.getLabel2H()),
                Boolean.TRUE.equals(mod.getLabel13C()),
                Boolean.TRUE.equals(mod.getLabel15N()),
                Boolean.TRUE.equals(mod.getLabel18O())).getFormula();
    }

    public static @NotNull Formula buildIsotopicModFormula(char aminoAcid, boolean label2h, boolean label13c, boolean label15n, boolean label18o)
    {
        Formula formula = new Formula();
        Map<String, Integer> composition = getUnimod().getAminoAcidComposition(aminoAcid);
        if(composition != null)
        {
            for(String element: composition.keySet())
            {
                int count = composition.get(element);
                if(element.equals("H") && label2h)
                {
                    formula.addElement(ChemElement.H, -count);
                    formula.addElement(ChemElement.H2, count);
                }
                else if(element.equals("C") && label13c)
                {
                    formula.addElement(ChemElement.C, -count);
                    formula.addElement(ChemElement.C13, count);
                }
                else if(element.equals("N") && label15n)
                {
                    formula.addElement(ChemElement.N, -count);
                    formula.addElement(ChemElement.N15, count);
                }
                else if(element.equals("O") && label18o)
                {
                    formula.addElement(ChemElement.O, -count);
                    formula.addElement(ChemElement.O18, count);
                }
            }
        }
        return  formula;
    }

    // --------------------------------------------------------------------
    // Wildcard modifications in Skyline
    // --------------------------------------------------------------------
    /*
    new UniModModificationData
    {
         Name = "Label:15N",
         LabelAtoms = LabelAtoms.N15,
         Structural = false, Hidden = false,
    }
    */
    private static final IsotopeModification label15N = IsotopeModification.createisotopicMod("Label:15N", null, null, null,
            false, false, true, false);

    /*
    new UniModModificationData
    {
         Name = "Label:13C",
         LabelAtoms = LabelAtoms.C13,
         Structural = false, Hidden = false,
    }
    */
    private static final IsotopeModification label13C = IsotopeModification.createisotopicMod("Label:13C", null, null, null,
            false, true, false, false);

    /*
    Unimod is incomplete for Label13C
    13C(2) - nothing in Unimod (AAs with 2C - G)
    13C(3): http://www.unimod.org/modifications_view.php?editid1=1296 (Specificity in Unimod: A) (AAs with 3C - A,C,S)
    13C(4): http://www.unimod.org/modifications_view.php?editid1=1266 (Specificity in Unimod: M) (AAs with 4C - N,D,T)
    13C(5): http://www.unimod.org/modifications_view.php?editid1=772   (Specificity in Unimod: P) (AAs with 5C - Q,E,M,P,V)
    13C(6): http://www.unimod.org/modifications_view.php?editid1=188   (Specificity in Unimod: R,K,L,I) (AAs with 6C - R,H,I,L,K,)
    13C(9): http://www.unimod.org/modifications_view.php?editid1=184   (Specificity in Unimod: Y,F) (AAs with 9C - F,Y)
    13C(11) - nothing in Unimod  (AAs with 11C - W)
     */


    /*
    new UniModModificationData
    {
         Name = "Label:13C15N",
         LabelAtoms = LabelAtoms.N15 | LabelAtoms.C13,
         Structural = false, Hidden = false,
    }
    */
    private static final IsotopeModification label13C15N = IsotopeModification.createisotopicMod("Label:13C15N", null, null, null,
            false, true, true, false);

    /*
    Unimod is incomplete for Label13C15N
    "UNIMOD:1297, Label:13C(3)15N(1), C'3N' - C3N, Sites: A, (Missing amino acid specificities: C, S, U)
    "UNIMOD:1298, Label:13C(4)15N(1), C'4N' - C4N, Sites: D, (Missing amino acid specificities: T)
    "UNIMOD:268, Label:13C(5)15N(1), C'5N' - C5N, Sites: P:E:V:M,
    "UNIMOD:695, Label:13C(6)15N(1), C'6N' - C6N, Sites: I:L,
    "UNIMOD:259, Label:13C(6)15N(2), C'6N'2 - C6N2, Sites: K,
    "UNIMOD:267, Label:13C(6)15N(4), C'6N'4 - C6N4, Sites: R,
    "UNIMOD:269, Label:13C(9)15N(1), C'9N' - C9N, Sites: F, (Missing amino acid specificities: Y)

    Missing in Unimod:
    Label:13C(2)15N(1) (G)
    Label:13C(4)15N(2) (N)
    Label:13C(5)15N(2) (Q)
    Label:13C(6)15N(3) (H)
    Label:13C(11)15N(2) (W)
    Label:13C(12)15N(3) (O)
     */

    public static boolean isWildcardModification (PxModification pxMod)
    {
        if (pxMod != null && pxMod.isIsotopicMod())
        {
            IsotopeModification skylineMod = IsotopeModification.create(TargetedMSService.get().getIsotopeModification(pxMod.getDbModId()));
            return label13C.equals(skylineMod) || label15N.equals(skylineMod) || label13C15N.equals(skylineMod);
        }
        return false;
    }

    public static List<UnimodModification> getMatchesIfWildcardSkylineMod(PxModification pxMod, List<Character> aminoAcidSites)
    {
        if (pxMod != null && pxMod.isIsotopicMod())
        {
            IsotopeModification skylineMod = IsotopeModification.create(TargetedMSService.get().getIsotopeModification(pxMod.getDbModId()));
            return getMatchesIfWildcardModification(skylineMod, aminoAcidSites);
        }
        return Collections.emptyList();
    }

    private static List<UnimodModification> getMatchesIfWildcardModification (IsotopeModification skylineMod, List<Character> aminoAcidSites)
    {
        if (label13C.equals(skylineMod)
        || label15N.equals(skylineMod)
        || label13C15N.equals(skylineMod))
        {
            Set<String> formulas = new HashSet<>();
            for (Character aaSite: aminoAcidSites)
            {
                formulas.add(buildIsotopeModFormula(skylineMod, aaSite));
            }
            UnimodModifications uMods = getUnimod();
            List<UnimodModification> matches = new ArrayList<>();
            for (String formula: formulas)
            {
                matches.addAll(uMods.getByFormula(formula));
            }
            matches.sort(Comparator.comparing(UnimodModification::getName));
            return matches;
        }
        return Collections.emptyList();
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testGetHardCodedModMatch()
        {
           var match = getMatchIfHardCodedMod(label13C615N2_CtermK);
            assertNotNull(match);
            assertEquals("Unexpected match for modification label13C615N2_CtermK", 259, match.getId());

            match = getMatchIfHardCodedMod(label13C615N4_CtermR);
            assertNotNull(match);
            assertEquals("Unexpected match for modification label13C615N4_CtermR", 267, match.getId());

            match = getMatchIfHardCodedMod(label13C6_CtermK);
            assertNotNull(match);
            assertEquals("Unexpected match for modification label13C6_CtermK", 188, match.getId());

            match = getMatchIfHardCodedMod(label13C6_CtermR);
            assertNotNull(match);
            assertEquals("Unexpected match for modification label13C6_CtermR", 188, match.getId());
        }

        @Test
        public void testGetWildcardModMatches()
        {
            var allAminoAcids = getUnimod().getAminoAcids();
            var matches = getMatchesIfWildcardModification(label15N, allAminoAcids);
            // "UNIMOD:994, Label:15N(1), N' - N, Sites: C:D:A:L:I:G:E:F:S:T:P:M:Y:V, Isotopic: true"
            // "UNIMOD:995, Label:15N(2), N'2 - N2, Sites: Q:N:K:W, Isotopic: true"
            // "UNIMOD:996, Label:15N(3), N'3 - N3, Sites: H, Isotopic: true"
            // "UNIMOD:897, Label:15N(4), N'4 - N4, Sites: R, Isotopic: true"
            assertEquals(4, matches.size());
            var matchIds = matches.stream().map(UnimodModification::getId).sorted().collect(Collectors.toList());
            var expectedMatchIds = List.of(994, 995, 996, 897).stream().sorted().collect(Collectors.toList());
            assertArrayEquals("Unexpected Unimod Ids for label15N", expectedMatchIds.toArray(), matchIds.toArray());


            matches = getMatchesIfWildcardModification(label13C, allAminoAcids);
            // "UNIMOD:1296, Label:13C(3), C'3 - C3, Sites: A, Isotopic: true"
            // "UNIMOD:1266, Label:13C(4), C'4 - C4, Sites: M, Isotopic: true"
            // "UNIMOD:772, Label:13C(5), C'5 - C5, Sites: P, Isotopic: true"
            // "UNIMOD:188, Label:13C(6), C'6 - C6, Sites: R:I:L:K, Isotopic: true"
            // "UNIMOD:184, Label:13C(9), C'9 - C9, Sites: Y:F, Isotopic: true"
            assertEquals(5, matches.size());
            matchIds = matches.stream().map(UnimodModification::getId).sorted().collect(Collectors.toList());
            expectedMatchIds = List.of(1296, 1266, 772, 188, 184).stream().sorted().collect(Collectors.toList());
            assertArrayEquals("Unexpected Unimod Ids for label13C", expectedMatchIds.toArray(), matchIds.toArray());


            matches = getMatchesIfWildcardModification(label13C15N, allAminoAcids);
            // "UNIMOD:1297, Label:13C(3)15N(1), C'3N' - C3N, Sites: A, Isotopic: true"
            // "UNIMOD:1298, Label:13C(4)15N(1), C'4N' - C4N, Sites: D, Isotopic: true"
            // "UNIMOD:268, Label:13C(5)15N(1), C'5N' - C5N, Sites: P:E:V:M, Isotopic: true"
            // "UNIMOD:695, Label:13C(6)15N(1), C'6N' - C6N, Sites: I:L, Isotopic: true"
            // "UNIMOD:259, Label:13C(6)15N(2), C'6N'2 - C6N2, Sites: K, Isotopic: true"
            // "UNIMOD:267, Label:13C(6)15N(4), C'6N'4 - C6N4, Sites: R, Isotopic: true"
            // "UNIMOD:269, Label:13C(9)15N(1), C'9N' - C9N, Sites: F, Isotopic: true"
            assertEquals(7, matches.size());
            matchIds = matches.stream().map(UnimodModification::getId).sorted().collect(Collectors.toList());
            expectedMatchIds = List.of(1297, 1298, 268, 695, 259, 267, 269).stream().sorted().collect(Collectors.toList());
            assertArrayEquals("Unexpected Unimod Ids for label13C15N", expectedMatchIds.toArray(), matchIds.toArray());
        }
    }
}

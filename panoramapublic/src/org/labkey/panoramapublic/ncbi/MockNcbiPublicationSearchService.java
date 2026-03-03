package org.labkey.panoramapublic.ncbi;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.Pair;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.ncbi.NcbiConstants.DB;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Mock implementation of {@link NcbiPublicationSearchService} that returns canned data for PMID 23689285
 * (Abbatiello et al., Mol Cell Proteomics 2013). Used by Selenium tests when NCBI is not reachable.
 */
public class MockNcbiPublicationSearchService implements NcbiPublicationSearchService
{
    private static final String PMID = "23689285";

    private static final String CITATION = "Abbatiello SE, Mani DR, Schilling B, Maclean B, Zimmerman LJ, " +
            "Feng X, Cusack MP, Sedransk N, Hall SC, Addona T, Allen S, Dodder NG, Ghosh M, Held JM, Hedrick V, " +
            "Inerowicz HD, Jackson A, Keshishian H, Kim JW, Lyssand JS, Riley CP, Rudnick P, Sadowski P, " +
            "Shaddox K, Smith D, Tomazela D, Wahlander A, Waldemarson S, Whitwell CA, You J, Zhang S, " +
            "Kinsinger CR, Mesri M, Rodriguez H, Borchers CH, Buck C, Fisher SJ, Gibson BW, Liebler D, " +
            "Maccoss M, Neubert TA, Paulovich A, Regnier F, Skates SJ, Tempst P, Wang M, Carr SA. " +
            "Design, implementation and multisite evaluation of a system suitability protocol for the quantitative " +
            "assessment of instrument performance in liquid chromatography-multiple reaction monitoring-MS (LC-MRM-MS). " +
            "Mol Cell Proteomics. 2013 Sep;12(9):2623-39. doi: 10.1074/mcp.M112.027078. Epub 2013 May 20. " +
            "PMID: 23689285; PMCID: PMC3769335.";

    private static final Date PUBLISHED_DATE;
    static
    {
        try
        {
            PUBLISHED_DATE = new SimpleDateFormat("yyyy MMM").parse("2013 Sep");
        }
        catch (ParseException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public @Nullable String getCitation(String publicationId, DB database)
    {
        if (PMID.equals(publicationId))
        {
            return CITATION;
        }
        return null;
    }

    @Override
    public @Nullable Pair<String, String> getPubMedLinkAndCitation(String pubmedId)
    {
        if (PMID.equals(pubmedId))
        {
            return new Pair<>(NcbiConstants.getPubmedLink(pubmedId), CITATION);
        }
        return null;
    }

    @Override
    public @Nullable PublicationMatch searchForPublication(@NotNull ExperimentAnnotations expAnnotations, @Nullable Logger logger)
    {
        List<PublicationMatch> matches = searchForPublication(expAnnotations, 1, logger, true);
        return matches.isEmpty() ? null : matches.get(0);
    }

    @Override
    public List<PublicationMatch> searchForPublication(@NotNull ExperimentAnnotations expAnnotations, int maxResults, @Nullable Logger logger, boolean getCitations)
    {
        PublicationMatch match = new PublicationMatch(
                PMID,
                DB.PubMed,
                false,   // foundByPxId (simulates finding via PXD010535)
                false,  // foundByUrl
                false,  // foundByDoi
                true,   // authorMatch
                true,   // titleMatch
                PUBLISHED_DATE
        );

        if (getCitations)
        {
            match.setCitation(CITATION);
        }

        return Collections.singletonList(match);
    }
}

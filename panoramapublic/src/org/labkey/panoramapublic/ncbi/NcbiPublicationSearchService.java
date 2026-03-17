package org.labkey.panoramapublic.ncbi;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.Pair;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.ncbi.NcbiConstants.DB;

import java.util.List;

/**
 * Service for searching PubMed Central (PMC) and PubMed for publications associated with Panorama Public datasets.
 */
public interface NcbiPublicationSearchService
{
    int MAX_RESULTS = 5;

    static NcbiPublicationSearchService get()
    {
        return NcbiPublicationSearchServiceImpl.getInstance();
    }

    @Nullable String getCitation(String publicationId, DB database);

    @Nullable Pair<String, String> getPubMedLinkAndCitation(String pubmedId);

    /**
     * Searches PMC and PubMed for a publication associated with the experiment.
     * Returns the top match (highest priority) if multiple matches are found, or null if none.
     */
    @Nullable PublicationMatch searchForPublication(@NotNull ExperimentAnnotations expAnnotations, @Nullable Logger logger);

    List<PublicationMatch> searchForPublication(@NotNull ExperimentAnnotations expAnnotations, int maxResults, @Nullable Logger logger, boolean getCitations);
}

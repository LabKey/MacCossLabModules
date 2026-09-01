/*
 * Copyright (c) 2026 LabKey Corporation
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

    /**
     * Send a minimal request to NCBI with the given key.
     */
    @NotNull NcbiApiKeyCheck checkApiKey(@Nullable String apiKey);

    @Nullable Pair<String, String> getPubMedLinkAndCitation(String pubmedId);

    /**
     * Searches PMC and PubMed for a publication associated with the experiment.
     * Returns the top match (highest priority) if multiple matches are found, or null if none.
     */
    /**
     * @throws NcbiSearchException if a request to NCBI fails. An empty result therefore means no
     * publication was found, not that the search could not be run.
     */
    @Nullable PublicationMatch searchForPublication(@NotNull ExperimentAnnotations expAnnotations, @Nullable Logger logger);

    /**
     * @throws NcbiSearchException if a request to NCBI fails. An empty result therefore means no
     * publication was found, not that the search could not be run.
     */
    List<PublicationMatch> searchForPublication(@NotNull ExperimentAnnotations expAnnotations, int maxResults, @Nullable Logger logger, boolean getCitations);
}

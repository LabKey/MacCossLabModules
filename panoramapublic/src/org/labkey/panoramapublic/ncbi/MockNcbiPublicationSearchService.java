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
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock implementation of {@link NcbiPublicationSearchService} that returns canned data registered by tests.
 * Used by Selenium tests when running on TeamCity.
 * Extends {@link NcbiPublicationSearchServiceImpl} and only overrides {@link #getString(String, Logger)},
 * the single method that makes HTTP calls to NCBI. All search logic, filtering, author/title
 * verification, citation parsing, and priority filtering run through the real implementation code.
 * Tests register mock articles via {@link #register}, providing the database, ID, search key,
 * metadata fields, and citation. The mock builds internal lookup maps from this data and returns
 * appropriate responses when the real search logic calls {@code getString()}.
 */
public class MockNcbiPublicationSearchService extends NcbiPublicationSearchServiceImpl
{
    // ESearch: searchKey -> list of IDs (per database)
    private final Map<String, List<String>> _pmcSearchResults = new HashMap<>();
    private final Map<String, List<String>> _pubmedSearchResults = new HashMap<>();

    // ESummary: ID -> metadata JSONObject (per database)
    private final Map<String, JSONObject> _pmcMetadata = new HashMap<>();
    private final Map<String, JSONObject> _pubmedMetadata = new HashMap<>();

    // Citations: PMID -> citation string
    private final Map<String, String> _citations = new HashMap<>();

    /**
     * Register a mock article. The mock stores the data in internal lookup maps used by
     * {@link #getString(String)}.
     * @param database     "pmc" or "pubmed" — the NCBI database this article is in
     * @param id           the article ID in the given database (numeric ID for pmc or pubmed)
     * @param searchKey    what ESearch query term finds this article (e.g. PXD ID for PMC, author last name for PubMed)
     * @param pmid         linked PubMed ID for PMC articles. PMC articles usually have a corresponding PMID in their
     *                     metadata (articleids array), which the real code extracts via {@code extractPubMedId()}.
     *                     Null for pubmed articles (where {@code id} is already the PMID).
     * @param title        article title
     * @param authors      comma-separated author list (e.g. "Abbatiello SE,Mani DR,Schilling B")
     * @param pubDate      publication date in "YYYY/MM/DD HH:MM" format, matching the NCBI ESummary
     *                     sortpubdate (PubMed) or sortdate (PMC) field that parsePublicationDate() checks first
     * @param source       journal abbreviation used as ESummary "source" field (e.g. "Mol Cell Proteomics", "bioRxiv")
     * @param journalFull  full journal name used as ESummary "fulljournalname" field
     * @param citation     NLM citation string (for getCitation, keyed by PMID; null if not applicable)
     */
    public void register(String database, String id, String searchKey,
                         @Nullable String pmid, String title, String authors,
                         String pubDate, String source, String journalFull,
                         @Nullable String citation)
    {
        boolean isPmc = "pmc".equalsIgnoreCase(database);

        // Build ESummary metadata
        JSONObject metadata = new JSONObject();
        metadata.put("title", title);
        metadata.put("sorttitle", title.toLowerCase());
        metadata.put("source", source);
        metadata.put("fulljournalname", journalFull);
        // Real NCBI ESummary returns sortpubdate (PubMed) or sortdate (PMC) in "YYYY/MM/DD HH:MM" format.
        // parsePublicationDate() checks these first before falling back to pubdate.
        metadata.put(isPmc ? "sortdate" : "sortpubdate", pubDate);

        // Authors array
        JSONArray authorsArray = new JSONArray();
        for (String author : authors.split(","))
        {
            authorsArray.put(new JSONObject().put("name", author.trim()));
        }
        metadata.put("authors", authorsArray);

        // For PMC articles, add articleids with PMC ID and optional PMID
        if (isPmc)
        {
            JSONArray articleIds = new JSONArray();
            articleIds.put(new JSONObject().put("idtype", "pmcid").put("value", "PMC" + id));
            if (pmid != null)
            {
                articleIds.put(new JSONObject().put("idtype", "pmid").put("value", pmid));
            }
            metadata.put("articleids", articleIds);
        }

        // Store in appropriate maps
        if (isPmc)
        {
            _pmcSearchResults.computeIfAbsent(searchKey, k -> new ArrayList<>()).add(id);
            _pmcMetadata.put(id, metadata);
        }
        else
        {
            _pubmedSearchResults.computeIfAbsent(searchKey, k -> new ArrayList<>()).add(id);
            _pubmedMetadata.put(id, metadata);
        }

        // Store citation keyed by PMID.
        // For PMC articles, the PMID is in the pmid parameter.
        // For PubMed articles, the id parameter is already the PMID.
        if (citation != null)
        {
            String citationKey = isPmc ? pmid : id;
            if (citationKey != null)
            {
                _citations.put(citationKey, citation);
            }
        }
    }

    /**
     * Returns canned responses for NCBI API requests based on registered mock data.
     * Handles ESearch, ESummary, and Citation Exporter URLs.
     */
    @Override
    protected String getString(String url, Logger log) throws IOException
    {
        if (url.contains("esearch.fcgi"))
        {
            return handleESearch(url).toString();
        }
        else if (url.contains("esummary.fcgi"))
        {
            return handleESummary(url).toString();
        }
        else if (url.contains("lit/ctxp"))
        {
            return handleCitation(url).toString();
        }
        throw new IOException("MockNcbiPublicationSearchService: unexpected URL: " + url);
    }

    private JSONObject handleESearch(String url)
    {
        boolean isPmc = "pmc".equals(extractQueryParam(url, "db"));
        Map<String, List<String>> searchMap = isPmc ? _pmcSearchResults : _pubmedSearchResults;

        // Match registered search keys against the decoded ESearch query term only, not the whole
        // URL, so a key cannot accidentally match part of another parameter (tool/email) or another
        // key. The real ESearch term wraps the key in quotes (e.g. "PXD056793"), so contains() on
        // the term is the right granularity.
        String term = extractQueryParam(url, "term");

        JSONArray idList = new JSONArray();
        if (term != null)
        {
            for (Map.Entry<String, List<String>> entry : searchMap.entrySet())
            {
                if (term.contains(entry.getKey()))
                {
                    entry.getValue().forEach(idList::put);
                }
            }
        }

        JSONObject esearchResult = new JSONObject();
        esearchResult.put("idlist", idList);
        return new JSONObject().put("esearchresult", esearchResult);
    }

    private JSONObject handleESummary(String url)
    {
        boolean isPmc = "pmc".equals(extractQueryParam(url, "db"));
        Map<String, JSONObject> metadataMap = isPmc ? _pmcMetadata : _pubmedMetadata;

        // ESummary requests a comma-separated list of IDs in the "id" parameter. Match registered
        // IDs against that list (exactly, not by substring), rather than scanning the whole URL.
        String idParam = extractQueryParam(url, "id");
        List<String> requestedIds = idParam == null ? List.of() : Arrays.asList(idParam.split(","));

        JSONObject result = new JSONObject();
        for (Map.Entry<String, JSONObject> entry : metadataMap.entrySet())
        {
            if (requestedIds.contains(entry.getKey()))
            {
                result.put(entry.getKey(), entry.getValue());
            }
        }

        return new JSONObject().put("result", result);
    }

    /**
     * Build a citation JSON response matching the NCBI Literature Citation Exporter format.
     * The real API returns {@code {"nlm":{"orig":"citation text..."}}}.
     * If no citation is registered for the ID, returns an empty JSON object.
     */
    private JSONObject handleCitation(String url)
    {
        String id = extractQueryParam(url, "id");
        String citation = id != null ? _citations.get(id) : null;
        if (citation != null)
        {
            return new JSONObject().put("nlm", new JSONObject().put("orig", citation));
        }
        return new JSONObject();
    }

    /**
     * Returns the URL-decoded value of the given query parameter, or null if it is not present.
     * Used to scope mock matching to a specific parameter (db, term, id) instead of the whole URL.
     */
    private static @Nullable String extractQueryParam(String url, String name)
    {
        int queryStart = url.indexOf('?');
        String query = queryStart >= 0 ? url.substring(queryStart + 1) : url;
        for (String pair : query.split("&"))
        {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name))
            {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}

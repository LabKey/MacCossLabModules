package org.labkey.panoramapublic.ncbi;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.panoramapublic.ncbi.NcbiConstants.DB;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock implementation of {@link NcbiPublicationSearchService} that returns canned data registered by tests.
 * Used by Selenium tests when running on TeamCity.
 * Extends {@link NcbiPublicationSearchServiceImpl} and only overrides the two methods that make HTTP calls
 * to NCBI: {@link #getJson(String)} (used by ESearch/ESummary) and {@link #getCitation(String, DB)}
 * (used for the Citation Exporter API). All search logic, filtering, author/title verification, and
 * priority filtering run through the real implementation code.
 * Tests register mock articles via {@link #register}, providing the database, ID, search key,
 * metadata fields, and citation. The mock builds internal lookup maps from this data and returns
 * appropriate JSON responses when the real search logic calls {@code getJson()} or {@code getCitation()}.
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
     * {@link #getJson(String)} and {@link #getCitation(String, DB)}.
     * @param database     "pmc" or "pubmed" — the NCBI database this article is in
     * @param id           the article ID in the given database (numeric ID for pmc or pubmed)
     * @param searchKey    what ESearch query term finds this article (e.g. PXD ID for PMC, author last name for PubMed)
     * @param pmid         linked PubMed ID for PMC articles. PMC articles usually have a corresponding PMID in their
     *                     metadata (articleids array), which the real code extracts via {@code extractPubMedId()}.
     *                     Null for pubmed articles (where {@code id} is already the PMID).
     * @param title        article title
     * @param authors      comma-separated author list (e.g. "Abbatiello SE,Mani DR,Schilling B")
     * @param pubDate      publication date string (e.g. "2013 Sep")
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
        metadata.put("pubdate", pubDate);

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
     * Returns canned JSON for NCBI ESearch and ESummary API requests based on registered mock data.
     */
    @Override
    protected JSONObject getJson(String url) throws IOException
    {
        if (url.contains("esearch.fcgi"))
        {
            return handleESearch(url);
        }
        else if (url.contains("esummary.fcgi"))
        {
            return handleESummary(url);
        }
        throw new IOException("MockNcbiPublicationSearchService: unexpected URL: " + url);
    }

    /**
     * Returns the registered citation for the given PubMed ID, or null if not registered.
     */
    @Override
    public @Nullable String getCitation(String pubMedId, DB database)
    {
        return _citations.get(pubMedId);
    }

    private JSONObject handleESearch(String url)
    {
        boolean isPmc = url.contains("db=pmc");
        Map<String, List<String>> searchMap = isPmc ? _pmcSearchResults : _pubmedSearchResults;

        JSONArray idList = new JSONArray();
        for (Map.Entry<String, List<String>> entry : searchMap.entrySet())
        {
            if (url.contains(entry.getKey()))
            {
                entry.getValue().forEach(idList::put);
            }
        }

        JSONObject esearchResult = new JSONObject();
        esearchResult.put("idlist", idList);
        return new JSONObject().put("esearchresult", esearchResult);
    }

    private JSONObject handleESummary(String url)
    {
        boolean isPmc = url.contains("db=pmc");
        Map<String, JSONObject> metadataMap = isPmc ? _pmcMetadata : _pubmedMetadata;

        JSONObject result = new JSONObject();
        for (Map.Entry<String, JSONObject> entry : metadataMap.entrySet())
        {
            if (url.contains(entry.getKey()))
            {
                result.put(entry.getKey(), entry.getValue());
            }
        }

        return new JSONObject().put("result", result);
    }
}

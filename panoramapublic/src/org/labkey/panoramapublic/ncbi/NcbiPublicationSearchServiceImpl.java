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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.panoramapublic.datacite.DataCiteService;
import org.labkey.panoramapublic.message.PrivateDataReminderSettings;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.ncbi.NcbiConstants.DB;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.panoramapublic.ncbi.PublicationMatch.MATCH_DOI;
import static org.labkey.panoramapublic.ncbi.PublicationMatch.MATCH_PANORAMA_URL;
import static org.labkey.panoramapublic.ncbi.PublicationMatch.MATCH_PX_ID;

/**
 * Real implementation of {@link NcbiPublicationSearchService} that searches NCBI via HTTP.
 */
public class NcbiPublicationSearchServiceImpl implements NcbiPublicationSearchService
{
    private static final Logger LOG = LogHelper.getLogger(NcbiPublicationSearchServiceImpl.class, "Search NCBI for publications associated with Panorama Public datasets");

    // Static holder for the service instance
    private static volatile NcbiPublicationSearchService _instance = new NcbiPublicationSearchServiceImpl();

    public static NcbiPublicationSearchService getInstance()
    {
        return _instance;
    }

    public static void setInstance(NcbiPublicationSearchService impl)
    {
        _instance = impl;
    }

    // NCBI API endpoints
    private static final String ESEARCH_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi";
    private static final String ESUMMARY_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi";

    // NCBI Literature Citation Exporter endpoints
    private static final String PUBMED_CITATION_EXPORTER_URL = "https://api.ncbi.nlm.nih.gov/lit/ctxp/v1/pubmed/?format=citation&id=";
    private static final String PMC_CITATION_EXPORTER_URL =    "https://api.ncbi.nlm.nih.gov/lit/ctxp/v1/pmc/?format=citation&id=";

    // API parameters
    private static final int RATE_LIMIT_DELAY_MS = 400; // NCBI allows 3 requests/sec
    private static final int TIMEOUT_MS = 10000; // 10 seconds

    // NCBI eutils intermittently returns transient 5xx errors and read timeouts, even well under
    // the rate limit. Retry those a few times with exponential backoff before giving up.
    private static final int MAX_HTTP_ATTEMPTS = 3; // initial try + 2 retries
    private static final int RETRY_BASE_DELAY_MS = 500; // exponential backoff base

    // NCBI suggests using the 'tool' and 'email' parameters on E-utilities URLs
    // https://www.nlm.nih.gov/dataguide/eutilities/utilities.html
    private static final String TOOL = "PanoramaPublic";
    private static final String EMAIL = "panorama@proteinms.net";

    // Preprint indicators
    private static final String[] PREPRINT_INDICATORS = {
            "preprint", "biorxiv", "medrxiv", "chemrxiv", "arxiv",
            "research square", "preprints.org"
    };

    // Title keyword extraction
    private static final int MIN_KEYWORD_LENGTH = 3;
    private static final double KEYWORD_MATCH_THRESHOLD = 0.6; // 60% of keywords must match
    private static final int MIN_KEYWORDS_FOR_MATCH = 2;

    // Stop words for title keyword matching. These words are not meaningful discriminators between papers.
    // Function words: articles, prepositions, conjunctions, auxiliary verbs, pronouns.
    // Title fillers: common words in paper titles that are not discriminating.
    private static final Set<String> TITLE_STOP_WORDS = Set.of(

            // Function words
            "the", "this", "that", "these", "those", "its",                          // articles
            "for", "with", "from", "into", "upon", "via", "after", "about",          // prepositions
            "between", "through", "across", "under", "over", "during",
            "and", "but", "than",                                                    // conjunctions
            "are", "was", "were", "been", "have", "has", "can", "may",               // auxiliary verbs
            "our", "their",                                                          // pronouns
            "not", "all", "also", "both", "each", "how", "use", "used",              // other function words
            "here", "well", "two", "one", "more", "most", "only", "such", "other", "which",

            // Title fillers
            "using", "based", "reveals", "revealed", "show", "shows", "shown",
            "role", "study", "studies"
    );

    private static Logger getLog(@Nullable Logger logger)
    {
        return logger != null ? logger : LOG;
    }

    @Override
    public @Nullable String getCitation(String publicationId, DB database)
    {
        return getCitation(publicationId, database, null);
    }

    public @Nullable String getCitation(String publicationId, DB database, @Nullable Logger logger)
    {
        Logger log = getLog(logger);

        if (publicationId == null || !publicationId.matches(NcbiConstants.PUBMED_ID))
        {
            return null;
        }

        String baseUrl = database == DB.PMC ? PMC_CITATION_EXPORTER_URL : PUBMED_CITATION_EXPORTER_URL;
        String queryUrl = baseUrl + publicationId;

        try
        {
            String response = getString(queryUrl, log);
            return parseCitation(response, publicationId, database, log);
        }
        catch (IOException e)
        {
            log.error("Error submitting a request to NCBI Literature Citation Exporter. URL: {}", queryUrl, e);
        }
        return null;
    }

    @Override
    public Pair<String, String> getPubMedLinkAndCitation(String pubmedId)
    {
        String citation = getCitation(pubmedId, DB.PubMed);
        return citation != null ? new Pair<>(NcbiConstants.getPubmedLink(pubmedId), citation) : null;
    }

    static String parseCitation(String response, String publicationId, DB database)
    {
        return parseCitation(response, publicationId, database, null);
    }

    static String parseCitation(String response, String publicationId, DB database, @Nullable Logger logger)
    {
        Logger log = getLog(logger);
        try
        {
            var jsonObject = new JSONObject(response);
            var nlmInfo = jsonObject.optJSONObject("nlm");
            return null != nlmInfo ? nlmInfo.getString("orig") : null;
        }
        catch (JSONException e)
        {
            log.error("Error parsing response from NCBI Literature Citation Exporter for {} ID {}", database.getLabel(), publicationId, e);
        }
        return null;
    }

    @Override
    public PublicationMatch searchForPublication(@NotNull ExperimentAnnotations expAnnotations, @Nullable Logger logger)
    {
        List<PublicationMatch> matches = searchForPublication(expAnnotations, 1, logger, true);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    @Override
    public List<PublicationMatch> searchForPublication(@NotNull ExperimentAnnotations expAnnotations, int maxResults, @Nullable Logger logger, boolean getCitations)
    {
        Logger log = getLog(logger);
        maxResults = Math.max(1, Math.min(maxResults, NcbiPublicationSearchService.MAX_RESULTS));
        log.info("Starting publication search for experiment: {}", expAnnotations.getId());

        // Search PubMed Central first
        List<PublicationMatch> matchedArticles = searchPmc(expAnnotations, log);

        // If no PMC results, fall back to PubMed
        if (matchedArticles.isEmpty())
        {
            log.info("No PMC articles found, trying PubMed fallback");
            matchedArticles = searchPubMed(expAnnotations, log);
        }

        // Build and return result
        if (matchedArticles.isEmpty())
        {
            log.info("No publications found");
            return Collections.emptyList();
        }

        if (matchedArticles.size() > maxResults)
        {
            matchedArticles = matchedArticles.subList(0, maxResults);
        }

        if (getCitations)
        {
            // Fetch citations for each match
            for (PublicationMatch match : matchedArticles)
            {
                rateLimit();
                match.setCitation(getCitation(match.getPublicationId(), match.getPublicationType(), log));
            }
        }

        log.info("Returning {}", StringUtilsLabKey.pluralize(matchedArticles.size(), "publication"));
        return matchedArticles;
    }

    /**
     * Search PubMed Central
     */
    private @NotNull List<PublicationMatch> searchPmc(@NotNull ExperimentAnnotations expAnnotations, Logger log)
    {
        Map<String, String> searchTermsByStrategy = buildSearchTerms(expAnnotations);

        // Search PMC using each strategy, accumulating results
        Map<String, List<String>> pmcIdsByStrategy = new HashMap<>();
        for (Map.Entry<String, String> entry : searchTermsByStrategy.entrySet())
        {
            String strategy = entry.getKey();
            String searchTerm = entry.getValue();
            log.debug("Searching PMC by {}: {}", strategy, searchTerm);
            List<String> ids = searchPmc(quote(searchTerm), log);
            if (!ids.isEmpty())
            {
                pmcIdsByStrategy.put(strategy, ids);
                log.debug("Found {} PMC articles by {}", ids.size(), strategy);
            }
            rateLimit();
        }

        // Build reverse map: PMC ID -> strategies that found it
        Map<String, List<String>> idToStrategies = new HashMap<>();
        pmcIdsByStrategy.forEach((strategy, ids) ->
                ids.forEach(id -> idToStrategies.computeIfAbsent(id, k -> new ArrayList<>()).add(strategy)));

        log.info("Total unique PMC IDs found: {}", idToStrategies.size());

        // Fetch and verify PMC articles
        List<PublicationMatch> pmcArticles = fetchAndVerifyPmcArticles(idToStrategies.keySet(), idToStrategies, expAnnotations, log);

        // Apply priority filtering
        return applyPriorityFiltering(pmcArticles, expAnnotations.getCreated(), log);
    }

    private @NotNull Map<String, String> buildSearchTerms(@NotNull ExperimentAnnotations expAnnotations)
    {
        Map<String, String> terms = new LinkedHashMap<>();

        if (!StringUtils.isBlank(expAnnotations.getPxid()))
            terms.put(MATCH_PX_ID, expAnnotations.getPxid());
        if (expAnnotations.getShortUrl() != null)
            terms.put(MATCH_PANORAMA_URL, expAnnotations.getShortUrl().renderShortURL());
        if (!StringUtils.isBlank(expAnnotations.getDoi()))
            terms.put(MATCH_DOI, expAnnotations.getDoi().startsWith("http")
                    ? expAnnotations.getDoi()
                    : DataCiteService.toUrl(expAnnotations.getDoi()));
        return terms;
    }

    /**
     * Search PubMed Central with the given query
     */
    private List<String> searchPmc(String query, Logger log)
    {
        return executeSearch(query, "pmc", log);
    }

    /**
     * Search PubMed with the given query
     */
    private List<String> searchPubMed(String query, Logger log)
    {
        return executeSearch(query, "pubmed", log);
    }

    /**
     * Execute search using NCBI ESearch API
     */
    private List<String> executeSearch(String query, String database, Logger log)
    {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = ESEARCH_URL +
            "?" + buildCommonParams(database) +
            "&term=" + encodedQuery +
            "&retmax=" + NcbiPublicationSearchService.MAX_RESULTS +
            "&retmode=json";

        try
        {
            JSONObject json = getJson(url, log);
            JSONObject eSearchResult = json.getJSONObject("esearchresult");
            JSONArray idList = eSearchResult.getJSONArray("idlist");

            List<String> ids = new ArrayList<>();
            for (int i = 0; i < idList.length(); i++)
            {
                ids.add(idList.getString(i));
            }
            return ids;
        }
        catch (IOException | JSONException e)
        {
            log.error("Error searching {} with query: {}", database, query, e);
            return Collections.emptyList();
        }
    }

    /**
     * Execute an HTTP GET request and parse the response as JSON.
     * @throws IOException if the request fails or the server returns a non-2xx response
     * @throws JSONException if the response body is not valid JSON
     */
    protected JSONObject getJson(String url, Logger log) throws IOException
    {
        return new JSONObject(getString(url, log));
    }

    /**
     * Execute an HTTP GET request and return the response body as a string. Retry warnings are
     * written to {@code log} so they land in the pipeline job log when invoked from the reminder
     * job (and in the server log for UI-triggered searches, which pass the static logger).
     * @throws IOException if the request fails or the server returns a non-2xx response
     */
    protected String getString(String url, Logger log) throws IOException
    {
        // Retry transient NCBI failures (5xx, read timeouts) with exponential backoff;
        // other failures (e.g. 4xx) are permanent and fail fast.
        for (int attempt = 1; ; attempt++)
        {
            try
            {
                return executeGet(url);
            }
            catch (IOException e)
            {
                if (attempt >= MAX_HTTP_ATTEMPTS || !isRetryable(e))
                {
                    throw e;
                }
                long delayMs = retryDelayMs(attempt);
                getLog(log).warn("NCBI request failed (attempt {} of {}); retrying in {} ms. URL: {}; cause: {}",
                        attempt, MAX_HTTP_ATTEMPTS, delayMs, url, e.toString());
                sleepMs(delayMs);
            }
        }
    }

    private String executeGet(String url) throws IOException
    {
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
            .setConnectTimeout(Timeout.ofMilliseconds(TIMEOUT_MS))
            .setSocketTimeout(Timeout.ofMilliseconds(TIMEOUT_MS))
            .build();

        RequestConfig requestConfig = RequestConfig.custom()
            .setResponseTimeout(Timeout.ofMilliseconds(TIMEOUT_MS))
            .build();

        BasicHttpClientConnectionManager connectionManager = new BasicHttpClientConnectionManager();
        connectionManager.setConnectionConfig(connectionConfig);

        try (CloseableHttpClient client = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(connectionManager)
                .build())
        {
            HttpGet getRequest = new HttpGet(url);
            return client.execute(getRequest, response -> {
                int status = response.getCode();
                if (status < 200 || status >= 300)
                {
                    // Read the body only for client errors; 5xx bodies are typically large,
                    // uninformative HTML error pages.
                    String body = (status >= 400 && status < 500 && response.getEntity() != null)
                            ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8)
                            : null;
                    throw new HttpResponseException(status, errorDetail(status, response.getReasonPhrase(), body));
                }
                return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            });
        }
    }

    /**
     * Builds the message for a non-2xx HttpResponseException. For client errors (4xx) the response
     * body is appended (e.g. NCBI's "API key invalid" message) so the cause is visible in the server
     * log; for other statuses only the reason phrase is used (5xx bodies are uninformative HTML).
     */
    private static String errorDetail(int status, String reasonPhrase, @Nullable String body)
    {
        if (status >= 400 && status < 500 && !StringUtils.isBlank(body))
        {
            return reasonPhrase + " - " + StringUtils.abbreviate(body.strip(), 500);
        }
        return reasonPhrase;
    }

    /**
     * Transient NCBI failures worth retrying: read timeouts and 5xx responses.
     * 4xx and other errors are treated as permanent and fail fast.
     */
    private static boolean isRetryable(IOException e)
    {
        if (e instanceof SocketTimeoutException)
        {
            return true;
        }
        if (e instanceof HttpResponseException hre)
        {
            return hre.getStatusCode() >= 500;
        }
        return false;
    }

    // Exponential backoff: 500ms after the 1st failure, 1000ms after the 2nd, etc. No jitter is
    // needed because both callers (the daily reminder job and the UI search) issue NCBI requests
    // sequentially, so a retry never collides with a sibling request from the same caller.
    private static long retryDelayMs(int attempt)
    {
        return (long) RETRY_BASE_DELAY_MS << (attempt - 1);
    }

    private static void sleepMs(long ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Fetch metadata for PMC articles using ESummary API
     */
    private Map<String, JSONObject> fetchPmcMetadata(Collection<String> pmcIds, Logger log)
    {
        return fetchMetadata(pmcIds, "pmc", log);
    }

    /**
     * Fetch metadata for PubMed articles using ESummary API
     */
    private Map<String, JSONObject> fetchPubMedMetadata(Collection<String> pmids, Logger log)
    {
        return fetchMetadata(pmids, "pubmed", log);
    }

    /**
     * Fetch metadata using NCBI ESummary API (batch request)
     */
    private Map<String, JSONObject> fetchMetadata(Collection<String> ids, String database, Logger log)
    {
        if (ids.isEmpty()) return Collections.emptyMap();

        String idString = String.join(",", ids);
        String url = ESUMMARY_URL +
            "?" + buildCommonParams(database) +
            "&id=" + URLEncoder.encode(idString, StandardCharsets.UTF_8) +
            "&retmode=json";

        try
        {
            JSONObject json = getJson(url, log);
            JSONObject result = json.optJSONObject("result");
            if (result == null) return Collections.emptyMap();

            // Extract metadata for each ID
            Map<String, JSONObject> metadata = new HashMap<>();
            for (String id : ids)
            {
                JSONObject articleData = result.optJSONObject(id);
                if (articleData != null)
                {
                    metadata.put(id, articleData);
                }
            }
            return metadata;
        }
        catch (IOException | JSONException e)
        {
            log.error("Error fetching {} metadata for IDs: {}", database, ids, e);
            return Collections.emptyMap();
        }
    }

    /**
     * Fetch and verify PMC articles (filter preprints, check author/title matches)
     */
    private List<PublicationMatch> fetchAndVerifyPmcArticles(
        Set<String> pmcIds,
        Map<String, List<String>> idToStrategies,
        ExperimentAnnotations expAnnotations,
        Logger log)
    {
        if (pmcIds.isEmpty()) return Collections.emptyList();

        // Fetch all metadata in batch
        Map<String, JSONObject> metadata = fetchPmcMetadata(pmcIds, log);

        List<PublicationMatch> articles = new ArrayList<>();
        String firstName = expAnnotations.getSubmitterUser() != null
            ? expAnnotations.getSubmitterUser().getFirstName() : null;
        String lastName = expAnnotations.getSubmitterUser() != null
            ? expAnnotations.getSubmitterUser().getLastName() : null;

        for (String pmcId : pmcIds)
        {
            JSONObject articleData = metadata.get(pmcId);
            if (articleData == null) continue;

            // Filter out preprints
            if (isPreprint(articleData))
            {
                String source = articleData.optString("source", "");
                String journal = articleData.optString("fulljournalname", "");
                log.info("Excluded preprint PMC{}: source=\"{}\", journal=\"{}\"", pmcId, source, journal);
                continue;
            }

            // Check author and title matches
            boolean authorMatch = checkAuthorMatch(articleData, firstName, lastName);
            boolean titleMatch = checkTitleMatch(articleData, expAnnotations.getTitle());

            // Extract PubMed ID, if found
            String pubMedId = extractPubMedId(articleData);
            String publicationId = pubMedId == null ? pmcId : pubMedId;
            DB publicationType = pubMedId == null ? DB.PMC : DB.PubMed;

            // Get strategies that found this article
            List<String> strategies = idToStrategies.getOrDefault(pmcId, Collections.emptyList());

            // Map strategies to boolean flags
            boolean foundByPxId = strategies.contains(MATCH_PX_ID);
            boolean foundByUrl = strategies.contains(MATCH_PANORAMA_URL);
            boolean foundByDoi = strategies.contains(MATCH_DOI);

            Date pubDate = parsePublicationDate(articleData, log);

            PublicationMatch article = new PublicationMatch(
                publicationId,
                publicationType,
                foundByPxId,
                foundByUrl,
                foundByDoi,
                authorMatch,
                titleMatch,
                pubDate
            );
            articles.add(article);

            log.info("PMC{} -> {} {} | Match info: {}", pmcId, publicationType, publicationId, article.getMatchInfo());
        }

        return articles;
    }

    /**
     * Apply priority filtering to narrow down results
     */
    static List<PublicationMatch> applyPriorityFiltering(List<PublicationMatch> articles, @NotNull Date referenceDate, Logger log)
    {
        if (articles.size() <= 1) return articles;

        // Priority 1: Filter to articles found by multiple data IDs (most reliable)
        List<PublicationMatch> multipleIds = articles.stream()
            .filter(a -> countDataIdMatches(a) >= 2)
            .collect(Collectors.toList());

        if (!multipleIds.isEmpty())
        {
            log.debug("Filtered to {} article(s) found by multiple IDs", multipleIds.size());
            articles = multipleIds;
        }

        if (articles.size() <= 1) return sortByDateProximity(articles, referenceDate);

        // Priority 2: Filter to articles with both Author AND Title match
        List<PublicationMatch> bothMatches = articles.stream()
            .filter(a -> a.matchesAuthor() && a.matchesTitle())
            .collect(Collectors.toList());

        if (!bothMatches.isEmpty())
        {
            log.debug("Filtered to {} article(s) with both Author and Title match", bothMatches.size());
            articles = bothMatches;
        }

        return sortByDateProximity(articles, referenceDate);
    }

    /**
     * Sort articles by proximity of publication date to the experiment creation date (proxy for data submission date)
     * Articles with dates closest to the experiment creation date come first.
     * Articles without a publication date are sorted to the end.
     */
    static List<PublicationMatch> sortByDateProximity(List<PublicationMatch> articles, @NotNull Date experimentDate)
    {
        if (articles.size() <= 1)
        {
            return articles;
        }
        long refTime = experimentDate.getTime();
        articles = new ArrayList<>(articles);
        articles.sort(Comparator.comparingLong(a ->
            a.getPublicationDate() != null ? Math.abs(a.getPublicationDate().getTime() - refTime) : Long.MAX_VALUE
        ));
        return articles;
    }

    private static int countDataIdMatches(PublicationMatch a)
    {
        int count = 0;
        if (a.matchesProteomeXchangeId()) count++;
        if (a.matchesPanoramaUrl()) count++;
        if (a.matchesDoi()) count++;
        return count;
    }

    /**
     * Fall back to PubMed search if PMC finds nothing
     */
    private List<PublicationMatch> searchPubMed(ExperimentAnnotations expAnnotations, Logger log)
    {
        String firstName = expAnnotations.getSubmitterUser() != null
            ? expAnnotations.getSubmitterUser().getFirstName() : null;
        String lastName = expAnnotations.getSubmitterUser() != null
            ? expAnnotations.getSubmitterUser().getLastName() : null;
        String title = expAnnotations.getTitle();

        if (StringUtils.isBlank(firstName) || StringUtils.isBlank(lastName) || StringUtils.isBlank(title))
        {
            log.info("Cannot perform PubMed fallback - missing author or title information");
            return Collections.emptyList();
        }

        // Search PubMed: "LastName FirstName[Author] AND Title NOT preprint[Publication Type]"
        // PubMed has no escape mechanism for special characters like brackets or parentheses —
        // the recommended approach is to remove them before searching.
        // See: https://pubmed.ncbi.nlm.nih.gov/help/
        String query = String.format("%s %s[Author] AND %s NOT preprint[Publication Type]",
            stripQuerySpecialChars(lastName), stripQuerySpecialChars(firstName), stripQuerySpecialChars(title));

        log.debug("PubMed fallback query: {}", query);
        List<String> pmids = searchPubMed(query, log);

        if (pmids.isEmpty())
        {
            log.info("PubMed fallback found no results");
            return Collections.emptyList();
        }

        log.info("PubMed fallback found {} result(s), verifying...", pmids.size());

        // Fetch metadata and verify
        Map<String, JSONObject> metadata = fetchPubMedMetadata(pmids, log);

        List<PublicationMatch> articles = new ArrayList<>();
        for (String pmid : pmids)
        {
            JSONObject articleData = metadata.get(pmid);
            if (articleData == null) continue;

            // Filter out preprints
            if (isPreprint(articleData))
            {
                log.info("Excluded preprint PMID {}", pmid);
                continue;
            }

            // Verify both author AND title match
            boolean authorMatch = checkAuthorMatch(articleData, firstName, lastName);
            boolean titleMatch = checkTitleMatch(articleData, title);

            // Only accept if BOTH match
            if (authorMatch && titleMatch)
            {
                Date pubDate = parsePublicationDate(articleData, log);
                PublicationMatch article = new PublicationMatch(
                    pmid,
                    DB.PubMed,
                    false,  // Not found by PX ID
                    false,  // Not found by Panorama URL
                    false,  // Not found by DOI
                    true,   // Matched by author
                    true,   // Matched by title
                    pubDate
                );
                articles.add(article);
                log.info("PMID {} verified with both Author and Title match", pmid);
            }
        }

        log.info("Verified {} of {} PubMed results", articles.size(), pmids.size());
        return articles;
    }

    /**
     * Check if article is a preprint
     */
    static boolean isPreprint(JSONObject metadata)
    {
        String source = metadata.optString("source", "").toLowerCase();
        String journal = metadata.optString("fulljournalname", "").toLowerCase();

        for (String indicator : PREPRINT_INDICATORS)
        {
            if (source.contains(indicator) || journal.contains(indicator))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if article matches the author (submitter).
     * NCBI ESummary returns author names in "LastName Initials" format (e.g. "Jones A", "van der Berg M").
     * We match by checking that the author name starts with the submitter's last name followed by a space,
     * and that the remainder starts with the submitter's first initial.
     * Diacritics are stripped before comparison so that "García" matches "Garcia".
     */
    static boolean checkAuthorMatch(JSONObject metadata, String firstName, String lastName)
    {
        if (StringUtils.isBlank(firstName) || StringUtils.isBlank(lastName))
        {
            return false;
        }

        JSONArray authors = metadata.optJSONArray("authors");
        if (authors == null || authors.isEmpty())
        {
            return false;
        }

        String firstInitial = stripDiacritics(firstName.substring(0, 1).toLowerCase());
        String lastNameLower = stripDiacritics(lastName.toLowerCase());

        for (int i = 0; i < authors.length(); i++)
        {
            JSONObject author = authors.optJSONObject(i);
            if (author == null) continue;

            String authorName = stripDiacritics(author.optString("name", "").toLowerCase());

            // Match format: "LastName FirstInitial" - require a space after the last name
            // to avoid prefix collisions (e.g. "Li" matching "Liang")
            if (authorName.startsWith(lastNameLower))
            {
                // Last name must be followed by a space (before initials) or be the entire name
                if (authorName.length() == lastNameLower.length())
                {
                    return true; // Exact last name match, no initials
                }
                if (authorName.charAt(lastNameLower.length()) == ' ')
                {
                    String afterLastName = authorName.substring(lastNameLower.length()).trim();
                    if (afterLastName.startsWith(firstInitial))
                    {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Strip diacritical marks from a string (e.g. "Villén" → "Villen", "Müller" → "Muller").
     * Uses Unicode NFD decomposition to separate base characters from combining marks,
     * then removes the marks.
     */
    static String stripDiacritics(String str)
    {
        if (str == null) return "";
        return Normalizer.normalize(str, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
    }

    /**
     * Check if article title matches the dataset title
     */
    static boolean checkTitleMatch(JSONObject metadata, String datasetTitle)
    {
        if (StringUtils.isBlank(datasetTitle))
        {
            return false;
        }

        // Try title first, then sorttitle as fallback
        String title = normalizeTitle(metadata.optString("title"));
        String normalizedArticleTitle = title.isEmpty()
            ? normalizeTitle(metadata.optString("sorttitle"))
            : title;

        if (normalizedArticleTitle.isEmpty())
        {
            return false;
        }

        String normalizedDatasetTitle = normalizeTitle(datasetTitle);

        // Try exact match first
        if (normalizedArticleTitle.equals(normalizedDatasetTitle))
        {
            return true;
        }

        // Bi-directional keyword matching: match if either direction meets the threshold.
        List<String> articleKeywords = extractTitleKeywords(normalizedArticleTitle);
        List<String> datasetKeywords = extractTitleKeywords(normalizedDatasetTitle);

        return keywordsMatch(datasetKeywords, articleKeywords)
            || keywordsMatch(articleKeywords, datasetKeywords);
    }

    /**
     * Check if at least 60% of {@code sourceKeywords} are present in {@code targetKeywords} (with a minimum of 2).
     */
    private static boolean keywordsMatch(List<String> sourceKeywords, List<String> targetKeywords)
    {
        if (sourceKeywords.size() < MIN_KEYWORDS_FOR_MATCH || targetKeywords.size() < MIN_KEYWORDS_FOR_MATCH)
        {
            return false;
        }

        Set<String> targetSet = new java.util.HashSet<>(targetKeywords);

        long matchCount = sourceKeywords.stream()
            .filter(targetSet::contains)
            .count();

        int required = (int) Math.ceil(sourceKeywords.size() * KEYWORD_MATCH_THRESHOLD);
        required = Math.max(required, Math.min(2, sourceKeywords.size()));
        return matchCount >= required;
    }

    /**
     * Normalize title for comparison: decode HTML entities (e.g. {@code &amp;}), strip HTML tags
     * (e.g. {@code <i>}, {@code </i>}), strip diacritics, lowercase, replace punctuation with space
     * (so "data-independent" becomes "data independent"), and normalize whitespace.
     * The tag regex {@code </?[a-zA-Z]+>} matches letter-only tag names with no attributes e.g. {@code <i>}, {@code <sub>}). 
     */
    static String normalizeTitle(String title)
    {
        if (title == null) return "";

        return stripDiacritics(StringEscapeUtils.unescapeHtml4(title.toLowerCase())
            .replaceAll("</?[a-zA-Z]+>", " "))       // Strip HTML tags (e.g. <i>, </i>, <sub>)
            .replaceAll("[^\\w\\s]", " ")            // Replace punctuation with space
            .replaceAll("\\s+", " ")                 // Normalize whitespace
            .trim();
    }

    /**
     * Extract meaningful keywords from title for matching.
     * Returns all qualifying words (no cap) — the caller uses a percentage threshold.
     */
    static List<String> extractTitleKeywords(String title)
    {
        if (StringUtils.isBlank(title))
        {
            return Collections.emptyList();
        }

        String[] words = title.toLowerCase().split("\\s+");
        List<String> keywords = new ArrayList<>();

        for (String word : words)
        {
            // Remove non-alphanumeric characters
            word = word.replaceAll("[^a-z0-9]", "");

            // Check length and exclude stop words
            if (word.length() >= MIN_KEYWORD_LENGTH && !TITLE_STOP_WORDS.contains(word))
            {
                keywords.add(word);
            }
        }

        return keywords;
    }

    /**
     * Extract PMID from PMC metadata
     */
    static @Nullable String extractPubMedId(JSONObject pmcMetadata)
    {
        // PMC articles contain PMID in articleids array
        JSONArray articleIds = pmcMetadata.optJSONArray("articleids");
        if (articleIds != null)
        {
            for (int i = 0; i < articleIds.length(); i++)
            {
                JSONObject idObj = articleIds.optJSONObject(i);
                if (idObj != null && "pmid".equals(idObj.optString("idtype")))
                {
                    return idObj.optString("value");
                }
            }
        }

        return null;
    }

    /**
     * Parse publication date from ESummary metadata.
     * Tries "sortpubdate" or "sortdate" first (format "YYYY/MM/DD HH:MM"), then falls back to
     * "pubdate" and "epubdate" (formats "YYYY Mon DD", "YYYY Mon", or "YYYY").
     */
    static @Nullable Date parsePublicationDate(JSONObject metadata, Logger log)
    {
        // Try sort dates first — these have a consistent "YYYY/MM/DD HH:MM" format
        for (String field : new String[]{"sortpubdate", "sortdate"})
        {
            String dateStr = metadata.optString(field, "").trim();
            if (!StringUtils.isBlank(dateStr))
            {
                try
                {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm");
                    sdf.setLenient(false);
                    return sdf.parse(dateStr);
                }
                catch (ParseException ignored)
                {
                }
            }
        }

        // Fall back to pubdate / epubdate
        String dateStr = "";
        for (String field : new String[]{"pubdate", "epubdate"})
        {
            dateStr = metadata.optString(field, "").trim();
            if (!StringUtils.isBlank(dateStr)) break;
        }
        if (StringUtils.isBlank(dateStr))
        {
            return null;
        }

        for (String format : new String[]{"yyyy MMM dd", "yyyy MMM", "yyyy"})
        {
            try
            {
                SimpleDateFormat sdf = new SimpleDateFormat(format);
                sdf.setLenient(false);
                return sdf.parse(dateStr);
            }
            catch (ParseException ignored)
            {
            }
        }
        log.debug("Unable to parse publication date: {}", dateStr);
        return null;
    }

    /**
     * Rate limiting: wait 400ms between API requests
     */
    private static void rateLimit()
    {
        sleepMs(RATE_LIMIT_DELAY_MS);
    }

    /**
     * Remove characters that have special meaning in PubMed query syntax (brackets, parentheses, quotes).
     * PubMed provides no escape mechanism for these characters. The recommended approach is to remove them.
     * See: https://pubmed.ncbi.nlm.nih.gov/help/
     */
    static String stripQuerySpecialChars(String value)
    {
        if (value == null) return "";
        return value.replaceAll("[\\[\\]()\"]", "").replaceAll("\\s+", " ").trim();
    }

    /**
     * Build the common NCBI API parameters (db, tool, email) with URL encoding.
     */
    private static String buildCommonParams(String database)
    {
        return buildCommonParams(database, PrivateDataReminderSettings.get().getNcbiApiKey());
    }

    // Builds the shared eutils query parameters. The API key is passed in (rather than looked up)
    // so this can be unit tested without a running server.
    private static String buildCommonParams(String database, @Nullable String apiKey)
    {
        String params = "db=" + URLEncoder.encode(database, StandardCharsets.UTF_8) +
            "&tool=" + URLEncoder.encode(TOOL, StandardCharsets.UTF_8) +
            "&email=" + URLEncoder.encode(EMAIL, StandardCharsets.UTF_8);

        // An NCBI API key (configured in the Private Data Reminder Settings) raises the eutils
        // rate limit from 3 to 10 requests/sec. Append it when one has been entered.
        if (!StringUtils.isBlank(apiKey))
        {
            params += "&api_key=" + URLEncoder.encode(apiKey.trim(), StandardCharsets.UTF_8);
        }
        return params;
    }

    /**
     * Wrap string in quotes for exact phrase match search.
     * Any embedded double quotes are removed since NCBI provides no escape mechanism for them.
     */
    private static String quote(String str)
    {
        return "\"" + str.replace("\"", "") + "\"";
    }

    public static class TestCase extends Assert
    {
        // -- parseCitation tests --

        @Test
        public void testParseCitation()
        {
            // Valid NLM citation response
            String json = "{\"nlm\":{\"orig\":\"Abbatiello SE, Mani DR. Mol Cell Proteomics. 2013 Sep;12(9):2623-39. PMID: 23689285; PMCID: PMC3769335.\"}}";
            assertEquals("Abbatiello SE, Mani DR. Mol Cell Proteomics. 2013 Sep;12(9):2623-39. PMID: 23689285; PMCID: PMC3769335.", parseCitation(json, "23689285", DB.PubMed));

            // Missing nlm key
            assertNull(parseCitation("{\"ama\":{\"orig\":\"something\"}}", "23689285", DB.PubMed));

            // Malformed JSON
            assertNull(parseCitation("not json", "23689285", DB.PubMed));

            // Empty nlm object (missing "orig" key)
            assertNull(parseCitation("{\"nlm\":{}}", "23689285", DB.PubMed));
        }

        // -- isPreprint tests --

        @Test
        public void testIsPreprint()
        {
            // Non-preprint
            assertFalse(isPreprint(articleMetadata("J Proteome Res", "Journal of Proteome Research")));

            // Preprint by source
            assertTrue(isPreprint(articleMetadata("medRxiv", "")));
            assertTrue(isPreprint(articleMetadata("bioRxiv", "bioRxiv : the preprint server for biology")));

            // Preprint by journal name
            assertTrue(isPreprint(articleMetadata("", "Research Square")));
            assertTrue(isPreprint(articleMetadata("", "ChemRxiv preprint")));

            // Case insensitive
            assertTrue(isPreprint(articleMetadata("BIORXIV", "")));

            // Empty fields
            assertFalse(isPreprint(articleMetadata("", "")));
        }

        // -- checkAuthorMatch tests --

        @Test
        public void testCheckAuthorMatch()
        {
            // Standard match: "Jones A" matches firstName=Andrew, lastName=Jones
            JSONObject metadata = metadataWithAuthors("Smith CD", "Jones A", "Brown EF");
            assertTrue(checkAuthorMatch(metadata, "Andrew", "Jones"));

            // Match with multiple initials: "Jones AB" matches firstName=Andrew, lastName=Jones
            assertTrue(checkAuthorMatch(metadataWithAuthors("Jones AB"), "Andrew", "Jones"));

            // Match is case-insensitive
            assertTrue(checkAuthorMatch(metadataWithAuthors("jones a"), "Andrew", "Jones"));
            assertTrue(checkAuthorMatch(metadataWithAuthors("JONES A"), "andrew", "jones"));

            // Author with full first name: "Jones Andrew"
            assertTrue(checkAuthorMatch(metadataWithAuthors("Jones Andrew"), "Andrew", "Jones"));

            // No matching author
            assertFalse(checkAuthorMatch(metadataWithAuthors("Smith CD", "Brown EF"), "Andrew", "Jones"));

            // Blank first or last name
            assertFalse(checkAuthorMatch(metadataWithAuthors("Jones A"), "", "Jones"));
            assertFalse(checkAuthorMatch(metadataWithAuthors("Jones A"), "Andrew", ""));
            assertFalse(checkAuthorMatch(metadataWithAuthors("Jones A"), null, "Jones"));

            // Empty authors array
            assertFalse(checkAuthorMatch(new JSONObject(), "Andrew", "Jones"));

            // Short last name prefix collisions — last name must be followed by a space
            assertFalse("'Li' should not match 'Liang B'",
                    checkAuthorMatch(metadataWithAuthors("Liang B"), "Alice", "Li"));
            assertFalse("'Li' should not match 'Liu C'",
                    checkAuthorMatch(metadataWithAuthors("Liu C"), "Andrew", "Li"));
            assertTrue(checkAuthorMatch(metadataWithAuthors("Li A"), "Alice", "Li"));

            // Compound last names: hyphenated and apostrophe
            assertTrue(checkAuthorMatch(metadataWithAuthors("Smith-Jones A"), "Andrew", "Smith-Jones"));
            assertFalse(checkAuthorMatch(metadataWithAuthors("Smith-Jones A"), "Andrew", "Smith"));
            assertTrue(checkAuthorMatch(metadataWithAuthors("O'Brien K"), "Kate", "O'Brien"));

            // Name particles
            assertTrue(checkAuthorMatch(metadataWithAuthors("von Haller M"), "Mark", "von Haller"));
            assertTrue(checkAuthorMatch(metadataWithAuthors("de Silva R"), "Raj", "de Silva"));

            // Diacritics — accented characters match unaccented and vice versa
            assertTrue(checkAuthorMatch(metadataWithAuthors("Villén J"), "Judit", "Villén"));
            assertTrue(checkAuthorMatch(metadataWithAuthors("Villén J"), "Judit", "Villen"));
            assertTrue(checkAuthorMatch(metadataWithAuthors("Villen J"), "Judit", "Villén"));
            assertTrue(checkAuthorMatch(metadataWithAuthors("Müller E"), "Eric", "Muller"));
            assertTrue(checkAuthorMatch(metadataWithAuthors("Muller E"), "Eric", "Müller"));
        }

        // -- checkTitleMatch tests --

        @Test
        public void testCheckTitleMatchExact()
        {
            // Exact match (case-insensitive, punctuation-stripped)
            JSONObject metadata = metadataWithTitle("Carafe in-silico spectral library generation for DIA");
            assertTrue(checkTitleMatch(metadata, "Carafe in-silico spectral library generation for DIA"));
            assertTrue(checkTitleMatch(metadata, "carafe in silico spectral library generation for dia"));
            assertTrue(checkTitleMatch(metadata, "Carafe: in silico spectral library generation for DIA!"));
        }

        @Test
        public void testCheckTitleMatchKeywords()
        {
            // Article: "Carafe enables high quality in silico spectral library generation for data-independent acquisition proteomics"
            // Dataset: "Carafe: a tool for in silico spectral library generation for DIA proteomics"
            // Dataset keywords: "carafe", "tool", "silico", "spectral", "library", "generation", "dia", "proteomics" (8 keywords)
            // Matches in article: "carafe", "silico", "spectral", "library", "generation", "proteomics" (6 of 8 = 73%) -> pass
            JSONObject metadata = metadataWithTitle("Carafe enables high quality in silico spectral library generation for data-independent acquisition proteomics");
            assertTrue(checkTitleMatch(metadata, "Carafe: a tool for in silico spectral library generation for DIA proteomics"));

            // Keywords not present in article title —  below 60%
            assertFalse(checkTitleMatch(metadata, "Carafe for generating spectrum libraries from DIA data"));

            // Dataset title tool short
            assertFalse(checkTitleMatch(metadata, "Carafe"));
        }

        @Test
        public void testCheckTitleMatchBidirectional()
        {
            // Forward direction fails: dataset is very specific, article title is short.
            // Dataset keywords: "comprehensive", "phosphoproteomic", "analysis", "novel", "biomarkers", "lung", "cancer", "cell", "lines" (9)
            // Only "lung", "cancer" found in article (2 of 9 = 22%) -> forward fails
            //
            // Reverse direction passes: article keywords: "lung", "cancer", "proteomics" (3)
            // "lung" and "cancer" found in dataset (2 of 3 = 67%) -> passes 60% threshold
            JSONObject metadata = metadataWithTitle("Lung cancer proteomics");
            assertTrue(checkTitleMatch(metadata,
                    "Comprehensive phosphoproteomic analysis reveals novel biomarkers in lung cancer cell lines"));

            // Both directions fail — completely unrelated titles
            assertFalse(checkTitleMatch(metadataWithTitle("Cardiac tissue lipidomics"),
                    "Hepatic transcriptomics in zebrafish embryos"));
        }

        @Test
        public void testCheckTitleMatchEdgeCases()
        {
            // Blank dataset title
            assertFalse(checkTitleMatch(metadataWithTitle("Some article"), ""));
            assertFalse(checkTitleMatch(metadataWithTitle("Some article"), null));

            // Missing title in metadata
            assertFalse(checkTitleMatch(new JSONObject(), "Some title"));

            // Stop words in titles should be ignored — only meaningful keywords matter.
            // All words other than "Cats" and "Dogs" will be ignored
            assertFalse(checkTitleMatch(metadataWithTitle("The Role of Cats: A study based on all other studies"),
                    "The Role of Dogs: A study based on all other studies"));
        }

        @Test
        public void testCheckTitleMatchHyphens()
        {
            // "data-independent" in one title should match "data independent" in the other
            JSONObject metadata = metadataWithTitle("Data-independent acquisition proteomics workflow");
            assertTrue("Hyphenated should match unhyphenated",
                    checkTitleMatch(metadata, "Data independent acquisition proteomics workflow"));
        }

        @Test
        public void testCheckTitleMatchHtmlTags()
        {
            // NCBI sometimes returns titles with HTML tags like <i>in vivo</i>
            JSONObject metadata = metadataWithTitle("<i>In vivo</i> analysis of protein interactions");
            assertTrue("HTML tags should be stripped for matching",
                    checkTitleMatch(metadata, "In vivo analysis of protein interactions"));

            // Multiple tags
            metadata = metadataWithTitle("Role of <i>E. coli</i> chaperones in <b>protein</b> folding");
            assertTrue(checkTitleMatch(metadata, "Role of E. coli chaperones in protein folding"));
        }

        @Test
        public void testCheckTitleMatchDiacritics()
        {
            // Accented characters in titles
            assertTrue("Accented title should match unaccented",
                    checkTitleMatch(metadataWithTitle("Protéomique des échantillons hépatiques"),
                            "Proteomique des echantillons hepatiques"));
        }

        @Test
        public void testCheckTitleMatchSorttitleFallback()
        {
            // When "title" is missing, falls back to "sorttitle"
            JSONObject metadata = new JSONObject();
            metadata.put("title", "");
            metadata.put("sorttitle", "phosphoproteomics of lung cancer");
            assertTrue(checkTitleMatch(metadata, "Phosphoproteomics of Lung Cancer"));

            // When "title" is present, it is used (not "sorttitle")
            metadata = new JSONObject();
            metadata.put("title", "Phosphoproteomics of Lung Cancer");
            metadata.put("sorttitle", "completely different sort title");
            assertTrue(checkTitleMatch(metadata, "Phosphoproteomics of Lung Cancer"));
        }
        
        // -- extractTitleKeywords tests --

        @Test
        public void testExtractTitleKeywords()
        {
            // Normal title — all non-stop words >= 3 chars are extracted
            List<String> keywords = extractTitleKeywords("Novel phosphoproteomics workflow for cardiac tissue samples");
            assertTrue(keywords.contains("novel"));
            assertTrue(keywords.contains("phosphoproteomics"));
            assertTrue(keywords.contains("workflow"));
            assertTrue(keywords.contains("cardiac"));
            assertTrue(keywords.contains("tissue"));
            assertTrue(keywords.contains("samples"));
            assertFalse("'for' is too short", keywords.contains("for"));

            // Domain-specific words are NOT stop words — they should be kept
            keywords = extractTitleKeywords("Quantitative proteomics characterization identification analysis");
            assertTrue("'proteomics' is domain-specific, not a stop word", keywords.contains("proteomics"));
            assertTrue("'characterization' is domain-specific", keywords.contains("characterization"));
            assertTrue("'identification' is domain-specific", keywords.contains("identification"));
            assertTrue("'analysis' is domain-specific", keywords.contains("analysis"));
            assertTrue(keywords.contains("quantitative"));

            // Function words and title fillers are excluded
            keywords = extractTitleKeywords("the study using based reveals role");
            assertFalse("'the' is a function word", keywords.contains("the"));
            assertFalse("'study' is a title filler", keywords.contains("study"));
            assertFalse("'using' is a title filler", keywords.contains("using"));
            assertFalse("'based' is a title filler", keywords.contains("based"));
            assertFalse("'reveals' is a title filler", keywords.contains("reveals"));
            assertFalse("'role' is a title filler", keywords.contains("role"));

            // Short meaningful words (>= 3 chars) are kept
            keywords = extractTitleKeywords("DIA analysis of lung cell iron metabolism in mice");
            assertTrue("'dia' (3 chars) should be kept", keywords.contains("dia"));
            assertTrue("'lung' (4 chars) should be kept", keywords.contains("lung"));
            assertTrue("'cell' (4 chars) should be kept", keywords.contains("cell"));
            assertTrue("'iron' (4 chars) should be kept", keywords.contains("iron"));
            assertTrue("'mice' (4 chars) should be kept", keywords.contains("mice"));
            assertFalse("'of' (2 chars) is too short", keywords.contains("of"));
            assertFalse("'in' (2 chars) is too short", keywords.contains("in"));

            // No cap on number of keywords
            keywords = extractTitleKeywords("alpha bravo charlie delta foxtrot hotel india juliet kilo lima");
            assertEquals(10, keywords.size());

            // Blank input
            assertTrue(extractTitleKeywords("").isEmpty());
            assertTrue(extractTitleKeywords(null).isEmpty());
        }

        // -- normalizeTitle tests --

        @Test
        public void testNormalizeTitle()
        {
            assertEquals("hello world", normalizeTitle("Hello, World!"));
            assertEquals("test driven development", normalizeTitle("Test-Driven Development"));
            assertEquals("multiple spaces become one", normalizeTitle("  Multiple   spaces become one  "));
            assertEquals("", normalizeTitle(null));
            assertEquals("", normalizeTitle(""));

            // HTML tags are stripped
            assertEquals("in vivo analysis of protein", normalizeTitle("<i>in vivo</i> analysis of protein"));
            assertEquals("e coli proteomics", normalizeTitle("<i>E. coli</i> proteomics"));

            // HTML entities are decoded: &amp; replaced by space (not left as "amp")
            assertEquals("proteomics genomics", normalizeTitle("Proteomics &amp; Genomics"));
            assertEquals("e coli analysis", normalizeTitle("&lt;i&gt;E. coli&lt;/i&gt; analysis"));

            // Non-tag angle bracket expressions are NOT stripped by the tag regex; < and > are replaced by punctuation step.
            assertEquals("proteins 10 kda threshold", normalizeTitle("Proteins <10 kDa threshold"));
            assertEquals("proteins with mw 10 kda and 5 kda", normalizeTitle("Proteins with MW <10 kDa and >5 kDa"));

            // Apostrophes are replaced with space (like other punctuation)
            assertEquals("garcia s analysis", normalizeTitle("García's Analysis"));

            // Hyphens become spaces: "data-independent" matches "data independent"
            assertEquals("data independent acquisition", normalizeTitle("data-independent acquisition"));
            assertEquals("data independent acquisition", normalizeTitle("data independent acquisition"));
        }

        @Test
        public void testStripQuerySpecialChars()
        {
            // Parentheses removed entirely (not replaced with space)
            assertEquals("proteins analysis", stripQuerySpecialChars("protein(s) analysis"));
            assertEquals("novel approach", stripQuerySpecialChars("[novel] approach"));

            // Double quotes removed
            assertEquals("the omics revolution", stripQuerySpecialChars("the \"omics\" revolution"));

            // Null and empty
            assertEquals("", stripQuerySpecialChars(null));
            assertEquals("", stripQuerySpecialChars(""));

            // No special chars — unchanged
            assertEquals("Smith John", stripQuerySpecialChars("Smith John"));

            // Chars removed entirely — no space inserted between adjacent text
            assertEquals("ABC", stripQuerySpecialChars("A([B])C"));
        }

        @Test
        public void testQuote()
        {
            assertEquals("\"PXD012345\"", quote("PXD012345"));

            // Inner double quotes are removed
            assertEquals("\"title with quotes\"", quote("title with \"quotes\""));
        }

        // -- extractPubMedId tests --

        @Test
        public void testExtractPubMedId()
        {
            // PMC metadata with PMID
            JSONObject metadata = new JSONObject();
            JSONArray articleIds = new JSONArray();
            articleIds.put(new JSONObject().put("idtype", "pmcid").put("value", "PMC1234567"));
            articleIds.put(new JSONObject().put("idtype", "pmid").put("value", "28691345"));
            metadata.put("articleids", articleIds);
            assertEquals("28691345", extractPubMedId(metadata));

            // No PMID in articleids
            metadata = new JSONObject();
            articleIds = new JSONArray();
            articleIds.put(new JSONObject().put("idtype", "pmcid").put("value", "PMC1234567"));
            metadata.put("articleids", articleIds);
            assertNull(extractPubMedId(metadata));

            // No articleids key
            assertNull(extractPubMedId(new JSONObject()));
        }

        // -- parsePublicationDate tests --

        @Test
        public void testParsePublicationDate()
        {
            // sortpubdate is preferred (format "YYYY/MM/DD HH:MM")
            assertNotNull(parsePublicationDate(metadataWithDate("sortpubdate", "2025/11/06 00:00"), LOG));

            // sortdate is next
            assertNotNull(parsePublicationDate(metadataWithDate("sortdate", "2025/11/06 00:00"), LOG));

            // sortpubdate takes priority over pubdate
            JSONObject metadata = new JSONObject();
            metadata.put("sortpubdate", "2025/11/06 00:00");
            metadata.put("pubdate", "2024 Jan");
            Date parsed = parsePublicationDate(metadata, LOG);
            assertNotNull(parsed);
            assertTrue("sortpubdate should be used over pubdate", parsed.toString().contains("2025"));

            // Falls back to "pubdate" when sort dates are absent
            assertNotNull(parsePublicationDate(metadataWithDate("pubdate", "2024 Jan 15"), LOG));

            // "YYYY Mon" format
            assertNotNull(parsePublicationDate(metadataWithDate("pubdate", "2024 Jan"), LOG));

            // "YYYY" format
            assertNotNull(parsePublicationDate(metadataWithDate("pubdate", "2024"), LOG));

            // Falls back to epubdate when pubdate is blank
            assertNotNull(parsePublicationDate(metadataWithDate("epubdate", "2024 Mar 01"), LOG));

            // Unparseable date
            assertNull(parsePublicationDate(metadataWithDate("pubdate", "not-a-date"), LOG));

            // Empty date fields
            assertNull(parsePublicationDate(new JSONObject(), LOG));
        }

        // -- applyPriorityFiltering tests --

        @Test
        public void testApplyPriorityFiltering()
        {
            Date articleDate = new Date();

            // Single article returned as-is
            PublicationMatch single = createMatch("111", true, false, false, false, false, articleDate);
            List<PublicationMatch> result = applyPriorityFiltering(List.of(single), articleDate, LOG);
            assertEquals(1, result.size());

            // Articles found by multiple data IDs preferred over single-ID matches
            PublicationMatch multiFieldMatch = createMatch("222", true, true, false, true, true, articleDate);
            PublicationMatch singleFieldMatch = createMatch("333", true, false, false, true, true, articleDate);
            result = applyPriorityFiltering(List.of(singleFieldMatch, multiFieldMatch), articleDate, LOG);
            assertEquals(1, result.size());
            assertEquals("222", result.getFirst().getPublicationId());

            // Among single-ID matches, author+title both matching preferred
            PublicationMatch bothMatch = createMatch("444", true, false, false, true, true, articleDate);
            PublicationMatch authorOnly = createMatch("555", true, false, false, true, false, articleDate);
            result = applyPriorityFiltering(List.of(authorOnly, bothMatch), articleDate, LOG);
            assertEquals(1, result.size());
            assertEquals("444", result.getFirst().getPublicationId());
        }

        @Test
        public void testSortByDateProximity()
        {
            Date exptSubmitDate = new Date();
            Date closer = new Date(exptSubmitDate.getTime() - 86400000L); // 1 day before
            Date farther = new Date(exptSubmitDate.getTime() - 86400000L * 365); // 1 year before

            PublicationMatch farMatch = createMatch("111", true, false, false, false, false, farther);
            PublicationMatch closeMatch = createMatch("222", true, false, false, false, false, closer);
            PublicationMatch noDate = createMatch("333", true, false, false, false, false, null);

            List<PublicationMatch> result = sortByDateProximity(List.of(farMatch, noDate, closeMatch), exptSubmitDate);
            assertEquals("222", result.get(0).getPublicationId()); // closest
            assertEquals("111", result.get(1).getPublicationId()); // farther
            assertEquals("333", result.get(2).getPublicationId()); // no date last
        }

        // -- PublicationMatch round-trip tests --

        @Test
        public void testPublicationMatchRoundTrip()
        {
            PublicationMatch original = new PublicationMatch("12345", DB.PubMed, true, true, false, true, false, null);
            assertEquals("ProteomeXchange ID, Panorama URL, Author", original.getMatchInfo());

            PublicationMatch restored = PublicationMatch.fromMatchInfo("12345", DB.PubMed, original.getMatchInfo());
            assertTrue(restored.matchesProteomeXchangeId());
            assertTrue(restored.matchesPanoramaUrl());
            assertFalse(restored.matchesDoi());
            assertTrue(restored.matchesAuthor());
            assertFalse(restored.matchesTitle());

            // All flags
            original = new PublicationMatch("67890", DB.PMC, true, true, true, true, true, null);
            assertEquals("ProteomeXchange ID, Panorama URL, DOI, Author, Title", original.getMatchInfo());
            restored = PublicationMatch.fromMatchInfo("67890", DB.PMC, original.getMatchInfo());
            assertTrue(restored.matchesProteomeXchangeId());
            assertTrue(restored.matchesPanoramaUrl());
            assertTrue(restored.matchesDoi());
            assertTrue(restored.matchesAuthor());
            assertTrue(restored.matchesTitle());

            // Empty match info
            restored = PublicationMatch.fromMatchInfo("11111", DB.PubMed, "");
            assertFalse(restored.matchesProteomeXchangeId());
            assertFalse(restored.matchesAuthor());

            // Null match info
            restored = PublicationMatch.fromMatchInfo("11111", DB.PubMed, null);
            assertFalse(restored.matchesProteomeXchangeId());
        }

        // -- HTTP retry tests --

        @Test
        public void testIsRetryable()
        {
            // Read timeouts and 5xx responses are transient NCBI failures -> retry
            assertTrue(isRetryable(new SocketTimeoutException("Read timed out")));
            assertTrue(isRetryable(new HttpResponseException(500, "Internal Server Error")));
            assertTrue(isRetryable(new HttpResponseException(503, "Service Unavailable")));

            // 4xx and generic IO errors are permanent -> fail fast
            assertFalse(isRetryable(new HttpResponseException(400, "Bad Request")));
            assertFalse(isRetryable(new HttpResponseException(404, "Not Found")));
            assertFalse(isRetryable(new IOException("connection reset")));
        }

        @Test
        public void testRetryDelayMs()
        {
            // Exponential backoff: 500ms, 1000ms, 2000ms per attempt.
            assertEquals(500, retryDelayMs(1));
            assertEquals(1000, retryDelayMs(2));
            assertEquals(2000, retryDelayMs(3));
        }

        @Test
        public void testErrorDetail()
        {
            // 4xx: the response body is appended so the cause (e.g. an invalid API key) is logged
            String detail = errorDetail(400, "Bad Request", "{\"error\":\"API key invalid\"}");
            assertTrue(detail.contains("Bad Request"));
            assertTrue(detail.contains("API key invalid"));

            // 5xx: body omitted (uninformative)
            assertEquals("Internal Server Error", errorDetail(500, "Internal Server Error", "<html>oops</html>"));

            // 4xx with blank or null body: just the reason phrase, no trailing separator
            assertEquals("Bad Request", errorDetail(400, "Bad Request", ""));
            assertEquals("Bad Request", errorDetail(400, "Bad Request", null));
        }

        @Test
        public void testBuildCommonParams()
        {
            // Always includes db, tool, email
            String params = buildCommonParams("pmc", null);
            assertTrue(params.contains("db=pmc"));
            assertTrue(params.contains("tool=" + TOOL));
            assertTrue(params.contains("email="));

            // No api_key when the key is null, empty, or blank
            assertFalse("api_key should be absent when no key is set", params.contains("api_key"));
            assertFalse(buildCommonParams("pmc", "").contains("api_key"));
            assertFalse(buildCommonParams("pmc", "   ").contains("api_key"));

            // api_key appended (and trimmed) when a key is set
            assertTrue(buildCommonParams("pubmed", "ABC123").contains("api_key=ABC123"));
            assertTrue(buildCommonParams("pmc", "  ABC123  ").contains("api_key=ABC123"));
        }

        // -- Helper methods for building test JSON --

        private static JSONObject articleMetadata(String source, String fullJournalName)
        {
            JSONObject metadata = new JSONObject();
            metadata.put("source", source);
            metadata.put("fulljournalname", fullJournalName);
            return metadata;
        }

        private static JSONObject metadataWithAuthors(String... authorNames)
        {
            JSONObject metadata = new JSONObject();
            JSONArray authors = new JSONArray();
            for (String name : authorNames)
            {
                authors.put(new JSONObject().put("name", name));
            }
            metadata.put("authors", authors);
            return metadata;
        }

        private static JSONObject metadataWithTitle(String title)
        {
            JSONObject metadata = new JSONObject();
            metadata.put("title", title);
            return metadata;
        }

        private static JSONObject metadataWithDate(String field, String dateStr)
        {
            JSONObject metadata = new JSONObject();
            metadata.put(field, dateStr);
            return metadata;
        }

        private static PublicationMatch createMatch(String id, boolean pxId, boolean url, boolean doi,
                                                    boolean author, boolean title, @Nullable Date pubDate)
        {
            return new PublicationMatch(id, DB.PubMed, pxId, url, doi, author, title, pubDate);
        }
    }
}

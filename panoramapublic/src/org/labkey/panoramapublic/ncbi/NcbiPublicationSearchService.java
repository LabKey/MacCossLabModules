package org.labkey.panoramapublic.ncbi;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.ncbi.NcbiConstants.DB;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.panoramapublic.ncbi.PublicationMatch.MATCH_DOI;
import static org.labkey.panoramapublic.ncbi.PublicationMatch.MATCH_PANORAMA_URL;
import static org.labkey.panoramapublic.ncbi.PublicationMatch.MATCH_PX_ID;

/**
 * Service for searching PubMed Central (PMC) and PubMed for publications associated with private Panorama Public datasets.
 */
public class NcbiPublicationSearchService
{
    private static final Logger LOG = LogHelper.getLogger(NcbiPublicationSearchService.class, "Search NCBI for publications associated with Panorama Public datasets");

    // NCBI API endpoints
    private static final String ESEARCH_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi";
    private static final String ESUMMARY_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi";

    // NCBI Literature Citation Exporter endpoints
    private static final String PUBMED_CITATION_EXPORTER_URL = "https://api.ncbi.nlm.nih.gov/lit/ctxp/v1/pubmed/?format=citation&id=";
    private static final String PMC_CITATION_EXPORTER_URL =    "https://api.ncbi.nlm.nih.gov/lit/ctxp/v1/pmc/?format=citation&id=";

    // API parameters
    private static final int RATE_LIMIT_DELAY_MS = 400; // NCBI allows 3 requests/sec
    public static final int MAX_RESULTS = 5;
    private static final int TIMEOUT_MS = 10000; // 10 seconds
    private static final String NCBI_EMAIL = "panorama@proteinms.net";

    // Preprint indicators
    private static final String[] PREPRINT_INDICATORS = {
            "preprint", "biorxiv", "medrxiv", "chemrxiv", "arxiv",
            "research square", "preprints.org"
    };

    // Title keyword extraction
    private static final int MIN_KEYWORD_LENGTH = 3;
    private static final double KEYWORD_MATCH_THRESHOLD = 0.6; // 60% of keywords must match

    // Stop words for title keyword matching. These words are not meaningful discriminators between papers.
    // Function words: articles, prepositions, conjunctions, auxiliary verbs, pronouns.
    // Title fillers: common words in paper titles that are not discriminating.
    private static final Set<String> TITLE_STOP_WORDS = Set.of(

            // Function words
            "the", "this", "that", "these", "those", "its",                          // articles / determiners
            "for", "with", "from", "into", "upon", "via", "after", "about",          // prepositions
            "between", "through", "across", "under", "over", "during",
            "and", "but", "than",                                                    // conjunctions
            "are", "was", "were", "been", "have", "has", "can", "may",               // auxiliary / modal verbs
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

    /**
     * Fetches the NLM-style citation for a publication using the NCBI Literature Citation Exporter.
     * Supports both PubMed and PMC publication types.
     * @param publicationId numeric publication ID (e.g. "28691345")
     * @param database PubMed or PMC
     * @return the NLM citation string, or null if the lookup fails or the ID is invalid
     */
    public static @Nullable String getCitation(String publicationId, DB database)
    {
        if (publicationId == null || !publicationId.matches(NcbiConstants.PUBMED_ID))
        {
            return null;
        }

        String baseUrl = database == DB.PMC ? PMC_CITATION_EXPORTER_URL : PUBMED_CITATION_EXPORTER_URL;
        String queryUrl = baseUrl + publicationId;

        HttpURLConnection conn = null;
        try
        {
            URL url = new URL(queryUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            int status = conn.getResponseCode();

            if (status == HttpURLConnection.HTTP_OK)
            {
                String response;
                try (InputStream in = conn.getInputStream())
                {
                    response = IOUtils.toString(in, StandardCharsets.UTF_8);
                }
                return parseCitation(response, publicationId, database);
            }
        }
        catch (IOException e)
        {
            LOG.error("Error submitting a request to NCBI Literature Citation Exporter. URL: " + queryUrl, e);
        }
        finally
        {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    /**
     * Does a citation lookup for the given PubMedId using the NCBI's Literature Citation Exporter.
     * @param pubmedId PubMed Id
     * @return the PubMed link and the NLM-style citation if the lookup is successful
     */
    public static Pair<String, String> getPubMedLinkAndCitation(String pubmedId)
    {
        String citation = getCitation(pubmedId, DB.PubMed);
        return citation != null ? new Pair<>(NcbiConstants.getPubmedLink(pubmedId), citation) : null;
    }

    private static String parseCitation(String response, String publicationId, DB database)
    {
        try
        {
            var jsonObject = new JSONObject(response);
            var nlmInfo = jsonObject.optJSONObject("nlm");
            return null != nlmInfo ? nlmInfo.getString("orig") : null;
        }
        catch (JSONException e)
        {
            LOG.error("Error parsing response from NCBI Literature Citation Exporter for " + database.getLabel() + " ID " + publicationId, e);
        }
        return null;
    }

    /**
     * Search for publications associated with the given experiment.
     * Returns the top result if multiple results are found
     * @param logger optional logger; when null, uses the class logger
     */
    public static PublicationMatch searchForPublication(@NotNull ExperimentAnnotations expAnnotations, @Nullable Logger logger)
    {
        List<PublicationMatch> matches = searchForPublication(expAnnotations, 1, logger, true);
        return matches.isEmpty() ? null : matches.get(0);
    }

    /**
     * Search for publications associated with the given experiment.
     * Search PubMed Central first, then fall back to PubMed if needed.
     * @param maxResults maximum number of article matches to return (capped at 5)
     * @param logger optional logger; when null, uses the class logger
     */
    public static List<PublicationMatch> searchForPublication(@NotNull ExperimentAnnotations expAnnotations, int maxResults, @Nullable Logger logger, boolean getCitations)
    {
        Logger log = getLog(logger);
        maxResults = Math.max(1, Math.min(maxResults, MAX_RESULTS));
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
                match.setCitation(getCitation(match.getPublicationId(), match.getPublicationType()));
            }
        }

        log.info("Returning {}", StringUtilsLabKey.pluralize(matchedArticles.size(), "publication"));
        return matchedArticles;
    }

    /**
     * Search PubMed Central
     */
    private static @NotNull List<PublicationMatch> searchPmc(@NotNull ExperimentAnnotations expAnnotations, Logger log)
    {
        // Track PMC IDs found by each strategy
        Map<String, List<String>> pmcIdsByStrategy = new HashMap<>();

        // Step 1: Search PMC by PX ID
        if (!StringUtils.isBlank(expAnnotations.getPxid()))
        {
            log.debug("Searching PMC by PX ID: {}", expAnnotations.getPxid());
            List<String> ids = searchPmc(quote(expAnnotations.getPxid()), log);
            if (!ids.isEmpty())
            {
                pmcIdsByStrategy.put(MATCH_PX_ID, ids);
                log.debug("Found {} PMC articles by PX ID", ids.size());
            }
            rateLimit();
        }

        // Step 2: Search PMC by Panorama URL
        if (expAnnotations.getShortUrl() != null)
        {
            String panoramaUrl = expAnnotations.getShortUrl().renderShortURL();
            log.debug("Searching PMC by Panorama URL: {}", panoramaUrl);
            List<String> ids = searchPmc(quote(panoramaUrl), log);
            if (!ids.isEmpty())
            {
                pmcIdsByStrategy.put(MATCH_PANORAMA_URL, ids);
                log.debug("Found {} PMC articles by Panorama URL", ids.size());
            }
            rateLimit();
        }

        // Step 3: Search PMC by DOI
        if (!StringUtils.isBlank(expAnnotations.getDoi()))
        {
            String doiUrl = expAnnotations.getDoi().startsWith("http")
                ? expAnnotations.getDoi()
                : DataCiteService.toUrl(expAnnotations.getDoi());
            log.debug("Searching PMC by DOI: {}", doiUrl);
            List<String> ids = searchPmc(quote(doiUrl), log);
            if (!ids.isEmpty())
            {
                pmcIdsByStrategy.put(MATCH_DOI, ids);
                log.debug("Found {} PMC articles by DOI", ids.size());
            }
            rateLimit();
        }

        // Accumulate unique PMC IDs and track which strategies found each
        Set<String> uniquePmcIds = new HashSet<>();
        Map<String, List<String>> idToStrategies = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : pmcIdsByStrategy.entrySet())
        {
            String strategy = entry.getKey();
            for (String id : entry.getValue())
            {
                uniquePmcIds.add(id);
                idToStrategies.computeIfAbsent(id, k -> new ArrayList<>()).add(strategy);
            }
        }

        log.info("Total unique PMC IDs found: {}", uniquePmcIds.size());

        // Fetch and verify PMC articles
        List<PublicationMatch> pmcArticles = fetchAndVerifyPmcArticles(uniquePmcIds, idToStrategies, expAnnotations, log);

        // Apply priority filtering
        return applyPriorityFiltering(pmcArticles, expAnnotations.getCreated(), log);
    }

    /**
     * Search PubMed Central with the given query
     */
    private static List<String> searchPmc(String query, Logger log)
    {
        return executeSearch(query, "pmc", log);
    }

    /**
     * Search PubMed with the given query
     */
    private static List<String> searchPubMed(String query, Logger log)
    {
        return executeSearch(query, "pubmed", log);
    }

    /**
     * Execute search using NCBI ESearch API
     */
    private static List<String> executeSearch(String query, String database, Logger log)
    {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = ESEARCH_URL +
            "?db=" + database +
            "&term=" + encodedQuery +
            "&retmax=" + MAX_RESULTS +
            "&retmode=json" +
            "&email=" + URLEncoder.encode(NCBI_EMAIL, StandardCharsets.UTF_8);

        try
        {
            JSONObject json = getJson(url);
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
    private static JSONObject getJson(String url) throws IOException
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
            HttpGet get = new HttpGet(url);
            return client.execute(get, response -> {
                int status = response.getCode();
                if (status < 200 || status >= 300)
                {
                    throw new HttpResponseException(status, response.getReasonPhrase());
                }
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                return new JSONObject(body);
            });
        }
    }

    /**
     * Fetch metadata for PMC articles using ESummary API
     */
    private static Map<String, JSONObject> fetchPmcMetadata(Collection<String> pmcIds, Logger log)
    {
        return fetchMetadata(pmcIds, "pmc", log);
    }

    /**
     * Fetch metadata for PubMed articles using ESummary API
     */
    private static Map<String, JSONObject> fetchPubMedMetadata(Collection<String> pmids, Logger log)
    {
        return fetchMetadata(pmids, "pubmed", log);
    }

    /**
     * Fetch metadata using NCBI ESummary API (batch request)
     */
    private static Map<String, JSONObject> fetchMetadata(Collection<String> ids, String database, Logger log)
    {
        if (ids.isEmpty()) return Collections.emptyMap();

        String idString = String.join(",", ids);
        String url = ESUMMARY_URL +
            "?db=" + database +
            "&id=" + URLEncoder.encode(idString, StandardCharsets.UTF_8) +
            "&retmode=json" +
            "&email=" + URLEncoder.encode(NCBI_EMAIL, StandardCharsets.UTF_8);

        try
        {
            JSONObject json = getJson(url);
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
    private static List<PublicationMatch> fetchAndVerifyPmcArticles(
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

            // Extract PubMed ID, is found
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
    private static List<PublicationMatch> applyPriorityFiltering(List<PublicationMatch> articles, @NotNull Date referenceDate, Logger log)
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
     * Sort articles by proximity of publication date to the reference date (experiment creation date).
     * Articles with dates closest to the reference date come first.
     * Articles without a publication date are sorted to the end.
     */
    private static List<PublicationMatch> sortByDateProximity(List<PublicationMatch> articles, @NotNull Date referenceDate)
    {
        if (articles.size() <= 1)
        {
            return articles;
        }
        long refTime = referenceDate.getTime();
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
    private static List<PublicationMatch> searchPubMed(ExperimentAnnotations expAnnotations, Logger log)
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
        String query = String.format("%s %s[Author] AND %s NOT preprint[Publication Type]",
            lastName, firstName, title);

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
    private static boolean isPreprint(JSONObject metadata)
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
     * Check if article matches the author (submitter)
     */
    private static boolean checkAuthorMatch(JSONObject metadata, String firstName, String lastName)
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

        String firstInitial = firstName.substring(0, 1).toLowerCase();
        String lastNameLower = lastName.toLowerCase();

        for (int i = 0; i < authors.length(); i++)
        {
            JSONObject author = authors.optJSONObject(i);
            if (author == null) continue;

            String authorName = author.optString("name", "").toLowerCase();

            // Match format: "LastName FirstInitial" or "LastName F"
            if (authorName.startsWith(lastNameLower))
            {
                String afterLastName = authorName.substring(lastNameLower.length()).trim();
                if (afterLastName.startsWith(firstInitial))
                {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if article title matches the dataset title
     */
    private static boolean checkTitleMatch(JSONObject metadata, String datasetTitle)
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
        // This handles cases where one title is much more specific than the other.
        return keywordsMatch(datasetTitle, normalizedArticleTitle)
            || keywordsMatch(metadata.optString("title", metadata.optString("sorttitle", "")), normalizedDatasetTitle);
    }

    /**
     * Check if keywords extracted from {@code sourceTitle} are present in {@code normalizedTarget}.
     * Returns true if at least 60% of keywords match (with a minimum of 2).
     */
    private static boolean keywordsMatch(String sourceTitle, String normalizedTarget)
    {
        List<String> keywords = extractTitleKeywords(sourceTitle);
        if (keywords.isEmpty())
        {
            return false;
        }

        long matchCount = keywords.stream()
            .filter(keyword -> normalizedTarget.contains(keyword))
            .count();

        int required = (int) Math.ceil(keywords.size() * KEYWORD_MATCH_THRESHOLD);
        required = Math.max(required, Math.min(2, keywords.size()));
        return matchCount >= required;
    }

    /**
     * Normalize title for comparison (lowercase, remove punctuation, normalize whitespace)
     */
    private static String normalizeTitle(String title)
    {
        if (title == null) return "";

        return title.toLowerCase()
            .replaceAll("[^\\w\\s]", "")  // Remove punctuation
            .replaceAll("\\s+", " ")       // Normalize whitespace
            .trim();
    }

    /**
     * Extract meaningful keywords from title for matching.
     * Returns all qualifying words (no cap) — the caller uses a percentage threshold.
     */
    private static List<String> extractTitleKeywords(String title)
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
    private static @Nullable String extractPubMedId(JSONObject pmcMetadata)
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
     * Tries "pubdate" first, then "epubdate". Dates are typically in "YYYY Mon DD" or "YYYY Mon" format.
     */
    private static @Nullable Date parsePublicationDate(JSONObject metadata, Logger log)
    {
        String dateStr = metadata.optString("pubdate", "");
        if (StringUtils.isBlank(dateStr))
        {
            dateStr = metadata.optString("epubdate", "");
        }
        if (StringUtils.isBlank(dateStr))
        {
            return null;
        }

        // NCBI dates are typically "YYYY Mon DD", "YYYY Mon", or "YYYY"
        String[] formats = {"yyyy MMM dd", "yyyy MMM", "yyyy"};
        for (String format : formats)
        {
            try
            {
                return new SimpleDateFormat(format).parse(dateStr);
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
        try
        {
            Thread.sleep(RATE_LIMIT_DELAY_MS);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Wrap string in quotes for exact match search
     */
    private static String quote(String str)
    {
        return "\"" + str + "\"";
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
            assertEquals(null, parseCitation("{\"ama\":{\"orig\":\"something\"}}", "23689285", DB.PubMed));

            // Malformed JSON
            assertEquals(null, parseCitation("not json", "23689285", DB.PubMed));

            // Empty nlm object (missing "orig" key)
            assertEquals(null, parseCitation("{\"nlm\":{}}", "23689285", DB.PubMed));
        }

        // -- isPreprint tests --

        @Test
        public void testIsPreprint()
        {
            // Non-preprint
            assertFalse(isPreprint(articleMetadata("J Proteome Res", "Journal of Proteome Research")));

            // Preprint by source
            assertTrue(isPreprint(articleMetadata("bioRxiv", "bioRxiv")));
            assertTrue(isPreprint(articleMetadata("medRxiv", "")));

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
            // Standard match: "Sharma V" matches firstName=Vagisha, lastName=Sharma
            JSONObject metadata = metadataWithAuthors("Sharma V", "Jones AB", "Smith CD");
            assertTrue(checkAuthorMatch(metadata, "Vagisha", "Sharma"));

            // Match is case-insensitive
            assertTrue(checkAuthorMatch(metadataWithAuthors("sharma v"), "Vagisha", "Sharma"));
            assertTrue(checkAuthorMatch(metadataWithAuthors("SHARMA V"), "vagisha", "sharma"));

            // No matching author
            assertFalse(checkAuthorMatch(metadataWithAuthors("Jones AB", "Smith CD"), "Vagisha", "Sharma"));

            // Blank first or last name
            assertFalse(checkAuthorMatch(metadataWithAuthors("Sharma V"), "", "Sharma"));
            assertFalse(checkAuthorMatch(metadataWithAuthors("Sharma V"), "Vagisha", ""));
            assertFalse(checkAuthorMatch(metadataWithAuthors("Sharma V"), null, "Sharma"));

            // Empty authors array
            assertFalse(checkAuthorMatch(new JSONObject(), "Vagisha", "Sharma"));

            // Author with full first name: "Sharma Vagisha"
            assertTrue(checkAuthorMatch(metadataWithAuthors("Sharma Vagisha"), "Vagisha", "Sharma"));
        }

        // -- checkTitleMatch tests --

        @Test
        public void testCheckTitleMatchExact()
        {
            // Exact match (case-insensitive, punctuation-stripped)
            JSONObject metadata = metadataWithTitle("Quantitative Proteomics of Muscle Fibers");
            assertTrue(checkTitleMatch(metadata, "Quantitative Proteomics of Muscle Fibers"));
            assertTrue(checkTitleMatch(metadata, "quantitative proteomics of muscle fibers"));
            assertTrue(checkTitleMatch(metadata, "Quantitative Proteomics of Muscle Fibers!"));
        }

        @Test
        public void testCheckTitleMatchKeywords()
        {
            // Article: "quantitative proteomics reveals muscle fibers composition"
            // Dataset: "muscle fibers composition proteomics quantitative patterns"
            // Dataset keywords: "muscle", "fibers", "composition", "proteomics", "quantitative", "patterns" (6 keywords)
            // Matches in article: "muscle", "fibers", "composition", "proteomics", "quantitative" (5 of 6 = 83%) -> pass
            JSONObject metadata = metadataWithTitle("A study of quantitative proteomics reveals muscle fibers composition");
            assertTrue(checkTitleMatch(metadata, "muscle fibers composition proteomics quantitative patterns"));

            // Keywords not present in article title — well below 60%
            assertFalse(checkTitleMatch(metadata, "Novel cardiac lipids quantitation in mouse tissue"));
        }

        @Test
        public void testCheckTitleMatchThreshold()
        {
            // Article contains: "phosphoproteomics", "analysis", "lung", "cancer", "cell", "lines"
            JSONObject metadata = metadataWithTitle("Phosphoproteomics analysis of lung cancer cell lines");

            // 4 of 5 keywords match (80%) -> pass (threshold is 60%)
            assertTrue(checkTitleMatch(metadata, "phosphoproteomics lung cancer cell biomarkers"));

            // Only 2 of 5 keywords match (40%) -> fail
            assertFalse(checkTitleMatch(metadata, "phosphoproteomics kidney heart liver cancer"));
        }

        @Test
        public void testCheckTitleMatchShortKeywords()
        {
            // Short but meaningful words (3-4 chars) should be extracted as keywords
            JSONObject metadata = metadataWithTitle("DIA proteomics of lung cell iron metabolism in mice");
            assertTrue(checkTitleMatch(metadata, "DIA lung cell iron mice proteomics"));
        }

        @Test
        public void testCheckTitleMatchBidirectional()
        {
            // Forward direction fails: dataset is very specific, article is short.
            // Dataset keywords: "comprehensive", "phosphoproteomics", "analysis", "novel", "biomarkers", "lung", "cancer", "cell", "lines" (9)
            // Only "lung", "cancer" found in article (2 of 9 = 22%) -> forward fails
            //
            // Reverse direction passes: article keywords: "lung", "cancer", "proteomics" (3)
            // All 3 found in dataset -> 100% -> reverse passes
            JSONObject metadata = metadataWithTitle("Lung cancer proteomics");
            assertTrue(checkTitleMatch(metadata,
                    "Comprehensive phosphoproteomics analysis reveals novel biomarkers in lung cancer cell lines"));

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

            // Dataset title with only stop words and short words below MIN_KEYWORD_LENGTH
            // "a the and for with" -> all stop words or < 3 chars -> no keywords
            assertFalse(checkTitleMatch(metadataWithTitle("Different title entirely"), "a the and for with"));
        }

        @Test
        public void testCheckTitleMatchSorttitleFallback()
        {
            // When "title" is missing, falls back to "sorttitle"
            JSONObject metadata = new JSONObject();
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
            assertEquals("testdriven development", normalizeTitle("Test-Driven Development"));
            assertEquals("multiple spaces become one", normalizeTitle("  Multiple   spaces become one  "));
            assertEquals("", normalizeTitle(null));
            assertEquals("", normalizeTitle(""));
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
            // "YYYY Mon DD" format
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
            Date refDate = new Date();

            // Single article returned as-is
            PublicationMatch single = createMatch("111", true, false, false, false, false, refDate);
            List<PublicationMatch> result = applyPriorityFiltering(List.of(single), refDate, LOG);
            assertEquals(1, result.size());

            // Articles found by multiple data IDs preferred over single-ID matches
            PublicationMatch multiId = createMatch("222", true, true, false, true, true, refDate);
            PublicationMatch singleId = createMatch("333", true, false, false, true, true, refDate);
            result = applyPriorityFiltering(List.of(singleId, multiId), refDate, LOG);
            assertEquals(1, result.size());
            assertEquals("222", result.get(0).getPublicationId());

            // Among single-ID matches, author+title both matching preferred
            PublicationMatch bothMatch = createMatch("444", true, false, false, true, true, refDate);
            PublicationMatch authorOnly = createMatch("555", true, false, false, true, false, refDate);
            result = applyPriorityFiltering(List.of(authorOnly, bothMatch), refDate, LOG);
            assertEquals(1, result.size());
            assertEquals("444", result.get(0).getPublicationId());
        }

        @Test
        public void testSortByDateProximity()
        {
            Date refDate = new Date();
            Date closer = new Date(refDate.getTime() - 86400000L); // 1 day before
            Date farther = new Date(refDate.getTime() - 86400000L * 365); // 1 year before

            PublicationMatch farMatch = createMatch("111", true, false, false, false, false, farther);
            PublicationMatch closeMatch = createMatch("222", true, false, false, false, false, closer);
            PublicationMatch noDate = createMatch("333", true, false, false, false, false, null);

            List<PublicationMatch> result = sortByDateProximity(List.of(farMatch, noDate, closeMatch), refDate);
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

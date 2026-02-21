package org.labkey.panoramapublic.ncbi;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.panoramapublic.datacite.DataCiteService;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.ncbi.PublicationMatch.PublicationType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

    // API parameters
    private static final int RATE_LIMIT_DELAY_MS = 400; // NCBI allows 3 requests/sec
    private static final int MAX_RESULTS = 5;
    private static final int TIMEOUT_MS = 10000; // 10 seconds
    private static final String NCBI_EMAIL = "panorama@proteinms.net";

    // Preprint indicators
    private static final String[] PREPRINT_INDICATORS = {
            "preprint", "biorxiv", "medrxiv", "chemrxiv", "arxiv",
            "research square", "preprints.org"
    };

    // Title keyword extraction
    private static final int MIN_KEYWORD_LENGTH = 5;
    private static final int MAX_KEYWORDS = 5;

    // Stop words for title matching
    private static final Set<String> TITLE_STOP_WORDS = Set.of(
        "analysis", "study", "using", "based", "data", "dataset", "proteomics",
        "method", "methods", "approach", "application", "investigation",
        "examination", "characterization", "identification", "quantification",
        "comparison", "evaluation"
    );

    private static Logger getLog(@Nullable Logger logger)
    {
        return logger != null ? logger : LOG;
    }

    /**
     * Search for publications associated with the given experiment.
     * Returns the top result if multiple results are found
     * @param logger optional logger; when null, uses the class logger
     */
    public static PublicationMatch searchForPublication(@NotNull ExperimentAnnotations expAnnotations, @Nullable Logger logger)
    {
        List<PublicationMatch> matches = searchForPublication(expAnnotations, 1, logger);
        return matches.isEmpty() ? null : matches.get(0);
    }

    /**
     * Search for publications associated with the given experiment.
     * Search PubMed Central first, then fall back to PubMed if needed.
     * @param maxResults maximum number of article matches to return (capped at 5)
     * @param logger optional logger; when null, uses the class logger
     */
    public static List<PublicationMatch> searchForPublication(@NotNull ExperimentAnnotations expAnnotations, int maxResults, @Nullable Logger logger)
    {
        Logger log = getLog(logger);
        maxResults = Math.max(1, Math.min(maxResults, MAX_RESULTS));
        log.info("Starting publication search for experiment: " + expAnnotations.getId());

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
        log.info("Returning " + matchedArticles.size() + StringUtilsLabKey.pluralize(matchedArticles.size(), "publication"));
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
            log.debug("Searching PMC by PX ID: " + expAnnotations.getPxid());
            List<String> ids = searchPmc(quote(expAnnotations.getPxid()));
            if (!ids.isEmpty())
            {
                pmcIdsByStrategy.put(MATCH_PX_ID, ids);
                log.debug("Found " + ids.size() + " PMC articles by PX ID");
            }
            rateLimit();
        }

        // Step 2: Search PMC by Panorama URL
        if (expAnnotations.getShortUrl() != null)
        {
            String panoramaUrl = expAnnotations.getShortUrl().renderShortURL();
            log.debug("Searching PMC by Panorama URL: " + panoramaUrl);
            List<String> ids = searchPmc(quote(panoramaUrl));
            if (!ids.isEmpty())
            {
                pmcIdsByStrategy.put(MATCH_PANORAMA_URL, ids);
                log.debug("Found " + ids.size() + " PMC articles by Panorama URL");
            }
            rateLimit();
        }

        // Step 3: Search PMC by DOI
        if (!StringUtils.isBlank(expAnnotations.getDoi()))
        {
            String doiUrl = expAnnotations.getDoi().startsWith("http")
                ? expAnnotations.getDoi()
                : DataCiteService.toUrl(expAnnotations.getDoi());
            log.debug("Searching PMC by DOI: " + doiUrl);
            List<String> ids = searchPmc(quote(doiUrl));
            if (!ids.isEmpty())
            {
                pmcIdsByStrategy.put(MATCH_DOI, ids);
                log.debug("Found " + ids.size() + " PMC articles by DOI");
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

        log.info("Total unique PMC IDs found: " + uniquePmcIds.size());

        // Fetch and verify PMC articles
        List<PublicationMatch> pmcArticles = fetchAndVerifyPmcArticles(uniquePmcIds, idToStrategies, expAnnotations, log);

        // Apply priority filtering
        return applyPriorityFiltering(pmcArticles, log);
    }

    /**
     * Search PubMed Central with the given query
     */
    private static List<String> searchPmc(String query)
    {
        return executeSearch(query, "pmc");
    }

    /**
     * Search PubMed with the given query
     */
    private static List<String> searchPubMed(String query)
    {
        return executeSearch(query, "pubmed");
    }

    /**
     * Execute search using NCBI ESearch API
     */
    private static List<String> executeSearch(String query, String database)
    {
        HttpURLConnection conn = null;
        try
        {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
            String urlString = ESEARCH_URL +
                "?db=" + database +
                "&term=" + encodedQuery +
                "&retmax=" + MAX_RESULTS +
                "&retmode=json" +
                "&email=" + URLEncoder.encode(NCBI_EMAIL, StandardCharsets.UTF_8.toString());

            conn = openGet(urlString);

            // Parse JSON response
            JSONObject json = readJson(conn);
            JSONObject eSearchResult = json.getJSONObject("esearchresult");
            JSONArray idList = eSearchResult.getJSONArray("idlist");

            List<String> ids = new ArrayList<>();
            for (int i = 0; i < idList.length(); i++)
            {
                ids.add(idList.getString(i));
            }
            return ids;
        }
        catch (Exception e)
        {
            LOG.error("Error searching " + database + " with query: " + query, e);
            return Collections.emptyList();
        }
        finally
        {
            if (conn != null) conn.disconnect();
        }
    }

    private static HttpURLConnection openGet(String urlString) throws Exception
    {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        return conn;
    }

    private static JSONObject readJson(HttpURLConnection conn) throws Exception
    {
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(conn.getInputStream())))
        {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return new JSONObject(sb.toString());
        }
    }

    /**
     * Fetch metadata for PMC articles using ESummary API
     */
    private static Map<String, JSONObject> fetchPmcMetadata(Collection<String> pmcIds)
    {
        return fetchMetadata(pmcIds, "pmc");
    }

    /**
     * Fetch metadata for PubMed articles using ESummary API
     */
    private static Map<String, JSONObject> fetchPubMedMetadata(Collection<String> pmids)
    {
        return fetchMetadata(pmids, "pubmed");
    }

    /**
     * Fetch metadata using NCBI ESummary API (batch request)
     */
    private static Map<String, JSONObject> fetchMetadata(Collection<String> ids, String database)
    {
        if (ids.isEmpty()) return Collections.emptyMap();

        HttpURLConnection conn = null;
        try
        {
            String idString = String.join(",", ids);
            String urlString = ESUMMARY_URL +
                "?db=" + database +
                "&id=" + URLEncoder.encode(idString, StandardCharsets.UTF_8.toString()) +
                "&retmode=json" +
                "&email=" + URLEncoder.encode(NCBI_EMAIL, StandardCharsets.UTF_8.toString());

            conn = openGet(urlString);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200)
            {
                LOG.warn("NCBI ESummary returned non-200 response: " + responseCode);
                return Collections.emptyMap();
            }

            JSONObject json = readJson(conn);
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
        catch (Exception e)
        {
            LOG.error("Error fetching " + database + " metadata for IDs: " + ids, e);
            return Collections.emptyMap();
        }
        finally
        {
            if (conn != null) conn.disconnect();
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
        Map<String, JSONObject> metadata = fetchPmcMetadata(pmcIds);

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
                log.info("Excluded preprint PMC" + pmcId + ": source=\"" + source + "\", journal=\"" + journal + "\"");
                continue;
            }

            // Check author and title matches
            boolean authorMatch = checkAuthorMatch(articleData, firstName, lastName);
            boolean titleMatch = checkTitleMatch(articleData, expAnnotations.getTitle());

            // Extract PubMed ID, is found
            String pubMedId = extractPubMedId(articleData, pmcId);
            String publicationId = pubMedId == null ? pmcId : pubMedId;
            PublicationType publicationType = pubMedId == null ? PublicationType.PMC : PublicationType.PMID;

            // Get strategies that found this article
            List<String> strategies = idToStrategies.getOrDefault(pmcId, Collections.emptyList());

            // Map strategies to boolean flags
            boolean foundByPxId = strategies.contains(MATCH_PX_ID);
            boolean foundByUrl = strategies.contains(MATCH_PANORAMA_URL);
            boolean foundByDoi = strategies.contains(MATCH_DOI);

            PublicationMatch article = new PublicationMatch(
                publicationId,
                publicationType,
                foundByPxId,
                foundByUrl,
                foundByDoi,
                authorMatch,
                titleMatch
            );
            articles.add(article);

            log.info("PMC" + pmcId + " -> " + publicationType + " " + publicationId +
                " | Match info: " + article.getMatchInfo());
        }

        return articles;
    }

    /**
     * Apply priority filtering to narrow down results
     */
    private static List<PublicationMatch> applyPriorityFiltering(List<PublicationMatch> articles, Logger log)
    {
        if (articles.size() <= 1) return articles;

        // Priority 1: Filter to articles found by multiple data IDs (most reliable)
        List<PublicationMatch> multipleIds = articles.stream()
            .filter(a -> countDataIdMatches(a) >= 2)
            .collect(Collectors.toList());

        if (!multipleIds.isEmpty())
        {
            log.debug("Filtered to " + multipleIds.size() + " article(s) found by multiple IDs");
            articles = multipleIds;
        }

        if (articles.size() <= 1) return articles;

        // Priority 2: Filter to articles with both Author AND Title match
        List<PublicationMatch> bothMatches = articles.stream()
            .filter(a -> a.matchesAuthor() && a.matchesTitle())
            .collect(Collectors.toList());

        if (!bothMatches.isEmpty())
        {
            log.debug("Filtered to " + bothMatches.size() + " article(s) with both Author and Title match");
            return bothMatches;
        }

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

        log.debug("PubMed fallback query: " + query);
        List<String> pmids = searchPubMed(query);

        if (pmids.isEmpty())
        {
            log.info("PubMed fallback found no results");
            return Collections.emptyList();
        }

        log.info("PubMed fallback found " + pmids.size() + " result(s), verifying...");

        // Fetch metadata and verify
        Map<String, JSONObject> metadata = fetchPubMedMetadata(pmids);

        List<PublicationMatch> articles = new ArrayList<>();
        for (String pmid : pmids)
        {
            JSONObject articleData = metadata.get(pmid);
            if (articleData == null) continue;

            // Filter out preprints
            if (isPreprint(articleData))
            {
                log.info("Excluded preprint PMID " + pmid);
                continue;
            }

            // Verify both author AND title match
            boolean authorMatch = checkAuthorMatch(articleData, firstName, lastName);
            boolean titleMatch = checkTitleMatch(articleData, title);

            // Only accept if BOTH match
            if (authorMatch && titleMatch)
            {
                PublicationMatch article = new PublicationMatch(
                    pmid,
                    PublicationType.PMID,
                    false,  // Not found by PX ID
                    false,  // Not found by Panorama URL
                    false,  // Not found by DOI
                    true,   // Matched by author
                    true    // Matched by title
                );
                articles.add(article);
                log.info("PMID " + pmid + " verified with both Author and Title match");
            }
        }

        log.info("Verified " + articles.size() + " of " + pmids.size() + " PubMed results");
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
        if (authors == null || authors.length() == 0)
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

        // Try sorttitle first, then title
        String articleTitle = normalizeTitle(metadata.optString("sorttitle"));
        if (articleTitle.isEmpty())
        {
            articleTitle = normalizeTitle(metadata.optString("title"));
        }

        if (articleTitle.isEmpty())
        {
            return false;
        }

        String normalizedDatasetTitle = normalizeTitle(datasetTitle);

        // Try exact match first
        if (articleTitle.equals(normalizedDatasetTitle))
        {
            return true;
        }

        // Fall back to keyword matching
        List<String> keywords = extractTitleKeywords(datasetTitle);
        if (keywords.isEmpty())
        {
            return false;
        }

        // All keywords must be present in article title
        for (String keyword : keywords)
        {
            if (!articleTitle.contains(keyword.toLowerCase()))
            {
                return false;
            }
        }

        return true;
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
     * Extract meaningful keywords from title for matching
     */
    private static List<String> extractTitleKeywords(String title)
    {
        if (StringUtils.isBlank(title))
        {
            return Collections.emptyList();
        }

        // Extract words of minimum length
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

        // Return top N keywords
        return keywords.stream()
            .limit(MAX_KEYWORDS)
            .collect(Collectors.toList());
    }

    /**
     * Extract PMID from PMC metadata
     */
    private static @Nullable String extractPubMedId(JSONObject pmcMetadata, String pmcId)
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
}

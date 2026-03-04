package org.labkey.panoramapublic.ncbi;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.panoramapublic.ncbi.NcbiConstants.DB;

import java.io.IOException;

/**
 * Mock implementation of {@link NcbiPublicationSearchService} that returns canned data for PMID 23689285
 * (Abbatiello et al., Mol Cell Proteomics 2013). Used by Selenium tests when NCBI is not reachable.
 *
 * Extends {@link NcbiPublicationSearchServiceImpl} and only overrides the two methods that make HTTP calls
 * to NCBI: {@link #getJson(String)} (used by ESearch/ESummary) and {@link #getCitation(String, DB)}
 * (used by the Citation Exporter API). All search logic, filtering, author/title verification, and
 * priority filtering run through the real implementation code.
 */
public class MockNcbiPublicationSearchService extends NcbiPublicationSearchServiceImpl
{
    private static final String PMID = "23689285";
    private static final String PMC_ID = "3769335";

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

    private static final String ARTICLE_TITLE = "Design, implementation and multisite evaluation of a system suitability " +
            "protocol for the quantitative assessment of instrument performance in liquid chromatography-multiple " +
            "reaction monitoring-MS (LC-MRM-MS).";

    /**
     * Returns canned JSON for NCBI ESearch and ESummary API requests.
     * <ul>
     *   <li>ESearch for PMC with a query containing "PXD010535" returns PMC ID 3769335</li>
     *   <li>ESummary for PMC ID 3769335 returns article metadata (authors, title, pubdate, PMID)</li>
     *   <li>All other requests return empty results</li>
     * </ul>
     * This allows the real search logic in {@link NcbiPublicationSearchServiceImpl} to run against mock data.
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
     * Returns the canned NLM citation for PMID 23689285. Returns null for any other publication ID.
     * Overrides the real implementation which makes an HTTP call to the NCBI Citation Exporter API.
     */
    @Override
    public @Nullable String getCitation(String publicationId, DB database)
    {
        if (PMID.equals(publicationId))
        {
            return CITATION;
        }
        return null;
    }

    /**
     * Handle mock ESearch requests. Returns PMC ID 3769335 when query contains "PXD010535",
     * empty results otherwise.
     */
    private JSONObject handleESearch(String url)
    {
        JSONObject result = new JSONObject();
        JSONObject esearchResult = new JSONObject();

        if (url.contains("PXD010535"))
        {
            esearchResult.put("idlist", new JSONArray().put(PMC_ID));
        }
        else
        {
            esearchResult.put("idlist", new JSONArray());
        }

        result.put("esearchresult", esearchResult);
        return result;
    }

    /**
     * Handle mock ESummary requests. Returns article metadata for PMC ID 3769335.
     */
    private JSONObject handleESummary(String url)
    {
        JSONObject response = new JSONObject();
        JSONObject result = new JSONObject();

        if (url.contains(PMC_ID))
        {
            JSONObject articleData = new JSONObject();
            articleData.put("title", ARTICLE_TITLE);
            articleData.put("sorttitle", ARTICLE_TITLE.toLowerCase());
            articleData.put("source", "Mol Cell Proteomics");
            articleData.put("fulljournalname", "Molecular & cellular proteomics : MCP");
            articleData.put("pubdate", "2013 Sep");
            articleData.put("epubdate", "2013 May 20");

            // Authors — include first 3 plus a few more for realism
            JSONArray authors = new JSONArray();
            authors.put(new JSONObject().put("name", "Abbatiello SE"));
            authors.put(new JSONObject().put("name", "Mani DR"));
            authors.put(new JSONObject().put("name", "Schilling B"));
            authors.put(new JSONObject().put("name", "Maclean B"));
            authors.put(new JSONObject().put("name", "Zimmerman LJ"));
            authors.put(new JSONObject().put("name", "Carr SA"));
            articleData.put("authors", authors);

            // Article IDs — PMC ID and PubMed ID
            JSONArray articleIds = new JSONArray();
            articleIds.put(new JSONObject().put("idtype", "pmcid").put("value", "PMC" + PMC_ID));
            articleIds.put(new JSONObject().put("idtype", "pmid").put("value", PMID));
            articleData.put("articleids", articleIds);

            result.put(PMC_ID, articleData);
        }

        response.put("result", result);
        return response;
    }
}

/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.panoramapublic.proteomexchange;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.AjaxCompletion;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NcbiUtils
{
    private static final Pattern pattern = Pattern.compile("new Array\\(\"(.*)\"\\),");
    private static final String eutilsUrl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi?db=taxonomy";
    private static final String autoCompUrl = "https://blast.ncbi.nlm.nih.gov/portal/utils/autocomp.fcgi?dict=taxids_sg&q=";

    private static final String citationEporterUrl = "https://api.ncbi.nlm.nih.gov/lit/ctxp/v1/pubmed/?format=citation&id=";
    public static final String PUBMED_ID = "^[0-9]{1,8}$"; // https://libguides.library.arizona.edu/c.php?g=406096&p=2779570

    private static final Logger LOG = LogHelper.getLogger(NcbiUtils.class, "Messages about using the NCBI utilities");

    public static List<JSONObject> getCompletions(String token) throws PxException
    {
        List<JSONObject> completions = new ArrayList<>();

        HttpURLConnection conn = null;
        try
        {
            if (!StringUtils.isBlank(token))
            {
                // https://blast.ncbi.nlm.nih.gov/Blast.cgi?PROGRAM=blastn&BLAST_PROGRAMS=megaBlast&PAGE_TYPE=BlastSearch&SHOW_DEFAULTS=on&LINK_LOC=blasthome
                // https://stackoverflow.com/questions/24768956/retrieve-the-autocomplete-list-from-another-site-ncbi
                URL url = new URL( autoCompUrl+ PageFlowUtil.encodeURIComponent(token.toLowerCase()));
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                int status = conn.getResponseCode();

                if (status == HttpURLConnection.HTTP_OK)
                {
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)))
                    {
                        String result;

                        while ((result = in.readLine()) != null)
                        {
                            // LOG.info(result);
                            // Example: NSuggest_CreateData("zebraf", new Array("zebrafish (taxid:7955)", "Zebrafish nervous necrosis virus (taxid:1286775)"), 1);
                            Matcher match = pattern.matcher(result);
                            if (match.find())
                            {
                                String orgs = match.group(1);
                                String[] orgsArr = orgs.split("\"");
                                for (String org : orgsArr)
                                {
                                    int idx = org.indexOf("(taxid:");
                                    if(idx != -1 && org.toLowerCase().contains(token.toLowerCase()))
                                    {
                                        completions.add(new AjaxCompletion(org, org).toJSON());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        catch(IOException e)
        {
            throw new PxException("Error doing NCBI autocomplete lookup: " + e.getMessage(), e);
        }
        finally
        {
            if(conn != null) conn.disconnect();
        }
        return completions;
    }

    public static Map<Integer, String> getScientificNames(List<Integer> taxIds) throws PxException
    {
        String queryUrl = eutilsUrl + "&id=" + StringUtils.join(taxIds, ",");

        Map<Integer, String> sciNameMap = new HashMap<>();

        HttpURLConnection conn = null;
        try
        {
            URL url = new URL(queryUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            int status = conn.getResponseCode();

            if (status == HttpURLConnection.HTTP_OK)
            {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(conn.getInputStream());

                NodeList nodes = doc.getElementsByTagName("DocSum");
                for(int i = 0; i < nodes.getLength(); i++)
                {
                    Element node = (Element)nodes.item(i);
                    Node idNode = node.getElementsByTagName("Id").item(0);
                    String taxidStr = null;
                    if(idNode != null)
                    {
                        Node taxidNode = idNode.getFirstChild();
                        if (taxidNode instanceof CharacterData)
                        {
                            taxidStr = ((CharacterData) taxidNode).getData();
                        }
                    }

                    NodeList children = node.getElementsByTagName("Item");
                    for(int j = 0; j < children.getLength(); j++)
                    {
                        Element child = (Element)children.item(j);
                        if(!StringUtils.isBlank(taxidStr) && ("ScientificName").equalsIgnoreCase(child.getAttribute("Name")))
                        {
                            Node sciName = child.getFirstChild();
                            if(sciName instanceof CharacterData)
                            {
                                Integer taxid = Integer.parseInt(taxidStr);
                                sciNameMap.put(taxid, ((CharacterData) sciName).getData());
                                break;
                            }
                        }
                    }
                }
            }
        }
        catch (IOException | SAXException | ParserConfigurationException e)
        {
            throw new PxException("Error doing NCBI lookup for scientific names.", e);
        }
        finally
        {
            if(conn != null) conn.disconnect();
        }
        return sciNameMap;
    }

    /**
     * Does a citation lookup for the given PubMedId using the NCBI's Literature Citation Exporter (https://api.ncbi.nlm.nih.gov/lit/ctxp).
     * Example URL: https://api.ncbi.nlm.nih.gov/lit/ctxp/v1/pubmed/?format=citation&id=28691345
     * @param pubmedId PubMed Id
     * @return the PubMed link and the NLM-style citation if the lookup is successful
     */
    public static Pair<String, String> getLinkAndCitation (String pubmedId)
    {
        if (pubmedId == null || !pubmedId.matches(PUBMED_ID))
        {
            return null;
        }

        String queryUrl = citationEporterUrl + pubmedId;

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
                String citation = getCitation(response, pubmedId);

                return citation != null ? new Pair<>(getPubmedLink(pubmedId), citation) : null;
            }
        }
        catch (IOException e)
        {
            LOG.error("Error submitting a request to NCBI Literature Citation Exporter. URL: " + queryUrl, e);
        }
        finally
        {
            if(conn != null) conn.disconnect();
        }
        return null;
    }

    private static String getPubmedLink(String pubmedId)
    {
        // Example: https://pubmed.ncbi.nlm.nih.gov/29331002
        return "https://pubmed.ncbi.nlm.nih.gov/" + pubmedId;
    }

    private static String getCitation(String response, String pubmedId)
    {
        try
        {
            var jsonObject = new JSONObject(response);
            /* We are interested in the NLM style citation.
            nlm: {
                orig: "ENCODE Project Consortium. An integrated encyclopedia of DNA elements in the human genome. Nature. 2012 Sep 6;489(7414):57-74. doi: 10.1038/nature11247. PMID: 22955616; PMCID: PMC3439153.",
                format: "ENCODE Project Consortium. An integrated encyclopedia of DNA elements in the human genome. Nature. 2012 Sep 6;489(7414):57-74. doi: 10.1038/nature11247. PMID: 22955616; PMCID: PMC3439153."
            }
            */
            var nlmInfo = jsonObject.optJSONObject("nlm");
            return null != nlmInfo ? nlmInfo.getString("orig") : null;
        }
        catch (JSONException e)
        {
            LOG.error("Error parsing response from NCBI Literature Citation Exporter for pubmedID " + pubmedId, e);
        }
        return null;
    }
}

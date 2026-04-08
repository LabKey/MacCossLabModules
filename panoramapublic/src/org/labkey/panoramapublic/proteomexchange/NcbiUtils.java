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

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.IntHashMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.AjaxCompletion;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Logger;

/**
 * Utilities for NCBI taxonomy lookups (autocomplete and scientific name resolution).
 */
public class NcbiUtils
{
    private static final Pattern pattern = Pattern.compile("new Array\\(\"(.*)\"\\),");
    private static final String eutilsUrl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi?db=taxonomy";
    private static final String autoCompUrl = "https://blast.ncbi.nlm.nih.gov/portal/utils/autocomp.fcgi?dict=taxids_sg&q=";

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

        HttpURLConnection conn = null;
        try
        {
            URL url = new URL(queryUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            int status = conn.getResponseCode();

            if (status == HttpURLConnection.HTTP_OK)
            {
                return parseScientificNames(conn.getInputStream());
            }
            return new IntHashMap<>();
        }
        catch (IOException | SAXException | ParserConfigurationException e)
        {
            throw new PxException("Error doing NCBI lookup for scientific names.", e);
        }
        finally
        {
            if(conn != null) conn.disconnect();
        }
    }

    /**
     * Returns the {@link DocumentBuilder} used to parse NCBI eSummary responses. NCBI's response
     * begins with a {@code <!DOCTYPE eSummaryResult PUBLIC ... esummary-v1.dtd>} declaration, so
     * we use the {@code _ALLOWING_DOCTYPE} variant which permits the DOCTYPE but keeps every
     * other XXE-mitigation in place.
     */
    static DocumentBuilder getDocumentBuilder() throws ParserConfigurationException
    {
        return XmlBeansUtil.DOCUMENT_BUILDER_FACTORY_ALLOWING_DOCTYPE.newDocumentBuilder();
    }

    // Parses an NCBI eSummary taxonomy XML response and returns a map of taxid -> scientific name.
    private static Map<Integer, String> parseScientificNames(InputStream in)
            throws ParserConfigurationException, SAXException, IOException
    {
        Document doc = getDocumentBuilder().parse(in);

        Map<Integer, String> sciNameMap = new IntHashMap<>();
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
        return sciNameMap;
    }

    public static class TestCase extends Assert
    {
        // NCBI esummary taxonomy response captured from
        // https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi?db=taxonomy&id=9606,10090,4932
        private static final String ESUMMARY_TAXONOMY_RESPONSE =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                "<!DOCTYPE eSummaryResult PUBLIC \"-//NLM//DTD esummary v1 20041029//EN\" \"https://eutils.ncbi.nlm.nih.gov/eutils/dtd/20041029/esummary-v1.dtd\">\n" +
                "<eSummaryResult>\n" +
                "<DocSum>\n" +
                "    <Id>9606</Id>\n" +
                "    <Item Name=\"Status\" Type=\"String\">active</Item>\n" +
                "    <Item Name=\"Rank\" Type=\"String\">species</Item>\n" +
                "    <Item Name=\"Division\" Type=\"String\">primates</Item>\n" +
                "    <Item Name=\"ScientificName\" Type=\"String\">Homo sapiens</Item>\n" +
                "    <Item Name=\"CommonName\" Type=\"String\">human</Item>\n" +
                "    <Item Name=\"TaxId\" Type=\"Integer\">9606</Item>\n" +
                "    <Item Name=\"AkaTaxId\" Type=\"Integer\">0</Item>\n" +
                "    <Item Name=\"Genus\" Type=\"String\"></Item>\n" +
                "    <Item Name=\"Species\" Type=\"String\"></Item>\n" +
                "    <Item Name=\"Subsp\" Type=\"String\"></Item>\n" +
                "    <Item Name=\"ModificationDate\" Type=\"Date\">2024/09/10 00:00</Item>\n" +
                "</DocSum>\n" +
                "\n" +
                "<DocSum>\n" +
                "    <Id>10090</Id>\n" +
                "    <Item Name=\"Status\" Type=\"String\">active</Item>\n" +
                "    <Item Name=\"Rank\" Type=\"String\">species</Item>\n" +
                "    <Item Name=\"Division\" Type=\"String\">rodents</Item>\n" +
                "    <Item Name=\"ScientificName\" Type=\"String\">Mus musculus</Item>\n" +
                "    <Item Name=\"CommonName\" Type=\"String\">house mouse</Item>\n" +
                "    <Item Name=\"TaxId\" Type=\"Integer\">10090</Item>\n" +
                "    <Item Name=\"AkaTaxId\" Type=\"Integer\">0</Item>\n" +
                "    <Item Name=\"Genus\" Type=\"String\"></Item>\n" +
                "    <Item Name=\"Species\" Type=\"String\"></Item>\n" +
                "    <Item Name=\"Subsp\" Type=\"String\"></Item>\n" +
                "    <Item Name=\"ModificationDate\" Type=\"Date\">2025/06/16 00:00</Item>\n" +
                "</DocSum>\n" +
                "\n" +
                "<DocSum>\n" +
                "    <Id>4932</Id>\n" +
                "    <Item Name=\"Status\" Type=\"String\">active</Item>\n" +
                "    <Item Name=\"Rank\" Type=\"String\">species</Item>\n" +
                "    <Item Name=\"Division\" Type=\"String\">budding yeasts &amp; allies</Item>\n" +
                "    <Item Name=\"ScientificName\" Type=\"String\">Saccharomyces cerevisiae</Item>\n" +
                "    <Item Name=\"CommonName\" Type=\"String\">brewer's yeast</Item>\n" +
                "    <Item Name=\"TaxId\" Type=\"Integer\">4932</Item>\n" +
                "    <Item Name=\"AkaTaxId\" Type=\"Integer\">0</Item>\n" +
                "    <Item Name=\"Genus\" Type=\"String\"></Item>\n" +
                "    <Item Name=\"Species\" Type=\"String\"></Item>\n" +
                "    <Item Name=\"Subsp\" Type=\"String\"></Item>\n" +
                "    <Item Name=\"ModificationDate\" Type=\"Date\">2025/08/11 00:00</Item>\n" +
                "</DocSum>\n" +
                "\n" +
                "</eSummaryResult>\n";

        @Test
        public void testParseScientificNames() throws Exception
        {
            Map<Integer, String> names = parseScientificNames(toStream(ESUMMARY_TAXONOMY_RESPONSE));
            assertEquals(3, names.size());
            assertEquals("Homo sapiens", names.get(9606));
            assertEquals("Mus musculus", names.get(10090));
            assertEquals("Saccharomyces cerevisiae", names.get(4932));
        }

        private static InputStream toStream(String xml)
        {
            return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        }
    }
}

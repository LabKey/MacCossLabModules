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
package org.labkey.targetedms.proteomexchange;

import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.resource.FileResource;
import org.labkey.api.util.Path;
import org.labkey.targetedms.TargetedMSModule;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class UnimodParser
{
    public UnimodModifications parse() throws PxException
    {
        Module module = ModuleLoader.getInstance().getModule(TargetedMSModule.class);
        FileResource resource = (FileResource)module.getModuleResolver().lookup(Path.parse("unimod_NO_NAMESPACE.xml"));
        if(resource == null)
        {
            throw new PxException("UNIMOD xml file resource not found.");
        }
        File unimodXml = resource.getFile();
        if(unimodXml == null)
        {
            throw new PxException("UNIMOD xml file not found.");
        }
        return parse(unimodXml);
    }

    public UnimodModifications parse(File unimodXml) throws PxException
    {
        if(!unimodXml.exists())
        {
            throw new PxException("UNIMOD xml file does not exist: " + unimodXml);
        }
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db;
        Document document;
        try
        {
            db = dbf.newDocumentBuilder();
            document = db.parse(unimodXml);
        }
        catch (ParserConfigurationException | SAXException | IOException e)
        {
            throw new PxException("Error parsing UNIMOD xml: " + unimodXml, e);
        }

        Element root = document.getDocumentElement();
        if(root == null)
        {
            throw new PxException("UNIMOD xml document has no root document element.");
        }

        UnimodModifications uMods = new UnimodModifications();
        readModifications(root, uMods);
        readAminoAcids(root, uMods);
        return uMods;
    }

    private void readAminoAcids(Element root, UnimodModifications uMods) throws PxException
    {
        NodeList list = root.getElementsByTagName("aa");
        for(int i = 0; i < list.getLength(); i++)
        {
            Node n =  list.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                {
                    parseAminoAcid((Element)n, uMods);
                }
            }
        }
    }

    private void parseAminoAcid(Element aaEl, UnimodModifications uMods) throws PxException
    {
        String title = aaEl.getAttribute("title");
        if(title == null || title.length() > 1 || !Character.isUpperCase(title.charAt(0)))
        {
            return;
        }

        NodeList nl = aaEl.getElementsByTagName("element");
        Map<String, Integer> composition = new HashMap<>();
        for(int i = 0; i < nl.getLength(); i++)
        {
            Element el = (Element) nl.item(i);
            String symbol = el.getAttribute("symbol");
            String number = el.getAttribute("number");
            composition.put(symbol, Integer.parseInt(number));
        }
        uMods.addAminoAcid(title.charAt(0), composition);
    }

    private void readModifications(Element root, UnimodModifications uMods) throws PxException
    {
        NodeList list = root.getElementsByTagName("mod");
        for(int i = 0; i < list.getLength(); i++)
        {
            Node n =  list.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                {
                    uMods.add(parseModification((Element)n));
                }
            }
        }
    }

    private UnimodModification parseModification(Element modEl)
    {
        String title = modEl.getAttribute("title");
        Integer id = Integer.parseInt(modEl.getAttribute("record_id"));

        String formula = getFormula(modEl.getElementsByTagName("delta"));

        UnimodModification uMod = new UnimodModification(id, title, formula);

        NodeList nl = modEl.getElementsByTagName("specificity");
        for(int i = 0; i < nl.getLength(); i++)
        {
            Element specEl = (Element) nl.item(i);
            String site = specEl.getAttribute("site");
            String cls = specEl.getAttribute("classification");
            if(site.equalsIgnoreCase("N-term"))
            {
                uMod.setNterm(true);
            }
            else if(site.equalsIgnoreCase("C-term"))
            {
                uMod.setCterm(true);
            }
            else
            {
                uMod.addSite(site, cls);
            }
        }

        return uMod;
    }

    private String getFormula(NodeList nl)
    {
        StringBuilder formula_pos = new StringBuilder();
        StringBuilder formula_neg = new StringBuilder();
        if(nl.getLength() > 0)
        {
            nl = ((Element)nl.item(0)).getElementsByTagName("element");
            for(int i = 0; i < nl.getLength(); i++)
            {
                Element el = (Element)nl.item(i);
                String symbol = el.getAttribute("symbol");
                switch (symbol)
                {
                    case "2H":
                        symbol = "H'";
                        break;
                    case "13C":
                        symbol = "C'";
                        break;
                    case "15N":
                        symbol = "N'";
                        break;
                    case "18O":
                        symbol = "O'";
                        break;
                }
                Integer number = Integer.parseInt(el.getAttribute("number"));
                if(number > 0)
                {
                    formula_pos.append(symbol).append(number);
                }
                else
                {
                    formula_neg.append(symbol).append(-(number));
                }
            }
        }

        String formula = formula_pos.toString();
        if(formula_neg.length() > 0)
        {
            String sep = formula.length() > 0 ? " - " : "-";
            formula = formula + sep + formula_neg;
        }
        return UnimodModification.normalizeFormula(formula);
    }
}

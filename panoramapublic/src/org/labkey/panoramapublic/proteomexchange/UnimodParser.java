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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.resource.FileResource;
import org.labkey.api.util.Path;
import org.labkey.panoramapublic.PanoramaPublicModule;
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
import java.util.Objects;

public class UnimodParser
{
    public UnimodModifications parse() throws PxException
    {
        Module module = ModuleLoader.getInstance().getModule(PanoramaPublicModule.class);
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

    private UnimodModification parseModification(Element modEl) throws PxException
    {
        String title = modEl.getAttribute("title");
        Integer id = Integer.parseInt(modEl.getAttribute("record_id"));

        NodeList deltaEl = modEl.getElementsByTagName("delta");
        UnimodModification uMod = new UnimodModification(id, title, getFormula(deltaEl));

        boolean isIsotopic = false;

        NodeList nl = modEl.getElementsByTagName("specificity");
        for(int i = 0; i < nl.getLength(); i++)
        {
            Element specEl = (Element) nl.item(i);
            String site = specEl.getAttribute("site");
            String cls = specEl.getAttribute("classification");
            String pos = specEl.getAttribute("position");
            Position position = Position.forLabel(pos);
            if(site.equalsIgnoreCase("N-term"))
            {
                uMod.setNterm(position);
            }
            else if(site.equalsIgnoreCase("C-term"))
            {
                uMod.setCterm(position);
            }
            else
            {
                uMod.addSite(site, position);
            }
            if ("Isotopic label".equals(cls))
            {
                isIsotopic = true;
            }
        }
        isIsotopic = isIsotopic && checkTrueIsotopeMod(deltaEl);
        uMod.setIsotopic(isIsotopic);

        return uMod;
    }

    // From Skyline/Executables/UnimodCompiler
    private boolean checkTrueIsotopeMod(NodeList nl)
    {
        int label15N = 0;
        int label13C = 0;
        int label18O = 0;
        int label2H = 0;
        boolean has15N = false;
        boolean has13C = false;
        boolean has18O = false;
        boolean has2H = false;

        if(nl.getLength() > 0)
        {
            nl = ((Element)nl.item(0)).getElementsByTagName("element");
            for(int i = 0; i < nl.getLength(); i++)
            {
                Element el = (Element)nl.item(i);
                String symbol = el.getAttribute("symbol");
                int number = Integer.parseInt(el.getAttribute("number"));
                has15N = has15N || "15N".equals(symbol);
                has13C = has13C || "13C".equals(symbol);
                has18O = has18O || "18O".equals(symbol);
                has2H = has2H || "2H".equals(symbol);
                label15N += "15N".equals(symbol) || "N".equals(symbol) ? 0 : number;
                label13C += "13C".equals(symbol) || "C".equals(symbol) ? 0 : number;
                label18O += "18O".equals(symbol) || "O".equals(symbol) ? 0 : number;
                label2H += "2H".equals(symbol) || "H".equals(symbol) ? 0 : number;
            }
        }

        return (has15N || has13C || has18O || has2H) &&
                (!has15N || label15N == 0) && (!has13C || label13C == 0)
                && (!has18O || label18O == 0) && (!has2H || label2H == 0);
    }

    private Formula getFormula(NodeList nl) throws PxException
    {
        Formula formula = new Formula();
        if(nl.getLength() > 0)
        {
            nl = ((Element)nl.item(0)).getElementsByTagName("element");
            for(int i = 0; i < nl.getLength(); i++)
            {
                Element el = (Element)nl.item(i);
                String symbol = el.getAttribute("symbol");
                ChemElement chemElement = ChemElement.getElement(symbol);
//                ChemElement chemElement = switch (symbol)
//                        {
//                            case "2H" -> ChemElement.H2;
//                            case "13C" -> ChemElement.C13;
//                            case "15N" -> ChemElement.N15;
//                            case "18O" -> ChemElement.O18;
//                            default -> ChemElement.getElementForSymbol(symbol);
//                        };
                if (chemElement == null)
                {
                    throw new PxException("Unrecognized element in formula: " + symbol);
                }
                Integer number = Integer.parseInt(el.getAttribute("number"));
                formula.addElement(chemElement, number);
            }
        }
        return formula;
    }

    public static class Specificity
    {
        private final String _site;
        private final Position _position;

        public Specificity(String site, Position position)
        {
            _site = site;
            _position = position;
        }

        public String getSite()
        {
            return _site;
        }

        public Position getPosition()
        {
            return _position;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Specificity that = (Specificity) o;
            return getSite().equals(that.getSite()) && getPosition() == that.getPosition();
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(getSite(), getPosition());
        }

        public String toString()
        {
            return _site + (_position != null && Position.Anywhere != _position ? "(" + getPosition().getLabel() + ")" : "");
        }
    }

    public static class TermSpecificity
    {
        private final Terminus _term;
        private final Position _position;

        public TermSpecificity(@NotNull Terminus term, @NotNull Position position)
        {
            _term = term;
            _position = position;
        }

        public Terminus getTerm()
        {
            return _term;
        }

        public Position getPosition()
        {
            return _position;
        }

        public String toString()
        {
            return getTerm().getFullName() + " (" + getPosition().getLabel() + ")";
        }
    }

    public enum Position {

        Anywhere("Anywhere", true),
        AnyNterm("Any N-term", true),
        AnyCterm("Any C-term", true),
        ProteinNTerm("Protein N-term", false),
        ProteinCTerm("Protein C-term", false);

        private final String _label;
        private final boolean _anywhere;
        Position(String label, boolean anywhere)
        {
            _label = label;
            _anywhere = anywhere;
        }

        public String getLabel()
        {
            return _label;
        }

        static Position forLabel(String label) throws PxException
        {
            for (Position p: values())
            {
                if (p._label.equals(label))
                {
                    return p;
                }
            }
            throw new PxException("Cannot find a match for specificity position seen in Unimod.xml: " + label);
        }

        public boolean isAnywhere()
        {
            return _anywhere;
        }
    }

    public enum Terminus
    {
        N("N-term"), C("C-term");

        private final String fullName;
        Terminus(String fullName)
        {
            this.fullName = fullName;
        }

        public String getFullName()
        {
            return fullName;
        }
    }
}

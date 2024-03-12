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
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class UnimodParser
{
    private static final String NAMESPACE = "http://www.unimod.org/xmlns/schema/unimod_2";

    public UnimodModifications parse() throws PxException
    {
        Module module = ModuleLoader.getInstance().getModule(PanoramaPublicModule.class);
        UnimodModifications uMods = new UnimodModifications();
        File unimodXml = getXmlFile("unimod.xml", module);
        parse(unimodXml, uMods, true);
        // unimod_xl.xml contains master entries for crosslinks, e.g. Xlink:BuUrBu
        // Details are on this page: https://www.unimod.org/xlink.html. Link to download the file is also available on this page.
        File unimodXlXml = getXmlFile("unimod_xl.xml", module);
        parse(unimodXlXml, uMods, false);
        return uMods;
    }

    @NotNull
    private File getXmlFile(String fileName, Module module) throws PxException
    {
        FileResource resource = (FileResource) module.getModuleResolver().lookup(Path.parse(fileName));
        if(resource == null)
        {
            throw new PxException("UNIMOD xml file resource, " + fileName + ", not found.");
        }
        File unimodXml = resource.getFile();
        if(unimodXml == null)
        {
            throw new PxException("UNIMOD xml file, " + fileName + ", not found.");
        }
        return unimodXml;
    }

    private void parse(File unimodXml, UnimodModifications uMods, boolean isPrimaryXml) throws PxException
    {
        if(!unimodXml.exists())
        {
            throw new PxException("UNIMOD xml file does not exist: " + unimodXml);
        }
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
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

        readModifications(root, uMods);
        if (isPrimaryXml)
        {
            readAminoAcids(root, uMods);
            updateDiffIsotopeMods(uMods);
        }
    }

    private void readAminoAcids(Element root, UnimodModifications uMods) throws PxException
    {
        NodeList list = root.getElementsByTagNameNS(NAMESPACE, "aa");
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

        NodeList nl = aaEl.getElementsByTagNameNS(NAMESPACE, "element");
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
        NodeList list = root.getElementsByTagNameNS(NAMESPACE, "mod");
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

    // For isotope modifications that are the heavy versions of a structural modification, calculate the isotope formula
    // as the difference between the formula of the modification and the formula of the associated, unlabeled structural
    // modification.
    // For example: the isotope formula for Dimethyl:2H(6) is the difference between the formulas of Dimethyl:2H(6) and Dimethyl.
    // This difference is H'6C2-H2 - H4C2 = H'6-H6
    // This is how it is represented in Skyline, so we need to be able to lookup Unimod matches based on this formula.
    private void updateDiffIsotopeMods(UnimodModifications uMods)
    {
        for (UnimodModification uMod: uMods.getModifications())
        {
            if (uMod.isIsotopeLabel())
            {
                UnimodModification parentStrMod = uMods.getByName(uMod.getIsotopeLabelName());
                if (parentStrMod != null)
                {
                    Formula diffFormula = uMod.getFormula().subtractFormula(parentStrMod.getFormula());
                    if (isIsotopicDiff(diffFormula))
                    {
                        uMods.updateDiffIsotopeMod(uMod, diffFormula, parentStrMod);
                    }
                }
            }
        }
    }

    /**
     * @return true if the given formula is a balanced isotopic label, where the only thing changing is the isotopes
     *  of the atoms in question.
     */
    private static boolean isIsotopicDiff(Formula diffFormula)
    {
        var elementCounts = diffFormula.getElementCounts();
        Set<ChemElement> elements = new HashSet<>(elementCounts.keySet());

        for (var element: elementCounts.keySet())
        {
            int labelCount = elementCounts.get(element);
            if (ChemElement.HEAVY_LABELS.containsKey(element))
            {
                // Must be adding labeled atoms and removing unlabeled atoms
                if (labelCount < 0)
                {
                    return false;
                }

                var monoEl = ChemElement.HEAVY_LABELS.get(element);
                if (!elementCounts.containsKey(monoEl))
                {
                    return false;
                }

                int monoCount = elementCounts.get(monoEl);

                if (labelCount + monoCount != 0)
                {
                    return false;
                }

                elements.remove(monoEl);
                elements.remove(element);
            }
        }
        return elements.size() == 0;
    }

    private UnimodModification parseModification(Element modEl) throws PxException
    {
        String title = modEl.getAttribute("title");
        Integer id = Integer.parseInt(modEl.getAttribute("record_id"));

        NodeList deltaEl = modEl.getElementsByTagNameNS(NAMESPACE, "delta");
        UnimodModification uMod = new UnimodModification(id, title, getFormula(deltaEl));

        NodeList nl = modEl.getElementsByTagNameNS(NAMESPACE, "specificity");
        for(int i = 0; i < nl.getLength(); i++)
        {
            Element specEl = (Element) nl.item(i);
            String site = specEl.getAttribute("site");
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
        }
        uMod.setTrueIsotopic(checkTrueIsotopeMod(deltaEl));

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
            nl = ((Element)nl.item(0)).getElementsByTagNameNS(NAMESPACE, "element");
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
            nl = ((Element)nl.item(0)).getElementsByTagNameNS(NAMESPACE, "element");
            for(int i = 0; i < nl.getLength(); i++)
            {
                Element el = (Element)nl.item(i);
                String symbol = el.getAttribute("symbol");
                ChemElement chemElement = ChemElement.getElement(symbol);
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

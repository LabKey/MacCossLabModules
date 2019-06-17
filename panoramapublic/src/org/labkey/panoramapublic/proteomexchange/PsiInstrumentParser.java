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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PsiInstrumentParser
{
    private Map<String, PsiInstrument> _instruments;

    public PsiInstrument getInstrument(String instrumentName) throws PxException
    {
        if(_instruments == null)
        {
            _instruments = new HashMap<>();
            List<PsiInstrument> allInstruments = getInstruments();

            for(PsiInstrument instrument: allInstruments)
            {
               _instruments.put(instrument.getName(), instrument);
            }
        }
        return _instruments.get(instrumentName);
    }

    public static List<PsiInstrument> getInstruments() throws PxException
    {
        Module module = ModuleLoader.getInstance().getModule(TargetedMSModule.class);
        FileResource resource = (FileResource)module.getModuleResolver().lookup(Path.parse("psi-ms-PARSED.xml"));
        if(resource == null)
        {
            throw new PxException("Resource not found: psi-ms-PARSED.xml.");
        }
        File file = resource.getFile();
        if(file == null)
        {
            throw new PxException("File not found: psi-ms-PARSED.xml.");
        }
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db;
        Document document;
        try
        {
            db = dbf.newDocumentBuilder();
            document = db.parse(file);
        }
        catch (ParserConfigurationException | SAXException | IOException e)
        {
            throw new PxException("Error parsing psi-ms-PARSED.xml. " + e.getMessage(), e);
        }

        Element root = document.getDocumentElement();
        if(root == null)
        {
            throw new PxException("psi-ms-PARSED.xml document has no root document element.");
        }
        List<PsiInstrument> instruments = new ArrayList<>();
        NodeList list = root.getElementsByTagName("instrument");
        for(int i = 0; i < list.getLength(); i++)
        {
            Node n =  list.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                {
                    Element instrEl = (Element) n;
                    String name = instrEl.getAttribute("name");
                    String id = instrEl.getAttribute("id");
                    String description = instrEl.getAttribute("description");
                    String vendor = instrEl.getAttribute("vendor");

                    instruments.add(new PsiInstrument(id, name, description, vendor));
                }
            }
        }
        return instruments;
    }

    public static class PsiInstrument
    {
        private final String _id;
        private final String _name;
        private final String _description;
        private final String _vendor;

        PsiInstrument(String id, String name, String description, String vendor)
        {
            _id = id;
            _name = name;
            _description = description;
            _vendor = vendor;
        }

        public String getId()
        {
            return _id;
        }

        public String getName()
        {
            return _name;
        }

        public String getDescription()
        {
            return _description;
        }

        public String getVendor()
        {
            return _vendor;
        }

        public String getDisplayName()
        {
            return getName() + " (" + getVendor() + ")";
        }
    }
}

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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.User;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.targetedms.PublishTargetedMSExperimentsController;
import org.labkey.targetedms.model.ExperimentAnnotations;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Created by vsharma on 1/8/2018.
 */
public class PxXmlWriter extends PxWriter
{
    private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private static CvParamElement peerReviewedEl = new CvParamElement("MS", "MS:1002854", "Peer-reviewed dataset");
    private static CvParamElement nonPeerReviewedEl = new CvParamElement("MS", "MS:1002855", "Non peer-reviewed dataset");
    private static final String INDENT = "  ";

    private XMLStreamWriter _writer;

    public PxXmlWriter(Writer out) throws IOException
    {
        XMLOutputFactory outFactory = XMLOutputFactory.newInstance();
        try
        {
            _writer = outFactory.createXMLStreamWriter(out);
        }
        catch (XMLStreamException e)
        {
            throw new IOException(e);
        }
    }

    public PxXmlWriter(OutputStream out) throws IOException
    {
        XMLOutputFactory outFactory = XMLOutputFactory.newInstance();
        try
        {
            _writer = outFactory.createXMLStreamWriter(out);
        }
        catch (XMLStreamException e)
        {
            throw new IOException(e);
        }
    }

    void begin(ExperimentAnnotations experimentAnnotations) throws PxException
    {
        try
        {
            _writer.writeStartDocument("UTF-8", "1.0");
            _writer.writeCharacters("\n");
            _writer.writeStartElement("ProteomeXchangeDataset");
            _writer.writeAttribute("id", experimentAnnotations.getPxid() == null ? "PXD000000" : experimentAnnotations.getPxid());
            _writer.writeAttribute("formatVersion", "1.4.0");
            _writer.writeAttribute("xsi:noNamespaceSchemaLocation", "proteomeXchange-1.4.0.xsd");
            _writer.writeNamespace("xsi","http://www.w3.org/2001/XMLSchema-instance");

            writeCVList(_writer);
        }
        catch (XMLStreamException e)
        {
            throw new PxException("Error writing beginning of document.", e);
        }
    }

    void end() throws PxException
    {
        try
        {
            _writer.writeCharacters("\n");
            _writer.writeEndElement();
            _writer.writeEndDocument();

        }
        catch (XMLStreamException e)
        {
            throw new PxException("Error writing document end", e);
        }
    }

    void close() throws PxException
    {
        if(_writer != null)
        {
            try
            {
                _writer.close();
            }
            catch (XMLStreamException ignored){}
        }
    }

    @Override
    void writeChangeLog(PublishTargetedMSExperimentsController.PxExportForm form) throws PxException
    {
        if(!StringUtils.isBlank(form.getChangeLog()))
        {
            Element changeLogEl = new Element(("ChangeLog"));
            Element clEntryEl = new Element("ChangeLogEntry");
            clEntryEl.setText(form.getChangeLog());
            changeLogEl.addChild(clEntryEl);
            try
            {
                writeElement(_writer, changeLogEl);
            }
            catch (XMLStreamException e)
            {
                throw new PxException("Error writing change log.", e);
            }
        }
    }

    void writeDatasetLinkList(ShortURLRecord accessUrl) throws PxException
    {
        /*
        <FullDatasetLinkList>
            <FullDatasetLink>
                <cvParam cvRef="MS" accession="MS:1002032" name="PeptideAtlas dataset URI" value="http://www.PeptideAtlas.org/PASS/PASS00726" />
            </FullDatasetLink>
            <FullDatasetLink>
                <cvParam cvRef="MS" accession="MS:1002420" name="PASSEL experiment URI" value="https://db.systemsbiology.net/sbeams/cgi/PeptideAtlas/GetSELExperiments?SEL_experiment_id=145" />
            </FullDatasetLink>
            <FullDatasetLink>
                <cvParam cvRef="MS" accession="MS:1002031" name="PASSEL transition group browser URI" value="https://db.systemsbiology.net/sbeams/cgi/PeptideAtlas/GetSELTransitions?row_limit=5000&amp;SEL_experiments=145&amp;QUERY_NAME=AT_GetSELTransitions&amp;action=QUERY&amp;uploaded_file_not_saved=1&amp;apply_action=QUERY" />
            </FullDatasetLink>
        </FullDatasetLinkList>
         */

        Element fullDatasetLinkList = new Element("FullDatasetLinkList");
        Element fullDatasetLink = new Element("FullDatasetLink");
        // MS:1002873 Panorama Public dataset URI
        fullDatasetLink.addChild(new CvParamElement("MS", "MS:1002873", "Panorama Public dataset URI", getAccessUrlString(accessUrl)));
        fullDatasetLinkList.addChild(fullDatasetLink);
        try
        {
            writeElement(_writer, fullDatasetLinkList);
        }
        catch (XMLStreamException e)
        {
            throw new PxException("Error writing dataset link list.", e);
        }
    }

    void writeKeywordList(ExperimentAnnotations expAnnotations) throws PxException
    {
        /*
        <KeywordList>
            <cvParam cvRef="MS" accession="MS:1001926" name="curator keyword" value="selected reaction monitoring" />
            <cvParam cvRef="MS" accession="MS:1001925" name="submitter keyword" value=" Metal Oxide Chromatography, phosphoproteomics"/>
        </KeywordList>
         */
        String keywords = expAnnotations.getKeywords();
        if(!StringUtils.isBlank(keywords))
        {
            Element keywordList = new Element("KeywordList");
            String[] array = StringUtils.split(keywords, ",");
            for(String keyword: array)
            {
                keywordList.addChild(new CvParamElement("MS", "MS:1001925", "submitter keyword", keyword.trim()));
            }
            try
            {
                writeElement(_writer, keywordList);
            }
            catch (XMLStreamException e)
            {
                throw new PxException("Error writing keywords.", e);
            }
        }
    }

    void writePublicationList(ExperimentAnnotations expAnnotations, PublishTargetedMSExperimentsController.PxExportForm form) throws PxException
    {
        /*
        <PublicationList>
            <Publication id="PMID26216844">
                <cvParam cvRef="MS" accession="MS:1000879" name="PubMed identifier" value="26216844" />
                <cvParam cvRef="MS" accession="MS:1002866" name="Reference" value="Rücker N, Billig S, Bücker R, Jahn D, Wittmann C, Bange FC, Acetate Dissimilation and Assimilation in Mycobacterium tuberculosis Depend on Carbon Availability. 2015 197(19):3182-90" />
            </Publication>
        </PublicationList>

        OR

        <PublicationList>
            <Publication id="pending">
                <cvParam cvRef="MS" accession="MS:1002858" name="Dataset with its publication pending"/>
            </Publication>
        </PublicationList>
         */

        Element publication_list = new Element("PublicationList");
        boolean hasPubmedId = !StringUtils.isBlank(form.getPublicationId());
        if(hasPubmedId || form.getPeerReviewed())
        {
            Element publication = new Element("Publication");
            String id = hasPubmedId ? "PMID" + form.getPublicationId() : "pubmed_id_pending";
            publication.setAttributes(Collections.singletonList(new Attribute("id", id)));
            if(hasPubmedId)
            {
                publication.addChild(new CvParamElement("MS", "MS:1000879", "PubMed identifier", form.getPublicationId()));
            }
            String reference = form.getPublicationReference();
            if(StringUtils.isBlank(reference))
            {
                reference = expAnnotations.getCitation();
            }
            publication.addChild(new CvParamElement("MS", "MS:1002866", "Reference", reference));
            publication_list.addChild(publication);
        }
        else
        {
            // MS:1002865 - "Accepted manuscript" A dataset has one associated manuscript, which has been accepted but no PubMedID is available yet
            // MS:1002858 - "Dataset with its publication pending" A dataset which has an associated manuscript pending for publication.
            // MS:1002853 - "Dataset with no associated published manuscript"
            Element publication = new Element("Publication");
            publication.setAttributes(Collections.singletonList(new Attribute("id", "pending")));
            publication.addChild(new CvParamElement("MS", "MS:1002858", "Dataset with its publication pending"));
            publication_list.addChild(publication);
        }
        try
        {
            writeElement(_writer, publication_list);
        }
        catch (XMLStreamException e)
        {
            throw new PxException("Error writing publication list.", e);
        }
    }

    void writeContactList(ExperimentAnnotations expAnnotations, PublishTargetedMSExperimentsController.PxExportForm form) throws PxException
    {
        /*
        <ContactList>
            <Contact id="c001">
                <cvParam cvRef="MS" accession="MS:1002332" name="lab head" />
                <cvParam cvRef="MS" accession="MS:1000586" name="contact name" value="Eva-Mari Aro" />
                <cvParam cvRef="MS" accession="MS:1000590" name="contact affiliation" value="University of Turku" />
                <cvParam cvRef="MS" accession="MS:1000589" name="contact email" value="evaaro@utu.fi" />
            </Contact>
            <Contact id="c002">
                <cvParam cvRef="MS" accession="MS:1002037" name="dataset submitter" />
                <cvParam cvRef="MS" accession="MS:1000586" name="contact name" value="Dorota Muth-Pawlak" />
                <cvParam cvRef="MS" accession="MS:1000590" name="contact affiliation" value="University of Turku" />
                <cvParam cvRef="MS" accession="MS:1000589" name="contact email" value="dokrmu@utu.fi" />
            </Contact>
        </ContactList>
         */
        Element list = new Element("ContactList");
        User labHead = expAnnotations.getLabHeadUser();
        String labHeadAffiliation = expAnnotations.getLabHeadAffiliation();
        if(labHead == null)
        {
            // Use submitter details if a lab head was not saved with the experiment details.
            labHead = expAnnotations.getSubmitterUser();
            labHeadAffiliation = expAnnotations.getSubmitterAffiliation();
        }
        String labHeadName = labHead != null ? labHead.getFullName() : null;
        String labHeadEmail = labHead != null ? labHead.getEmail() : null;
        // Check if there is a form override
        if(!StringUtils.isBlank(form.getLabHeadName()))
        {
            labHeadName = form.getLabHeadName();
            labHeadEmail = form.getLabHeadEmail();
            labHeadAffiliation = form.getLabHeadAffiliation();
        }

        Element labHeadEl = new Element("Contact");
        labHeadEl.setAttributes(Collections.singletonList(new Attribute("id", "lab_head")));
        labHeadEl.addChild(new CvParamElement("MS", "MS:1002332", "lab head"));
        labHeadEl.addChild(new CvParamElement("MS", "MS:1000586", "contact name", labHeadName));
        if(!StringUtils.isBlank(labHeadEmail))
        {
            labHeadEl.addChild(new CvParamElement("MS", "MS:1000589", "contact email", labHeadEmail));
        }
        if(!StringUtils.isBlank(labHeadAffiliation))
        {
            labHeadEl.addChild(new CvParamElement("MS", "MS:1000590", "contact affiliation", labHeadAffiliation));
        }
        list.addChild(labHeadEl);

        User submitter = expAnnotations.getSubmitterUser();
        if(submitter != null)
        {
            Element submitterEl = new Element("Contact");
            submitterEl.setAttributes(Collections.singletonList(new Attribute("id", "dataset_submitter")));
            submitterEl.addChild(new CvParamElement("MS", "MS:1002037", "dataset submitter"));
            submitterEl.addChild(new CvParamElement("MS", "MS:1000586", "contact name", submitter.getFullName()));
            submitterEl.addChild(new CvParamElement("MS", "MS:1000589", "contact email", submitter.getEmail()));
            if(!StringUtils.isBlank(expAnnotations.getSubmitterAffiliation()))
            {
                submitterEl.addChild(new CvParamElement("MS", "MS:1000590", "contact affiliation", expAnnotations.getSubmitterAffiliation()));
            }
            list.addChild(submitterEl);
        }

        try
        {
            writeElement(_writer, list);
        }
        catch (XMLStreamException e)
        {
            throw new PxException("Error writing contact list.", e);
        }
    }

    void writeModificationList(ExperimentAnnotations expAnnotations) throws PxException
    {
        /*
        <ModificationList>
            <cvParam cvRef="MS" accession="MS:1002864" name="No PTMs are included in the dataset" />
        </ModificationList>
         */
        /*
        <ModificationList>
            <cvParam cvRef="MOD" accession="MOD:00587" name="6x(13)C,4x(15)N labeled L-arginine" />
            <cvParam cvRef="MOD" accession="MOD:00582" name="6x(13)C,2x(15)N labeled L-lysine" />
            <cvParam cvRef="MOD" accession="MOD:01060" name="S-carboxamidomethyl-L-cysteine" />
        </ModificationList>
         */
        Element mod_list = new Element("ModificationList");
        List<ExperimentModificationGetter.PxModification> mods = ExperimentModificationGetter.getModifications(expAnnotations);
        if(mods.size() == 0)
        {
            mod_list.addChild(new CvParamElement("MS", "MS:1002864", "No PTMs are included in the dataset"));
        }
        Set<String> seen = new HashSet<>();
        for(ExperimentModificationGetter.PxModification mod: mods)
        {
            if(seen.contains(mod.getName()))
            {
                continue;
            }
            seen.add(mod.getName());
            String unimodId = mod.hasUnimodId() ? mod.getUnimodId() : "NO_UNIMOD_ID";
            mod_list.addChild(new CvParamElement("UNIMOD", unimodId, mod.getName()));
        }

        try
        {
            writeElement(_writer, mod_list);
        }
        catch (XMLStreamException e)
        {
            throw new PxException("Error writing modification list.", e);
        }
    }

    void writeInstrumentList(ExperimentAnnotations expAnnotations) throws PxException
    {
        /*
        <InstrumentList>
            <Instrument id="TSQ">
                <cvParam cvRef="MS" accession="MS:1001510" name="TSQ Vantage" />
            </Instrument>
        </InstrumentList>
         */
        Element instrument_list = new Element("InstrumentList");
        List<String> instruments = expAnnotations.getInstruments();
        PsiInstrumentParser parser = new PsiInstrumentParser();
        int i = 1;
        for(String instrumentName: instruments)
        {
            PsiInstrumentParser.PsiInstrument instrument = parser.getInstrument(instrumentName);

            if(instrument == null)
            {
                instrument = new PsiInstrumentParser.PsiInstrument("NO_PSI_ID", instrumentName, null, null);
            }

            instrument_list.addChild(getInstrumentElement(instrument, i++));
        }
        try
        {
            writeElement(_writer, instrument_list);
        }
        catch (XMLStreamException e)
        {
            throw new PxException("Error writing instrument list.", e);
        }
    }

    @NotNull
    private PxXmlWriter.Element getInstrumentElement(PsiInstrumentParser.PsiInstrument instrument, int index)
    {
        Element instrument1 = new Element("Instrument");
        instrument1.setAttributes(Collections.singletonList(new Attribute("id", "instrument_" + index)));
        instrument1.addChild(new CvParamElement("MS", instrument.getId(), instrument.getName()));
        return instrument1;
    }

    void writeSpeciesList(ExperimentAnnotations expAnnotations) throws PxException
    {
        /*
        <SpeciesList>
            <Species>
                <cvParam cvRef="MS" accession="MS:1001469" name="taxonomy: scientific name" value="Synechocystis sp. PCC 6803" />
                <cvParam cvRef="MS" accession="MS:1001467" name="taxonomy: NCBI TaxID" value="1148" />
            </Species>
        </SpeciesList>
         */

        Map<String, Integer> organisms = expAnnotations.getOrganismAndTaxId();
        Element sp_list = new Element("SpeciesList");

        List<Integer> taxIds = new ArrayList<>();
        for(Integer taxId: organisms.values())
        {
            if(taxId != null)
            {
                taxIds.add(taxId);
            }
        }

        Map<Integer, String> sciNameMap = getScientificNames(taxIds);

        for(String orgName: organisms.keySet())
        {
            Integer taxid = organisms.get(orgName);
            String sciName = orgName;
            if(taxid != null)
            {
                sciName = sciNameMap.get(taxid);
                if(StringUtils.isBlank(sciName))
                {
                    sciName = orgName;
                }
            }

            String taxIdStr = taxid == null ? "NO_TAX_ID" : String.valueOf(taxid);
            Element sp = new Element("Species");
            sp.addChild(new CvParamElement("MS", "MS:1001469", "taxonomy: scientific name", sciName));
            sp.addChild(new CvParamElement("MS", "MS:1001467", "taxonomy: NCBI TaxID", taxIdStr));

            sp_list.addChild(sp);
        }

        try
        {
            writeElement(_writer, sp_list);
        }
        catch (XMLStreamException e)
        {
            throw new PxException("Error writing species list.", e);
        }
    }

    void writeDatasetOriginList() throws PxException
    {
        // TODO: Check for re-processed data??
        Element do_list = new Element("DatasetOriginList");
        Element dor = new Element("DatasetOrigin");
        dor.addChild(new CvParamElement("MS", "MS:1002868", "Original data"));
        do_list.addChild(dor);

        try
        {
            writeElement(_writer, do_list);
        }
        catch (XMLStreamException e)
        {
            throw new PxException("Error writing dataset origin list.", e);
        }
    }

    void writeDatasetIdentifierList(String pxId, ShortURLRecord accessUrl) throws PxException
    {
        Element di_list = new Element("DatasetIdentifierList");
        Element di1 = new Element("DatasetIdentifier");
        di1.addChild(new CvParamElement("MS", "MS:1001919", "ProteomeXchange accession number", StringUtils.isBlank(pxId) ? "PXD000000" : pxId));
//        di2.addChild(new CvParamElement("MS", "MS:1001921", "ProteomeXchange accession number version number", "1"));
        di_list.addChild(di1);
        Element di2 = new Element("DatasetIdentifier");
        di2.addChild(new CvParamElement("MS", "MS:1002872", "Panorama Public dataset identifier", getAccessUrlString(accessUrl)));
        di_list.addChild(di2);
        try
        {
            writeElement(_writer, di_list);
        }
        catch (XMLStreamException e)
        {
            throw new PxException("Error writing dataset identifier list", e);
        }
    }

    private void writeCVList(XMLStreamWriter writer) throws XMLStreamException
    {
        Element el = new Element("CvList");

        Element el1 = makeCVElement("MS", "PSI-MS", "https://raw.githubusercontent.com/HUPO-PSI/psi-ms-Cv/master/psi-ms.obo");
        Element el2 = makeCVElement("MOD", "PSI-MOD", "https://raw.githubusercontent.com/HUPO-PSI/psi-mod-CV/master/PSI-MOD.obo");
        Element el3 = makeCVElement("UNIMOD", "UNIMOD", "http://www.unimod.org/obo/unimod.obo");

        List<Element> cvEls = new ArrayList<>(3);
        cvEls.add(el1);
        cvEls.add(el2);
        cvEls.add(el3);

        el.setChildren(cvEls);

        writeElement(writer, el);
    }

    private Element makeCVElement (String id, String fullName, String uri) throws XMLStreamException
    {
        List<Attribute> attributes = new ArrayList<>(3);
        attributes.add(new Attribute("id", id));
        attributes.add(new Attribute("fullName", fullName));
        attributes.add(new Attribute("uri", uri));

        Element el = new Element("Cv");
        el.setAttributes(attributes);
        return el;
    }

    void writeDatasetSummary(ExperimentAnnotations annotations, PublishTargetedMSExperimentsController.PxExportForm form) throws PxException
    {
        Element el = new Element("DatasetSummary");
        List<Attribute> attributes = new ArrayList<>(3);
        attributes.add(new Attribute("announceDate", dateFormat.format(new Date())));
        attributes.add(new Attribute("hostingRepository", "PanoramaPublic"));
        attributes.add(new Attribute("title", annotations.getTitle()));

        el.setAttributes(attributes);

        Element desc = new Element("Description");
        desc.setText(annotations.getAbstract());
        el.addChild(desc);


        Element reviewLevel = new Element("ReviewLevel");
        reviewLevel.addChild((form.getPeerReviewed() || annotations.isPublished()) ? peerReviewedEl : nonPeerReviewedEl);
        el.addChild(reviewLevel);

        Element repoSupport = new Element("RepositorySupport");
        repoSupport.addChild(new CvParamElement("MS", "MS:1002856", "Supported dataset by repository"));
        el.addChild(repoSupport);

        try
        {
            writeElement(_writer, el);
        }
        catch (XMLStreamException e)
        {
            throw new PxException("Error writing dataset summary.", e);
        }
    }

    private void writeElement(XMLStreamWriter writer, Element element) throws XMLStreamException
    {
        writeElement(writer, element, INDENT);
    }

    private void writeElement(XMLStreamWriter writer, Element element, String indent) throws XMLStreamException
    {
        writer.writeCharacters("\n");
        writer.writeCharacters(indent);
        if(StringUtils.isBlank(element.getText()) && element.getChildren().size() == 0)
        {
            writer.writeEmptyElement(element.getName());
        }
        else
        {
            writer.writeStartElement(element.getName());
        }

        if(!StringUtils.isBlank(element.getText()))
        {
            writer.writeCharacters(element.getText());
        }

        for(Attribute attrib: element.getAttributes())
        {
            writer.writeAttribute(attrib.getName(), attrib.getValue());
        }

        for(Element child: element.getChildren())
        {
            writeElement(writer, child, indent + INDENT);
        }

        if(!StringUtils.isBlank(element.getText()) || element.getChildren().size() != 0)
        {
            if(element.getChildren().size() != 0)
            {
                writer.writeCharacters("\n");
                writer.writeCharacters(indent);
            }
            writer.writeEndElement();
        }
    }

    private static class Element
    {
        private final String _name;
        private String _text;
        private List<Attribute> _attributes;
        private List<Element> _children;

        public Element(String name)
        {
            _name = name;
        }

        public String getName()
        {
            return _name;
        }

        public String getText()
        {
            return _text;
        }

        public void setText(String text)
        {
            _text = text;
        }

        public List<Attribute> getAttributes()
        {
            return _attributes != null ? _attributes : Collections.emptyList();
        }

        public void setAttributes(List<Attribute> attributes)
        {
            _attributes = attributes;
        }

        public List<Element> getChildren()
        {
            return _children != null ? _children : Collections.emptyList();
        }

        public void setChildren(List<Element> children)
        {
            _children = children;
        }

        public void addChild(Element element)
        {
            if(_children == null)
            {
                _children = new ArrayList<>();
            }
            _children.add(element);
        }
    }

    private static final class Attribute
    {
        private final String _name;
        private final String _value;

        private Attribute(String name, String value)
        {
            _name = name;
            _value = value;
        }

        public String getName()
        {
            return _name;
        }

        public String getValue()
        {
            return _value;
        }
    }

    private final static class CvParamElement extends Element
    {
        public CvParamElement(String cvRef, String accession, String name)
        {
            super("cvParam");
            List<Attribute> attributes = new ArrayList<>(3);
            attributes.add(new Attribute("cvRef", cvRef));
            attributes.add(new Attribute("accession", accession));
            attributes.add(new Attribute("name", name));
            setAttributes(attributes);
        }

        public CvParamElement(String cvRef, String accession, String name, String value)
        {
            super("cvParam");
            List<Attribute> attributes = new ArrayList<>(3);
            attributes.add(new Attribute("cvRef", cvRef));
            attributes.add(new Attribute("accession", accession));
            attributes.add(new Attribute("name", name));
            attributes.add(new Attribute("value", value));
            setAttributes(attributes);
        }
    }
}

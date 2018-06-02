/*
 * Copyright (c) 2013 LabKey Corporation
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

package org.labkey.passport;

import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.passport.model.IFeature;
import org.labkey.passport.model.IFile;
import org.labkey.passport.model.IKeyword;
import org.labkey.passport.model.IPeptide;
import org.labkey.passport.model.IProtein;
import org.labkey.passport.view.ProteinListView;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PassportController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(PassportController.class);

    public PassportController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            ProteinListView runListView = ProteinListView.createView(getViewContext());
            VBox vbox = new VBox();
            vbox.addView(runListView);
            return vbox;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ProteinAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
//            Portal.resetPages(getContainer(),new ArrayList<>(),true);
            ViewContext viewContext = getViewContext();
            String accession = viewContext.getRequest().getParameter("accession");
            IProtein protein = getProtein(accession);
            return new JspView<>("/org/labkey/passport/view/ProteinWebPart.jsp", protein);
        }


        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    private void populateUniprotData(IProtein p)
    {
        String url = "http://www.uniprot.org/uniprot/?query=accession:"+p.getAccession()+"&format=xml";

        try
        {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            int responseCode = con.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) { // success
                BufferedReader in = new BufferedReader(new InputStreamReader(
                        con.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // print result
//                DOMParser parser = new DOMParser();
//                parser.parse(url);
//                Document doc = parser.getDocument();
                DocumentBuilderFactory dbf =
                        DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                InputSource is = new InputSource();
                is.setCharacterStream(new StringReader(response.toString()));

                Document doc = db.parse(is);
                doc.getFirstChild();
                Element entry = (Element) doc.getFirstChild().getFirstChild();
                NodeList featureElements = entry.getElementsByTagName("feature");
                List<IFeature> features = new ArrayList<>();
                for(int i = 0; i < featureElements.getLength(); i++) {
                    Element feature = (Element) featureElements.item(i);
                    IFeature f = new IFeature();
                    f.setType(feature.getAttribute("type"));
                    f.setDescription(feature.getAttribute("description"));
                    Element location = (Element) feature.getElementsByTagName("location").item(0);
                    if(f.isVariation()) {
                        Integer loc =Integer.parseInt(((Element) location.getElementsByTagName("position").item(0)).getAttribute("position"));
                        if(loc == null)
                            continue;
                        f.setStartIndex(loc);
                        f.setEndIndex(loc);
                        String original = feature.getElementsByTagName("original").item(0).getFirstChild().getNodeValue();
                        String variation = feature.getElementsByTagName("variation").item(0).getFirstChild().getNodeValue();
                        f.setOriginal(original);
                        f.setVariation(variation);
                    } else {
                        if(location.getElementsByTagName("begin").getLength() == 1) {
                            Integer begin =Integer.parseInt(((Element) location.getElementsByTagName("begin").item(0)).getAttribute("position"));
                            Integer end =Integer.parseInt(((Element) location.getElementsByTagName("end").item(0)).getAttribute("position"));
                            if(begin == null || end == null)
                                continue;
                            f.setStartIndex(begin);
                            f.setEndIndex(end);
                        } else {
                            Integer loc =Integer.parseInt(((Element) location.getElementsByTagName("position").item(0)).getAttribute("position"));
                            if(loc == null)
                                continue;
                            f.setStartIndex(loc);
                            f.setEndIndex(loc);
                        }

                    }
                    features.add(f);
                }
                p.setFeatures(features.toArray(new IFeature[features.size()]));
            } else {
                System.out.println("GET request not worked");
            }
          }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }


    // returns null if no protein found
    private IProtein getProtein(String accession)
    {
        SQLFragment targetedMSProteinQuery = new SQLFragment();
        targetedMSProteinQuery.append("SELECT pg.id, pg.label, pg.gene, pg.species, pg.preferredname, pg.runid, r.dataid, r.filename, r.created, r.modified, r.formatversion " +
                "FROM targetedms.peptidegroup pg, targetedms.runs r " +
                "WHERE r.id = pg.runid AND r.container = ? AND pg.accession = ?;");
        targetedMSProteinQuery.add(getContainer().getId());
        targetedMSProteinQuery.add(accession);
        UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), "targetedms");
        try {
            Map<String, Object> map = new SqlSelector(schema.getDbSchema(), targetedMSProteinQuery).getMap();

            IProtein p = new IProtein();
            if(map == null)
                return null;
            p.setLabel((String) map.get("label"));
            p.setGene((String) map.get("gene"));
            p.setSpecies((String) map.get("species"));
            p.setPreferredname((String) map.get("preferredname"));
            p.setPepGroupId((Integer) map.get("id"));

            IFile f = new IFile();
            f.setFileName((String) map.get("filename"));
            f.setSoftwareVersion((String) map.get("softwareversion"));
            f.setCreatedDate((Date) map.get("created"));
            f.setModifiedDate((Date) map.get("modified"));
            f.setRunId((Integer) map.get("runid"));

            p.setFile(f);
            p.setAccession(accession);
            getProtTableInfo(p);
            getKeywords(p);
            populateUniprotData(p);
            p.setPep(getPeptides(p));
            return p;
        }
        catch(Exception e) {
            return null;
        }

    }

    private void getKeywords(IProtein p) {
        String qs = "SELECT kw.keywordid, kw.keyword, kw.category, kc.label "+
        "FROM "+
        "targetedms.peptidegroup pg, "+
        "targetedms.runs r, "+
        "prot.sequences p, "+
        "prot.annotations a, "+
        "prot.identifiers pi, "+
        "passport.keywords kw, "+
        "passport.keywordcategories kc "+
        "WHERE r.id = pg.runid AND p.seqid = pg.sequenceid AND a.seqid = p.seqid AND pi.identid = a.annotident AND kw.keywordid = pi.identifier AND kc.categoryid = kw.category "+
        "AND r.container = ? AND pg.accession = ? ";
        UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), "targetedms");
        SQLFragment keywordQuery = new SQLFragment();
        keywordQuery.append(qs);
        keywordQuery.add(getContainer().getId());
        keywordQuery.add(p.getAccession());

        SqlSelector sqlSelector = new SqlSelector(schema.getDbSchema(), keywordQuery);
        List<IKeyword> keywords = new ArrayList<>();
        try {sqlSelector.forEach(prot -> {
            keywords.add(new IKeyword(prot.getString("keywordid"),
                    prot.getString("category"),
                    prot.getString("keyword"),
                    prot.getString("label")));
        });
        }catch(Exception e){
            return; // TODO what to do if error here?
        }
        p.setKeywords(keywords.toArray(new IKeyword[keywords.size()]));
    }

    private void getProtTableInfo(IProtein p) {
        if(p == null)
            return;
        UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), "targetedms");
        SQLFragment protTableQuery = new SQLFragment();
        protTableQuery.append("SELECT DISTINCT id.seqid, s.* FROM prot.identifiers AS id, prot.sequences AS s WHERE identifier = ? AND id.seqid = s.seqid;");
        protTableQuery.add(p.getAccession());
        SqlSelector sqlSelectorProt = new SqlSelector(schema.getDbSchema(), protTableQuery);
        try {sqlSelectorProt.forEach(prot -> {
            p.setDescription(prot.getString("description"));
            p.setSequence(prot.getString("protsequence"));
            p.setLength(prot.getInt("length"));
        });
        }catch(Exception e){
            return; // TODO what to do if error here?
        }
    }

    private IPeptide[] getPeptides(IProtein p) {
        if(p == null)
            return null;
            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), "targetedms");
            SQLFragment peptidesQuery = new SQLFragment();
            peptidesQuery.append("SELECT gp.id, p.sequence, p.id AS peptideid, gp.charge AS precursorcharge, p.startindex, p.endindex,pci.totalarea,pci.id AS panoramaprecursorid, r.name ");
            peptidesQuery.append("FROM targetedms.generalmolecule gm, targetedms.peptide p, targetedms.generalprecursor gp,targetedms.precursorchrominfo pci,targetedms.samplefile sf,targetedms.replicate r ");
            peptidesQuery.append("WHERE p.id = gm.id AND gp.generalmoleculeid = gm.id AND pci.precursorid = gp.id AND sf.id = pci.samplefileid AND r.id = sf.replicateid AND gm.peptidegroupid = ?;");
            peptidesQuery.add(p.getPepGroupId());
            Map<Integer, IPeptide> peptideMap = new HashMap<>();
        try {
            SqlSelector sqlSelectorPep = new SqlSelector(schema.getDbSchema(), peptidesQuery);
            sqlSelectorPep.forEach(pep -> {
                int peptideId = pep.getInt("peptideid");
                IPeptide peptide = peptideMap.get(peptideId);
                if(peptide == null) {
                    peptide = new IPeptide();
                    peptide.setSequence(pep.getString("sequence"));
                    peptide.setStartIndex(pep.getInt("startindex"));
                    peptide.setEndIndex(pep.getInt("endindex"));
                    peptide.setPanoramaPeptideId(peptideId);
                    peptide.setProteinId(p.getProteinId());
                }
                long totalArea = pep.getLong("totalarea");
                String replicateName = pep.getString("name");
                if(replicateName.equals("BeforeIncubation")) {
                    peptide.setBeforeIntensity(totalArea);
                    peptide.setPrecursorbeforeid(pep.getInt("panoramaprecursorid"));
                } else if(replicateName.equals("AfterIncubation")) {
                    peptide.setAfterIntensity(totalArea);
                }
                peptideMap.put(peptide.getPanoramaPeptideId(), peptide);
            });
        } catch(Exception e) {
            return null; // TODO what to do if error here?
        }
        IPeptide[] peptides = peptideMap.values().toArray(new IPeptide[peptideMap.size()]);
        for(int i = 0; i < peptides.length; i++) {
            IPeptide peptide = peptides[i];
            // NORMALIZE AFTER INTENSITY TODO
//                var normalizedAfterTotalArea = null;
//                // Normalize AfterIncubation TotalArea to global standards from Panorama
//                if(afterIncubation.GlobalStandardArea != null && beforeIncubation.GlobalStandardArea != null) {
//                    var afterRatioToGlobalStandards  = afterIncubation.TotalArea.value / afterIncubation.GlobalStandardArea.value;
//                    var beforeRatioToGlobalStandards  = beforeIncubation.TotalArea.value / beforeIncubation.GlobalStandardArea.value;
//                    var afterToBeforeRatio = afterRatioToGlobalStandards /
//                            beforeRatioToGlobalStandards;
//                    normalizedAfterTotalArea = beforeIncubation.TotalArea.value * afterToBeforeRatio;
//                    afterIncubation.TotalArea.value = normalizedAfterTotalArea;
//                }
        }
        return peptides;
    }
}
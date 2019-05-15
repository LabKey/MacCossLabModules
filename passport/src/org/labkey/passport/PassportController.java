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

import com.mchange.v2.sql.SqlUtils;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlFactory;
import org.labkey.api.data.SqlScriptRunner;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
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
import org.labkey.passport.model.IProject;
import org.labkey.passport.model.IProtein;
import org.labkey.passport.view.ProteinListView;
import org.labkey.remoteapi.query.ExecuteSqlCommand;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;
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
        String url = "https://www.uniprot.org/uniprot/?query=accession:"+p.getAccession()+"&format=xml";
        List<IFeature> features = new ArrayList<>();
        try
        {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            int responseCode = con.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) { // success
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                DocumentBuilderFactory dbf =
                        DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                InputSource is = new InputSource();
                is.setCharacterStream(new StringReader(response.toString()));

                Document doc = db.parse(is);
                doc.getFirstChild();
                Element entry = (Element) doc.getFirstChild().getFirstChild();
                NodeList featureElements = entry.getElementsByTagName("feature");
                for(int i = 0; i < featureElements.getLength(); i++) {
                    try {
                        Element feature = (Element) featureElements.item(i);
                        IFeature f = new IFeature();
                        f.setType(feature.getAttribute("type"));
                        f.setDescription(feature.getAttribute("description"));
                        Element location = (Element) feature.getElementsByTagName("location").item(0);
                        if(f.isVariation()) {
                            if(location.getChildNodes().getLength() == 1) {
                                Integer loc =Integer.parseInt(((Element) location.getElementsByTagName("position").item(0)).getAttribute("position"));
                                if(loc == null)
                                    continue;
                                f.setStartIndex(loc);
                                f.setEndIndex(loc);
                                if(feature.getElementsByTagName("original").getLength() == 0 || feature.getElementsByTagName("variation").getLength() == 0) {
                                    continue;
                                }
                                String original = feature.getElementsByTagName("original").item(0).getFirstChild().getNodeValue();
                                String variation = feature.getElementsByTagName("variation").item(0).getFirstChild().getNodeValue();
                                f.setOriginal(original);
                                f.setVariation(variation);
                            } else if(location.getChildNodes().getLength() == 2) {
                                f = getPosition(f, location);
                                if(f == null)
                                    continue;
                            }

                        } else {
                           f = getPosition(f, location);
                           if(f == null)
                               continue;
                        }
                        features.add(f);
                    } catch(Exception e) {
                        // we don't really care at the moment but exception is likely if xml is formatted differently than expected or given in the spec which happens sometimes
                        continue;
                    }
                }
                p.setFeatures(features.toArray(new IFeature[features.size()]));
            } else {
                System.out.println("GET request did not work");
            }
          }
        catch (Exception e)
        {
            p.setFeatures(features.toArray(new IFeature[features.size()]));
            e.printStackTrace();
        }
    }

    private IFeature getPosition(IFeature f, Element location) {
        if(location.getElementsByTagName("begin").getLength() == 1) {
            Integer begin =Integer.parseInt(((Element) location.getElementsByTagName("begin").item(0)).getAttribute("position"));
            Integer end =Integer.parseInt(((Element) location.getElementsByTagName("end").item(0)).getAttribute("position"));
            if(begin == null || end == null)
                return null;
            f.setStartIndex(begin);
            f.setEndIndex(end);
        } else {
            Integer loc =Integer.parseInt(((Element) location.getElementsByTagName("position").item(0)).getAttribute("position"));
            if(loc == null)
                return null;
            f.setStartIndex(loc);
            f.setEndIndex(loc);
        }
        return f;
    }
    // returns null if no protein found
    private IProtein getProtein(String accession)
    {
        SQLFragment targetedMSProteinQuery = new SQLFragment();
        targetedMSProteinQuery.append("SELECT ps.seqid as seqid, ps.bestgenename, ps.description, ps.protsequence, ps.length, " +
                "pg.id as pgid, pg.species, pg.preferredname, pg.runid, pg.label, " +
                "r.dataid, r.filename, r.created, r.modified, r.formatversion " +
                "FROM targetedms.peptidegroup pg, targetedms.runs r, prot.sequences ps " +
                "WHERE r.id = pg.runid AND r.container = ? AND ps.seqid = pg.sequenceid " +
                "AND pg.accession = ? LIMIT 1;");
        targetedMSProteinQuery.add(getContainer().getId());
        targetedMSProteinQuery.add(accession);
        UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), "targetedms");
        try {
            Map<String, Object> map = new SqlSelector(schema.getDbSchema(), targetedMSProteinQuery).getMap();

            IProtein p = new IProtein();
            if(map == null)
                return null;
            p.setLabel((String) map.get("label"));
            p.setGene((String) map.get("bestgenename"));
            p.setSpecies((String) map.get("species"));
            p.setPreferredname((String) map.get("preferredname"));
            p.setPepGroupId((Integer) map.get("pgid"));
            p.setSequenceId((Integer) map.get("seqid"));
            p.setDescription((String) map.get("description"));
            p.setSequence((String) map.get("protsequence"));
            p.setLength((Integer) map.get("length"));

            IFile f = new IFile();
            f.setFileName((String) map.get("filename"));
            f.setSoftwareVersion((String) map.get("softwareversion"));
            f.setCreatedDate((Date) map.get("created"));
            f.setModifiedDate((Date) map.get("modified"));
            f.setRunId((Integer) map.get("runid"));

            p.setFile(f);
            p.setAccession(accession);
            populateProteinKeywords(p);
            populateUniprotData(p);
            populatePeptides(p);
//            populateProjects(p); TODO: project stuff needs more work - will happen next update
            return p;
        }
        catch(Exception e) {
            return null;
        }

    }

    private void populateProjects(IProtein p)
    {
        String qs = "SELECT r.id, r.filename, r.container, pg.id as pgid, p.startindex, p.endindex, p.sequence, p.peptidemodifiedsequence, p.id as pepid FROM "+
        "( SELECT pg.sequenceid, r.container FROM targetedms.peptidegroup pg, targetedms.runs r " +
                "WHERE r.id = pg.runid AND r.container = ? AND pg.accession = ?) a, " +
                "targetedms.peptidegroup pg, targetedms.runs r, targetedms.peptide p, targetedms.generalmolecule gm " +
                "WHERE pg.sequenceid = a.sequenceid AND r.id = pg.runid AND gm.peptidegroupid = pg.id AND p.id = gm.id " +
                "AND r.container != ?";
        SQLFragment projectsQuery = new SQLFragment();
        projectsQuery.append(qs);
        projectsQuery.add(getContainer());
        projectsQuery.add(p.getAccession());
        projectsQuery.add(getContainer());

        UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), "targetedms");
        SqlSelector sqlSelectorProjects = new SqlSelector(schema.getDbSchema(), projectsQuery);

        String localfolder = "31df7149-20d7-1036-9f4d-73a96a275a08";
        String livefolder = "b5d567d5-849e-1032-b843-3013bb9b1d5e";
        Container panoramaPublic = ContainerManager.getForId(localfolder);
        if(panoramaPublic == null)
            return;
        List<Container> children = ContainerManager.getAllChildren(panoramaPublic, getUser());
        Map<Integer, IProject> projects = new HashMap<>();
        try{
            sqlSelectorProjects.forEach(peptide -> {
                int projId =peptide.getInt("id");
                if(!projects.containsKey(projId)) {
                    IProject proj = new IProject();
                    proj.setRunId(peptide.getInt("id"));
                    proj.setPeptideGroupId(peptide.getInt("pgid"));
                    proj.setFileName(peptide.getString("filename"));
                    proj.setContainer(ContainerManager.getForId(peptide.getString("container")));
                    for(int i = 0; i < children.size(); i++) {
                        if(children.get(i).equals(proj.getContainer())) {
                            projects.put(projId, proj);
                            break;
                        }
                    }
                }
                IPeptide pep = new IPeptide();
                pep.setStartIndex(peptide.getInt("startindex"));
                pep.setEndIndex(peptide.getInt("endindex"));
                pep.setSequence(peptide.getString("sequence"));
                pep.setPeptideModifiedSequence(peptide.getString("peptidemodifiedsequence"));
                pep.setPanoramaPeptideId(peptide.getInt("pepid"));
                projects.get(projId).addPeptide(pep);

            });
        } catch(Exception e) {
            System.out.print("ERROR GETTING PROJECTS");
            System.out.print(e.getStackTrace());
        }
        p.setProjects(projects.values().toArray(new IProject[projects.size()]));
    }

    private void populateProteinKeywords(IProtein p) {
        String qs = "SELECT kw.keywordid, kw.keyword, kw.category, kc.label " +
                "FROM prot.sequences p, prot.annotations a, prot.identifiers pi, passport.keywords kw, passport.keywordcategories kc " +
                "WHERE p.seqid = ? AND a.seqid = p.seqid AND pi.identid = a.annotident AND kw.keywordid = pi.identifier AND kc.categoryid = kw.category";
        UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), "targetedms");
        SQLFragment keywordQuery = new SQLFragment();
        keywordQuery.append(qs);
        keywordQuery.add(p.getSequenceId());

        SqlSelector sqlSelector = new SqlSelector(schema.getDbSchema(), keywordQuery);
        List<IKeyword> keywords = new ArrayList<>();
        try {sqlSelector.forEach(prot -> {
            keywords.add(new IKeyword(prot.getString("keywordid"),
                    prot.getString("category"),
                    prot.getString("keyword"),
                    prot.getString("label")));
        });
        }catch(Exception e){
            System.out.print("Error populating keywords");
            e.printStackTrace();
            p.setKeywords(new IKeyword[0]);
            return;
        }
        p.setKeywords(keywords.toArray(new IKeyword[keywords.size()]));
    }

    private void populatePeptides(IProtein p) {
        if(p == null)
            return;

        Map<Integer, IPeptide> peptideMap = new HashMap<>();

        UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), "targetedms");
        TableInfo tinfo = schema.getTable("Passport_TotalPrecursorArea");

        SimpleFilter sf = new SimpleFilter();
        sf.addCondition(new BaseColumnInfo("PepGroupId"), p.getPepGroupId());

        try{
            new TableSelector(tinfo,sf, null).forEachResults(pep -> {
                int peptideId =
                        pep.getInt("peptideid");
                IPeptide peptide = peptideMap.get(peptideId);
                if(peptide == null) {
                    peptide = new IPeptide();
                    peptide.setSequence(pep.getString("peptidesequence"));
                    peptide.setStartIndex(pep.getInt("startindex"));
                    peptide.setEndIndex(pep.getInt("endindex"));
                    peptide.setPanoramaPeptideId(peptideId);
                    peptide.setProteinId(p.getSequenceId());
                }
                long totalArea = pep.getLong("totalarea");
                String replicateName = pep.getString("replicate");
                if(replicateName.equals("BeforeIncubation")) {
                    peptide.setBeforeTotalArea(totalArea);
                    peptide.setPrecursorbeforeid(pep.getInt("panoramaprecursorid"));
                    peptide.setBeforeSumArea(pep.getInt("sumarea"));
                } else if(replicateName.equals("AfterIncubation")) {
                    peptide.setAfterTotalArea(totalArea);
                    peptide.setPrecursorafterid(pep.getInt("panoramaprecursorid"));
                    peptide.setAfterSumArea(pep.getInt("sumarea"));
                }
                peptideMap.put(peptide.getPanoramaPeptideId(), peptide);
            });
        } catch(Exception e) {
            System.out.print("Error populating peptides");
            e.printStackTrace();
            p.setPep(new IPeptide[0]);
            return;
        }
        IPeptide[] peptides = peptideMap.values().toArray(new IPeptide[peptideMap.size()]);
        for(int i = 0; i < peptides.length; i++) {
            IPeptide peptide = peptides[i];
                // Normalize AfterIncubation TotalArea to global standards from Panorama
                double afterRatioToGlobalStandards  = peptide.getAfterTotalArea() / peptide.getAfterSumArea();
                double beforeRatioToGlobalStandards  = peptide.getBeforeTotalArea() / peptide.getBeforeSumArea();
                double afterToBeforeRatio = afterRatioToGlobalStandards / beforeRatioToGlobalStandards;
                double normalizedAfterTotalArea = peptide.getBeforeTotalArea() * afterToBeforeRatio;
                peptide.setBeforeIntensity(peptide.getBeforeTotalArea());
                peptide.setAfterIntensity(normalizedAfterTotalArea);
    }
        p.setPep(peptides);
    }
}
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.passport.model.IProtein" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.passport.model.IKeyword" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.passport.model.IFeature" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<?> me = (JspView<?>) HttpView.currentView();
    IProtein protein = (IProtein)me.getModelBean();
    final String contextPath = AppProps.getInstance().getContextPath();
%>
<!--START IMPORTS-->

<script src="//ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js"></script>
<link rel="stylesheet" href="//code.jquery.com/ui/1.11.4/themes/smoothness/jquery-ui.css">
<script src="//code.jquery.com/ui/1.11.4/jquery-ui.js"></script>
<script src="<%=h(contextPath)%>/passport/js/d3.v3.js"></script>
<script src="<%=h(contextPath)%>/passport/js/protein.js"></script>
<script src="<%=h(contextPath)%>/passport/js/peakareachart.js"></script>
<script type="text/javascript">
    LABKEY.requiresCss("/passport/css/protein.css");
    LABKEY.requiresCss("/passport/css/peakareachart.css");
</script>
<script>
    var protein = <%=protein.getJSON().toString(2)%>
    var chomatogramUrl = "<%=h(new ActionURL("targetedms", "precursorChromatogramChart", getContainer()))%>";
    var showPeptideUrl = "<%=h(new ActionURL("targetedms", "showPeptide", getContainer()))%>";
</script>
<!--END IMPORTS-->

<%if(protein != null) {%>
<!-- PROTEIN INFO HEADER START -->
<div id="passportContainer">
    <div id="basicproteininfo">
        <h2><%=h(protein.getName())%>
            <a href="<%=h(new ActionURL("targetedms", "downloadDocument", getContainer()))%>runId=<%=h(protein.getFile().getRunId())%>">
                <img src="<%=h(contextPath)%>/passport/img/download.jpg" style="width:40x; height:20px; margin-left:5px;" alt="Download Skyline dataset" title="Download Skyline dataset from PanoramaWeb.org">
            </a><sub title="month/day/year">Data Uploaded: <%=h(protein.getFile().getCreatedDate())%></sub>
        </h2>
        <p id="apiLinks">Sources:&nbsp;<a href="<%=h(new ActionURL("targetedms", "showProtein", getContainer()))%>id=<%=h(protein.getPepGroupId())%>">Panorama</a> &#8759; <a href="http://www.uniprot.org/uniprot/<%=h(protein.getAccession())%>">Uniprot</a></p>
        <ul style="max-width:300px;"><!-- Color Scheme: http://paletton.com/#uid=72X0X0kCyk3sipxvvmIKxgXRodf-->
            <li style="border-left: 6px solid #A01C00">
                <span>Protein:&nbsp;</span><%=h(protein.getPreferredname())%></li>
            <li style="border-left: 6px solid #0B1A6D">
                <span>Gene:&nbsp;</span><%=h(protein.getGene())%></li>
            <li style="border-left: 6px solid #00742B">
                <span>Organism:&nbsp;</span><%=h(protein.getSpecies())%></li>
            <%
            List<IKeyword> molecularFunctions = new ArrayList();
            List<IKeyword> biologicalProcesses = new ArrayList();
            IKeyword[] keywords = protein.getKeywords();
            for(int i = 0; i < keywords.length; i++) {
                if(keywords[i].categoryId.equals("KW-9999"))
                    biologicalProcesses.add(keywords[i]);
                if(keywords[i].categoryId.equals("KW-9992"))
                    molecularFunctions.add(keywords[i]);
            }
            %>

            <%if(biologicalProcesses.size() > 0) {%>
            <li style="border-left: 6px solid #A07200">
                <span title="Biological process">Biological process:&nbsp;</span><br/>
                <%for(int i = 0; i < biologicalProcesses.size(); i++){%>
                <a href="http://www.uniprot.org/keywords/<%=h(biologicalProcesses.get(i).id)%>" target="_blank"><%=h(biologicalProcesses.get(i).label)%></a>
                <%if(i!= biologicalProcesses.size()-1) {%>, <%}%>
                <%}%>
            </li>
            <%}%>
            <%if(molecularFunctions.size() > 0) {%>
            <li style="border-left: 6px solid #A07200">
                <span title="Molecular function">Molecular function:&nbsp;</span><br/>
                <%for(int i = 0; i < molecularFunctions.size(); i++){%>
                <a href="http://www.uniprot.org/keywords/<%=h(molecularFunctions.get(i).id)%>" target="_blank"><%=h(molecularFunctions.get(i).label)%></a>
                <%if(i != molecularFunctions.size()-1) {%>, <%}%>
                <%}%>
            </li>
            <%}%>

        </ul>
        <ul id="sequenceDisplay">
            <li style="border-left: 6px solid #550269">
                <span>Sequence:</span>
                <div id="sequenceDisplayTableContainer">
                    <table><tbody>
                        <%
                            String[] seqSegs = protein.getProtSeqHTML();
                            for(int i = 0; i < seqSegs.length; i++) {
                                if(i % 10 == 0) {%>
                                    <%if(i > 0) {%>
                                        </tr>
                                    <%}%>
                                    <tr style="text-align:right;">
                                        <%for(int j = i; j < i+10; j++) {
                                            if(j+2 > seqSegs.length) {%>
                                                <td><%=h(protein.getSequence().length())%></td>
                                            <%break;
                                            } else {%>
                                                <td> <%=h((j+1)*10)%></td>
                                        <%}%>

                                        <%}%>
                                    </tr>
                                    <%if(i < seqSegs.length) {%>
                                        <tr  style="text-align:left;">
                                    <%}%>
                                <%}%>
                                <td>
                                    <%=seqSegs[i]%>
                                </td>
                                <%if(i == seqSegs.length -1) {%>
                                    </tr>
                                <%}%>
                            <%
                            }
                        %>
                    </tbody>
                    </table>
                </div>
            </li>
        </ul>
    </div>
<!-- PROTEIN INFO HEADER END -->

<!-- FILTER OPTIONS START -->
    <div id="filterContainer"><img src="<%=h(contextPath)%>/passport/img/filtericon.png" id="filtericon"/>
        <h1>Filter Options</h1>
        <div class="filterBox">
            <h2>Peptides:&nbsp;
                <span id="filteredPeptideCount">
                    <green><%=h(protein.getPep().length)%></green>/<%=h(protein.getPep().length)%>
                </span>
                <span id="copytoclipboard" clipboard="" style="color:rgb(85, 26, 139); cursor:pointer;" title="Copy filtered peptide list to clipboard">  Copy</span>
            </h2>
            <p>
                <label for="peptideSort">Sort by:&nbsp;</label>
                <select id="peptideSort" name="peptideSort">
                    <option value="intensity">Intensity</option>
                    <option value="sequencelocation">Sequence Location</option>
                </select>
            </p>
            <p>
                <label for="filterdeg">Degradation:&nbsp;</label>
                <input id="filterdeg" type="text" name="filterdeg" readonly="readonly" style="border:0; color:#A01C00; background-color: transparent; font-weight:bold;"/>
            <div id="rangesliderdeg" class="slider-range"></div>
            </p>
            <p>
                <label for="filterpeplength">Sequence length:&nbsp;</label>
                <input id="filterpeplength" type="text" name="filterpeplength" readonly="readonly" style="border:0; color:#A01C00;  background-color: transparent; font-weight:bold;"/>
            <div id="rangesliderlength" class="slider-range"></div>
            </p>
        </div>
        <div id="pepListBox">
            <ul id="livepeptidelist"></ul>
        </div>
        <div class="filterBox">
            <h2>SNPs:</h2>
            <p>Coming soon</p>
        </div>
        <div class="filterBox">
            <h2>PTMs:</h2>
            <label for="showFeatures">Show features:&nbsp;</label>
            <input id="showFeatures" type="checkbox" name="showFeatures" readonly="readonly" style="border:0; color:#A01C00; font-weight:bold;"/>
        </div>
        <button id="formreset" type="button">Reset</button>
    </div>
<!-- FILTER OPTIONS END -->
<!-- CHART START -->
    <div id="chart"></div>
    <div id="peptide"></div>
    <div id="protein"></div>
<!-- CHART END -->
    <div id="selectedPeptideChromatogramContainer">
        <img id="selectedPeptideChromatogram" src="" alt="Chromatogram not available for the selected peptide"/>
        <span>Source:&nbsp;<a id="selectedPeptideLink" href="" title="View peptide on PanoramaWeb">PanoramaWeb</a></span>
    </div>
    <div id="peptideinfo"></div>
</div>
<%} else {
    // PROTEIN DOES NOT EXIST - SHOW ERROR MESSAGE
    ViewContext viewContext = getViewContext();
    String accession = viewContext.getRequest().getParameter("accession");
%>
    <p>Protein <strong><%=h(accession)%></strong> does not exist.</p>
<%}%>




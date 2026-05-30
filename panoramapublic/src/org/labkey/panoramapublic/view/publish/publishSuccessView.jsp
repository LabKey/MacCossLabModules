<%
/*
 * Copyright (c) 2023-2026 LabKey Corporation
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
%>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.panoramapublic.model.ExperimentAnnotations" %>
<%@ page import="org.labkey.panoramapublic.query.CatalogEntryManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("PanoramaPublic/css/slideshow.css");
        dependencies.add("PanoramaPublic/js/slideshow.js");
    }
%>

<%
    JspView<PanoramaPublicController.PublishSuccessViewBean> me = HttpView.currentView();
    var bean = me.getModelBean();
    ExperimentAnnotations copiedExperiment = bean.getCopiedExperiment();
    boolean canAddCatalogEntry = CatalogEntryManager.getCatalogEntrySettings().isEnabled() && CatalogEntryManager.getEntryForExperiment(copiedExperiment) == null;

    String successMsg = bean.madePublic()
            ? String.format("Data on %s at %s was made public%s", bean.getJournalName(), bean.getAccessUrl(),
                            (bean.addedPublication() ? " and publication details were added." : "."))
            : bean.addedPublication() ?  String.format("Publication details were updated for data on %s at %s.", bean.getJournalName(), bean.getAccessUrl()) : "";

    String pxdMessage = null;
    if (copiedExperiment.getPxid() != null)
    {
        pxdMessage = String.format("Accession %s will be %s on ProteomeXchange by a %s administrator.",
                copiedExperiment.getPxid(), bean.madePublic() ? "made public" : "updated", bean.getJournalName());
    }
%>

<div>
    <%=h(successMsg)%>
    <span style="margin-left:10px;">
        <%=link("View Data", PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(bean.getCopiedExperiment().getContainer())).target("_blank")%>
    </span>
    <% if (pxdMessage != null) { %>
        <div style="margin-top:10px;"><%=h(pxdMessage)%></div>
    <% } %>

    <% if (canAddCatalogEntry) { %>
    <div style="margin-top:15px;">
        You can publicize your data by providing a brief description and an image that will be displayed in a slideshow
        on <%=h(LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getShortName())%> (<%=h(AppProps.getInstance().getBaseServerUrl())%>).
        Click the button below to add an entry in
        the Panorama Public data catalog.
        <div style="margin:15px 0 15px 0">
            <%=button("Add Catalog Entry").href(PanoramaPublicController.getAddCatalogEntryUrl(copiedExperiment)).style("margin-left: 10px").primary(true)%>
        </div>
        <% if (CatalogEntryManager.hasEntries(CatalogEntryManager.CatalogEntryType.Approved)) { %>
        <div>
            <span style="text-decoration: underline; font-style: italic;">Slideshow preview:</span>
            <div id="slideshowPlaceholder"></div>
        </div>
        <% } %>
    </div>
    <% } %>

    <div style="margin-top:20px;">
        <%=link("Back to Folder", PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(bean.getSourceContainer()))%>
    </div>
</div>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">

    <% if (canAddCatalogEntry) { %>
        Ext4.onReady(function() {
            if (appendSlidesContainer("slideshowPlaceholder")) {
                initSlides(3, <%=q(CatalogEntryManager.CatalogEntryType.Approved.toString())%>);
            }

            window.onresize = function() {
                setDescSize(false);
            }
        });
    <% } %>

</script>
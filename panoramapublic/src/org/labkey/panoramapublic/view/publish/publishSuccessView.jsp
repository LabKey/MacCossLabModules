<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.panoramapublic.model.ExperimentAnnotations" %>
<%@ page import="org.labkey.panoramapublic.query.CatalogEntryManager" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
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
    JspView<PanoramaPublicController.PublishSuccessViewBean> me = (JspView<PanoramaPublicController.PublishSuccessViewBean>) HttpView.currentView();
    var bean = me.getModelBean();
    ExperimentAnnotations copiedExperiment = bean.getCopiedExperiment();
    boolean canAddCatalogEntry = CatalogEntryManager.getCatalogEntrySettings().isEnabled() && CatalogEntryManager.getEntryForExperiment(copiedExperiment) == null;

    String successMsg = bean.isMadePublic()
            ? String.format("Data on %s at %s was made public%s", bean.getJournalName(), bean.getAccessUrl(),
                            (bean.isAddedPublication() ? " and publication details were added." : "."))
            : bean.isAddedPublication() ?  String.format("Publication details were updated for data on %s at %s.", bean.getJournalName(), bean.getAccessUrl()) : "";

    String pxdMessage = null;
    if (copiedExperiment.getPxid() != null)
    {
        pxdMessage = String.format("Accession %s will be %s on ProteomeXchange by a %s administrator.",
                copiedExperiment.getPxid(), bean.isMadePublic() ? "made public" : "updated", bean.getJournalName());
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
            <%=button("Add Catalog Entry").href(PanoramaPublicController.getAddCatalogEntryUrl(copiedExperiment)).style("margin-left: 10px")%>
        </div>
        <% if (CatalogEntryManager.hasEntries(CatalogEntryManager.CatalogEntryType.Approved)) { %>
        <div>
            <span style="text-decoration: underline; font-style: italic;">Slideshow preview:</span>
            <div class="banner">
                <table style="width: 100%;"><tbody>
                <tr>
                    <td height="100%" style="width: 100%">
                        <table style="width: 100%;">
                            <tbody>
                            <tr>
                                <td height="100%">
                                    <div id="slides" style="width:100%;">
                                        <div class="slideshow-container">
                                            <a class="prev" onclick="plusSlides(-1)">&#10094;</a> <a class="next" onclick="plusSlides(1)">&#10095;</a>
                                        </div>
                                        <br />
                                        <div style="text-align:center" class="slideshow-dots"></div>
                                    </div>
                                </td>
                                <td height="100%" style="width:100%;vertical-align: middle;">
                                    <table id="description">
                                        <tbody>
                                        <tr>
                                            <td class="slideshow-texts"></td>
                                        </tr>
                                        <tr><td height="100">&nbsp;</td></tr>
                                        </tbody>
                                    </table>
                                </td>
                            </tr>
                            </tbody>
                        </table>
                    </td>
                    <!-- Add an empty cell with padding so that the description text stays inside the background -->
                    <td style="padding-right: 50px;text-align: center; vertical-align: top">&nbsp;</td>
                </tr>
                </tbody>
                </table>
            </div>
        </div>
        <% } %>
    </div>
    <% } %>

    <div style="margin-top:20px;">
        <%=link("Back to Folder", PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(bean.getSourceContainer()))%>
    </div>
</div>

<script type="text/javascript">

    <% if (canAddCatalogEntry) { %>
        Ext4.onReady(function() {
            slideIndex = 0;
            initSlides(3, <%=q(CatalogEntryManager.CatalogEntryType.Approved.toString())%>);
        });

        window.onresize = function() {
            setDescSize(false);
        }
    <% } %>

</script>
<%@ page import="org.springframework.validation.BindingResult" %>
<%@ page import="org.labkey.skylinetoolsstore.SkylineToolsStoreController" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
        dependencies.add("skylinetoolsstore/js/functions.js");
    }
%>

<%
    Object errorAttribute = request.getAttribute(BindingResult.MODEL_KEY_PREFIX + "form");
    Object hideForm = request.getAttribute(BindingResult.MODEL_KEY_PREFIX + "hideForm");

    Object formTitleObj = request.getAttribute(BindingResult.MODEL_KEY_PREFIX + "formTitle");
    final String formTitle = (formTitleObj != null) ? formTitleObj.toString() : "";
    Object formValueObj = request.getAttribute(BindingResult.MODEL_KEY_PREFIX + "formValue");
    final int formValue = (formValueObj != null) ? (int)formValueObj : 0;
    Object formReviewObj = request.getAttribute(BindingResult.MODEL_KEY_PREFIX + "formReview");
    final String formReview = (formReviewObj != null) ? formReviewObj.toString() : "";

    Object toolId = request.getAttribute(BindingResult.MODEL_KEY_PREFIX + "toolId");
    Object ratingId = request.getAttribute(BindingResult.MODEL_KEY_PREFIX + "ratingId");

    final String contextPath = AppProps.getInstance().getContextPath();
    final String imgDir = contextPath + "/skylinetoolsstore/img/";
%>
<style>
    #slider
    {
        border: 0 !important;
        width: 100px;
        height: 20px;
        background-color: #8e8d8d;
        background: url('<%= h(imgDir) %>star_empty20x20.png') repeat-x;
        z-index: 0;
        overflow: hidden;
        cursor:pointer;
    }
    #sliderover
    {
        width:<%= formValue * 20 %>px;
        height:20px;
        background: url('<%= h(imgDir) %>star_full20x20.png') repeat-x;
        z-index:99;
    }
    .ui-slider-handle { display:none; }
</style>
<%
    if (errorAttribute != null) {
%>
<p class="labkey-error"><%= h(errorAttribute.toString()) %></p>
<%
    }
    if (hideForm == null || (boolean)hideForm != true)
    {
%>
<form action="<%=h(urlFor(SkylineToolsStoreController.SubmitRatingAction.class))%>" enctype="multipart/form-data" method="post">
    Title: <input type="text" name="title" value="<%= h(formTitle) %>"><br>
    <input type="text" id="ratingvalue" name="value" style="display:none;" value="<%= formValue %>">
    <div id="slider">
        <div id="sliderover"></div>
    </div>
    <textarea name="review" rows="6" cols="60"><%= h(formReview) %></textarea><br /><br />
<% if (toolId != null) { %>
    <input type="hidden" name="toolId" value="<%=h(toolId)%>" />
<% } %>
<% if (ratingId != null) { %>
    <input type="hidden" name="ratingId" value="<%=h(ratingId)%>" />
<% } %>
    <input type="submit" value="Submit Review" />
</form>
<% } %>

<br />
<%= PageFlowUtil.generateBackButton()  %>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    $(function() {
        initRatingSlider($("#slider"), $("#sliderover"), $("#ratingvalue"));
    });
</script>

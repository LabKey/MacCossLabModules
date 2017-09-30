<%@ page import="org.springframework.validation.BindingResult" %>
<%@ page import="org.labkey.skylinetoolsstore.SkylineToolsStoreController" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
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
    final String jsDir = contextPath + "/skylinetoolsstore/js/";
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
<form action="<%= urlFor(SkylineToolsStoreController.SubmitRatingAction.class) %>" enctype="multipart/form-data" method="post">
    Title: <input type="text" name="title" value="<%= h(formTitle) %>"><br>
    <input type="text" id="ratingvalue" name="value" style="display:none;" value="<%= formValue %>">
    <div id="slider">
        <div id="sliderover"></div>
    </div>
    <textarea name="review" rows="6" cols="60"><%= h(formReview) %></textarea><br /><br />
<% if (toolId != null) { %>
    <input type="hidden" name="toolId" value="<%= toolId %>" />
<% } %>
<% if (ratingId != null) { %>
    <input type="hidden" name="ratingId" value="<%= ratingId %>" />
<% } %>
    <input type="submit" value="Submit Review" />
</form>
<% } %>

<br />
<%= PageFlowUtil.generateBackButton()  %>

<script type="text/javascript" src="<%= h(jsDir) %>functions.js"></script>
<script src="//ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js"></script>
<script src="//ajax.googleapis.com/ajax/libs/jqueryui/1.10.3/jquery-ui.min.js"></script>
<link rel="stylesheet" href="https://code.jquery.com/ui/1.10.3/themes/smoothness/jquery-ui.css">
<script type="text/javascript">
    $(function() {
        initRatingSlider($("#slider"), $("#sliderover"), $("#ratingvalue"));
    });
</script>

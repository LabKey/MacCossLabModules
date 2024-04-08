<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.testresults.TestResultsController" %>
<%@ page import="java.io.File" %>
<%@ page import="java.nio.file.Files" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
        dependencies.add("TestResults/css/style.css");
    }
%>

<%
    /**
     * User: Yuval Boss, yuval(at)uw.edu
     * Date: 10/05/2015
     */
    JspView<?> me = (JspView<?>) HttpView.currentView();
    File[] errorFiles = (File[])me.getModelBean();
    Container c = getContainer();
%>

<%@include file="menu.jsp" %>

<p>All the files listed below at one point or another failed to post.  When a run is successfully posted through this page it gets removed from the list.</p>
<% if (errorFiles.length == 0) { %>
    <p>There are 0 failed xml files store.</p>
<% } else { %>
    <button type="button" class="postBtn">Re-Post All</button>
    <div id="loading" style="display: none;">
        Loading...
    </div>
<p class="text"></p>
    <table class="decoratedtable">
        <tr><td>File Name</td><td>Date Originally Saved</td><td>Message</td></tr>
        <%
            for (File f : errorFiles) {
                if (f.getName().equals(".upload.log")) // LabKey system file
                    continue; %>
        <tr>
            <td class="<%=h(f.getName())%>"><%=h(f.getName())%></td>
            <td class="<%=h(f.getName())%>"><%=h(Files.getLastModifiedTime(f.toPath()))%></td>
            <td id="<%=h(f.getName())%>-message" class="<%=h(f.getName())%>"></td>
        </tr>
        <% } %>
    </table><br/>
<% } %>
<p><a href="filecontent-begin.view">Files</a></p>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    $(document).ajaxStart(function() {
        $("#loading").show();
    }).ajaxStop(function() {
        $("#loading").hide();
    });

    $('.postBtn').click(function() {
        $('.text').text('');
        $.ajax({
            type: "POST",
            dataType: 'json',
            data: {"X-LABKEY-CSRF": LABKEY.CSRF},
            url: "<%=h(new ActionURL(TestResultsController.PostErrorFilesAction.class, c))%>",
            success: function(data) {
                $.each(data, function(key, value) {
                    if (value == "Success!") {
                        changeColor('#caff95', key);
                        document.getElementById(key+"-message").textContent="Success!";
                    } else {
                        changeColor('#ffcaca', key);
                        document.getElementById(key+"-message").textContent=value;
                    }
                });
            },
            error: function(XMLHttpRequest, textStatus, errorThrown) {
                alert("Status: " + textStatus); alert("Error: " + errorThrown);
            }
        });
    });

    function changeColor(color, elClass) {
        var block = document.getElementsByClassName(elClass);
        for (var i = 0; i < block.length; i++) {
            block[i].style.background = color;
        }
    };
</script>
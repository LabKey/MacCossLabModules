<%@ page import="org.labkey.panoramapublic.query.JournalManager" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.files.FileContentService" %>
<%@ page import="org.labkey.api.targetedms.TargetedMSService" %>
<%@ page import="org.labkey.api.webdav.WebdavService" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<labkey:errors/>
<%
    JspView<JournalManager.PublicDataUser> me = (JspView<JournalManager.PublicDataUser>) HttpView.currentView();
    var publicDataUser = me.getModelBean();
    // NOTE: This is a link to the data download documentation page on PanoramaWeb.  It will not work on any other server.
    var downloadDataDocHref = "/home/wiki-page.view?name=download_public_data";
    // WebDAV URL to the RawFiles folder in the file root
    var webdavUrl = AppProps.getInstance().getBaseServerUrl() + AppProps.getInstance().getContextPath()
            + WebdavService.getPath()
            .append(getContainer().getParsedPath())
            .append(FileContentService.FILES_LINK, true)
            .append(TargetedMSService.RAW_FILES_DIR, true)
            .encode();
%>
<p>
    Select one or more files or folders in the browser above and click the download icon ( <span class="fa fa-download"></span> ).
    Data can also be downloaded by mapping this folder as a network drive in Windows Explorer, or by using a
    <%=link("WebDAV").href("https://en.wikipedia.org/wiki/WebDAV").clearClasses()%>
    client such as <span class="nobr"><%=link("CyberDuck").href("https://cyberduck.io").clearClasses()%></span>
    or <span class="nobr"><%=link("WinSCP").href("https://winscp.net/eng/docs/introduction").clearClasses()%></span>.
    For details look at <%=link("Download data from Panorama Public").href(downloadDataDocHref).clearClasses()%>.
    Use the following URL, login email and password to connect to this folder:
    <br/>
    <br/>
    URL: <b class="bold"><span class="nobr" id="webdav_url_link"><%=h(webdavUrl)%></span></b>
    <br/>
    Login email: <b class="bold"><%=h(publicDataUser.getEmail())%></b>
    <br/>
    Password: <b class="bold"><%=h(publicDataUser.getPassword())%></b>
</p>

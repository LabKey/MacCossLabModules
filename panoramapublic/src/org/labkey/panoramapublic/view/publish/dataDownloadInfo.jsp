<%
/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
<%@ page import="org.labkey.api.files.FileContentService" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.targetedms.TargetedMSService" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.webdav.WebdavService" %>
<%@ page import="org.labkey.panoramapublic.query.JournalManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<labkey:errors/>
<%
    JspView<JournalManager.PublicDataUser> me = HttpView.currentView();
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
    <br/>
    <br/>
    Data can also be downloaded by mapping this folder as a network drive in Windows Explorer, or by using a
    <%=simpleLink("WebDAV", "https://en.wikipedia.org/wiki/WebDAV")%>
    client such as <span class="nobr"><%=simpleLink("CyberDuck", "https://cyberduck.io")%></span>
    or <span class="nobr"><%=simpleLink("WinSCP", "https://winscp.net/eng/docs/introduction")%></span>.
    WebDAV downloads require an account on the PanoramaWeb server. Information on obtaining an account and other download options
    is available on the <%=simpleLink("Download data from Panorama Public", downloadDataDocHref)%> help page.
    Use the following URL to connect to this folder for WebDAV downloads:
    <br/>
    URL: <b class="bold"><span class="nobr" id="webdav_url_link"><%=h(webdavUrl)%></span></b>
    <br/>
    <br/>
    <b class="bold">Note: </b> If you plan to download large volumes of data or datasets from multiple projects via WebDAV,
    please contact the PanoramaWeb team in advance so that we can coordinate the download to minimize load on the server
    and ensure reliable access.
</p>

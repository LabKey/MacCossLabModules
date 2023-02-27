<%
    /*
     * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("PanoramaPublic/css/slideshow.css");
        dependencies.add("PanoramaPublic/js/slideshow.js");
    }
%>

<!-- The HTML, CSS and JavaScript to display the slideshow have been copied from the Wiki page on PanoramaWeb. -->

<labkey:errors/>

<script type="text/javascript">

    Ext4.onReady(function() {

        slideIndex = 0;
        initSlides();
    });

    window.onresize = function() {
        setDescSize(false);
    }

</script>

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
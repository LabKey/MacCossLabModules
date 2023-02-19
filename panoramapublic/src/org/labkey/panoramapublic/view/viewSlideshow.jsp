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
    }
%>

<!-- The HTML, CSS and JavaScript to display the slideshow have been copied from the Wiki page on PanoramaWeb. -->

<labkey:errors/>

<script type="text/javascript">

    let slideIndex;
    let slides, dots, text;
    let wait;

    Ext4.onReady(function() {

        slideIndex = 0;
        initSlides();
    });

    window.onresize = function() {
        setDescSize(false);
    }

    function setDescSize(fixed)
    {
        let desc = document.getElementById("description");
        if (fixed) {
            desc.style.width = desc.offsetWidth + "px";
        } else {
            desc.style.width = "90%";
        }
    }

    function showSlidesTimer()
    {
        if (!wait) {
            showSlides();
        }
    }

    function initSlides()
    {
        // Get a list of the approved catalog entries.
        Ext4.Ajax.request({
            url: LABKEY.ActionURL.buildURL('panoramapublic', 'getCatalogApi.api'/*, null, {maxEntries : 3}*/),
            method: 'GET',
            success: LABKEY.Utils.getCallbackWrapper(addSlides),
            failure: function () {
                onFailure("Request was unsuccessful. The server may be unavailable. Please try reloading the page.")
            }
        });
    }

    function addSlides(json)
    {
        if (json) {

            if (json["error"])
            {
                onFailure("There was an error: " + json["error"]);
                return;
            }
            const catalog = json["catalog"];

            let slideshowContainer = document.getElementsByClassName('slideshow-container')[0];
            let slideshowDots = document.getElementsByClassName('slideshow-dots')[0];
            let slideshowTexts = document.getElementsByClassName('slideshow-texts')[0];

            // console.log("Catalog length: " + catalog.length);
            for(let i = 0; i < catalog.length; i++)
            {
                let entry = catalog[i];
                // console.log("Entry: " + entry.accessUrl + ", " + entry.title + ", " + entry.imageUrl);
                appendCoverSlide(entry, slideshowContainer);
                appendDot(slideshowDots, i + 1);
                appendText(entry, slideshowTexts);
            }
            showSlides();
        }
        else
        {
            onFailure("Server did not return a valid response.");
        }
    }

    function appendCoverSlide(entry, coverslideContainer)
    {
        // Example:
        // <div class="coverslide" style="display: block;">
        // <a href="https://panoramaweb.org/SkylineForSmallMolecules.url">
        // <img src="/labkey/_webdav/home/%40files/Slides/Thompson_600x400.png" class="slideimg" alt="" border="0" width="600" height="400" />
        // </a>
        // </div>
        const coverslide = document.createElement('div');
        coverslide.setAttribute('class', 'coverslide');
        coverslide.setAttribute('style', 'display: block;')
        const html = '<a href="' + entry.accessUrl + '">' // accessUrl is already encoded in GetCatalogApiAction
                + '<img src="' + entry.imageUrl // imageUrl is already encoded in GetCatalogApiAction
                + '" class="slideimg" style="border:0;" width="600" height="400" alt="Image"/>'
                + '</a>';
        coverslide.innerHTML += html;
        coverslideContainer.appendChild(coverslide);
    }

    function appendDot(dotsContainer, index)
    {
        // Examples:
        // <span class="dot active" onclick="currentSlide(1)"></span>
        // <span class="dot" onclick="currentSlide(2)"></span>
        const dot = document.createElement('span');
        const cls = index === 1 ? "dot active" : "dot";
        dot.setAttribute('class', cls);
        dot.setAttribute('onclick', 'currentSlide(' + index + ')');
        dotsContainer.appendChild(dot);
    }

    function appendText(entry, textsContainer)
    {
        /*
        Example:
        <div class="text">
         <em>Panorama Public dataset</em></br>
         <em><strong>Skyline for Small Molecules: A Unifying Software Package for Quantitative Metabolomics</strong></em></br>
                     Describes the expansion of Skyline to data for small molecule analysis, including selected reaction monitoring (SRM), high-resolution mass spectrometry (HRMS), and calibrated quantification. Includes step-by-step instructions on using Skyline for small molecule quantitative method development and analysis of data acquired with a variety of mass spectrometers from multiple instrument vendors. All the Skyline documents containing the demonstrated workflows are available on Panorama Public.
          </div>
         */
        const txt = document.createElement('div');
        txt.setAttribute('class', 'text');
        const html = '<em>Panorama Public dataset</em></br>'
                   + '<em><strong>' + Ext4.htmlEncode(entry.title) + '</strong></em></br>'
                   + Ext4.htmlEncode(entry.description);
        txt.innerHTML += html;
        textsContainer.appendChild(txt);
    }

    function onFailure(message)
    {
        setTimeout(function() { console.log(message);}, 500);
    }

    function showSlides()
    {
        if (slideIndex > 0)
        {
            setDescSize(true);
        }
        slides = document.getElementsByClassName("coverslide");
        dots = document.getElementsByClassName("dot");
        text = document.getElementsByClassName("text");
        let i;
        for (i = 0; i < slides.length; i++)
        {
            slides[i].style.display = "none";
        }
        for (i = 0; i < text.length; i++)
        {
            text[i].style.display = "none";
        }
        if (slides.length > 0)
        {
            slideIndex++;
            if (slideIndex > slides.length) {
                slideIndex = 1;
            }
            for (i = 0; i < dots.length; i++) {
                dots[i].className = dots[i].className.replace(" active", "");
            }
            slides[slideIndex - 1].style.display = "block";
            text[slideIndex - 1].style.display = "block";
            dots[slideIndex - 1].className += " active";
            if (!wait) {
                setTimeout(showSlidesTimer, 10000); // Change image every 10 seconds
            }
        }
    }

    function plusSlides(position)
    {
        let i;
        setDescSize(true);
        slideIndex += position;
        if (slideIndex > slides.length) { slideIndex = 1; }
        else if (slideIndex < 1){ slideIndex = slides.length; }
        for (i = 0; i < slides.length; i++)
        {
            slides[i].style.display = "none";
        }
        for (i = 0; i < text.length; i++)
        {
            text[i].style.display = "none";
        }
        for (i = 0; i < dots.length; i++)
        {
            dots[i].className = dots[i].className.replace(" active", "");
        }
        slides[slideIndex-1].style.display = "block";
        text[slideIndex-1].style.display = "block";
        dots[slideIndex-1].className += " active";
        wait = true;
    }

    function currentSlide(index)
    {
        let i;
        if (index > slides.length) { index = 1; }
        else if (index < 1) { index = slides.length; }
        for (i = 0; i < slides.length; i++)
        {
            slides[i].style.display = "none";
        }
        for (i = 0; i < text.length; i++)
        {
            text[i].style.display = "none";
        }
        for (i = 0; i < dots.length; i++)
        {
            dots[i].className = dots[i].className.replace(" active", "");
        }
        text[index-1].style.display = "block";
        slides[index-1].style.display = "block";
        dots[index-1].className += " active";
        slideIndex = index;
        wait = true;
    }
</script>

<style>
    .slideimg {
        margin-right: 60px;
        margin-left: 60px;
    }
    .coverslide {
        display: none
    }
    .slideshow-container {
        position: relative;
    }
    .prev, .next {
        cursor: pointer;
        position: absolute;
        top: 50%;
        width: auto;
        padding: 32px;
        margin-top: -22px;
        color: black;
        font-weight: bold;
        font-size: 18px;
        transition: 0.6s ease;
        border-radius: 0 3px 3px 0;
    }
    .next {
        right: 0;
        border-radius: 3px 0 0 3px;
    }
    .prev:hover, .next:hover {
        background-color: rgba(128,128,128,128.8);
    }
    .text {
        color: black;
        font-size: 1.0vw;
        width: 100%;
        height: 100%;
        text-align: left;
        display:none;
        margin-left: 20px;
    }
    .dot {
        cursor: pointer;
        height: 13px;
        width: 13px;
        margin: 0 2px;
        background-color: #bbb;
        border-radius: 50%;
        display: inline-block;
        transition: background-color 0.6s ease;
    }
    .active, .dot:hover {
        background-color: #717171;
    }

    /* On smaller screens, decrease text size */
    @media only screen and (max-width: 300px) {
        .text {font-size: 11px}
    }

    div.banner {
        margin-top: 30px;
        background: #ffffff; /* Old browsers */
        /* IE9 SVG, needs conditional override of 'filter' to 'none' */
        background: url(data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiA/Pgo8c3ZnIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyIgd2lkdGg9IjEwMCUiIGhlaWdodD0iMTAwJSIgdmlld0JveD0iMCAwIDEgMSIgcHJlc2VydmVBc3BlY3RSYXRpbz0ibm9uZSI+CiAgPGxpbmVhckdyYWRpZW50IGlkPSJncmFkLXVjZ2ctZ2VuZXJhdGVkIiBncmFkaWVudFVuaXRzPSJ1c2VyU3BhY2VPblVzZSIgeDE9IjAlIiB5MT0iMCUiIHgyPSIwJSIgeTI9IjEwMCUiPgogICAgPHN0b3Agb2Zmc2V0PSIwJSIgc3RvcC1jb2xvcj0iI2ZmZmZmZiIgc3RvcC1vcGFjaXR5PSIxIi8+CiAgICA8c3RvcCBvZmZzZXQ9IjcwJSIgc3RvcC1jb2xvcj0iI2UwZTZlYiIgc3RvcC1vcGFjaXR5PSIxIi8+CiAgICA8c3RvcCBvZmZzZXQ9IjcwJSIgc3RvcC1jb2xvcj0iI2ZmZmZmZiIgc3RvcC1vcGFjaXR5PSIxIi8+CiAgICA8c3RvcCBvZmZzZXQ9IjEwMCUiIHN0b3AtY29sb3I9IiNmZmZmZmYiIHN0b3Atb3BhY2l0eT0iMSIvPgogIDwvbGluZWFyR3JhZGllbnQ+CiAgPHJlY3QgeD0iMCIgeT0iMCIgd2lkdGg9IjEiIGhlaWdodD0iMSIgZmlsbD0idXJsKCNncmFkLXVjZ2ctZ2VuZXJhdGVkKSIgLz4KPC9zdmc+);
        background: -moz-linear-gradient(top,  #ffffff 0%, #e0e6eb 70%, #ffffff 70%, #ffffff 100%); /* FF3.6+ */
        background: -webkit-gradient(linear, left top, left bottom, color-stop(0%,#ffffff), color-stop(70%,#e0e6eb), color-stop(70%,#ffffff), color-stop(100%,#ffffff)); /* Chrome,Safari4+ */
        background: -webkit-linear-gradient(top,  #ffffff 0%,#e0e6eb 70%,#ffffff 70%,#ffffff 100%); /* Chrome10+,Safari5.1+ */
        background: -o-linear-gradient(top,  #ffffff 0%,#e0e6eb 70%,#ffffff 70%,#ffffff 100%); /* Opera 11.10+ */
        background: -ms-linear-gradient(top,  #ffffff 0%,#e0e6eb 70%,#ffffff 70%,#ffffff 100%); /* IE10+ */
        background: linear-gradient(to bottom,  #ffffff 0%,#e0e6eb 70%,#ffffff 70%,#ffffff 100%); /* W3C */
        filter: progid:DXImageTransform.Microsoft.gradient( startColorstr='#ffffff', endColorstr='#e0e6eb',GradientType=0 ); /* IE6-8 */
    }
</style>


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
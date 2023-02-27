/*
  Methods to display the Panorama Public catalog slideshow.
 */

let slideIndex;
let slides, dots, text;
let wait;

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

function initSlides(maxEntries, entryType)
{
    const queryParams = {};
    if (Number.isSafeInteger(maxEntries) && maxEntries > 0)
    {
        queryParams["maxEntries"] = maxEntries;
    }
    if (entryType)
    {
        queryParams["entryType"] = entryType;
    }

    // Get a list of catalog entries.
    Ext4.Ajax.request({
        url: LABKEY.ActionURL.buildURL('panoramapublic', 'getCatalogApi.api', null, queryParams),
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
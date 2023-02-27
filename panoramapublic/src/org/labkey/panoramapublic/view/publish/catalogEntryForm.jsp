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
<%@ page import="org.apache.commons.io.FileUtils" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.panoramapublic.catalog.CatalogEntrySettings" %>
<%@ page import="org.labkey.panoramapublic.query.CatalogEntryManager" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
        dependencies.add("PanoramaPublic/cropperjs/cropper.min.js");
        dependencies.add("PanoramaPublic/cropperjs/cropper.min.css");
        dependencies.add("PanoramaPublic/cropperjs/jquery-cropper.min.js");
    }
%>
<%
    JspView<PanoramaPublicController.CatalogEntryBean> view = (JspView<PanoramaPublicController.CatalogEntryBean>) HttpView.currentView();
    var bean = view.getModelBean();
    var form = bean.getForm();

    var attachedFile = bean.getImageFileName();
    var attachmentUrl = bean.getImageUrlEncoded();

    CatalogEntrySettings settings = CatalogEntryManager.getCatalogEntrySettings();
    int descriptionCharLimit = settings.getMaxTextChars();
    long maxFileSize = settings.getMaxFileSize();
    String maxFileSizeMb = FileUtils.byteCountToDisplaySize(maxFileSize);
    int imgWidth = settings.getImgWidth();
    int imgHeight = settings.getImgHeight();

    ActionURL cancelUrl = PanoramaPublicController.getViewExperimentDetailsURL(form.getId(), form.getContainer());

%>
<labkey:errors/>

<style>
    /* Ensure the size of the image fit the container perfectly */
    .cropperBox img {
        display: block;
        /* This rule is very important, please don't ignore this */
        max-width: 100%;
    }
    #canvas {
        background-color: #ffffff;
        cursor: default;
        border: 1px solid black;
    }
    #mask{
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background-color: #000;
        display: none;
        z-index: 10000;
    }
    #cropperContainer
    {
        position: fixed;
        left: 50%;
        top: 50%;
        transform: translate(-50%, -50%);
        padding: 15px;
        background-color: #ffffff;
        cursor: pointer;
        z-index: 10001;
        display: none;
        box-shadow: 0 0 5px darkgray;
        border-radius:5px;
    }
    .noRemainingChars
    {
        color:red !important;
    }
    .greyText
    {
        color:grey;
    }
</style>

<div style="margin-top:10px;">
    <div style="margin-bottom:15px;">
        Use the form below to provide a brief description and an image that will be displayed in a slideshow on
        <%=h(LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getShortName())%> (<%=h(AppProps.getInstance().getBaseServerUrl())%>).
    </div>
    <form id="catalogEntryForm" method="post" enctype="multipart/form-data">
        <labkey:csrf />
        <%=generateReturnUrlFormField(form.getReturnActionURL())%>
        <table class="lk-fields-table">
            <tr>
                <td class="labkey-form-label" style="text-align:center;">Description:</td>
                <td>
                    <textarea id="descFieldInput" rows="8" cols="60" name="datasetDescription" ><%=h(form.getDatasetDescription())%></textarea>
                    <br/>
                    <div id="remainingChars" style="margin-bottom:15px;" class="greyText">
                        <span id="rchars"><%=descriptionCharLimit%></span> characters remaining
                    </div>
                </td>
            </tr>
            <tr>
                <td class="labkey-form-label" style="text-align:center;">Image:</td>
                <td>
                    <input id="imageFileInput" type="file" size="50" style="border: none; background-color: transparent;" accept="image/png,image/jpeg" />
                    <input id="modifiedImage" name="imageFile" type="hidden"/>
                    <input id="imageFileName" name="imageFileName" type="hidden"/>
                    <div style="margin-top:5px;" class="greyText">
                        PNG or JPG/JPEG file less than 5MB. Preferred dimensions 600(width) x 400(height) pixels.
                    </div>
                    <div id="preview" style="margin-top:10px; padding:10px;"></div>
                </td>
            </tr>
        </table>
        <br>
        <%=button("Submit").submit(true).disableOnClick(true)%>
        &nbsp;
        <%=button("Cancel").href(cancelUrl)%>
    </form>
</div>
<!-- Example modal div: http://jsfiddle.net/r77K8/1/ -->
<div id='mask'></div>
<div id="cropperContainer">
    <div id="cropperDiv">
        <canvas id="canvas">
            Your browser does not support the HTML5 canvas element.
        </canvas>
    </div>
    <div id="cropperButtons" style="margin-top:5px; text-align:center">
        Drag and resize the crop-box over the image, and click the "Crop" button to fit image to the slideshow dimensions.
        For best quality the image should be resized in an image processing software before uploading.
        <div style="margin-top:5px;">
            <a class="labkey-button" id="btnCrop">Crop</a>
            <a class="labkey-button" style="margin-left:5px;" id="btnCancel">Cancel</a>
        </div>
    </div>
</div>


<script type="text/javascript">

    let cropper;
    let croppedImageDataUrl;
    let fileName;
    let canvas;
    let context;
    let imageFileInput;

    const maxFileSize = <%=maxFileSize%>;
    const maxFileSizeMb = <%=qh(maxFileSizeMb)%>;
    const preferredWidth = <%=imgWidth%>;
    const preferredHeight = <%=imgHeight%>;

    (function($) {

        $(document).ready(function() {

            canvas  = $("#canvas");
            context = canvas.get(0).getContext("2d");
            imageFileInput = $("#imageFileInput");

            <% if (attachedFile != null) { %>
                // If we are editing an entry, show the attached image file in the preview box.
               initPreview(<%=q(attachmentUrl)%>, <%=q(attachedFile)%>);
            <% } %>

            imageFileInput.on('change', function(){

                if (this.files && this.files[0])
                {
                    const file = this.files[0];
                    fileName = file.name;
                    if (!(file.type.match(/^image\/png/) || file.type.match(/^image\/jpeg/)))
                    {
                        // https://developer.mozilla.org/en-US/docs/Web/Media/Formats/Image_types
                        alert("Invalid file type. Please select an image file (png, jpeg).");
                        imageFileInput.val("");
                        return;
                    }
                    if (file.size > maxFileSize)
                    {
                        alert("File size cannot be more than " + maxFileSizeMb + ".");
                        imageFileInput.val("");
                        return;
                    }
                    displayCropper(this.files[0]);
                }
            });

            $("#btnCrop").click(function() {
                croppedImageDataUrl = cropper.getCroppedCanvas({fillColor: "#fff", imageSmoothingEnabled: true, imageSmoothingQuality: 'high'})
                        // https://developer.mozilla.org/en-US/docs/Web/API/HTMLCanvasElement/toDataURL
                        .toDataURL("image/png");

                initPreview(croppedImageDataUrl, fileName, true);
                $("#imageFileName").val(fileName);
                $("#modifiedImage").val(croppedImageDataUrl);
                clearCropper(cropper);
            });
            $("#btnCancel").click(function() {
                clearCropper(cropper);
                const selectedFile = imageFileInput.prop('files') ? imageFileInput.prop('files')[0] : null;
                if (selectedFile && selectedFile.name !== $("#imageFileName").val())
                {
                    imageFileInput.val('');
                }
            });

            const descInput = $("#descFieldInput")
            if (descInput.val().length > 0)
            {
                limitDescription(descInput);
            }
            descInput.keyup(function()
            {
                limitDescription($(this));
            });
            descInput.keypress(function(e) {
                if ($(this).val().length === maxDescriptionLen)
                {
                    e.preventDefault();
                }
            });
        });

        const maxDescriptionLen = <%=descriptionCharLimit%>;
        function limitDescription(inputField)
        {
            inputField.val(inputField.val().substring(0, maxDescriptionLen));
            const remaining = maxDescriptionLen - inputField.val().length;
            $("#rchars").text(remaining);
            const remainingChars = $("#remainingChars");
            const hasNoRemainingCls = remainingChars.hasClass("noRemainingChars");
            if (remaining <= 0) {
                if (!hasNoRemainingCls) remainingChars.addClass("noRemainingChars");
            }
            else {
                if (hasNoRemainingCls) remainingChars.removeClass("noRemainingChars");
            }
        }

        function displayCropper(file)
        {
            const reader = new FileReader();
            reader.onload = function(evt) {

                const img = new Image();
                img.onload = function() {

                    let w = img.width;
                    let h = img.height;
                    if (w < preferredWidth || h < preferredHeight)
                    {
                        alert("Image must be at least " + preferredWidth + " pixels in width and "
                                + preferredHeight + " pixels in height. Dimensions of the selected image are: "
                                + w + " x " + h + " pixels.");
                        imageFileInput.val("");
                        return;
                    }

                    clearCropper(cropper);

                    // Try to fit the image in the viewport without needing to scroll
                    const cropperContainer = $("#cropperContainer");
                    cropperContainer.css("width", "95vw") // 95% of viewport width
                                    .css("height", "95vh"); // 95% of viewport height
                    $("#mask").fadeTo(500, 0.60);
                    cropperContainer.show();

                    const maxDisplayWidth = cropperContainer.width();
                    const maxDisplayHeight = cropperContainer.height() - $("#cropperButtons").outerHeight(true);

                    let scaleFactor = Math.min(maxDisplayWidth/w, maxDisplayHeight/h);
                    scaleFactor = Math.min(1, scaleFactor);

                    // Get the new width and height based on the scale factor
                    let newWidth = w * scaleFactor;
                    let newHeight = h * scaleFactor;
                    cropperContainer.width(newWidth);
                    cropperContainer.height(newHeight + $("#cropperButtons").outerHeight(true));

                    context.canvas.width  = newWidth;
                    context.canvas.height = newHeight;

                    context.drawImage(img, 0, 0, newWidth, newHeight);

                    cropper = new Cropper(document.getElementById("canvas"), {
                        viewMode: 2,
                        dragMode: 'move',
                        background: false,
                        aspectRatio: preferredWidth / preferredHeight,
                        movable: false,
                        rotatable: false,
                        scalable: false,
                        zoomable: false,
                        zoomOnTouch: false,
                        zoomOnWheel: false,
                        cropBoxMovable: true,
                        cropBoxResizable: true,
                        toggleDragModeOnDblclick: false,
                        autoCrop: true,
                        autoCropArea: 1, // If the image is already the preferred size (600 x 400) the crop-box should fit exactly
                        minCanvasWidth: preferredWidth,
                        minCanvasHeight: preferredHeight,
                        minCropBoxWidth: preferredWidth,
                        minCropBoxHeight: preferredHeight,
                        restore: false // set to false to avoid problems when resizing the browser window
                                       // (https://github.com/fengyuanchen/cropper/issues/488)
                    });
                };
                img.src = evt.target.result;
            };
            reader.readAsDataURL(file);
        }
        function clearCropper(cropper)
        {
            if (cropper) cropper.destroy();
            $("#cropperContainer").hide();
            $("#mask").hide();
            if (canvas && context) { context.clearRect(0, 0, canvas.width, canvas.height); }
        }
        function initPreview(url, filename, addEditBtn)
        {
            const preview = $("#preview");
            if (preview) {
                preview.empty();
                preview.append($("<img>").attr("src", url).css("border", "solid 1px lightgrey")
                        .attr("width", preferredWidth).attr("height", preferredHeight).attr("alt", ""));
                preview.append($("<div>").text(filename)); // jQuery escapes the provided string (http://api.jquery.com/text/#text2)

                if (addEditBtn === true)
                {
                    preview.append($("<a>").addClass("labkey-button").attr("id", "btnEdit")
                            .text("Edit").click(function() {
                                const files = imageFileInput.prop("files");
                                if (files && files[0])
                                {
                                    displayCropper(files[0]);
                                }
                                else
                                {
                                    alert("No file is selected.");
                                }
                            }));
                }
            }
        }
    })(jQuery);

</script>
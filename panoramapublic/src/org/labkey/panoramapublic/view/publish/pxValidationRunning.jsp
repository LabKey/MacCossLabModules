<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.pipeline.PipelineStatusUrls" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.pipeline.PipelineJob" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<labkey:errors/>
<%
    var view = (JspView<PanoramaPublicController.PxValidationStatusBean>) HttpView.currentView();
    var bean = view.getModelBean();
    int jobId = bean.getDataValidation().getJobId();
    var jobStatus = bean.getPipelineJobStatus();
    var onPageLoadMsg = jobStatus != null ? (String.format("Data validation job is %s. This page will automatically refresh with the validation progress.",
            jobStatus.isActive() ? (PipelineJob.TaskStatus.waiting.matches(jobStatus.getStatus()) ? "in the queue" : "running") : "complete"))
            : "Could not find job status for job with Id " + jobId;
    var jobLogHref = PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlDetails(getContainer(), jobId);
    var validationResultsUrl = new ActionURL(PanoramaPublicController.PxValidationStatusAction.class, getContainer())
            .addParameter("id", bean.getExpAnnotations().getId()).addParameter("validationId", bean.getDataValidation().getId());
    var forSubmit = getActionURL().getParameter("forSubmit");
    if (forSubmit != null)
    {
        validationResultsUrl.addParameter("forSubmit", Boolean.valueOf(forSubmit));
    }
%>

<div>
    <div class="alert alert-info" id="onPageLoadMsgDiv"><%=h(onPageLoadMsg)%></div>
    <div id="jobStatusDiv">
        <span style="margin-right:5px; font-weight:bold; text-decoration: underline;">Job Status: </span> <span id="jobStatusSpan"></span>
        <span style="margin-left:10px;"><%=link("[View Log]", jobLogHref)%></span>
    </div>
</div>

<div style="margin-top:10px;" id="validationProgressDiv"></div>

<script type="text/javascript">

    var htmlEncode = Ext4.util.Format.htmlEncode;

    const jobStatusSpan = document.getElementById("jobStatusSpan");
    const validationProgressDiv = document.getElementById("validationProgressDiv");
    let lastJobStatus = "";
    const FIVE_SEC = 5000;

    Ext4.onReady(makeRequest);

    function makeRequest() {
        Ext4.Ajax.request({
            url: LABKEY.ActionURL.buildURL('panoramapublic', 'pxValidationStatusApi.api', null, LABKEY.ActionURL.getParameters()),
            method: 'GET',
            success: LABKEY.Utils.getCallbackWrapper(displayStatus),
            failure: function () {
                onFailure("Request was unsuccessful. The server may be unavailable. Please try reloading the page.")
            }
        });
    }

    function onFailure(message)
    {
        setTimeout(function() { alert(message); }, 500);
    }

    function displayStatus(json) {

        if (json) {

            if (json["error"])
            {
                onFailure("There was an error: " + json["error"]);
                return;
            }
            const jobStatus = json["jobStatus"];
            const validationProgress = json["validationProgress"];
            const validationStatus = json["validationStatus"];
            if (!(validationProgress || validationStatus))
            {
                onFailure("Unexpected JSON response returned by the server.");
                return;
            }

            if (validationProgress) {
                validationProgressDiv.innerHTML = getValidationProgressHtml(validationProgress);
            }

            if (jobStatus) {
                const jobStatusLc = jobStatus.toLowerCase();
                if (lastJobStatus !== jobStatus) {
                    lastJobStatus = jobStatus;
                    jobStatusSpan.innerHTML = jobStatus;
                }
                if (!(jobStatusLc === "complete" || jobStatusLc === "error" || jobStatusLc === "cancelled" || jobStatusLc === "cancelling")) {
                    // If task is not yet complete then schedule another request.
                    setTimeout(makeRequest, FIVE_SEC);
                }
                else {
                    if (jobStatusLc === "complete") {
                        // The job is complete, open the validation results page
                        window.location.href = <%=q(validationResultsUrl)%>;
                        return;
                    }
                    var onPageLoadMsgDiv = document.getElementById("onPageLoadMsgDiv");
                    if (onPageLoadMsgDiv) {
                        onPageLoadMsgDiv.innerHTML = "";
                        onPageLoadMsgDiv.classList.remove('alert');
                        onPageLoadMsgDiv.classList.remove('alert-info');

                        if (jobStatusLc === "error") {
                            onPageLoadMsgDiv.innerHTML = "There were errors while running the validation job. Please " +
                                    '<%=link("view the validation log", jobLogHref)
                                .clearClasses().addClass("alert-link")%>' + " for details.";
                            onPageLoadMsgDiv.classList.add('alert', 'alert-warning', 'labkey-error');
                        }
                    }
                }
            }
            else {
                jobStatusSpan.innerHTML = "UNKNOWN"; // The job may have been deleted. That is why we did not get the job status in the response.
            }
        }
        else {
            onFailure("Server did not return a valid response.");
        }
    }

    function getValidationProgressHtml(validationProgress) {
        var html = "";
        if (validationProgress) {
            for (var i = 0; i < validationProgress.length; i++) {
                html += htmlEncode(validationProgress[i]) + "</br>";
            }
        }
        return html;
    }

</script>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    JspView<PanoramaPublicController.SearchPublicationsBean> view = HttpView.currentView();
    var form = view.getModelBean();
    ActionURL searchPublicationsUrl = new ActionURL(PanoramaPublicController.SearchPublicationsForDatasetAction.class, getContainer());
    ActionURL searchPublicationsApiUrl = new ActionURL(PanoramaPublicController.SearchPublicationsForDatasetApiAction.class, getContainer());
%>

<style>
    .search-pub-progress {
        margin: 10px 0;
        padding: 8px 12px;
        background: #f0f8ff;
        border: 1px solid #b0d4f1;
        border-radius: 4px;
        display: none;
    }
    .search-pub-progress.complete {
        background: #f0fff0;
        border-color: #b0d4b0;
    }
    .search-pub-result-error {
        color: #cc0000;
    }
</style>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">

    const _dataRegionName = <%= q(form.getDataRegionName()) %>;
    const _searchPublicationsApiUrl = <%= q(searchPublicationsApiUrl.getLocalURIString()) %>;
    const _searchPublicationsUrl = <%= q(searchPublicationsUrl.getLocalURIString()) %>;

    var _resultColumnsAdded = false;

    function addResultColumns()
    {
        if (_resultColumnsAdded) return;

        var dr = document.querySelector('table[data-region-name="' + _dataRegionName + '"]');
        if (!dr) return;

        // Add headers
        var headerRow = dr.querySelector('tr.labkey-col-header, thead tr');
        if (headerRow)
        {
            var headers = ['Papers Found', 'Publication IDs', 'Matches', ''];
            for (var i = 0; i < headers.length; i++)
            {
                var th = document.createElement('th');
                th.className = 'labkey-col-header';
                th.textContent = headers[i];
                headerRow.appendChild(th);
            }
        }

        // Add empty cells to each data row
        var rows = dr.querySelectorAll('tr.labkey-alternate-row, tr.labkey-row');
        for (var r = 0; r < rows.length; r++)
        {
            for (var c = 0; c < 4; c++)
            {
                var td = document.createElement('td');
                rows[r].appendChild(td);
            }
        }

        _resultColumnsAdded = true;
    }

    function getRowForExperimentId(expId)
    {
        // Scope the search to this data region's DOM element.
        var regionEl = document.querySelector('table[data-region-name="' + _dataRegionName + '"]');
        if (!regionEl) return null;

        var checkboxes = regionEl.querySelectorAll('input[name=".select"]');
        for (var i = 0; i < checkboxes.length; i++)
        {
            if (checkboxes[i].value === String(expId))
            {
                return checkboxes[i].closest('tr');
            }
        }
        return null;
    }

    function updateRowResults(expId, data)
    {
        const row = getRowForExperimentId(expId);
        if (!row) return;
        console.log("Found row");

        const cells = row.querySelectorAll('td');
        console.log("Found cells " + cells.length);
        // The last 4 cells are our result columns
        const resultCells = [cells[cells.length - 4], cells[cells.length - 3], cells[cells.length - 2], cells[cells.length - 1]];

        if (!data.success)
        {
            resultCells[0].innerHTML = '<span class="search-pub-result-error">Error</span>';
            resultCells[2].textContent = data.error || 'Unknown error';
            return;
        }

        const count = data.papersFound;
        if (count > 0)
        {
            resultCells[0].innerHTML = LABKEY.Utils.encodeHtml(count);

            // Publication IDs
            let pubHtml = '';
            let matchHtml = '';
            for (var i = 0; i < data.matches.length; i++)
            {
                const match = data.matches[i];
                pubHtml += '<a class="labkey-text-link"'
                        + ' href="' + LABKEY.Utils.encodeHtml(match.publicationUrl) + '"'
                        + ' target="_blank"'
                        + ' rel="noopener noreferrer" >'
                        + LABKEY.Utils.encodeHtml(match.publicationLabel)
                        + '</a>';

                matchHtml += '<div>' + LABKEY.Utils.encodeHtml(match.matchInfo) + '</div>';
            }
            resultCells[1].innerHTML = pubHtml;
            resultCells[2].innerHTML = matchHtml;

            // Notify link
            const notifyLinkUrl = LABKEY.ActionURL.buildURL(
                    "panoramapublic", "searchPublicationsForDataset",
                    LABKEY.ActionURL.getContainer(),
                    {id: expId}
            );
            resultCells[3].innerHTML = '<a href="' + LABKEY.Utils.encodeHtml(notifyLinkUrl) + '" target="_blank" class="labkey-text-link" >' + 'Notify</a>';
        }
        else
        {
            resultCells[0].innerHTML = '0';
        }
    }

    function searchPublications()
    {
        var dataRegion = LABKEY.DataRegions[_dataRegionName];
        if (!dataRegion)
        {
            alert('Data region not found.');
            return;
        }

        var selectedIds = dataRegion.getChecked();
        if (selectedIds.length === 0)
        {
            alert('Please select at least one dataset.');
            return;
        }

        // Add result columns to the table
        addResultColumns();

        var progressEl = document.getElementById('search-pub-progress');
        progressEl.style.display = 'block';
        progressEl.className = 'search-pub-progress';

        var btn = document.getElementById('search-pub-btn');
        btn.disabled = true;

        var total = selectedIds.length;
        var completed = 0;
        var foundCount = 0;

        function updateProgress()
        {
            progressEl.textContent = 'Searching ' + completed + ' of ' + total + ' datasets...';
        }

        function searchNext(index)
        {
            if (index >= total)
            {
                progressEl.textContent = 'Search complete. Found publications for ' + foundCount + ' of ' + total + ' datasets.';
                progressEl.className = 'search-pub-progress complete';
                btn.disabled = false;
                return;
            }

            updateProgress();
            var expId = selectedIds[index];

            LABKEY.Ajax.request({
                url: <%=q(new ActionURL(PanoramaPublicController.SearchPublicationsForDatasetApiAction.class, getContainer()))%>,
                method: 'GET',
                params: { id: expId },
                success: function(response) {
                    var data = JSON.parse(response.responseText);
                    updateRowResults(expId, data);
                    if (data.success && data.papersFound > 0)
                    {
                        foundCount++;
                    }
                    completed++;
                    searchNext(index + 1);
                },
                failure: function() {
                    updateRowResults(expId, { success: false, error: 'Request failed' });
                    completed++;
                    searchNext(index + 1);
                }
            });
        }

        searchNext(0);
    }
</script>

<labkey:errors/>

<div>
    <div style="margin: 15px 0;">
        Select datasets below, then click "Search Publications" to search PubMed and PMC for associated publications.
    </div>
    <div>
        <%=button("Search Publications").id("search-pub-btn").onClick("searchPublications();")%>
    </div>
    <div id="search-pub-progress" class="search-pub-progress"></div>

</div>

<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    JspView<PanoramaPublicController.SearchPublicationsBean> view = HttpView.currentView();
    var form = view.getModelBean();
%>

<style>
    .pub-search-progress {
        margin: 10px 0;
        padding: 8px 12px;
        background: #f0f8ff;
        border: 1px solid #b0d4f1;
        border-radius: 4px;
        display: none;
    }
    .pub-search-progress.complete {
        background: #f0fff0;
        border-color: #b0d4b0;
    }
    .pub-search-error {
        color: #cc0000;
    }
</style>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">

    const dataRegionName = <%= q(form.getDataRegionName()) %>;
    const headers = [
        { key: 'COUNT', label: 'Papers Found' },
        { key: 'PUBLICATION_IDS', label: 'Publication IDs' },
        { key: 'MATCHES', label: 'Matches' },
        { key: 'ACTION', label: '' } // intentionally blank
    ];
    const headerIndex = headers.reduce(function(map, col, index)
    {
        map[col.key] = index;
        return map;
    }, {});

    let resultColumnsAdded = false;
    let experimentsTable;

    LABKEY.Utils.onReady(function() {
        experimentsTable = dataRegionName ? document.querySelector('table[data-region-name="' + dataRegionName + '"]') : null;
        if (!experimentsTable)
        {
            console.log("Private experiments table could not be initialized for data region " + dataRegionName);
        }
    });

    function addResultColumns()
    {
        if (resultColumnsAdded || !experimentsTable) return;

        // Add headers
        const headerRow = experimentsTable.querySelector('tr.labkey-col-header, thead tr');
        if (headerRow)
        {
            for (let i = 0; i < headers.length; i++)
            {
                const th = document.createElement('th');
                th.className = 'labkey-col-header';
                th.textContent = headers[i].label;
                headerRow.appendChild(th);
            }
        }

        // Add empty cells to each data row
        const rows = experimentsTable.querySelectorAll('tr.labkey-alternate-row, tr.labkey-row');
        for (let r = 0; r < rows.length; r++)
        {
            for (let c = 0; c < headers.length; c++)
            {
                const td = document.createElement('td');
                rows[r].appendChild(td);
            }
        }

        resultColumnsAdded = true;
    }

    function getRowForExperimentId(expId)
    {
        if (!experimentsTable) return null;

        const checkboxes = experimentsTable.querySelectorAll('input[name=".select"]');
        for (let i = 0; i < checkboxes.length; i++)
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

        const cells = row.querySelectorAll('td');

        // Last N cells are result columns
        const headersSize = headers.length;
        const resultCells = Array.from(cells).slice(-headersSize);


        if (!data.success)
        {
            resultCells[headerIndex.COUNT].innerHTML = '<span class="pub-search-error">Error</span>';
            resultCells[headerIndex.MATCHES].textContent = data.error || 'Unknown error';

            return;
        }

        const count = data.papersFound;
        if (count > 0)
        {
            // Count
            resultCells[headerIndex.COUNT].innerHTML = LABKEY.Utils.encodeHtml(count);

            // Publication IDs and Matches
            let pubHtml = '';
            let matchHtml = '';

            for (let i = 0; i < data.matches.length; i++)
            {
                const match = data.matches[i];

                pubHtml += '<a class="labkey-text-link"'
                        + ' href="' + LABKEY.Utils.encodeHtml(match.publicationUrl) + '"'
                        + ' target="_blank"'
                        + ' rel="noopener noreferrer">'
                        + LABKEY.Utils.encodeHtml(match.publicationLabel)
                        + '</a>';

                matchHtml += '<div>'
                        + LABKEY.Utils.encodeHtml(match.matchInfo)
                        + '</div>';
            }
            resultCells[headerIndex.PUBLICATION_IDS].innerHTML = pubHtml;
            resultCells[headerIndex.MATCHES].innerHTML = matchHtml;

            // Notify link
            const notifyLinkUrl = LABKEY.ActionURL.buildURL(
                    "panoramapublic", "searchPublicationsForDataset",
                    LABKEY.ActionURL.getContainer(),
                    {id: expId}
            );
            resultCells[headerIndex.ACTION].innerHTML = '<a href="' + LABKEY.Utils.encodeHtml(notifyLinkUrl) + '" target="_blank" class="labkey-text-link" >' + 'Notify</a>';
        }
        else
        {
            resultCells[headerIndex.COUNT].innerHTML = '0';
        }
    }

    function searchPublications()
    {
        const dataRegion = LABKEY.DataRegions[dataRegionName];
        if (!dataRegion)
        {
            alert('Data region not found.');
            return;
        }

        const selectedIds = dataRegion.getChecked();
        if (selectedIds.length === 0)
        {
            alert('Please select at least one dataset.');
            return;
        }

        if (!experimentsTable) return;

        // Add result columns to the table
        addResultColumns();

        const progressEl = document.getElementById('pub-search-progress');
        progressEl.style.display = 'block';
        progressEl.className = 'pub-search-progress';

        const btn = document.getElementById('search-pub-btn');
        btn.disabled = true;

        const total = selectedIds.length;
        let completed = 0;
        let foundCount = 0;

        function updateProgress()
        {
            progressEl.textContent = 'Searching ' + completed + ' of ' + total + ' datasets...';
        }

        function searchNext(index)
        {
            if (index >= total)
            {
                progressEl.textContent = 'Search complete. Found publications for ' + foundCount + ' of ' + total + ' datasets.';
                progressEl.className = 'pub-search-progress complete';
                btn.disabled = false;
                return;
            }

            updateProgress();
            const expId = selectedIds[index];

            LABKEY.Ajax.request({
                url: <%=q(new ActionURL(PanoramaPublicController.SearchPublicationsForDatasetApiAction.class, getContainer()))%>,
                method: 'GET',
                params: { id: expId },
                success: function(response) {
                    const data = JSON.parse(response.responseText);
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
    <div id="pub-search-progress" class="pub-search-progress"></div>

</div>

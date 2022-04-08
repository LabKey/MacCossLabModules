<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.panoramapublic.proteomexchange.UnimodModification" %>
<%@ page import="org.labkey.panoramapublic.proteomexchange.Formula" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    JspView<PanoramaPublicController.UnimodMatchBean> view = (JspView<PanoramaPublicController.UnimodMatchBean>) HttpView.currentView();
    var bean = view.getModelBean();
    var form = bean.getForm();
    var modification = bean.getModification();
    var unimodMatches = bean.getUnimodMatches();
    var returnUrl = form.getReturnURLHelper(PanoramaPublicController.getViewExperimentDetailsURL(form.getId(), getContainer()));

    var defineCombinationModUrl = new ActionURL(PanoramaPublicController.DefineCombinationModificationAction.class, getContainer())
            .addParameter("id", form.getId())
            .addParameter("modificationId", form.getModificationId())
            .addReturnURL(form.getReturnActionURL(PanoramaPublicController.getViewExperimentDetailsURL(form.getId(), getContainer())));
%>
<labkey:errors/>

<style>
    .display-value {
        font-size: 14px;
        margin-top: 10px;
    }
</style>


<div id="unimodMatchDiv"></div>
<%if (bean.getUnimodMatches().size() == 0 && !bean.isIsotopicMod()) { %>
<div class="alert alert-info" style="width:800px;">
    Define a custom <%=button("Combination Modification").href(defineCombinationModUrl)%> if this modification is a combination of two modifications.
</div>
<% } %>
<div style="width:800px;margin-top:30px; margin-left:8px;"><%=button("Cancel").href(returnUrl).style("padding:4px 15px")%></div>

<script type="text/javascript">

    Ext4.onReady(function(){

        var items = [
            {
                xtype: 'displayfield',
                fieldCls: 'display-value',
                fieldLabel: "Name",
                value: <%=q(modification.getName())%>
            },
            {
                xtype: 'displayfield',
                fieldCls: 'display-value',
                fieldLabel: "Formula",
                value: <%=q(Formula.normalizeFormula(modification.getFormula()))%>
            },
            <% if (bean.isIsotopicMod()) { %>
            {
                xtype: 'displayfield',
                fieldCls: 'display-value',
                fieldLabel: "Label",
                value: <%=q(bean.getLabels())%>
            },
            <% } %>
            {
                xtype: 'displayfield',
                fieldCls: 'display-value',
                fieldLabel: "Amino Acid(s)",
                value: <%=q(modification.getAminoAcid())%>
            },
            {
                xtype: 'displayfield',
                fieldCls: 'display-value',
                fieldLabel: "Terminus",
                value: <%=q(modification.getTerminus())%>
            },
            {
                xtype: 'component',
                <% if (unimodMatches.size() == 0) { %>
                cls: 'alert labkey-error alert-warning',
                <% } %>
                html: unimodMatchesHtml()
            },
        ];

        <% for (int i = 0; i < unimodMatches.size(); i++) {
            UnimodModification unimodMatch = unimodMatches.get(i);
        %>
            items.push(createForm(<%=unimodMatches.size() > 1 ? i : -1%>,
                    <%=unimodMatch.getId()%>,
                    <%=q(unimodMatch.getLink().getHtmlString())%>,
                    <%=q(unimodMatch.getName())%>,
                    <%=q(unimodMatch.getNormalizedFormula())%>,
                    <%=q(unimodMatch.getModSitesWithPosition())%>,
                    <%=q(unimodMatch.getTerminus())%>
                    <% if (bean.isIsotopicMod() && unimodMatch.getDiffIsotopicFormula() != null) { %>
                        ,<%=q(unimodMatch.getDiffIsotopicFormula().getFormula())%>,
                        getDiffIsotopeDesc(<%=qh(unimodMatch.getName())%>,
                                <%=unimodMatch.getId()%>,
                                <%=qh(unimodMatch.getFormula().getFormula())%>,
                                <%=qh(unimodMatch.getParentStructuralMod().getName())%>,
                                <%=q(UnimodModification.getLink(unimodMatch.getParentStructuralMod().getId()).getHtmlString())%>,
                                <%=qh(unimodMatch.getParentStructuralModFormula())%>,
                                <%=qh(unimodMatch.getDiffIsotopicFormula().getFormula())%>),
                    <% } %>
                    ));
        <% } %>

        Ext4.create('Ext.panel.Panel', {
            renderTo: "unimodMatchDiv",
            border: false,
            frame: false,
            defaults: {
                labelWidth: 160,
                width: 800,
                labelStyle: 'background-color: #E0E6EA; padding: 5px;'
            },
            items: items
        });
    });

    function getDiffIsotopeDesc(name, unimodId, formula, parentName, parentUnimodLink, parentFormula, diffFormula) {
        var html = '<div style="color:darkslateblue;padding:6px;background-color:#f9f9f9;">'
                  + 'Difference between<br><b>' + name + '</b> and <b>' + parentName + '</b>' + ' (' + parentUnimodLink + ')'
                  + '<br><div style="margin-top:7px;">'
                  + formula + ' - ' + parentFormula + ' = <b>' + diffFormula + '</b>'
                  + '</div></div>'
        return html;
    }
    function createForm(index, unimodId, unimodLink, name, formula, sites, terminus, diffIsotopeFormula, diffIsotopeModDescription)
    {
        var items = [
            { xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF },
            {
                xtype: 'label',
                text: '---------------- Unimod Match ' + (index > -1 ? index + 1 : '') + ' ----------------',
                style: {'text-align': 'left', 'margin': '10px 0 10px 0'}
            },
            {
                xtype: 'hidden',
                name: 'id', // ExperimentAnnotationsId
                value: <%=form.getId()%>
            },
            {
                xtype: 'hidden',
                name: <%=q(ActionURL.Param.returnUrl.name())%>,
                value: <%=q(returnUrl)%>
            },
            {
                xtype: 'hidden',
                name: 'modificationId',
                value: <%=form.getModificationId()%>
            },
            {
                xtype: 'hidden',
                name: 'unimodId',
                value: unimodId
            },

            {
                xtype: 'displayfield',
                fieldCls: 'display-value',
                fieldLabel: "Name",
                value: '<div>' + name + ', ' + unimodLink + '</div>'
            },
            {
                xtype: 'displayfield',
                fieldCls: 'display-value',
                fieldLabel: "Formula",
                value: formula
            }
        ];

        if (diffIsotopeFormula) {
            items.push(
                    {
                        xtype: 'displayfield',
                        fieldCls: 'display-value',
                        fieldLabel: "Isotope Formula",
                        value: diffIsotopeFormula,
                        afterBodyEl: '<span style="font-size: 0.9em;">' + diffIsotopeModDescription + '</span>',
                        msgTarget : 'under',
                        listeners: {
                            scope: this,
                            render: function(cmp)
                            {
                                Ext4.create('Ext.tip.ToolTip', {
                                    target: cmp.getEl(),
                                    anchorToTarget: true,
                                    anchor: 'right',
                                    header: {html: "<b>Isotope Formula</b>", style:{padding:'0 0 0 15px',color: '#777777'}},
                                    frameHeader: true,
                                    autoHide: false,
                                    width: 350,
                                    bodyCls: 'alert alert-info',
                                    closable: true,
                                    html: "Isotope modifications in Skyline that are the heavy version of a structural modification " +
                                            "have a formula that is the difference between the formula of the modification and the formula " +
                                            "of the associated unlabeled structural modification.<br>For example: the isotope formula for Dimethyl:2H(6) is the difference " +
                                            "between the formulas of Dimethyl:2H(6) and Dimethyl.<br>This difference is H'6C2-H2 <b>-</b> H4C2 = <b>H'6-H6.</b>"
                                });
                            }
                        }
                    }
            )
        }

        items.push(
                {
                    xtype: 'displayfield',
                    fieldCls: 'display-value',
                    fieldLabel: "Sites",
                    value: sites
                },
                {
                    xtype: 'displayfield',
                    fieldCls: 'display-value',
                    fieldLabel: "Terminus",
                    value: terminus
                }
        );

        var form = Ext4.create('Ext.form.Panel', {
            standardSubmit: true,
            border: false,
            frame: false,
            defaults: {
                labelWidth: 160,
                width: 500,
                labelStyle: 'background-color: #C0C6CA; padding: 5px;'
            },
            items: items,
            buttonAlign: 'left',
            buttons: [
                {
                    text: "Save Match",
                    cls: 'labkey-button primary',
                    handler: function(button) {
                        button.setDisabled(true);
                        form.submit({
                            url: <%=q(urlFor(bean.isIsotopicMod()
                            ? PanoramaPublicController.MatchToUnimodIsotopeAction.class
                            : PanoramaPublicController.MatchToUnimodStructuralAction.class))%>,
                            method: 'POST'
                        });
                    }
                }]
        });
        return form;
    }

    function unimodMatchesHtml() {
        let html = '<div>';
        const modMatchCount = <%=unimodMatches.size()%>;
        html += modMatchCount === 0 ? 'No Unimod matches were found for the modification.'
                : 'The modification matches ' + modMatchCount + ' Unimod modification' + (modMatchCount > 1 ? 's' : '')
        + '. Click the link next to the Unimod name to view the modification page on the Unimod website.'
        + ' Click the "Save Match" button to associate the Unimod Id with the modification.';
        html += '</div>';
        return html;
    }
</script>

<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.panoramapublic.model.speclib.SpecLibDependencyType" %>
<%@ page import="org.labkey.panoramapublic.model.speclib.SpecLibSourceType" %>
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
    JspView<PanoramaPublicController.SpecLibInfoBean> view = (JspView<PanoramaPublicController.SpecLibInfoBean>) HttpView.currentView();
    var bean = view.getModelBean();
    var form = bean.getForm();
    var library = bean.getLibrary();
    var returnUrl = form.getReturnURLHelper(getContainer().getStartURL(getUser()));
%>

<div style="margin-bottom:10px;">
    <b>Spectral Library</b>: <%=h(library.getName())%>
    <br/>
    <b>File</b>: <%=h(library.getFileNameHint())%>
</div>
<labkey:errors/>
<div id="editSpecLibInfoForm"></div>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">

    Ext4.onReady(function(){

        const libraryUrl = LABKEY.Utils.helpPopup("Library URL", "URL where the library can be downloaded");
        const sourceAccession = LABKEY.Utils.helpPopup("Repository Accession", "Accession or identifier of the data in the repository. " +
             "This can be a ProteomeXchange ID (e.g. PXD000001), a MassIVE identifier (e.g. MSV000000001) or a PeptideAtlas identifier (e.g. PASS00001).");
        const sourceUsername = LABKEY.Utils.helpPopup("Repository Username", "Username to access the data in the repository if the data is private.");
        const sourcePassword = LABKEY.Utils.helpPopup("Repository Password", "Password to access the data in the repository if the data is private.");
        const dependencyType = LABKEY.Utils.helpPopup("Dependency Type", "How the library was used with the Skyline document.");

        var form = Ext4.create('Ext.form.Panel', {
            renderTo: "editSpecLibInfoForm",
            standardSubmit: true,
            border: false,
            frame: false,
            defaults: {
                labelWidth: 150,
                width: 500,
                labelStyle: 'background-color: #E0E6EA; padding: 5px;'
            },
            items: [
                { xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF },
                {
                    xtype: 'hidden',
                    name: 'id', // ExperimentAnnotationsId
                    value: <%=form.getId()%>
                },
                {
                    // instead of generateReturnUrlFormField(returnUrl)
                    xtype: 'hidden',
                    name: <%=q(ActionURL.Param.returnUrl.name())%>,
                    value: <%=q(returnUrl)%>
                },
                {
                    xtype: 'hidden',
                    name: 'specLibId', // targetedms.spectrumlibrary.id
                    value: <%=form.getSpecLibId()%>
                },
                <%if(form.getSpecLibInfoId() != null){%>
                {
                    xtype: 'hidden',
                    name: 'specLibInfoId', // panoramapublic.speclibinfo.id
                    value: <%=form.getSpecLibInfoId()%>
                },
                <%}%>
                {
                    xtype: 'combobox',
                    name: 'sourceType',
                    itemId: 'sourceType',
                    fieldLabel: "Library Source",
                    allowBlank: true,
                    editable: false,
                    value: <%=form.getSourceType() != null ? q(form.getSourceType()) : null%>,
                    store: [
                        <% for (SpecLibSourceType sourceType: SpecLibSourceType.valuesForLibrary()) { %>
                        [ <%= q(sourceType.name()) %>, <%= q(sourceType.getLabel()) %> ],
                        <% } %>
                    ],
                    listeners: {
                        change: function(cb, newValue) {
                            sourceTypeComboboxChanged(cb, newValue);
                        }
                    }
                },
                {
                    xtype: 'textfield',
                    name: 'sourceUrl',
                    itemId: 'sourceUrl',
                    fieldLabel: "Library URL",
                    disabled: <%=!SpecLibSourceType.PUBLIC_LIBRARY.name().equals(form.getSourceType())%>,
                    value: <%=q(form.getSourceUrl())%>,
                    afterLabelTextTpl: libraryUrl.html
                },
                {
                    xtype: 'textfield',
                    name: 'sourceAccession',
                    itemId: 'sourceAccession',
                    fieldLabel: "Accession",
                    disabled: <%=!SpecLibSourceType.OTHER_REPOSITORY.name().equals(form.getSourceType())%>,
                    value: <%=q(form.getSourceAccession())%>,
                    afterLabelTextTpl: sourceAccession.html
                },
                {
                    xtype: 'textfield',
                    name: 'sourceUsername',
                    itemId: 'sourceUsername',
                    fieldLabel: "User Name",
                    disabled: <%=!SpecLibSourceType.OTHER_REPOSITORY.name().equals(form.getSourceType())%>,
                    value: <%=q(form.getSourceUsername())%>,
                    afterLabelTextTpl: sourceUsername.html
                },
                {
                    xtype: 'textfield',
                    name: 'sourcePassword',
                    itemId: 'sourcePassword',
                    fieldLabel: "Password",
                    disabled: <%=!SpecLibSourceType.OTHER_REPOSITORY.name().equals(form.getSourceType())%>,
                    value: "", // Don't display the password; make the user enter it every time they edit
                    afterLabelTextTpl: sourcePassword.html
                },
                {
                    xtype: 'combobox',
                    name: 'dependencyType',
                    fieldLabel: "Dependency Type",
                    allowBlank: true,
                    editable: false,
                    value: <%=form.getDependencyType() != null ? q(form.getDependencyType()) : null%>,
                    afterLabelTextTpl: dependencyType.html,
                    store: [
                        <% for (SpecLibDependencyType sourceType: SpecLibDependencyType.values()) { %>
                        [ <%= q(sourceType.name()) %>, <%= q(sourceType.getLabel()) %> ],
                        <% } %>
                    ]
                }
            ],
            buttonAlign: 'left',
            buttons: [
                {
                    text: "Save",
                    cls: 'labkey-button primary',
                    handler: function(button) {
                        button.setDisabled(true);
                        form.submit({
                            url: <%=q(urlFor(PanoramaPublicController.EditSpecLibInfoAction.class))%>,
                            method: 'POST'
                        });
                    }
                },
                {
                    text: 'Cancel',
                    cls: 'labkey-button',
                    handler: function(btn) {
                        window.location = <%= q(returnUrl) %>;
                    }
                }]
        });

        libraryUrl.callback();
        sourceAccession.callback();
        sourceUsername.callback();
        sourcePassword.callback();
        dependencyType.callback();
    });

    function toggleTextFields(ownerCt, disable, fields) {
        for (var i = 0; i < fields.length; i += 1) {
            var tf = ownerCt.getComponent(fields[i]);
            if (tf) {
                if (disable) tf.setValue('');
                tf.setDisabled(disable);
            }
        }
    }

    function sourceTypeComboboxChanged(cb, newValue) {
        var otherRepo = newValue === <%=q(SpecLibSourceType.OTHER_REPOSITORY.name())%>;
        var publicLib = newValue === <%=q(SpecLibSourceType.PUBLIC_LIBRARY.name())%>;
        toggleTextFields(cb.ownerCt, !otherRepo, ['sourceAccession', 'sourceUsername', 'sourcePassword']);
        toggleTextFields(cb.ownerCt, !publicLib, ['sourceUrl']);
    }
</script>
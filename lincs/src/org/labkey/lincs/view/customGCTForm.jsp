<%
    /*
     * Copyright (c) 2015-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.lincs.LincsController" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<labkey:errors/>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    JspView<LincsController.CustomGCTBean> jspView = (JspView<LincsController.CustomGCTBean>) HttpView.currentView();
    LincsController.CustomGCTBean bean = jspView.getModelBean();
    LincsController.CustomGCTForm form = bean.getForm();
    List<LincsController.SelectedAnnotation> annotations = bean.getAnnotations();
%>

<style>
    td.inputCell {padding: 5px;}
    span.heading {margin-bottom:20px 10px 20px 10px;}
</style>

<script type="text/javascript">

    // Download Ext4 js and css files.
    // LABKEY.requiresExt4Sandbox();

    var replAnnotationCB = new Array();

    // Initialize
    Ext4.onReady(init);

    function init()
    {
        console.log("Initializing");
        // console.log(LABKEY.ActionURL.getContainer());
        displaySelectableAnnotations();
        toggle('none'); // Hide the "advanced" replicate annotations.
    }

    function displaySelectableAnnotations()
    {
        // Create a row for each annotation
        var targetRow = document.getElementById("annotationList");
        var advancedTargetRow = document.getElementById("annotationListAdvanced");
        <%for(LincsController.SelectedAnnotation annot : annotations) { %>

            var newRow = document.createElement("tr");
            newRow.className = '<%=annot.isAdvanced() ? "advanced" : "required"%>';
            var td1 = newRow.insertCell(0);
            var td2 = newRow.insertCell(1);
            td1.className = 'inputCell';
            td2.className = 'inputCell';

            var td1InnerHtml = '<span class="heading">' + '<%=annot.getDisplayName()%>';
            var td2InnerHtml = '<div class="input" id="' + '<%=annot.getName()%>' + '_combo_input" ></div>' +
                               '<div id="' + '<%=annot.getName()%>' + '_combo_selected" class="selected"></div>';

            td1.innerHTML = td1InnerHtml;
            td2.innerHTML = td2InnerHtml;


            <%if(annot.isAdvanced()) { %>
                advancedTargetRow.parentNode.insertBefore(newRow, advancedTargetRow.nextSibling);
                advancedTargetRow = newRow;
            <%} else {%>
                targetRow.parentNode.insertBefore(newRow, targetRow.nextSibling);
                targetRow = newRow;
            <%}%>

        <% } %>

        // Create multi selection enabled combo box for each annotation
        // Set up a model to use in our Store
        var model = Ext4.define('Annotation', {
            extend: 'Ext.data.Model',
            fields: [
                {name: 'DisplayName', type: 'string'},
                {name: 'NameValue',  type: 'string'}
            ]
        });

        <%for(LincsController.SelectedAnnotation annot : annotations) { %>
            var storeData = [];
            <%for (String value: annot.getValues()) { %>
                storeData.push({'DisplayName': '<%=value%>',
                                'NameValue': '<%=annot.getName()%>' + ":" + '<%=value%>'});
            <%}%>

            var store =  Ext4.create('Ext.data.Store', {
                model: model,
                data : storeData
            });

            var div_id =  '<%=annot.getName()%>' +"_combo_input";
            var combo_id = '<%=annot.getName()%>' +"_combo";
            var multiCombo = Ext4.create('Ext.form.field.ComboBox', {
                fieldLabel: '',
                renderTo: div_id,
                id: combo_id,
                multiSelect: true,
                displayField: 'DisplayName',
                valueField: 'NameValue',
                width: 300,
                labelWidth: 130,
                store: store,
                queryMode: 'local',
                listeners:{
                    scope: this,
                    'change': onChange
                }
            });

            replAnnotationCB.push(multiCombo);
        <% } %>
    }

    function onChange(field, newValue, oldValue, eOpts)
    {
        // console.log(newValue, oldValue);
        var comboboxId = field.id ;
        var owningDiv = Ext4.get(comboboxId + '_selected');

        if(!owningDiv) return;

        var selectedHtml = "";
        for(var i = 0; i < newValue.length; i += 1)
        {
            var record = field.findRecordByValue(newValue[i]);
            var nameValue = newValue[i];
            var displayValue = record ? record.get("DisplayName") : nameValue;
            var spanId = comboboxId + "_span";
            selectedHtml += '<img src="/labkey/_images/delete.png" style="width:10px; height:10px; margin-right:3px" ';
            selectedHtml += "onclick=\"deleteSelected('" + comboboxId + "', '" + nameValue + "');\"/>";
            selectedHtml += '<span id="' +spanId + '">' + displayValue + '</span><br>';
        }
        owningDiv.dom.innerHTML = selectedHtml;
    }

    function deleteSelected(comboBoxId, valToRemove)
    {
        var combo;
        for(var i = 0; i < replAnnotationCB.length; i += 1)
        {
            if(replAnnotationCB[i].id == comboBoxId)
            {
                combo = replAnnotationCB[i];
                break;
            }
        }
        if(combo)
        {
            var values = combo.getValue();
            var newValues = [];
            for(var i = 0; i < values.length; i += 1)
            {
                if(!(valToRemove === values[i]))
                {
                    newValues.push(values[i]);
                }
            }

            combo.setValue(newValues); // This will fire the 'change' event
        }
    }

    function toggleAdvanced()
    {
        var el = document.getElementById("toggleAdvanced");
        if(!el) return;

        var text = el.innerHTML;
        console.log(text);
        if(text === "Show all annotations")
        {
            el.innerHTML = "Hide advanced annotations";
            toggle('')
        }
        else
        {
            el.innerHTML = "Show all annotations";
            toggle('none');
        }
    }

    function toggle(state)
    {
        var elements = document.getElementsByClassName("advanced");
        for(var i = 0; i < elements.length; i++)
        {
            elements[i].style.display = state; // Toggle the "advanced" replicate annotations.
        }
    }

    function beforeSubmit()
    {
        var annotations = {};
        for(var i = 0; i < replAnnotationCB.length; i += 1)
        {
            var submitValueArr = replAnnotationCB[i].getSubmitValue();
            if(submitValueArr.length == 0) continue;

            var annotationName;
            var annotValues = [];
            for(var j = 0; j < submitValueArr.length; j += 1)
            {
                var splitStr = submitValueArr[j].split(":");
                annotationName = splitStr[0];
                var value = splitStr[1];
                annotValues.push(value);
            }
            annotations[annotationName] = annotValues;
        }

        var replAnnotParam = '';
        var first = true;
        for(var key in annotations)
        {
            if(!first) replAnnotParam+= ";";
            first = false;
            var values = annotations[key];
            replAnnotParam += key + ":";
            replAnnotParam += values.join();
        }
        console.log(replAnnotParam);

        var form = document.forms["customGCTForm"];
        if(form)
        {
            form.elements['selectedAnnotations'].value = replAnnotParam;
        }
    }
</script>

<labkey:form action="<%=h(buildURL(LincsController.CreateCustomGCTAction.class))%>" method="post" id="customGCTForm">

    <input type="hidden" name="selectedAnnotations"/>

    <table cellspacing="0" cellpadding="0" border="0">
        <tbody>
        <tr>
            <td style="font-weight:bold;">Experiment type:</td>
            <td>
                <select name="experimentType">
                    <%for(String expType: LincsController.CustomGCTForm.EXP_TYPES) {%>
                        <option <%=selected(form.getExperimentType().equals(expType))%> value="<%=expType%>"><%=expType%></option>
                    <%}%>
                </select>
            </td>
        </tr>
        <tr><td colspan="2" style="text-align:left; font-weight:bold; padding-top:20px;">
            Select replicate annotations
            <br/>
            <span style="color:red;font-size:x-small;font-weight:normal;">Multiple values can be selected in the drop-down list for each annotation</span>
        </td></tr>
        <tr id="annotationList">
        </tr>
        <tr>
            <td colspan="2" style="text-align:left; font-weight:bold; padding-top:20px;">
                <span style="text-decoration:underline;cursor:pointer" onclick="toggleAdvanced()" id="toggleAdvanced">Show all annotations</span>
            </td>
        </tr>
        <tr id="annotationListAdvanced">
        </tr>

        <tr>
           <td style="padding-top: 20px;"><%= button("Create GCT File").submit(true).onClick("beforeSubmit();") %></td>
        </tr>
        </tbody>
    </table>
</labkey:form>


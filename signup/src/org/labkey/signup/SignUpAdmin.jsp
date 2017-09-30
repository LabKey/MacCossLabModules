<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.data.PropertyManager" %>
<%@ page import="org.labkey.api.security.SecurityManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.signup.SignUpController" %>
<%@ page import="org.labkey.signup.SignUpModule" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="org.labkey.api.security.Group" %>
<%
    JspView<User> me = (JspView<User>) HttpView.currentView();
    User data = me.getModelBean();
    List<Container> list = ContainerManager.getAllChildren(ContainerManager.getRoot(),data);
    Map<String, Object> m = new HashMap<>(); // Map of containers to available security groups in the container.
%>

<script src="//code.jquery.com/jquery-1.10.2.js"></script>
<style type="text/css">
    table.gridtable {
        font-family: verdana,arial,sans-serif;
        font-size:11px;
        color:#333333;
        border-width: 1px;
        border-color: #666666;
        border-collapse: collapse;
    }
    table.gridtable th {
        border-width: 1px;
        padding: 8px;
        border-style: solid;
        border-color: #666666;
        background-color: #dedede;
    }
    table.gridtable td {
        border-width: 1px;
        padding: 8px;
        border-style: solid;
        border-color: #666666;
        background-color: #ffffff;
    }
</style>

<!--Creates drop down list of all containers-->
<h4 style="padding:0px; margin: 0px;">Add new user group rule</h4>
<form <%=formAction(SignUpController.AddPropertyAction.class, Method.Post)%>><labkey:csrf/>
    <select id="containerId" name="containerId" onchange="loadGroups(this.value)">
        <option disabled selected> -- select an option -- </option>
        <%for(Container c: list) {
        m.put(String.valueOf(c.getRowId()), SecurityManager.getGroups(c.getProject(), false));%> <!--Adds container and associated groups to map-->
        <option value="<%=h(c.getRowId())%>"><%=h(c.getPath())%></option>
    <%}%>
    </select>
    <!--Creates a drop down list of all groups in selected container (dynamic) no ajax-->
    <select onchange="showAdd(this.value)" id="groupName" name="groupName"></select>
    <labkey:button text="Add Rule" />
</form>
<br />
<!--Table that displays existing Container -> Group rules and option to remove -->
<table class="gridtable">
    <tr>
        <th>Container</th>
        <th>Group</th>
        <th>&nbsp;</th>
    </tr>

    <%for(Container c:list) { // iterate through containers and display the ones that have been associated with a sign-up group.
        PropertyManager.PropertyMap property = PropertyManager.getWritableProperties(c, SignUpModule.SIGNUP_CATEGORY, false);
        if(property != null && property.get(SignUpModule.SIGNUP_GROUP_NAME) != null) {
        %>
            <tr>
                <td><%=h(c.getPath())%></td>
                <td><%=h(property.get(SignUpModule.SIGNUP_GROUP_NAME))%></td>
                <td><a href="<%= h(buildURL(SignUpController.RemovePropertyAction.class, "containerId=" + c.getRowId()))%>">Remove</a></td>
            </tr>
        <%}
    }%>
</table>


<br />
<br />

<!--Creates drop down list of all groups-->
<h4 style="padding:0px; margin: 0px;">Add group conversion rule</h4>
<form <%=formAction(SignUpController.AddGroupChangeProperty.class, Method.Post)%>><labkey:csrf/>
    <select id="oldgroup" name="oldgroup">
        <option disabled selected> -- select an option -- </option>
        <%for(Container c: list) {
            if(c.isProject()) {
            List<Group> groups = SecurityManager.getGroups(c.getProject(), false);%>
        <option disabled><%=h(c.getName())%></option>
          <%for(Group g: groups) {%>
          <option value="<%=h(g.getUserId())%>">--<%=h(g.getName())%></option>
        <%}}}%>
    </select>
    <select id="newgroup" name="newgroup">
        <option disabled selected> -- select an option -- </option>
        <%for(Container c: list) {
            if(c.isProject()) {
                List<Group>  groups = SecurityManager.getGroups(c.getProject(), false);%>
        <option disabled><%=h(c.getName())%></option>
        <%for(Group g: groups) {%>
        <option value="<%=h(g.getUserId())%>">--<%=h(g.getName())%></option>
        <%}}}%>
    </select>
    <labkey:button text="Add Rule" />
</form>
<br />
<table class="gridtable">
    <tr>
        <th>Group A --></th>
        <th>Group B</th>
        <th>&nbsp;</th>
    </tr>
    <%  PropertyManager.PropertyMap groupToGroup = PropertyManager.getWritableProperties(SignUpModule.SIGNUP_GROUP_TO_GROUP, true);
        Set<String> keySet = groupToGroup.keySet();
        for(String key: keySet) {
        List<String> rules = Arrays.asList(groupToGroup.get(key).split("\\s*,\\s*"));
        for(String rule: rules) { if(!rule.equals("")) {%>
        <tr>
            <td><%=h(SecurityManager.getGroup(Integer.parseInt(key)))%> (<%=h(key)%>)</td>
            <td><%=h(SecurityManager.getGroup(Integer.parseInt(rule)))%> (<%=h(rule)%>)</td>
            <td><a href="<%= h(buildURL(SignUpController.RemoveGroupChangeProperty.class, "oldgroup=" + key + "&newgroup=" + rule))%>">Remove</a></td>
        </tr>
        <%}}}%>
</table>
<%
JSONObject json = new JSONObject(m);
%>
<script type="text/javascript">

    function showAdd(group) {
        if (group.length > 1)
            $('#submitBtn').css("display", "block");
        else
            $('#submitBtn').css("display", "none");
    }

    function loadGroups(container) {
        var obj = jQuery.parseJSON( <%= q(json.toString()) %> );
        $('#groupName')
                .find('option')
                .remove()
                .end();
        $.each(obj, function(key, value) {
            if (key == container) {
                for (var i = 0; i < value.length; i++) {
                    $('#groupName')
                            .append($("<option></option>")
                                    .attr("value", value[i])
                                    .text(value[i]));
                }
                if (value.length == 0)
                    showAdd("");
                else
                    showAdd(value[value.length - 1]);
            }
        });
    }
</script>
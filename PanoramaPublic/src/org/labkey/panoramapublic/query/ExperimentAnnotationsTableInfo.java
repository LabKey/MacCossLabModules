/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
package org.labkey.targetedms.query;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserIdRenderer;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RoleAssignment;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.FolderAdminRole;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SimpleNamedObject;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.targetedms.PublishTargetedMSExperimentsController;
import org.labkey.targetedms.TargetedMSManager;
import org.labkey.targetedms.TargetedMSSchema;
import org.labkey.targetedms.model.ExperimentAnnotations;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * User: vsharma
 * Date: 12/19/13
 * Time: 2:29 PM
 */
public class ExperimentAnnotationsTableInfo extends FilteredTable<TargetedMSSchema>
{

    public ExperimentAnnotationsTableInfo(final TargetedMSSchema schema, ContainerFilter cf)
    {
        this(TargetedMSManager.getTableInfoExperimentAnnotations(), schema, cf);
    }

    public ExperimentAnnotationsTableInfo(TableInfo tableInfo, TargetedMSSchema schema, ContainerFilter cf)
    {
        super(tableInfo, schema, cf);

        wrapAllColumns(true);
        setDetailsURL(new DetailsURL(PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(getContainer())));

        var citationCol = getMutableColumn(FieldKey.fromParts("Citation"));
        citationCol.setDisplayColumnFactory(colInfo -> new PublicationLinkDisplayColumn(colInfo));
        citationCol.setURLTargetWindow("_blank");
        citationCol.setLabel("Publication");

        var spikeInColumn = getMutableColumn(FieldKey.fromParts("SpikeIn"));
        spikeInColumn.setDisplayColumnFactory(colInfo -> new YesNoDisplayColumn(colInfo));

        var titleCol = getMutableColumn(FieldKey.fromParts("Title"));
        titleCol.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new DataColumn(colInfo, true)
                {
                    private FieldKey _containerKey = new FieldKey(getColumnInfo().getFieldKey().getParent(), "container");
                    private FieldKey _idKey = new FieldKey(getColumnInfo().getFieldKey().getParent(), "id");

                    @Override
                    public @NotNull Set<ClientDependency> getClientDependencies()
                    {
                        return PageFlowUtil.set(
                                ClientDependency.fromPath(AppProps.getInstance().getScheme() + "://ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js"),
                                ClientDependency.fromPath("/TargetedMS/css/dropDown.css"),
                                ClientDependency.fromPath("/TargetedMS/js/dropDownUtil.js"));
                    }

                    @Override
                    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                    {
                        Integer id = ctx.get(_idKey, Integer.class);
                        Container container = ctx.get(_containerKey, Container.class);
                        if(id != null && container != null)
                        {
                            ActionURL detailsPage = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(container); // experiment container
                            out.write("<span active=\"false\" loaded=\"false\" onclick=\"viewExperimentDetails(this,'" + container.getPath() + "', '" + id + "','" + detailsPage + "')\"><img id=\"expandcontract-" + id + "\" src=\"/labkey/_images/plus.gif\">&nbsp;");
                            out.write("</span>");
                        }
                        super.renderGridCellContents(ctx, out);
                    }

                    @Override
                    public void addQueryFieldKeys(Set<FieldKey> keys)
                    {
                        super.addQueryFieldKeys(keys);
                        keys.add(_containerKey);
                        keys.add(_idKey);
                    }
                };
            }
        });

        var containerCol = getMutableColumn(FieldKey.fromParts("Container"));
        ContainerForeignKey.initColumn(containerCol, getUserSchema());

        var shareCol = wrapColumn("Share", getRealTable().getColumn("Id"));
        shareCol.setDisplayColumnFactory(colInfo -> new DataColumn(colInfo)
        {
            @Override
            public @NotNull Set<ClientDependency> getClientDependencies()
            {
                return PageFlowUtil.set(
                        ClientDependency.fromPath("TargetedMS/css/ExperimentAnnotations.css"),
                        ClientDependency.fromPath("hopscotch/css/hopscotch.min.css"),
                        ClientDependency.fromPath("TargetedMS/js/ExperimentAnnotations.js"),
                        ClientDependency.fromPath("TargetedMS/js/clipboard.min.js"),
                        ClientDependency.fromPath("hopscotch/js/hopscotch.min.js")
                        );
            }

            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                // Get the ExperimentAnnotations record
                Integer experimentAnnotationsId = ctx.get(FieldKey.fromParts(colInfo.getAlias()), Integer.class);
                ExperimentAnnotations expAnnotations = ExperimentAnnotationsManager.get(experimentAnnotationsId);

                String accessUrl = JournalManager.getExperimentShortUrl(expAnnotations);

                if(accessUrl == null)
                {
                    out.write("");
                }
                else
                {
                    String content = "<div><a class=\"button-small button-small-green\" style=\"margin:0px 5px 0px 2px;\""
                                     + "href=\"\" onclick=\"showShareLink(this, '" + PageFlowUtil.filter(accessUrl) + "');return false;\""
                               + ">Share</a>";
                    content += "</div>";
                    out.write(content);
                }
            }
        });
        addColumn(shareCol);

        var labHeadCol = ExperimentUserForeignKey.initColumn(getColumn("LabHead"));
        labHeadCol.setDescription("A lab head is required for submitting data to ProteomeXchange.");

        ColumnInfo submitterCol = ExperimentUserForeignKey.initColumn(getColumn("Submitter"));
        // submitterCol.setUserEditable(getUserSchema().getUser().isInSiteAdminGroup() ? true : false);

        var instrCol = getMutableColumn("Instrument");
        instrCol.setDisplayColumnFactory(colInfo -> new AutoCompleteColumn(colInfo, new ActionURL(PublishTargetedMSExperimentsController.CompleteInstrumentAction.class, getContainer()), true, "Enter Instrument"));
        instrCol.setDescription("One or more instruments are required for submitting data to ProteomeXchange.");

        var organismCol = getMutableColumn("Organism");
        organismCol.setDisplayColumnFactory(colInfo -> new OrganismColumn(colInfo, new ActionURL(PublishTargetedMSExperimentsController.CompleteOrganismAction.class, getContainer()), false, "Enter Organism"));
        organismCol.setDescription("One or more organisms are required for submitting data to ProteomeXchange.");

        SQLFragment runCountSQL = new SQLFragment("(SELECT COUNT(r.ExperimentRunId) FROM ");
        runCountSQL.append(ExperimentService.get().getTinfoRunList(), "r");
        runCountSQL.append(" WHERE r.ExperimentId = ");
        runCountSQL.append(ExprColumn.STR_TABLE_ALIAS);
        runCountSQL.append(".ExperimentId)");
        ExprColumn runCountColumn = new ExprColumn(this, "Runs", runCountSQL, JdbcType.INTEGER);
        addColumn(runCountColumn);

        List<FieldKey> visibleColumns = new ArrayList<>();
        visibleColumns.add(FieldKey.fromParts("Share"));
        visibleColumns.add(FieldKey.fromParts("Title"));
        visibleColumns.add(FieldKey.fromParts("Organism"));
        visibleColumns.add(FieldKey.fromParts("Instrument"));
        visibleColumns.add(FieldKey.fromParts("SpikeIn"));
        visibleColumns.add(FieldKey.fromParts("Runs"));
        visibleColumns.add(FieldKey.fromParts("Keywords"));
        visibleColumns.add(FieldKey.fromParts("Citation"));
        visibleColumns.add(FieldKey.fromParts("pxid"));

        setDefaultVisibleColumns(visibleColumns);
    }

    @Override
    public String getName()
    {
        return TargetedMSSchema.TABLE_EXPERIMENT_ANNOTATIONS;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm);
    }

    public static class ExperimentUserForeignKey extends UserIdForeignKey
    {
        private FieldKey _fieldKey;

        static public BaseColumnInfo initColumn(ColumnInfo column)
        {
            ((BaseColumnInfo)column).setFk(new ExperimentUserForeignKey(column.getParentTable().getUserSchema(), column.getFieldKey()));
            ((BaseColumnInfo)column).setDisplayColumnFactory(colInfo -> new ExperimentUserDisplayColumn(colInfo));
            return (BaseColumnInfo)column;
        }

        public ExperimentUserForeignKey(UserSchema userSchema, FieldKey fieldKey)
        {
            super(userSchema);
            _fieldKey = fieldKey;
        }

        @Override
        public NamedObjectList getSelectList(RenderContext ctx)
        {
            NamedObjectList objectList = new NamedObjectList();

            // If there is an existing value in this column include that in the list
            if(_fieldKey != null)
            {
                Integer userId = ctx.get(_fieldKey, Integer.class);
                if (userId != null)
                {
                    addUser(objectList, userId);
                }
            }
            Container container = ctx.getContainer();
            if(container != null)
            {
                addUsers(objectList, container, RoleManager.getRole(FolderAdminRole.class));
                if(!container.isProject())
                {
                    addUsers(objectList, container.getProject(), RoleManager.getRole(ProjectAdminRole.class));
                }
            }
            return objectList;
        }

        private void addUsers(NamedObjectList objectList, Container container, Role adminRole)
        {
            Set<RoleAssignment> roles = container.getPolicy().getAssignments();
            for(RoleAssignment role: roles)
            {
                if (role.getRole().equals(adminRole))
                {
                    addUser(objectList, role.getUserId());
                }
            }
        }

        private void addUser(NamedObjectList objectList, Integer userId)
        {
            User u = UserManager.getUser(userId);
            if (u != null)
            {
                String displayName = getUserDisplayName(u);
                objectList.put(new SimpleNamedObject(String.valueOf(u.getUserId()), displayName));
            }
        }
    }

    public static class ExperimentUserDisplayColumn extends UserIdRenderer
    {
        public ExperimentUserDisplayColumn(ColumnInfo col)
        {
            super(col);
        }

        @Override
        public @NotNull String getFormattedValue(RenderContext ctx)
        {
            Integer userId = ctx.get(getColumnInfo().getFieldKey(), Integer.class);
            String userDisplayName = null;
            if(userId != null)
            {
                userDisplayName = getUserDisplayName(UserManager.getUser(userId));
            }
            return userDisplayName == null ? super.getFormattedValue(ctx) : userDisplayName;
        }
    }

    private static String getUserDisplayName(User u)
    {
        if(u == null)
        {
            return null;
        }

        String displayName = u.getDisplayName(null);
        if(!StringUtils.isBlank(u.getFullName()))
        {
            displayName = u.getFullName() + " (" + displayName + ")";
        }
        return displayName;
    }

    public static  class PublicationLinkDisplayColumn extends DataColumn
    {
        public PublicationLinkDisplayColumn(ColumnInfo colInfo)
        {
            super(colInfo);
        }

        @Override
        public String renderURL(RenderContext ctx)
        {
            Object publicationLink = ctx.get("PublicationLink");
            if (publicationLink != null)
            {
                return (String)publicationLink;
            }
            return null;
        }

        @Override
        public Object getValue(RenderContext ctx)
        {
            Object citation = ctx.get("Citation");

            if(citation != null)
            {
                String ellipsis = "...";
                String displayText = (String)citation;
                return displayText.length() > (50 - ellipsis.length()) ? displayText.substring(0, (50 - ellipsis.length())) + ellipsis : displayText;
            }
            return "";
        }

        @Override
        public Object getDisplayValue(RenderContext ctx)
        {
            return getValue(ctx);
        }

        @NotNull
        @Override
        public String getFormattedValue(RenderContext ctx)
        {
            return h(getValue(ctx));
        }

        @Override
        public void addQueryFieldKeys(Set<FieldKey> keys)
        {
            super.addQueryFieldKeys(keys);
            keys.add(FieldKey.fromParts("PublicationLink"));
        }
    }

    public static  class YesNoDisplayColumn extends DataColumn
    {
        public YesNoDisplayColumn(ColumnInfo colInfo)
        {
            super(colInfo);
        }

        @Override
        public Object getValue(RenderContext ctx)
        {
            Object value =  super.getValue(ctx);
            if(value != null)
            {
                return (Boolean)value ? "Yes" : "No";
            }
            return "";
        }

        @Override
        public Object getDisplayValue(RenderContext ctx)
        {
            return getValue(ctx);
        }

        @NotNull
        @Override
        public String getFormattedValue(RenderContext ctx)
        {
            return h(getValue(ctx));
        }
    }

    private static class AutoCompleteColumn extends DataColumn
    {
        private String _autoCompletionUrl;
        private boolean _prefetch;
        private String _placeholderText;

        public AutoCompleteColumn(ColumnInfo col, ActionURL autocompletionUrl, boolean prefetch, String placeHolderText)
        {
            super(col);
            _autoCompletionUrl = autocompletionUrl.getLocalURIString() + (!prefetch ? "token=%QUERY" : "");
            _autoCompletionUrl = PageFlowUtil.jsString(_autoCompletionUrl);
            _prefetch = prefetch;
            _placeholderText = placeHolderText;
        }

        @Override
        public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
        {
            String name = getFormFieldName(ctx);
            String valueString = value == null ? "" : value.toString();

            String renderId = "input-picker-div-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
            StringBuilder sb = new StringBuilder();

            sb.append("<script type=\"text/javascript\">");
            sb.append("LABKEY.requiresScript([\"/TargetedMS/js/ExpAnnotAutoComplete.js\"], function() {\n");
            sb.append("Ext4.onReady(function(){\n");
            sb.append("    initAutoComplete(").append(_autoCompletionUrl).append(", '").append(renderId).append("', ").append(_prefetch ? "true": "false").append(");\n");
            sb.append("});});\n");
            sb.append("</script>\n");
            sb.append("<div style=\"margin-top:5px;\" id=\"").append(renderId).append("\" class=\"scrollable-dropdown-menu\">");
            sb.append("<input type=\"text\" class=\"tags\" placeholder=\"" + _placeholderText + "\" name=\"" + name + "\" value=\"" + valueString + "\">");
            sb.append("</div>");
            sb.append("<div style=\"font-size:11px\">").append(PageFlowUtil.filter(getHelpText(), true, false)).append("</div>");

            out.write(sb.toString());
        }

        String getHelpText()
        {
            return "Type 3 or more letters to see a drop-down list of matching options. Only entries selected from the list will be saved.";
        }
    }

    private static class OrganismColumn extends AutoCompleteColumn
    {

        public OrganismColumn(ColumnInfo col, ActionURL autocompletionUrl, boolean prefetch, String placeHolderText)
        {
            super(col, autocompletionUrl, prefetch, placeHolderText);
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            String organismsStr = ctx.get(getColumnInfo().getFieldKey(), String.class);
            if(!StringUtils.isBlank(organismsStr))
            {
                out.write(PageFlowUtil.filter(ExperimentAnnotations.getOrganismsNoTaxId(organismsStr)));
            }
            else
            {
                super.renderGridCellContents(ctx, out);
            }
        }

        @Override
        String getHelpText()
        {
            return "Type 3 or more letters to see a drop-down list of matching options. It may take a few seconds to populate the list.\n"
                    + "The list displays up to 20 matching options. Continue typing to refine the list.\n"
                    + "Only entries selected from the list will be saved.";
        }
    }
}

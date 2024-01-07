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
package org.labkey.panoramapublic.query;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.UserIdRenderer;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.Group;
import org.labkey.api.security.MemberType;
import org.labkey.api.security.RoleAssignment;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.FolderAdminRole;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.DOM;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SimpleNamedObject;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.panoramapublic.PanoramaPublicController;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.PanoramaPublicSchema;
import org.labkey.panoramapublic.model.CatalogEntry;
import org.labkey.panoramapublic.model.DataLicense;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.view.publish.CatalogEntryWebPart;
import org.labkey.panoramapublic.view.publish.ShortUrlDisplayColumnFactory;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.labkey.api.util.DOM.Attribute.height;
import static org.labkey.api.util.DOM.Attribute.href;
import static org.labkey.api.util.DOM.Attribute.onclick;
import static org.labkey.api.util.DOM.Attribute.src;
import static org.labkey.api.util.DOM.Attribute.title;
import static org.labkey.api.util.DOM.Attribute.width;
import static org.labkey.api.util.DOM.IMG;
import static org.labkey.api.util.DOM.at;

/**
 * User: vsharma
 * Date: 12/19/13
 * Time: 2:29 PM
 */
public class ExperimentAnnotationsTableInfo extends FilteredTable<PanoramaPublicSchema>
{

    public ExperimentAnnotationsTableInfo(final PanoramaPublicSchema schema, ContainerFilter cf)
    {
        this(PanoramaPublicManager.getTableInfoExperimentAnnotations(), schema, cf);
    }

    public ExperimentAnnotationsTableInfo(TableInfo tableInfo, PanoramaPublicSchema schema, ContainerFilter cf)
    {
        super(tableInfo, schema, cf);

        wrapAllColumns(true);
        setDetailsURL(new DetailsURL(PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(getContainer())));

        var citationCol = getMutableColumn(FieldKey.fromParts("Citation"));
        citationCol.setDisplayColumnFactory(colInfo -> new PublicationLinkDisplayColumn(colInfo));
        citationCol.setURLTargetWindow("_blank");
        citationCol.setLabel("Publication");

        // Add column that combines the citation field with the submitter and lab head display names
        // to allow searches for author names in the Panorama Public search form
        addColumn(getAuthorsColumn());

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
                                ClientDependency.fromPath("internal/jQuery"),
                                ClientDependency.fromPath("Ext4"),
                                ClientDependency.fromPath("/PanoramaPublic/css/dropDown.css"),
                                ClientDependency.fromPath("/PanoramaPublic/js/dropDownUtil.js"));
                    }

                    @Override
                    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                    {
                        Integer id = ctx.get(_idKey, Integer.class);
                        Container container = ctx.get(_containerKey, Container.class);
                        if(id != null && container != null)
                        {
                            ActionURL detailsPage = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(container); // experiment container
                            DOM.SPAN(at(onclick, "viewExperimentDetails(this,'" + container.getPath() + "', '" + id + "','" + detailsPage + "')")
                                            .data("active", "false") // will be rendered as "data-active" attribute
                                            .data("loaded", "false"), // will be rendered as "data-loaded" attribute
                                    IMG(at(DOM.Attribute.id, "expandcontract-" + id)
                                            .at(src, PageFlowUtil.staticResourceUrl("_images/plus.gif"))),
                                    HtmlString.NBSP)
                                    .appendTo(out);
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
                        ClientDependency.fromPath("PanoramaPublic/css/ExperimentAnnotations.css"),
                        ClientDependency.fromPath("hopscotch/css/hopscotch.min.css"),
                        ClientDependency.fromPath("PanoramaPublic/js/ExperimentAnnotations.js"),
                        ClientDependency.fromPath("PanoramaPublic/js/clipboard.min.js"),
                        ClientDependency.fromPath("hopscotch/js/hopscotch.min.js")
                        );
            }

            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                // Get the ExperimentAnnotations record
                Integer experimentAnnotationsId = ctx.get(colInfo.getFieldKey(), Integer.class);
                ExperimentAnnotations expAnnotations = ExperimentAnnotationsManager.get(experimentAnnotationsId);

                String accessUrl = ExperimentAnnotationsManager.getExperimentShortUrl(expAnnotations);

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
        instrCol.setDisplayColumnFactory(colInfo -> new InstrumentColumn(colInfo, new ActionURL(PanoramaPublicController.CompleteInstrumentAction.class, getContainer()), true, "Enter Instrument"));
        instrCol.setDescription("One or more instruments are required for submitting data to ProteomeXchange.");

        var organismCol = getMutableColumn("Organism");
        organismCol.setDisplayColumnFactory(colInfo -> new OrganismColumn(colInfo, new ActionURL(PanoramaPublicController.CompleteOrganismAction.class, getContainer()), false, "Enter Organism"));
        organismCol.setDescription("One or more organisms are required for submitting data to ProteomeXchange.");

        SQLFragment runCountSQL = new SQLFragment("(SELECT COUNT(r.ExperimentRunId) FROM ");
        runCountSQL.append(ExperimentService.get().getTinfoRunList(), "r");
        runCountSQL.append(" WHERE r.ExperimentId = ");
        runCountSQL.append(ExprColumn.STR_TABLE_ALIAS);
        runCountSQL.append(".ExperimentId)");
        ExprColumn runCountColumn = new ExprColumn(this, "Runs", runCountSQL, JdbcType.INTEGER);
        runCountColumn.setLabel("Skyline Docs");
        addColumn(runCountColumn);

        var isPublicCol = wrapColumn("Public", getRealTable().getColumn("Id"));
        isPublicCol.setDisplayColumnFactory(colInfo -> new DataColumn(colInfo)
        {
            @Override
            public Object getValue(RenderContext ctx)
            {
                Integer experimentAnnotationsId = ctx.get(colInfo.getFieldKey(), Integer.class);
                ExperimentAnnotations expAnnotations = ExperimentAnnotationsManager.get(experimentAnnotationsId);
                if(expAnnotations != null)
                {
                    return expAnnotations.isPublic() ? "Yes" : "No";
                }
                return "Row not found in ExperimentAnnotations for id " + experimentAnnotationsId;
            }
            @Override
            public Object getDisplayValue(RenderContext ctx)
            {
                return getValue(ctx);
            }
            @Override
            public @NotNull HtmlString getFormattedHtml(RenderContext ctx)
            {
                return HtmlString.of((String)getValue(ctx));
            }
            @Override
            public Class getValueClass()
            {
                return String.class;
            }
            @Override
            public Class getDisplayValueClass()
            {
                return String.class;
            }

            @Override
            public boolean isFilterable()
            {
                return false;
            }

            @Override
            public boolean isSortable()
            {
                return false;
            }
        });
        addColumn(isPublicCol);

        var licenseCol = wrapColumn("Data License", getRealTable().getColumn("Id"));
        licenseCol.setURLTargetWindow("_blank");
        licenseCol.setDisplayColumnFactory(colInfo -> new DataColumn(colInfo)
        {
            @Override
            public Object getValue(RenderContext ctx)
            {
                Integer experimentAnnotationsId = ctx.get(colInfo.getFieldKey(), Integer.class);
                ExperimentAnnotations expAnnotations = ExperimentAnnotationsManager.get(experimentAnnotationsId);
                return expAnnotations != null ? expAnnotations.getDataLicense() : null;
            }

            @Override
            public Object getDisplayValue(RenderContext ctx)
            {
                DataLicense license = (DataLicense) getValue(ctx);
                return license != null ? license.getDisplayName() : "";
            }

            @Override
            public @NotNull HtmlString getFormattedHtml(RenderContext ctx)
            {
                return HtmlString.of((String) getDisplayValue(ctx));
            }

            @Override
            public String renderURL(RenderContext ctx)
            {
                DataLicense license = (DataLicense) getValue(ctx);
                return license != null ? license.getUrl() : null;
            }
            @Override
            public Class getValueClass()
            {
                return String.class;
            }
            @Override
            public Class getDisplayValueClass()
            {
                return String.class;
            }
        });
        addColumn(licenseCol);

        var sourceExptCol = wrapColumn("SourceExperiment", getRealTable().getColumn("SourceExperimentId"));
        ActionURL exptDetailsUrl = new ActionURL(PanoramaPublicController.ShowExperimentAnnotationsAction.class, getContainer());
        exptDetailsUrl.addParameter("id", "${SourceExperiment}");
        sourceExptCol.setURL(StringExpressionFactory.createURL(exptDetailsUrl));
        addColumn(sourceExptCol);

        var accessUrlCol = wrapColumn("Link", getRealTable().getColumn("ShortUrl"));
        accessUrlCol.setDisplayColumnFactory(new ShortUrlDisplayColumnFactory());
        addColumn(accessUrlCol);

        addColumn(getVersionCol());
        addColumn(getVersionCountCol());
        getMutableColumn("CreatedBy").setFk(new UserIdQueryForeignKey(schema));
        getMutableColumn("ModifiedBy").setFk(new UserIdQueryForeignKey(schema));

        ExprColumn catalogEntryCol = getCatalogEntryCol();
        catalogEntryCol.setDisplayColumnFactory(colInfo -> new CatalogEntryIconColumn(colInfo));
        addColumn(catalogEntryCol);

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

    private ExprColumn getAuthorsColumn()
    {
        // Concatenate the citation column with the full name of the submitter and the lab head associated with the experiment.
        // Users selected as submitter and lab head for experiments submitted to Panorama Public will always have a first and
        // last name. Do not use the displayname since it may not always contain their first or last names.
        // This column will not be included in the list of default columns.  It is added only to enable expanded searches for author names
        // in the Panorama Public search form.
        SqlDialect dialect = PanoramaPublicSchema.getSchema().getSqlDialect();
        SQLFragment usersSql = new SQLFragment(" SELECT ")
                .append(dialect.concatenate(new SQLFragment(" COALESCE(firstname, '') "), new SQLFragment("' '"), new SQLFragment(" COALESCE(lastname, '') ")))
                .append(" FROM ").append(CoreSchema.getInstance().getTableInfoUsersData(), "users")
                .append(" WHERE ")
                .append(" users.userid = ").append(ExprColumn.STR_TABLE_ALIAS).append(".submitter")
                .append(" OR")
                .append(" users.userid = ").append(ExprColumn.STR_TABLE_ALIAS).append(".labhead");
        SQLFragment authorsSql = dialect.concatenate(
                new SQLFragment(" (SELECT ").append(dialect.getSelectConcat(usersSql, ",")).append(") "),
                new SQLFragment("','"),
                new SQLFragment(" (COALESCE(").append(ExprColumn.STR_TABLE_ALIAS).append(".citation, '')) ")
                );
        return new ExprColumn(this, "Authors", authorsSql, JdbcType.VARCHAR);
    }

    @NotNull
    private ExprColumn getVersionCol()
    {
        SQLFragment maxVersionSql = new SQLFragment(" SELECT MAX(DataVersion) FROM ")
                .append(PanoramaPublicManager.getTableInfoExperimentAnnotations(), "ea")
                .append(" WHERE ea.SourceExperimentId IS NOT NULL AND ea.SourceExperimentId = ")
                .append(ExprColumn.STR_TABLE_ALIAS).append(".SourceExperimentId ");

        SQLFragment versionSql = new SQLFragment(" (SELECT CASE")
                .append(" WHEN DataVersion Is NULL THEN '' ")
                .append(" WHEN DataVersion = (").append(maxVersionSql).append(") THEN 'Current' ")
                .append(" ELSE CAST (DataVersion AS VARCHAR) END ")
                .append(" FROM ").append(PanoramaPublicManager.getTableInfoExperimentAnnotations(), "e")
                .append(" WHERE e.Id = ").append(ExprColumn.STR_TABLE_ALIAS).append(".Id) ");

        ExprColumn versionCol = new ExprColumn(this, "Version", versionSql, JdbcType.VARCHAR);
        return versionCol;
    }

    @NotNull
    private ExprColumn getVersionCountCol()
    {
        SQLFragment versionCountSql = new SQLFragment(" (SELECT COUNT(*) FROM ")
                .append(PanoramaPublicManager.getTableInfoExperimentAnnotations(), "e")
                .append(" WHERE e.SourceExperimentId = ").append(ExprColumn.STR_TABLE_ALIAS).append(".SourceExperimentId)");

        ExprColumn versionCountCol = new ExprColumn(this, "VersionCount", versionCountSql, JdbcType.INTEGER);
        ActionURL allVersionsLink = new ActionURL(PanoramaPublicController.ShowPublishedVersions.class, getContainer());
        allVersionsLink.addParameter("id", "${Id}");
        versionCountCol.setURL(StringExpressionFactory.createURL(allVersionsLink));
        return versionCountCol;
    }

    private ExprColumn getCatalogEntryCol()
    {
        SQLFragment catalogEntrySql = new SQLFragment(" (SELECT entry.id AS CatalogEntryId ")
                .append(" FROM ").append(PanoramaPublicManager.getTableInfoCatalogEntry(), "entry")
                .append(" WHERE ")
                .append(" entry.shortUrl = ").append(ExprColumn.STR_TABLE_ALIAS).append(".shortUrl")
                .append(") ");
        ExprColumn col = new ExprColumn(this, "CatalogEntry", catalogEntrySql, JdbcType.INTEGER);
        col.setDescription("Add or view the catalog entry for the experiment");
        return col;
    }

    @Override
    public String getName()
    {
        return PanoramaPublicSchema.TABLE_EXPERIMENT_ANNOTATIONS;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm);
    }

    public static class ExperimentUserForeignKey extends UserIdQueryForeignKey
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
            super(userSchema, true); // Include all users so that admins can get the email address etc. of submitters and lab heads
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
            // CONSIDER:  Use this instead? SecurityManager.getUsersWithPermissions(container, Collections.singleton(AdminPermission.class));
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
            else
            {
                // This could be a permissions group.  Add the group members
                Group group = SecurityManager.getGroup(userId);
                if (group != null)
                {
                    // Members of the group and sub-groups (not including "All Site Users")
                    Set<User> grpMembers = SecurityManager.getAllGroupMembers(group, MemberType.ACTIVE_USERS, false);
                    for (User member: grpMembers)
                    {
                        objectList.put(new SimpleNamedObject(String.valueOf(member.getUserId()), getUserDisplayName(member)));
                    }
                }
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
        public @NotNull HtmlString getFormattedHtml(RenderContext ctx)
        {
            Integer userId = ctx.get(getColumnInfo().getFieldKey(), Integer.class);
            String userDisplayName = null;
            if(userId != null)
            {
                userDisplayName = getUserDisplayName(UserManager.getUser(userId));
            }
            return userDisplayName == null ? super.getFormattedHtml(ctx) : HtmlString.of(userDisplayName);
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
            displayName = u.getFullName() + (!u.getFullName().equals(displayName) ? " (" + displayName + ")" : "");
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
        public HtmlString getFormattedHtml(RenderContext ctx)
        {
            return HtmlString.of(getValue(ctx));
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
        public HtmlString getFormattedHtml(RenderContext ctx)
        {
            return HtmlString.of(getValue(ctx));
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
            _autoCompletionUrl = PageFlowUtil.jsString(prefetch ? autocompletionUrl: autocompletionUrl.addParameter("token", "__QUERY__"));
            _prefetch = prefetch;
            _placeholderText = placeHolderText;
        }

        @Override
        public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
        {
            String name = getFormFieldName(ctx);
            String valueString = getStringValue(value, isDisabledInput(ctx));
            if (valueString == null)
            {
                valueString = "";
            }

            String renderId = getRenderId();
            StringBuilder sb = new StringBuilder();

            sb.append("<script type=\"text/javascript\">");
            sb.append("LABKEY.requiresScript([\"/PanoramaPublic/js/ExpAnnotAutoComplete.js\"], function() {\n");
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

        @NotNull
        String getRenderId()
        {
            return "input-picker-div-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
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

        @Override
        String getRenderId()
        {
            return "input-picker-div-organism";
        }
    }

    private static class InstrumentColumn extends AutoCompleteColumn
    {

        public InstrumentColumn(ColumnInfo col, ActionURL autocompletionUrl, boolean prefetch, String placeHolderText)
        {
            super(col, autocompletionUrl, prefetch, placeHolderText);
        }

        @Override
        String getRenderId()
        {
            return "input-picker-div-instrument";
        }
    }

    public static class CatalogEntryIconColumn extends DataColumn
    {
        public CatalogEntryIconColumn(ColumnInfo col)
        {
            super(col);
            super.setCaption("Catalog Entry");
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            User user = ctx.getViewContext().getUser();
            if (user == null || user.isGuest())
            {
                HtmlString.NBSP.appendTo(out);
                return;
            }
            Integer catalogEntryId = ctx.get(getColumnInfo().getFieldKey(), Integer.class);

            // Get the experiment connected with this catalog entry.
            Integer experimentId = ctx.get(FieldKey.fromParts("id"), Integer.class);
            if (experimentId != null)
            {
                ExperimentAnnotations expAnnot = ExperimentAnnotationsManager.get(experimentId);
                // Display the catalog entry link only if the user has the required permissions (Admin or PanoramaPublicSubmitter) in the the experiment folder.
                if (expAnnot != null && CatalogEntryWebPart.canBeDisplayed(expAnnot, user))
                {
                    CatalogEntry entry = catalogEntryId == null ? null : CatalogEntryManager.get(catalogEntryId);
                    String imageUrl = entry != null ? AppProps.getInstance().getContextPath() + "/PanoramaPublic/images/slideshow-icon-green.png"
                                                    : AppProps.getInstance().getContextPath() + "/PanoramaPublic/images/slideshow-icon.png";
                    String imageTitle = entry != null ? "View catalog entry" : "Add catalog entry";
                    ActionURL returnUrl = ctx.getViewContext().getActionURL().clone();
                    ActionURL catalogEntryLink = entry != null ? PanoramaPublicController.getViewCatalogEntryUrl(expAnnot, entry).addReturnURL(returnUrl)
                                                               : PanoramaPublicController.getAddCatalogEntryUrl(expAnnot).addReturnURL(returnUrl);
                    DOM.A(at(href, catalogEntryLink.getLocalURIString(), title, PageFlowUtil.filter(imageTitle)),
                            DOM.IMG(at(src, imageUrl, height, 22, width, 22)))
                            .appendTo(out);
                    return;
                }
            }
            HtmlString.EMPTY_STRING.appendTo(out);
        }
    }
}

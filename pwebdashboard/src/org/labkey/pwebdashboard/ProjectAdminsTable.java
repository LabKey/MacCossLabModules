package org.labkey.pwebdashboard;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerTable;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.WrappedColumn;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.Group;
import org.labkey.api.security.MemberType;
import org.labkey.api.security.PrincipalType;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.UserUrls;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Link;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by vsharma on 12/8/2016.
 */
public class ProjectAdminsTable extends ContainerTable
{
    public static final String NAME = "ProjectAdmins";

    public ProjectAdminsTable(@NotNull PwebDashboardSchema userSchema, ContainerFilter cf)
    {
        super(userSchema, cf);

        setName(NAME);

        BaseColumnInfo projectCol = new WrappedColumn(getMutableColumn("EntityId"), "Project");
        ContainerForeignKey.initColumn(projectCol, userSchema);
        addColumn(projectCol);


        SQLFragment projectAdminsSql = new SQLFragment("(SELECT ");
        projectAdminsSql.append(getSqlDialect().getGroupConcat(new SQLFragment("p.Name"), true, false))
                .append(" AS Admins FROM ").append(CoreSchema.getInstance().getTableInfoRoleAssignments(), "ra")
                .append(" INNER JOIN ").append(CoreSchema.getInstance().getTableInfoPrincipals(), "p")
                .append(" ON (ra.UserId = p.UserId)")
                .append(" WHERE ra.ResourceId = ").append(ExprColumn.STR_TABLE_ALIAS).append(".EntityId AND ra.Role = ?)");
        projectAdminsSql.add(RoleManager.getRole(ProjectAdminRole.class).getUniqueName());
        ExprColumn projectAdminsCol = new ExprColumn(this, "ProjectAdminsSearchable", projectAdminsSql, JdbcType.VARCHAR);
        projectAdminsCol.setReadOnly(true);
        addColumn(projectAdminsCol);


        BaseColumnInfo adminCol = new WrappedColumn(getMutableColumn("EntityId"), "ProjectAdmins");
        addColumn(adminCol);
        adminCol.setDisplayColumnFactory(colInfo -> new AdminsColumn(colInfo));

        List<FieldKey> visibleColumns = new ArrayList<>();
        visibleColumns.add(FieldKey.fromParts("Created"));
        visibleColumns.add(FieldKey.fromParts("CreatedBy"));
        visibleColumns.add(FieldKey.fromParts("Project"));
        visibleColumns.add(FieldKey.fromParts("ProjectAdmins"));
        setDefaultVisibleColumns(visibleColumns);

        // Only 'project' containers.
        Container root = ContainerManager.getRoot();
        SQLFragment sql = new SQLFragment("(Parent = ? AND CreatedBy IS NOT NULL)");
        sql.add(root.getEntityId());
        addCondition(sql);
    }

    class AdminsColumn extends DataColumn
    {
        public AdminsColumn(ColumnInfo col)
        {
            super(col);
        }

        @Override
        public Object getValue(RenderContext ctx)
        {
            String entityId = ctx.get(getColumnInfo().getFieldKey(), String.class);
            Container container = ContainerManager.getForId(entityId);

            if (container != null)
            {
                return getCommaSeparatedAdmins(container);
            }

            return null;
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
            String value = h(getValue(ctx));
            if(value != null) value = value.replaceAll(",", "<br />");
            return value != null ? value : "";
        }

        @Nullable
        private String getCommaSeparatedAdmins(Container container)
        {
            List<UserPrincipal> allAdmins = getAdmins(container);

            StringBuilder display = new StringBuilder();

            String space = "";

            if (allAdmins.size() > 0)
            {
                for(UserPrincipal user: allAdmins)
                {
                    display.append(space);
                    display.append(user.getName());
                    space = ", ";

                }

                return display.toString();
            }
            return null;
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            String entityId = ctx.get(getColumnInfo().getFieldKey(), String.class);
            Container container = ContainerManager.getForId(entityId);

            if (container != null)
            {
                List<UserPrincipal> allAdmins = getAdmins(container);

                StringBuilder display = new StringBuilder();

                String space = "";

                if (allAdmins.size() > 0)
                {
                    for(UserPrincipal user: allAdmins)
                    {
                        display.append(space);
                        display.append(getUserDetails(container, user));
                        space = ",&nbsp;";

                    }

                    out.write(display.toString());
                    return;
                }
            }
            else
            {
                out.write("");
            }
        }

        @NotNull
        private List<UserPrincipal> getAdmins(Container container)
        {
            List<UserPrincipal> allAdmins = new ArrayList<>();

            SecurityPolicy policy = SecurityPolicyManager.getPolicy(container);
            Role projectAdminRole = RoleManager.getRole(ProjectAdminRole.class);
            Role siteAdminRole = RoleManager.getRole(SiteAdminRole.class);
            Map<String, Map<PrincipalType, List<UserPrincipal>>> roleMap = policy.getAssignmentsAsMap();

            for (String roleName : roleMap.keySet())
            {
                Role role = RoleManager.getRole(roleName);
                if (role == null || (!(role.equals(projectAdminRole) || role.equals(siteAdminRole))))
                {
                    continue;
                }
                Map<PrincipalType, List<UserPrincipal>> projAdmins = roleMap.get(roleName);
                for (PrincipalType pType : projAdmins.keySet())
                {
                    if (pType.equals(PrincipalType.USER))
                    {
                        allAdmins.addAll(projAdmins.get(pType));
                    }
                    else if (pType.equals(PrincipalType.GROUP))
                    {
                        List<UserPrincipal> adminGroups = projAdmins.get(pType);
                        for(UserPrincipal group: adminGroups)
                        {
                            Group grp = SecurityManager.getGroup(group.getUserId());
                            Set<User> grpMembers = SecurityManager.getAllGroupMembers(grp, MemberType.ACTIVE_USERS);
                            allAdmins.addAll(grpMembers);
                        }
                    }
                }
            }
            return allAdmins;
        }

        private HtmlString getUserDetails(Container container, UserPrincipal member)
        {
            ActionURL url = PageFlowUtil.urlProvider(UserUrls.class).getUserDetailsURL(container, member.getUserId(), null);
            return new Link.LinkBuilder(member.getName()).href(url).clearClasses().build().getHtmlString();
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
    }
}

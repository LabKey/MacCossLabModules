package org.labkey.panoramapublic.query.speclib;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.RowIdQueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.HtmlString;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.PanoramaPublicSchema;
import org.labkey.panoramapublic.model.speclib.SpecLibInfo;
import org.labkey.panoramapublic.query.ContainerJoin;
import org.labkey.panoramapublic.query.PanoramaPublicTable;
import org.labkey.panoramapublic.query.SpecLibInfoManager;

public class SpecLibInfoTableInfo extends PanoramaPublicTable
{
    public SpecLibInfoTableInfo(PanoramaPublicSchema schema, ContainerFilter cf)
    {
        super(PanoramaPublicManager.getTableInfoSpecLibInfo(), schema, cf, ContainerJoin.ExpAnnotJoin);

        var dependencyTypeCol = getMutableColumn("DependencyType");
        if (dependencyTypeCol != null)
        {
            dependencyTypeCol.setFk(new LookupForeignKey()
            {
                @Override
                public TableInfo getLookupTableInfo()
                {
                    return getUserSchema().getTable(PanoramaPublicSchema.TABLE_LIB_DEPENDENCY_TYPE, cf);
                }
            });
        }

        var sourceTypeCol = getMutableColumn("SourceType");
        if (sourceTypeCol != null)
        {
            sourceTypeCol.setFk(new LookupForeignKey()
            {
                @Override
                public TableInfo getLookupTableInfo()
                {
                    return getUserSchema().getTable(PanoramaPublicSchema.TABLE_LIB_SOURCE_TYPE, cf);
                }
            });
        }

        var sourcePasswordCol = getMutableColumn("SourcePassword");
        if (sourcePasswordCol != null)
        {
            sourcePasswordCol.setDisplayColumnFactory(new PasswordDisplayColumnFactory());
        }
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new SpecLibInfoQueryUpdateService(this);
    }

    public static class PasswordDisplayColumnFactory implements DisplayColumnFactory
    {
        @Override
        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            return new DataColumn(colInfo)
            {
                @Override
                public Object getValue(RenderContext ctx)
                {
                    if (ctx.getViewContext().getUser().isInSiteAdminGroup())
                    {
                        // Show the password only to site admins
                        return super.getValue(ctx);
                    }
                    return "********";
                }

                @Override
                public Object getDisplayValue(RenderContext ctx)
                {
                    return getValue(ctx);
                }

                @Override
                public @NotNull HtmlString getFormattedHtml(RenderContext ctx)
                {
                    return HtmlString.of(getValue(ctx));
                }
            };
        }
    }

    // Update service allows row deletion but not insert or edit
    public static class SpecLibInfoQueryUpdateService extends RowIdQueryUpdateService<SpecLibInfo>
    {
        public SpecLibInfoQueryUpdateService(SpecLibInfoTableInfo tableInfo)
        {
            super(tableInfo);
        }

        @Override
        protected SpecLibInfo createNewBean()
        {
            return new SpecLibInfo();
        }

        @Override
        public SpecLibInfo get(User user, Container container, int key)
        {
            return new TableSelector(PanoramaPublicManager.getTableInfoSpecLibInfo()).getObject(key, SpecLibInfo.class);
        }

        @Override
        public void delete(User user, Container container, int key)
        {
            SpecLibInfoManager.deleteSpecLibInfo(key, container);
        }

        @Override
        protected SpecLibInfo insert(User user, Container container, SpecLibInfo bean) throws ValidationException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected SpecLibInfo update(User user, Container container, SpecLibInfo bean, Integer oldKey) throws ValidationException
        {
            throw new UnsupportedOperationException();
        }
    }
}

package org.labkey.panoramapublic.query.modification;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.RowIdQueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.PanoramaPublicSchema;
import org.labkey.panoramapublic.query.ContainerJoin;
import org.labkey.panoramapublic.query.PanoramaPublicTable;

public class ExperimentIsotopeModInfoTableInfo extends PanoramaPublicTable
{
    public ExperimentIsotopeModInfoTableInfo(PanoramaPublicSchema schema, ContainerFilter cf)
    {
        super(PanoramaPublicManager.getTableInfoExperimentIsotopeModInfo(), schema, cf, ContainerJoin.ExpAnnotJoin);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new RowIdQueryUpdateService<ExperimentModInfo>(this)
        {
            @Override
            protected ExperimentModInfo createNewBean()
            {
                return new ExperimentModInfo();
            }

            @Override
            public ExperimentModInfo get(User user, Container container, int key)
            {
                return new TableSelector(PanoramaPublicManager.getTableInfoExperimentIsotopeModInfo()).getObject(key, ExperimentModInfo.class);
            }

            @Override
            public void delete(User user, Container container, int key)
            {
                Table.delete(PanoramaPublicManager.getTableInfoExperimentIsotopeModInfo(), key);
            }

            @Override
            protected ExperimentModInfo insert(User user, Container container, ExperimentModInfo bean)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            protected ExperimentModInfo update(User user, Container container, ExperimentModInfo bean, Integer oldKey)
            {
                throw new UnsupportedOperationException();
            }
        };
    }
}

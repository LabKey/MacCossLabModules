package org.labkey.nextflow;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.SpringModule;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.WebPartFactory;

import java.util.Collection;
import java.util.List;

public class NextFlowModule extends SpringModule
{
    @Override
    protected void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        ActionURL adminUrl = new ActionURL(NextFlowController.NextFlowConfigurationAction.class, ContainerManager.getRoot());
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Configuration, "NextFlow Configuration", adminUrl, AdminPermission.class);
    }

    @Override
    protected void init()
    {
        addController(NextFlowController.NAME, NextFlowController.class);
    }

    @Override
    protected @NotNull Collection<? extends WebPartFactory> createWebPartFactories()
    {
        return List.of();
    }

    @Override
    public boolean hasScripts()
    {
        return false;
    }

    @Override
    public @NotNull Collection<String> getSchemaNames()
    {
        return List.of();
    }

}

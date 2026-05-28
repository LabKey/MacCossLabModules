/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.nextflow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.SpringModule;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.WebPartFactory;
import org.labkey.nextflow.pipeline.NextFlowPipelineProvider;

import java.util.Collection;
import java.util.List;

public class NextFlowModule extends SpringModule
{
    @Override
    protected void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        ActionURL adminUrl = new ActionURL(NextFlowController.NextFlowConfigurationAction.class, ContainerManager.getRoot());
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Configuration, "NextFlow Configuration", adminUrl, AdminPermission.class);

        PipelineService.get().registerPipelineProvider(new NextFlowPipelineProvider(this));
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
        return true;
    }

    @Override
    public @Nullable Double getSchemaVersion()
    {
        return 26.000;
    }

    @Override
    public @NotNull Collection<String> getSchemaNames()
    {
        return List.of(NextFlowManager.SCHEMA_NAME);
    }
}

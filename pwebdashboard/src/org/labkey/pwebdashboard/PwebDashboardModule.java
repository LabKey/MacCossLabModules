/*
 * Copyright (c) 2016 LabKey Corporation
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

package org.labkey.pwebdashboard;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.SpringModule;
import org.labkey.api.view.WebPartFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class PwebDashboardModule extends SpringModule
{
    public static final String NAME = "PanoramaWebDashboard";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public Double getSchemaVersion()
    {
        return null;
    }

    @Override
    public boolean hasScripts()
    {
        return false;
    }

    @Override
    public boolean getRequireSitePermission()
    {
        // Only site admins should be able to add the module.
        return true;
    }

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    @Override
    protected void init()
    {
        PwebDashboardSchema.register(this);
    }

    @Override
    public void startupAfterSpringConfig(ModuleContext moduleContext) { }

    @Override
    @NotNull
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return Collections.singleton(PwebDashboardSchema.NAME);
    }
}
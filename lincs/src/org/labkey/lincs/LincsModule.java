/*
 * Copyright (c) 2015-2017 LabKey Corporation
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

package org.labkey.lincs;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.lincs.view.LincsDataView;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class LincsModule extends DefaultModule
{
    public static final String NAME = "LINCS";

    @Override
    public String getName()
    {
        return NAME;
    }

    public double getVersion()
    {
        return 18.10;
    }

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        //return Collections.emptyList();
        BaseWebPartFactory runsList = new BaseWebPartFactory(LincsDataView.WEB_PART_NAME)
        {
            @Override
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                return new LincsDataView(portalCtx);
            }
        };
        return Collections.singleton(runsList);
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    protected void init()
    {
        addController(LincsController.NAME, LincsController.class);
        LincsSchema.register(this);
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(LincsManager.get().getSchemaName());
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new LincsContainerListener());
    }

    @Override
    @NotNull
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }
}
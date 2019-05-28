/*
 * Copyright (c) 2013 LabKey Corporation
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

package org.labkey.passport;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.SpringModule;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.passport.view.ProteinListView;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class PassportModule extends SpringModule
{
    public static final String CONTROLLER_NAME = "passport";

    @Override
    public String getName()
    {
        return "Passport";
    }

    @Override
    public double getVersion()
    {
        return 18.01;
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories() {
        BaseWebPartFactory paretoPlotFactory = new BaseWebPartFactory("Passport")
        {
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                QueryView v =  ProteinListView.createView(portalCtx);
                v.setTitle("Passport");
                v.setFrame(WebPartView.FrameType.PORTAL);
                return v;
            }
        };
        return Collections.singletonList(paretoPlotFactory);
    }

    @Override
    protected void init()
    {
        addController(CONTROLLER_NAME, PassportController.class);
    }

    @Override
    protected void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new PassportContainerListener());
    }

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
        return Collections.singleton("passport");
    }
}
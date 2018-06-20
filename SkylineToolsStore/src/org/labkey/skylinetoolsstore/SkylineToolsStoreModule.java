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

package org.labkey.skylinetoolsstore;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartConfigurationException;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.skylinetoolsstore.model.SkylineTool;
import org.labkey.skylinetoolsstore.view.SkylineToolsStoreWebPart;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Set;

public class SkylineToolsStoreModule extends DefaultModule
{
    public static final String CONTROLLER_NAME = "skyts";

    @Override
    public String getName()
    {
        return "SkylineToolsStore";
    }

    @Override
    public double getVersion()
    {
        return 16.2;
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<WebPartFactory>(
            Arrays.asList(
                    new BaseWebPartFactory("Skyline Tool Store") {
                        {
                            addLegacyNames("Skyline Tools Store");
                        }
                        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws WebPartConfigurationException
                        {
                            return new SkylineToolsStoreWebPart();
                        }
                    }
            ));
    }

    @Override
    protected void init()
    {
        addController(CONTROLLER_NAME, SkylineToolsStoreController.class, "skylinets", "skylinetoolsstore");
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new SkylineToolsStoreContainerListener());
    }

    @Override
    @NotNull
    public Collection<String> getSummary(Container c)
    {
        SkylineTool[] tools = SkylineToolsStoreManager.get().getTools(c);
        if (tools != null && tools.length > 0)
        {
            Collection<String> list = new LinkedList<>();
            list.add("SkylineToolStore Module: " + tools.length + " tool records.");
            return list;
        }
        return Collections.emptyList();
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return Collections.singleton("skylinetoolsstore");
    }
}
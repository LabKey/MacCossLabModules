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
import org.labkey.api.data.DbSchema;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.User;
import org.labkey.api.view.WebPartFactory;
import org.labkey.passport.view.PassportWebPart;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class PassportModule extends DefaultModule
{
    public static final String CONTROLLER_NAME = "passport";
    public static final WebPartFactory _passportFactory = new PassportWebPart();

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
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.singletonList(_passportFactory);
    }

    @Override
    protected void init()
    {
        addController(CONTROLLER_NAME, PassportController.class);
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new PassportContainerListener());
    }

    @Override
    @NotNull
    public Collection<String> getSummary(Container c, User user)
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
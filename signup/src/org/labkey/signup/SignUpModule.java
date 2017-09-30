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

package org.labkey.signup;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.UserManager;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class SignUpModule extends DefaultModule
{
    public static final String SIGNUP_CATEGORY = "signup";
    public static final String SIGNUP_GROUP_NAME = "groupId";
    public static final String SIGNUP_GROUP_TO_GROUP = "groupToGroup";

    @Override
    public String getName()
    {
        return "SignUp";
    }

    @Override
    public double getVersion()
    {
        return 13.24;
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
        BaseWebPartFactory signupWebpart = new BaseWebPartFactory("Sign Up")
        {
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart)
            {
                JspView<SignUpController.SignupForm> view = new JspView("/org/labkey/signup/signupPage.jsp", new SignUpController.SignupForm());
                view.setTitle("Sign Up");

                return view;
            }
        };

        List<WebPartFactory> webpartFactoryList = new ArrayList<>();
        webpartFactoryList.add(signupWebpart);
        return webpartFactoryList;
    }

    @Override
    protected void init()
    {
        addController("signup", SignUpController.class);
        SignUpSchema.register(this);
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new SignUpListener());
        UserManager.addUserListener(new SignUpListener());

        // Add a link in the admin console
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Configuration, "SignUp", SignUpController.getShowSignUpAdminUrl());
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
        return Collections.singleton("signup");
    }

    @Override
    public boolean getRequireSitePermission()
    {
        return true;
    }

}
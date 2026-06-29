/*
 * Copyright (c) 2015 LabKey Corporation
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

package org.labkey.testresults;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.Directive;
import org.labkey.api.security.SecurityManager;
import org.labkey.filters.ContentSecurityPolicyFilter;
import org.labkey.api.view.WebPartFactory;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * User: Yuval Boss, yuval(at)uw.edu
 * Date: 1/14/2015
 */

public class TestResultsModule extends DefaultModule
{
    private static final Logger LOG = LogHelper.getLogger(TestResultsModule.class, "Test results module");

    public static final WebPartFactory _testResultsFactory = new TestResultsWebPart();
    public static final String JOB_NAME = "TestResultsEmailTrigger";
    public static final String JOB_GROUP = "TestResultsGroup";

    // for saving
    public static final String TR_VIEW = "testresults-view";

    public interface ViewType {
        String DAY = "day";
        String WEEK= "wk";
        String MONTH = "mo";
        String YEAR = "yr";
        String ALLTIME = "at";
    }

    @Override
    public String getName()
    {
        return "TestResults";
    }

    @Override
    public @Nullable Double getSchemaVersion()
    {
        return 13.401;
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
        return Collections.singletonList(_testResultsFactory);
    }

    @Override
    protected void init()
    {
        addController("testresults", TestResultsController.class);
        TestResultsSchema.register(this);
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new TestResultsContainerListener());
        SecurityManager.registerAllowedConnectionSource("jquery-ui", "https://code.jquery.com/ui/1.13.2/jquery-ui.min.js");
        // jQuery UI CSS and its background images are loaded from code.jquery.com.
        // Register for style-src and img-src so they are not blocked by CSP.
        ContentSecurityPolicyFilter.registerAllowedSources("jquery-ui-css", Directive.Style, "code.jquery.com");
        ContentSecurityPolicyFilter.registerAllowedSources("jquery-ui-images", Directive.Image, "code.jquery.com");
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
        return Collections.singleton("testresults");
    }

    @Override
    @NotNull
    public void startBackgroundThreads()
    {
        try
        {
            Scheduler scheduler = new StdSchedulerFactory().getScheduler();
            JobKey jobKeyEmail = new JobKey(JOB_NAME, JOB_GROUP);
            TestResultsController.SetEmailCronAction.start(scheduler, jobKeyEmail);
        }
        catch (SchedulerException e)
        {
            LOG.error("Failed to start the test results email scheduler", e);
        }

    }
}
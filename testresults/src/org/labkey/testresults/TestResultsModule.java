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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
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
    public double getVersion()
    {
        return 13.33;
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
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new TestResultsContainerListener());
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
            e.printStackTrace();
        }

    }
}
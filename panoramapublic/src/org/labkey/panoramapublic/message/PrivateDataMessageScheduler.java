package org.labkey.panoramapublic.message;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.User;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.panoramapublic.pipeline.PrivateDataReminderJob;
import org.quartz.DateBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

public class PrivateDataMessageScheduler
{
    private static final Logger _log = LogHelper.getLogger(PrivateDataMessageScheduler.class, "Panorama Public private data reminder message scheduler");

    private static final PrivateDataMessageScheduler _instance = new PrivateDataMessageScheduler();

    public static PrivateDataMessageScheduler getInstance()
    {
        return _instance;
    }

    private PrivateDataMessageScheduler(){}

    public void initializeTimer()
    {
        try
        {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

            // Get configured quartz Trigger
            Trigger trigger = getTrigger();

            // Create a quartz job that invokes a pipeline job that posts private data reminder messages
            JobDetail job = JobBuilder.newJob(PrivateDataMessageSchedulerJob.class)
                    .withIdentity(PrivateDataMessageSchedulerJob.class.getCanonicalName())
                    .build();

            // TODO: Add this PrivateDataMessageScheduler instance to the Job context so the Job knows which digest to send
            // job.getJobDataMap().put(MESSAGE_SCHEDULER_KEY, this);

            // Schedule trigger to execute the message digest job on the configured schedule
            scheduler.scheduleJob(job, trigger);
        }
        catch (SchedulerException e)
        {
            throw new RuntimeException("Failed to schedule PrivateDataMessageScheduler job", e);
        }
    }

    protected Trigger getTrigger()
    {
        // 1st of every month at 8:00AM
//        return TriggerBuilder.newTrigger()
//                .withSchedule(CronScheduleBuilder.monthlyOnDayAndHourAndMinute(1, 8, 0))
//                .build();
        return TriggerBuilder.newTrigger()
                .withSchedule(SimpleScheduleBuilder.repeatMinutelyForever(1))
                .startAt(DateBuilder.futureDate(5, DateBuilder.IntervalUnit.SECOND))
                .build();
    }

    public static class PrivateDataMessageSchedulerJob implements Job
    {
        private final @Nullable User _user;

        @SuppressWarnings("unused")
        public PrivateDataMessageSchedulerJob()
        {
            this(null);
        }

        public PrivateDataMessageSchedulerJob(@Nullable User user)
        {
            _user = user;
        }

        @Override
        public void execute(JobExecutionContext context)
        {
            try
            {
                Container c = ContainerManager.getRoot();
                ViewBackgroundInfo vbi = new ViewBackgroundInfo(c, _user, null);
                PipeRoot root = PipelineService.get().findPipelineRoot(c);

                if (root == null || !root.isValid())
                {
                    throw new ConfigurationException("No valid pipeline root found in the root container");
                }


                PipelineJob job = new PrivateDataReminderJob(vbi, PipelineService.get().getPipelineRootSetting(ContainerManager.getRoot()), false);
                PipelineService.get().queueJob(job);
            }
            catch(Exception e)
            {
                _log.error("Error queuing PrivateDataReminderJob", e); // TODO: Anything else?
                // ExceptionUtil.logExceptionToMothership(null, e);

            }
        }
    }
}

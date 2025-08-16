package org.labkey.panoramapublic.message;

import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.User;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.panoramapublic.model.Journal;
import org.labkey.panoramapublic.pipeline.PrivateDataReminderJob;
import org.labkey.panoramapublic.query.JournalManager;
import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;

public class PrivateDataMessageScheduler
{
    private static final Logger _log = LogHelper.getLogger(PrivateDataMessageScheduler.class, "Panorama Public private data reminder message scheduler");

    private static final TriggerKey TRIGGER_KEY = new TriggerKey(PrivateDataMessageScheduler.class.getCanonicalName());

    private static final PrivateDataMessageScheduler _instance = new PrivateDataMessageScheduler();

    public static PrivateDataMessageScheduler getInstance()
    {
        return _instance;
    }

    private PrivateDataMessageScheduler(){}

    public void initialize(boolean enable)
    {
        try
        {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

            // Clear previous job, if present
            if (scheduler.checkExists(TRIGGER_KEY))
                scheduler.unscheduleJob(TRIGGER_KEY);

            if (!enable)
            {
                return;
            }

            // Get the quartz Trigger
            Trigger trigger = getTrigger();

            // Create a quartz job that queues a pipeline job that posts private data reminder messages
            JobDetail job = JobBuilder.newJob(PrivateDataMessageSchedulerJob.class)
                    .withIdentity(PrivateDataMessageScheduler.class.getCanonicalName())
                    .build();

            // Schedule trigger to send reminders on the configured schedule
            scheduler.scheduleJob(job, trigger);
        }
        catch (SchedulerException e)
        {
            throw new RuntimeException("Failed to schedule PrivateDataMessageScheduler job", e);
        }
    }

    protected Trigger getTrigger()
    {
        // Run at 8:00AM every morning
        return TriggerBuilder.newTrigger()
                .withIdentity(TRIGGER_KEY)
                .withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(8, 0))
                .build();
    }

    public static class PrivateDataMessageSchedulerJob implements Job
    {
        @SuppressWarnings("unused")
        public PrivateDataMessageSchedulerJob() {}

        @Override
        public void execute(JobExecutionContext context)
        {
            try
            {
                Journal panoramaPublic = JournalManager.getJournal(JournalManager.PANORAMA_PUBLIC);
                if (panoramaPublic == null)
                {
                    throw new ConfigurationException("Server does not have a Panorama Public project.");
                }

                Container c = panoramaPublic.getProject();

                User panoramaPublicAdmin = JournalManager.getJournalAdminUser(panoramaPublic);
                if (panoramaPublicAdmin == null)
                {
                    throw new ConfigurationException("Unable to find an admin user in the Panorama Public project.");
                }
                ViewBackgroundInfo vbi = new ViewBackgroundInfo(c, panoramaPublicAdmin, null);
                PipeRoot root = PipelineService.get().findPipelineRoot(c);

                if (root == null || !root.isValid())
                {
                    throw new ConfigurationException("No valid pipeline root found in the container " + c.getName());
                }

                PipelineJob job = new PrivateDataReminderJob(vbi, PipelineService.get().getPipelineRootSetting(c), false);
                PipelineService.get().queueJob(job);
            }
            catch(Exception e)
            {
                _log.error("Error queuing PrivateDataReminderJob", e);
                ExceptionUtil.logExceptionToMothership(null, e);

            }
        }
    }
}

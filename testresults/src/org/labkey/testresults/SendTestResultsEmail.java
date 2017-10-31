package org.labkey.testresults;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.notification.EmailMessage;
import org.labkey.api.notification.EmailService;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

// SendTestResultsEmail is a Quarts Job that sends an email to the Skyline developers with a summary of the nightly runs from the previous day at 8am to the present.
// It is currently scheduled to send an email every morning at 8am which is why we show all runs since the previous 8am.
public class SendTestResultsEmail implements org.quartz.Job
{
    public static final  String TEST_ADMIN = "testadmin";
    public static final String TEST_CUSTOM = "testcustom";
    public static final String TEST_GET_HTML_EMAIL = "gethtml";
    public static final String MORNING_EMAIL = "morningemail";

    public interface DEFAULT_EMAIL {
        String RECIPIENT = "skyline-dev@proteinms.net";
        String ADMIN_EMAIL = "yuval@uw.edu";
    }

    private static final Logger LOG = Logger.getLogger(SendTestResultsEmail.class);

    public SendTestResultsEmail()
    {

    }

    public Pair<String, String> getHTMLEmail(org.labkey.api.security.User from) {
        // Sends email for all runs since 8:01 the previous morning, at 8am every morning
        Container parent = ContainerManager.getForPath(new Path(new String[]{"home","development"}));
        //parent = ContainerManager.getForPath(new Path(new String[]{"home"})); // DEV ONLY, localhost container path

        List<Container> containers = ContainerManager.getAllChildren(parent, from);
        if(containers.size() == 0)
            return null;
        int totalErrorRuns = 0;
        int totalWarningRuns = 0;
        int totalGoodRuns = 0;
        int totalTotalPasses = 0;
        int totalMissingUsers = 0;
        StringBuilder message = new StringBuilder();
        message.append("<!doctype HTML><head><meta charset=\"UTF-8\"></head>");

        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 8);
        c.set(Calendar.MINUTE, 1);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        Date end = new Date(c.getTime().getTime());  // Calender date is previous date at 8:01 AM
        c.add(Calendar.DATE, -1);
        Date start = new Date(c.getTime().getTime());  // Calender date is previous date at 8:01 AM
        for(Container container: containers)
        {
            SQLFragment sqlFragment = new SQLFragment();
            RunDetail[] runs = TestResultsController.getRunsSinceDate(start, end, container, null, false, false);
            Arrays.sort(runs);

            if(runs.length == 0) // IF NO RUNS DONT ADD CONTAINER TO EMAIL
                continue;
            TestResultsController.ensureRunDataCached(runs, false);
            TestResultsController.populatePassesLeaksFails(runs);

            User[] users = TestResultsController.getTrainingDataForContainer(container);

            TestsDataBean data = new TestsDataBean(runs, users);
            Map<String, List<TestFailDetail>> todaysFailures = data.getFailedTestsByDate(new Date(), true);
            Map<String, List<TestLeakDetail>> todaysLeaks = data.getLeaksByDate(new Date(), true);
            User[] missingUsers = data.getMissingUsers(data.getRuns());
            List<User> nightsUsers = new ArrayList<>();
            // build message as an HTML email message
            message.append("<div style='margin:auto; text-align:center;'>");
            message.append("<h1>" + container.getName() + " <br /><span style='font-size:11px;'>starting: " + start.toString() + "</span></h1>");
            message.append("<h5 style='margin:0; padding:0;'><a href='https://skyline.gs.washington.edu/labkey/project/home/development/"+container.getName()+"/begin.view?'>View Full TestResults</a></h5>");
            message.append("</div>");

            // MAIN "rundown" table
            message.append("<table style='border-collapse: collapse; border-spacing: 0; margin:auto;'>" +
                    "<tr style='font-weight:600;'>" +
                    "<td>Computer name</td>" +
                    "<td>Average Memory</td>" +
                    "<td>Passes</td>" +
                    "<td>PostTime</td>" +
                    "<td>Duration</td>" +
                    "<td>Failures</td>" +
                    "<td>Leaks</td>" +
                    "</tr>");
            int errorRuns = 0;
            int warningRuns = 0;
            int goodRuns = 0;
            int totalPasses = 0;
            for (RunDetail run : runs)
            {
                for (User u : users)
                {
                    if (u.getId() == run.getUserid())
                    {
                        totalPasses += run.getPassedtests();
                        nightsUsers.add(u);
                        boolean isGoodRun = true;
                        String style = "background-color:#3EC23E;";
                        // ERROR: duration < 540, failures or leaks count > 0, more then 3 standard deviations away
                        // WARNING: Between 2 and 3 standard deviations away
                        // PASS: within 2 standard deviations away as well as not an ERROR or a WARNING
                        int errorCount = errorRuns;
                        if (isGoodRun && (u.getMeanmemory() == 0d || u.getMeantestsrun() == 0d))
                        {  // IF NO TRAINING DATA FOR USER
                            style = "background-color:#cccccc;";
                            isGoodRun = false;
                        }
                        if (run.getDuration() < 540 || run.getFailures().length > 0 || run.getLeaks().length > 0)
                        {
                            style = "background-color:#F24E4E;";
                            isGoodRun = false;
                            errorRuns++;
                        }
                        if (isGoodRun && (!u.fitsMemoryTrainingData(run.getAverageMemory(), 3) || !u.fitsRunCountTrainingData(run.getPassedtests(), 3)))
                        {
                            style = "background-color:#F24E4E;";
                            isGoodRun = false;
                            if (errorCount == errorRuns)
                                errorRuns++;
                        }
                        if (isGoodRun && (!u.fitsMemoryTrainingData(run.getAverageMemory(), 2) || !u.fitsRunCountTrainingData(run.getPassedtests(), 2)))
                        {
                            style = "background-color:#F2C94E;";
                            isGoodRun = false;
                            warningRuns++;
                        }
                        if(isGoodRun && run.hasHang()) {
                            style = "background-color:#F2C94E;";
                            isGoodRun = false;
                            warningRuns++;
                        }

                        if (isGoodRun)
                            goodRuns++;

                        message.append("<tr style='border-bottom:1px solid grey;'>");
                        message.append("\n<td style=\"" + style + " padding: 6px;\"><a href=\"" +
                                "http://skyline.ms" + new ActionURL(TestResultsController.ShowRunAction.class, container) + "runId=" + run.getId()
                                + "\" target=\"_blank\" style=\"text-decoration:none; font-weight:600; color:black;\">"
                                + run.getUsername() + "</a></td>");
                        message.append("\n<td style='padding: 6px;'>" + data.round(run.getAverageMemory(), 2) + "</td>");
                        message.append("\n<td style='padding: 6px;'>" + run.getPassedtests() + "</td>");
                        message.append("\n<td style='padding: 6px;'>" + run.getPostTime() + "</td>");
                        message.append("\n<td style='padding: 6px;'>" + run.getDuration() + "</td>");
                        message.append("\n<td style='padding: 6px; " + (run.getFailedtests() > 0 ? "color:red;" : "") + "'>" + run.getFailedtests() + "</td>");
                        message.append("\n<td style='padding: 6px; " + (run.getLeakedtests() > 0 ? "color:red;" : "") + "'>" + run.getLeakedtests() + "</td>");
                        message.append("</tr>");
                    }
                }
            }
            totalErrorRuns += errorRuns;
            totalGoodRuns += goodRuns;
            totalWarningRuns += warningRuns;
            totalMissingUsers += missingUsers.length;
            totalTotalPasses += totalPasses;

            for (User u : missingUsers)
            {
                message.append("<tr style='border-bottom:1px solid grey;'>");
                message.append("\n<td style=\"background-color:#F24E4E; padding: 6px;\">Missing " + u.getUsername() + "</td>");
                message.append("\n<td style='background-color:#F24E4E; padding: 6px;'></td>");
                message.append("\n<td style='background-color:#F24E4E; padding: 6px;'></td>");
                message.append("\n<td style='background-color:#F24E4E; padding: 6px;'></td>");
                message.append("\n<td style='background-color:#F24E4E; padding: 6px;'></td>");
                message.append("\n<td style='background-color:#F24E4E; padding: 6px;'></td>");
                message.append("\n<td style='background-color:#F24E4E; padding: 6px;'></td>");
                message.append("</tr>");
            }
            message.append("<tr style=\"float:right;\"><td></td><td></td><td></td><td></td><td></td><td></td><td>" + goodRuns + "/" + errorRuns + " (Pass/Fail)</td></tr>");
            message.append("</table>");
            List<Integer> noFailLeakRuns = new ArrayList<>();
            // FAILURES & LEAKS TABLE
            if (todaysFailures.size() > 0 || todaysLeaks.size() > 0)
            {
                message.append("\n<table style='border-collapse: collapse; border-spacing: 0; margin:auto;'>");
                message.append("\n<tr >");
                message.append("\n<td style='width:200px; overflow:hidden; padding:0px;'>Fail: <span style='font-weight:600; color:red;'>X</span> | Leak: <span style='font-weight:600; color:orange;'>X</span></td>");

                for (RunDetail run: runs)
                {
                    boolean addUser = false;

                    if (run.getFailedtests() > 0)
                        addUser = true;
                    if (!addUser)
                    {
                        if (run.getLeakedtests() > 0)
                            addUser = true;
                    }
                    if (addUser)
                        message.append("\n<td style='max-width:60px; width:60px; overflow:hidden; text-overflow: ellipsis; padding:3px; border:1px solid #ccc;'>" + run.getUsername() + "</td>");
                    else
                        noFailLeakRuns.add(run.getId());

                }
                message.append("\n</tr>");
                for (Map.Entry<String, List<TestFailDetail>> entry : todaysFailures.entrySet())
                {
                    message.append("\n<tr>");
                    message.append("\n<td style='overflow:hidden; text-overflow: ellipsis; padding:3px; border:1px solid #ccc;'>" + entry.getKey() + "</td>");
                    for (RunDetail run: runs)
                    {
                        if (noFailLeakRuns.contains(run.getId()))
                            continue;
                        message.append("\n<td style='width:60px; overflow:hidden; padding:3px; border:1px solid #ccc;'>");
                        TestFailDetail matchingFail = null;

                        for (TestFailDetail fail : entry.getValue())
                        {
                            if (fail.getTestRunId() == run.getId())
                            {
                                matchingFail = fail;
                            }
                        }
                        if (matchingFail != null)
                        {
                            message.append("\n<span style='font-weight:600; color:red;'>X</span>");
                        }

                        message.append("\n</td>");
                    }
                    message.append("\n</tr>");
                }
                for (Map.Entry<String, List<TestLeakDetail>> entry : todaysLeaks.entrySet())
                {
                    message.append("\n<tr>");
                    message.append("\n<td style='overflow:hidden; text-overflow: ellipsis; padding:3px; border:1px solid #ccc;'>" + entry.getKey() + "</td>");
                    for (RunDetail run: runs)
                    {
                        if (noFailLeakRuns.contains(run.getId()))
                            continue;
                        message.append("\n<td style='width:60px; overflow:hidden; padding:3px; border:1px solid #ccc;'>");
                        TestLeakDetail matchingLeak = null;

                        for (TestLeakDetail leak : entry.getValue())
                        {
                            if (leak.getTestRunId() == run.getId())
                            {
                                matchingLeak = leak;
                            }
                        }
                        if (matchingLeak != null)
                        {
                            message.append("\n<span style='font-weight:600; color:orange;'>X</span>");
                        }
                        message.append("\n</td>");
                    }
                    message.append("\n</tr>");
                }
                message.append("\n</table>");
            }
        }

        SimpleDateFormat formatter = new SimpleDateFormat("MM/dd");
        String subject = "TestResults " + formatter.format(start) + " - " + formatter.format(end) + " (8AM - 8AM)";
        if(totalErrorRuns > 0 || totalWarningRuns > 0) {
            subject += " | Err: " + totalErrorRuns + " Warn: " + totalWarningRuns;
        } else {
            subject = "ALL PASSED WAHOOOO!! - ";
        }
        subject += " Pass: " + totalGoodRuns +" ";
        if(totalMissingUsers > 0)
            subject += "Missing: "+ totalMissingUsers;

        subject += " | " + new DecimalFormat("#,###").format(totalTotalPasses) + " tests run";
        return new Pair(subject, message.toString());
    }
    public void execute(String ctx, org.labkey.api.security.User from, String emailTo) throws JobExecutionException
    {
        List<String> recipients = Collections.singletonList(emailTo);
        if(ctx.equals(MORNING_EMAIL))
            LOG.info("Sending daily emails...");

        Pair<String,String> msg = getHTMLEmail(from);

        EmailService svc = EmailService.get();
        EmailMessage emailMsg = svc.createMessage(from.getEmail(), recipients, msg.first);
        emailMsg.addContent(MimeMap.MimeType.HTML, msg.second);
        svc.sendMessages(Collections.singletonList(emailMsg), from, ContainerManager.getHomeContainer());
    }
    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException
    {
        ValidEmail admin = null;
        try
        {
            admin = new ValidEmail(DEFAULT_EMAIL.ADMIN_EMAIL);
        }
        catch (ValidEmail.InvalidEmailException e)
        {
            e.printStackTrace();
        }
        execute(MORNING_EMAIL, UserManager.getUser(admin), DEFAULT_EMAIL.RECIPIENT);
    }
}

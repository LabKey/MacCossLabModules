package org.labkey.testresults;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableSelector;
import org.labkey.api.notification.EmailMessage;
import org.labkey.api.notification.EmailService;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.testresults.model.BackgroundColor;
import org.labkey.testresults.model.RunDetail;
import org.labkey.testresults.model.RunProblems;
import org.labkey.testresults.model.User;
import org.labkey.testresults.view.RunDownBean;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

// SendTestResultsEmail is a Quarts Job that sends an email to the Skyline developers with a summary of the nightly runs from the previous day at 8am to the present.
// It is currently scheduled to send an email every morning at 8am which is why we show all runs since the previous 8am.
public class SendTestResultsEmail implements org.quartz.Job
{
    public static final String TEST_ADMIN = "testadmin";
    public static final String TEST_CUSTOM = "testcustom";
    public static final String TEST_GET_HTML_EMAIL = "gethtml";
    public static final String MORNING_EMAIL = "morningemail";

    private Date _date;

    public interface DEFAULT_EMAIL {
        String RECIPIENT = "skyline-dev@proteinms.net";
        String ADMIN_EMAIL = "skyline@proteinms.net";
    }

    private static final Logger LOG = LogManager.getLogger(SendTestResultsEmail.class);

    public SendTestResultsEmail()
    {
        _date = null;
    }

    public SendTestResultsEmail(Date date)
    {
        _date = date;
    }

    private String getBackgroundStyle(BackgroundColor color) {
        return "background-color:" + color + ";";
    }

    public Pair<String, String> getHTMLEmail(org.labkey.api.security.User from)
    {
        // Sends email for all runs since 8:01 the previous morning, at 8am every morning
        Container parent = ContainerManager.getHomeContainer().getChild("development");
        //parent = ContainerManager.getForPath(new Path(new String[]{"home"})); // DEV ONLY, localhost container path

        List<Container> containers = ContainerManager.getAllChildren(parent, from);
        if (containers.isEmpty())
            return new Pair<>("", "");
        int totalErrorRuns = 0;
        int totalWarningRuns = 0;
        int totalGoodRuns = 0;
        int totalTotalPasses = 0;
        int totalMissingUsers = 0;
        StringBuilder message = new StringBuilder();
        message.append("<!doctype HTML><head><meta charset=\"UTF-8\"></head>");

        Calendar c = Calendar.getInstance();
        if (_date != null)
            c.setTime(_date);
        TestResultsController.setToEightAM(c);
        Date end = new Date(c.getTime().getTime());  // Calender date is previous date at 8:01 AM
        c.add(Calendar.DATE, -1);
        Date start = new Date(c.getTime().getTime());  // Calender date is previous date at 8:01 AM
        SimpleDateFormat mdyFormatter = new SimpleDateFormat("MM/dd/yyyy");
        for (Container container : containers)
        {
            RunDetail[] runs = TestResultsController.getRunsSinceDate(start, end, container, null, false, false);
            Arrays.sort(runs);

            TestResultsController.ensureRunDataCached(runs, false);
            TestResultsController.populatePassesLeaksFails(runs);

            User[] users = TestResultsController.getUsers(container, null);

            RunDownBean data = new RunDownBean(runs, users);
            RunProblems problems = new RunProblems(data.getRunsByDate(end));
            User[] missingUsers = data.getMissingUsers(data.getRuns());

            if (runs.length == 0 && missingUsers.length == 0)
                continue;

            // build message as an HTML email message
            ActionURL containerUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(container);
            String testResultsUrl = AppProps.getInstance().getBaseServerUrl() + AppProps.getInstance().getContextPath() + containerUrl.getEncodedLocalURIString();
            message.append("<div style='margin:auto; text-align:center;'>")
                .append("<h1>").append(PageFlowUtil.filter(container.getName())).append("<br><span style='font-size:11px;'>starting: ").append(start.toString()).append("</span></h1>")
                .append("<h5 style='margin:0; padding:0;'><a href=\"" + testResultsUrl + "end=" + mdyFormatter.format(end) + "\">View Full TestResults</a></h5>")
                .append("</div>");

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
                    "<td>Git hash</td>" +
                    "</tr>");

            List<Integer> values = new ArrayList<>();
            new TableSelector(TestResultsSchema.getTableInfoGlobalSettings()).forEachResults(rs ->
            {
                values.add(rs.getInt("warningb"));
                values.add(rs.getInt("errorb"));
            });

            int warningBoundary = 2;
            int errorBoundary = 3;

            if (!values.isEmpty()) {
                warningBoundary = values.get(0);
                errorBoundary = values.get(1);
            }

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
                        boolean isGoodRun = true;
                        boolean highlightRuns = false, highlightMemory = false;
                        String style = getBackgroundStyle(BackgroundColor.pass);
                        // ERROR: duration < 540, failures or leaks count > 0, more then 3 standard deviations away
                        // WARNING: Between 2 and 3 standard deviations away
                        // PASS: within 2 standard deviations away as well as not an ERROR or a WARNING
                        if (u.getMeanmemory() == 0d || u.getMeantestsrun() == 0d || !u.isActive())
                        {  // IF NO TRAINING DATA FOR USER or INACTIVE
                            style = getBackgroundStyle(BackgroundColor.unknown);
                            isGoodRun = false;
                        }
                        boolean highlightDuration = run.getDuration() < 539 || run.getHang() != null;
                        if (highlightDuration || run.getFailures().length > 0 || run.getLeaks().length > 0)
                        {
                            style = getBackgroundStyle(BackgroundColor.error);
                            isGoodRun = false;
                            errorRuns++;
                        }
                        if (isGoodRun)
                        {
                            highlightMemory = !u.fitsMemoryTrainingData(run.getAverageMemory(), errorBoundary);
                            highlightRuns = !u.fitsRunCountTrainingData(run.getPassedtests(), errorBoundary);
                            if (highlightMemory || highlightRuns)
                            {
                                style = getBackgroundStyle(BackgroundColor.error);
                                isGoodRun = false;
                                errorRuns++;
                            }
                            else
                            {
                                highlightMemory = !u.fitsMemoryTrainingData(run.getAverageMemory(), warningBoundary);
                                highlightRuns = !u.fitsRunCountTrainingData(run.getPassedtests(), warningBoundary);
                                if (highlightMemory || highlightRuns)
                                {
                                    style = getBackgroundStyle(BackgroundColor.warn);
                                    isGoodRun = false;
                                    warningRuns++;
                                }
                            }
                        }
                        if (isGoodRun && run.getHang() != null)
                        {
                            style = getBackgroundStyle(BackgroundColor.warn);
                            isGoodRun = false;
                            warningRuns++;
                        }

                        if (isGoodRun)
                            goodRuns++;

                        message.append("<tr style='border-bottom:1px solid grey;'>")
                            .append("\n<td style=\"" + style + " padding: 6px;\"><a href=\"" +
                                new ActionURL(TestResultsController.ShowRunAction.class, container).addParameter("runId", Integer.valueOf(run.getId())).getURIString() +
                                "\" target=\"_blank\" style=\"text-decoration:none; font-weight:600; color:black;\">" +
                                PageFlowUtil.filter(run.getUserName()) + "</a></td>")
                            .append("\n<td style='padding: 6px; " + (highlightMemory ? style : "") + "'>" + data.round(run.getAverageMemory(), 2) + "</td>")
                            .append("\n<td style='padding: 6px; " + (highlightRuns ? style : "") + "'>" + run.getPassedtests() + "</td>")
                            .append("\n<td style='padding: 6px;'>" + run.getPostTime() + "</td>")
                            .append("\n<td style='padding: 6px; " + (highlightDuration ? style : "") + "'>" + run.getDuration() + (run.getHang() != null ? " (hang)" : "") + "</td>")
                            .append("\n<td style='padding: 6px; " + (run.getFailedtests() > 0 ? getBackgroundStyle(BackgroundColor.error) : "") + "'>" + run.getFailedtests() + "</td>")
                            .append("\n<td style='padding: 6px; " + (run.getLeaks().length > 0 ? getBackgroundStyle(BackgroundColor.error) : "") + "'>" + run.getLeaks().length + "</td>")
                            .append("\n<td style='padding: 6px;'> " + run.getGitHash() + "</td>")
                            .append("</tr>");
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
                message.append("<tr style='border-bottom: 1px solid grey;'>")
                    .append("\n<td style='" + getBackgroundStyle(BackgroundColor.error) + " padding: 6px;' colspan='7'>Missing " + u.getUsername() + "</td>")
                    .append("</tr>");
            }
            message.append("<tr>")
                .append("<td>" + goodRuns + "/" + errorRuns + " (Pass/Fail)</td>")
                .append("<td colspan='6' style='font-weight: 600; color: red;'>");
            // Files that failed to post
            int fileCount = 0;
            for (File f : TestResultsController.NIGHTLY_POSTER.getLocalPath(container).listFiles())
            {
                if (!f.getName().startsWith(".")) // ignore hidden files
                    fileCount++;
            }
            if (fileCount > 0)
                message.append("<a href=\"" + new ActionURL(TestResultsController.ErrorFilesAction.class, container).getURIString() + "\">" + fileCount + " post error file(s)</a>");
            message.append("</td>")
                .append("</tr>")
                .append("</table>");
            // Problems table
            if (problems.any())
            {
                message.append("\n<table style='border-collapse: collapse; border-spacing: 0; margin:auto;'>")
                    .append("\n<tr>")
                    .append("\n<td style='width:200px; overflow: hidden; padding: 0px;'>" +
                            "Fail: <span style='font-weight: 600; color: red;'>X</span> | " +
                            "Leak: <span style='font-weight: 600; color: orange;'>X</span> | " +
                            "Hang: <span style='font-weight: 600; color: navy;'>X</span>" +
                            "</td>");
                RunDetail[] problemRuns = problems.getRuns();
                for (RunDetail run : problemRuns)
                    message.append("\n<td style='max-width: 60px; width: 60px; overflow: hidden; text-overflow: ellipsis; padding: 3px; border: 1px solid #ccc;'>" + run.getUserName() + "</td>");
                message.append("\n</tr>");

                for (String test : problems.getTestNames())
                {
                    message.append("\n<tr>")
                        .append("\n<td style='overflow: hidden; text-overflow: ellipsis; padding: 3px; border: 1px solid #ccc;'>" + test + "</td>");
                    for (RunDetail run : problemRuns)
                    {
                        message.append("\n<td style='width: 60px; overflow: hidden; padding: 3px; border: 1px solid #ccc;'>");
                        if (problems.isFail(run, test))
                            message.append("\n<span style='font-weight: 600; color: red;'>X</span>");
                        boolean leakMem = problems.isMemoryLeak(run, test);
                        boolean leakHandle = problems.isHandleLeak(run, test);
                        String leakType = "";
                        if (leakMem && leakHandle)
                            leakType = "Memory and handle leak";
                        else if (leakMem)
                            leakType = "Memory leak";
                        else if (leakHandle)
                            leakType = "Handle leak";
                        if (!leakType.isEmpty())
                            message.append("\n<span style='font-weight: 600; color: orange;' title='" + leakType + "'>X</span>");
                        if (problems.isHang(run, test))
                            message.append("\n<span style='font-weight: 600; color: navy;'>X</span>");
                        message.append("\n</td>");
                    }
                    message.append("\n</tr>");
                }
                message.append("\n</table>");
            }
        }

        if (totalTotalPasses == 0)
            return new Pair(null, null);

        SimpleDateFormat formatter = new SimpleDateFormat("MM/dd");
        String subject = "TestResults " + formatter.format(start) + " - " + formatter.format(end) + " (8AM - 8AM)";
        if (totalErrorRuns > 0 || totalWarningRuns > 0) {
            subject += " | Err: " + totalErrorRuns + " Warn: " + totalWarningRuns;
        } else {
            subject = "ALL PASSED WAHOOOO!! - ";
        }
        subject += " Pass: " + totalGoodRuns +" ";
        if (totalMissingUsers > 0)
            subject += "Missing: " + totalMissingUsers;

        subject += " | " + new DecimalFormat("#,###").format(totalTotalPasses) + " tests run";
        return new Pair(subject, message.toString());
    }

    public void execute(String ctx, org.labkey.api.security.User from, String emailTo) throws JobExecutionException
    {
        List<String> recipients = Collections.singletonList(emailTo);
        if (ctx.equals(MORNING_EMAIL))
            LOG.info("Sending daily emails...");

        Pair<String, String> msg = getHTMLEmail(from);
        if (msg.first == null && msg.second == null)
            return;

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

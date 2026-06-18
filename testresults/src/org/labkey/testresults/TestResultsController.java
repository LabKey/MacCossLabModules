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

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.IntHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.files.FileContentService;
import org.labkey.api.notification.EmailMessage;
import org.labkey.api.notification.EmailService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.Pair;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.WebPartView;
import org.labkey.testresults.model.GlobalSettings;
import org.labkey.testresults.model.RunDetail;
import org.labkey.testresults.model.TestFailDetail;
import org.labkey.testresults.model.TestHandleLeakDetail;
import org.labkey.testresults.model.TestHangDetail;
import org.labkey.testresults.model.TestLeakDetail;
import org.labkey.testresults.model.TestMemoryLeakDetail;
import org.labkey.testresults.model.TestPassDetail;
import org.labkey.testresults.model.User;
import org.labkey.testresults.view.LongTermBean;
import org.labkey.testresults.view.RunDownBean;
import org.labkey.testresults.view.TestsDataBean;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.validation.BindException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;
import org.springframework.web.servlet.ModelAndView;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import static org.labkey.testresults.TestResultsModule.JOB_GROUP;
import static org.labkey.testresults.TestResultsModule.JOB_NAME;
import static org.labkey.testresults.TestResultsModule.ViewType;

/**
 * User: Yuval Boss, yuval(at)uw.edu
 * Date: 1/14/2015
 */
public class TestResultsController extends SpringActionController
{
    private static final Logger _log = LogManager.getLogger(TestResultsController.class);
    private static final String MDY_PATTERN = "MM/dd/yyyy";

    // SimpleDateFormat is not thread-safe and is lenient by default, so we never share a
    // single static instance. Each caller gets a fresh, strict formatter: strict parsing
    // makes nonsense dates like 13/45/2026 throw ParseException instead of silently rolling
    // over to a valid-but-wrong date (month 13 -> +1 year, day 45 -> spill into next month).
    private static SimpleDateFormat mdyFormat()
    {
        SimpleDateFormat format = new SimpleDateFormat(MDY_PATTERN);
        format.setLenient(false);
        return format;
    }

    private static final String KEY_SUCCESS = "Success";

    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(TestResultsController.class);

    // Tab name constants for menu highlighting
    public static class TabNames
    {
        // Request attribute name
        public static final String ACTIVE_TAB_ATTR = "activeTab";
        // CSS classes
        private static final String NAV_TAB_CLASS = "nav-tab";
        private static final String ACTIVE_CSS_CLASS = " active";

        // Tab identifiers
        public static final String OVERVIEW = "overview";
        public static final String USER = "user";
        public static final String RUN = "run";
        public static final String LONGTERM = "longterm";
        public static final String FLAGS = "flags";
        public static final String TRAINING_DATA = "trainingdata";
        public static final String ERRORS = "errors";

        /** Returns CSS class for a tab link: "nav-tab" or "nav-tab active" */
        public static String getTabClass(String tabName, String activeTab)
        {
            return NAV_TAB_CLASS + (tabName.equals(activeTab) ? ACTIVE_CSS_CLASS : "");
        }
    }

    private static Date parseDate(String dateStr) throws ParseException
    {
        return StringUtils.isNotBlank(dateStr) ? mdyFormat().parse(dateStr) : null;
    }

    /**
     * Recomputes one user's training statistics (mean/stddev of tests-run and memory) in the
     * given container from their remaining training runs. If the user has no training runs
     * left, deletes their UserData row instead.
     *
     * The delete branch matters: the recompute groups by (userid, container), so an empty
     * join produces zero output rows, ON CONFLICT never fires, and a stale UserData row would
     * otherwise survive with its old mean/stddev values (leaving the user listed on the
     * training-data page as if they still had training data). Callers must invoke this inside
     * an open transaction.
     */
    private static void recomputeUserData(DbScope scope, int userid, Container container)
    {
        SQLFragment remainingFragment = new SQLFragment();
        remainingFragment.append(
            "SELECT COUNT(*) FROM " + TestResultsSchema.getTableInfoTestRuns() + " " +
            "JOIN " + TestResultsSchema.getTableInfoTrain() +
            "   ON " + TestResultsSchema.getTableInfoTestRuns() + ".id = " + TestResultsSchema.getTableInfoTrain() + ".runid " +
            "WHERE userid = ? AND container = ?");
        remainingFragment.add(userid);
        remainingFragment.add(container.getEntityId());
        long remainingTrainRuns = new SqlSelector(TestResultsSchema.getSchema(), remainingFragment).getObject(Long.class);

        if (remainingTrainRuns == 0)
        {
            SQLFragment deleteFragment = new SQLFragment();
            deleteFragment.append("DELETE FROM " + TestResultsSchema.getTableInfoUserData() + " WHERE userid = ? AND container = ?");
            deleteFragment.add(userid);
            deleteFragment.add(container.getEntityId());
            new SqlExecutor(scope).execute(deleteFragment);
        }
        else
        {
            SQLFragment sqlFragmentUpdate = new SQLFragment();
            sqlFragmentUpdate.append(
                "INSERT INTO " + TestResultsSchema.getTableInfoUserData() + " " +
                "   (userid, container, meantestsrun, meanmemory, stddevtestsrun, stddevmemory) " +
                "SELECT ?, ?, avg(passedtests), avg(averagemem), stddev_pop(passedtests), stddev_pop(averagemem) " +
                "FROM " + TestResultsSchema.getTableInfoTestRuns() + " " +
                "JOIN " + TestResultsSchema.getTableInfoTrain() +
                "   ON " + TestResultsSchema.getTableInfoTestRuns() + ".id = " + TestResultsSchema.getTableInfoTrain() + ".runid " +
                "WHERE userid = ? AND container = ? " +
                "GROUP BY userid, container " +
                "ON CONFLICT(userid, container) DO UPDATE SET " +
                "   meantestsrun = excluded.meantestsrun, " +
                "   meanmemory = excluded.meanmemory, " +
                "   stddevtestsrun = excluded.stddevtestsrun, " +
                "   stddevmemory = excluded.stddevmemory");
            sqlFragmentUpdate.add(userid);
            sqlFragmentUpdate.add(container.getEntityId());
            sqlFragmentUpdate.add(userid);
            sqlFragmentUpdate.add(container.getEntityId());
            new SqlExecutor(scope).execute(sqlFragmentUpdate);
        }
    }

    // Form class for RetrainAllAction
    public static class RetrainAllForm
    {
        private String _mode = "reset";
        private int _maxRuns = 20;
        private int _minRuns = 5;
        private Integer _targetRuns; // backwards compatibility

        public String getMode() { return _mode; }
        public void setMode(String mode) { _mode = mode; }

        public int getMaxRuns()
        {
            // Support legacy targetRuns parameter
            if (_targetRuns != null)
                return _targetRuns;
            return _maxRuns;
        }
        public void setMaxRuns(int maxRuns) { _maxRuns = maxRuns; }

        public int getMinRuns() { return _minRuns; }
        public void setMinRuns(int minRuns) { _minRuns = minRuns; }

        public Integer getTargetRuns() { return _targetRuns; }
        public void setTargetRuns(Integer targetRuns) { _targetRuns = targetRuns; }

        public boolean isIncremental() { return "incremental".equalsIgnoreCase(_mode); }
    }

    public TestResultsController()
    {
        setActionResolver(_actionResolver);
    }

    public static final int POINT_RATIO = 30;

    /**
     * action to view rundown.jsp and also the landing page for module
     */
    @RequiresPermission(ReadPermission.class)
    public static class BeginAction extends SimpleViewAction<RunDownForm>
    {
        @Override
        public ModelAndView getView(RunDownForm form, BindException errors) throws Exception
        {
            Date endDate;
            try { endDate = form.getEndDate(); }
            catch (ParseException e)
            {
                errors.reject(ERROR_MSG, "Invalid date format: " + form.getEnd() + " (expected MM/dd/yyyy)");
                return new SimpleErrorView(errors);
            }

            RunDownBean bean = getRunDownBean(getUser(), getContainer(), endDate, form.getViewType());
            JspView<RunDownBean> view = new JspView<>("/org/labkey/testresults/view/rundown.jsp", bean);
            view.setFrame(WebPartView.FrameType.PORTAL);
            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addModuleNavTrail(root, getContainer());
        }
    }

    private static void addModuleNavTrail(NavTree root, Container container)
    {
        root.addChild("Test Results", new ActionURL(TestResultsController.BeginAction.class, container));
    }


    public static class RunDownForm
    {
        private String _end;
        private String _viewType;

        public String getEnd()
        {
            return _end;
        }
        public void setEnd(String end)
        {
            _end = end;
        }
        public Date getEndDate() throws ParseException
        {
            return parseDate(_end);
        }
        public String getViewType()
        {
            return _viewType;
        }
        public void setViewType(String viewType)
        {
            _viewType = viewType;
        }
    }

    public static RunDownBean getRunDownBean(org.labkey.api.security.User user, Container c) throws ParseException, IOException
    {
        return getRunDownBean(user, c, null, null);
    }

    // return TestDataBean specifically for rundown.jsp aka the home page of the module
    public static RunDownBean getRunDownBean(org.labkey.api.security.User user, Container c, Date endDateParam, String viewType) throws ParseException, IOException
    {

        Calendar cal = Calendar.getInstance();
        cal.setTime(endDateParam != null ? endDateParam : new Date());
        setToEightAM(cal);
        Date endDate = cal.getTime();
        cal.add(Calendar.DATE, -1);
        Date dateBefore1Day = cal.getTime();
        viewType = getViewType(viewType, ViewType.MONTH);

        Date startDate = getStartDate(viewType, ViewType.MONTH, endDate); // defaults to month

        RunDetail[] allRuns = getRunsSinceDate(startDate, endDate, c, null, false, false);
        List<RunDetail> todaysRuns = new ArrayList<>();
        List<RunDetail> monthRuns = new ArrayList<>();

        for (RunDetail run : allRuns) {
            long postTime = run.getPostTime().getTime();
            if (dateBefore1Day.getTime() < postTime && postTime < endDate.getTime()) {
                todaysRuns.add(run);
            } else {
                monthRuns.add(run);
            }
        }

        // show blank page if no runs exist
        if (todaysRuns.isEmpty() && monthRuns.isEmpty())
            return new RunDownBean(new RunDetail[0], new User[0], viewType, null, endDate);

        RunDetail[] today = todaysRuns.toArray(new RunDetail[0]);
        if (!todaysRuns.isEmpty())
            populateLastPassForRuns(today);

        // merge todays runs with past months runs to get array of all runs this week
        allRuns = ArrayUtils.addAll(monthRuns.toArray(new RunDetail[0]), today);
        // get all data for leaks and failures for runs of past month
        populateLeaks(allRuns);
        populateFailures(allRuns);
        populateHangs(allRuns);

        ensureRunDataCached(allRuns, false);

        User[] users = getUsers(user, c, null);
        return new RunDownBean(allRuns, users, viewType, null, endDate);
    }

    private static String getViewType(String viewType, String defaultViewType) {
        return viewType != null && (
            viewType.equals(ViewType.DAY) ||
            viewType.equals(ViewType.WEEK) ||
            viewType.equals(ViewType.MONTH) ||
            viewType.equals(ViewType.YEAR))
            ? viewType
            : defaultViewType;
    }

    // ensure all runs have necessary cached data
    // if not cache data this one time for future use
    public static void ensureRunDataCached(RunDetail[] runs, boolean keepObjData) {
        if (runs == null || runs.length == 0)
            return;
        // dont want memory running out, anything over 100 gets cached then cleaned afterwards
        if (runs.length > 100)
            keepObjData = false;
        try (DbScope.Transaction transaction = TestResultsSchema.getSchema().getScope().ensureTransaction())
        {
            for (RunDetail run : runs)
            {
                if (run.getPassedtests() == 0 && run.getPointsummary() == null)
                {
                    TestPassDetail[] passes = run.getPasses();
                    TestFailDetail[] failures = run.getFailures();
                    TestLeakDetail[] leaks = run.getLeaks();
                    if (passes == null)
                        passes = getPassesForRun(run);
                    if (failures == null)
                        failures = getFailuresForRun(run);
                    if (leaks == null)
                        leaks = getLeaksForRun(run);
                    byte[] passSummary = new byte[0];
                    try
                    {
                        passSummary = encodeRunPassSummary(passes);
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                    int avgMem = 0;
                    if (passes.length != 0)
                    {
                        for (TestPassDetail pass : passes)
                        {
                            avgMem += pass.getTotalMemory();
                        }
                        avgMem = avgMem / passes.length;
                    }

                    run.setPointsummary(passSummary);
                    run.setPassedtests(passes.length);
                    run.setFailedtests(failures.length);
                    run.setLeakedtests(leaks.length);
                    run.setAveragemem(avgMem);

                    Map<String, Object> runMap = new HashMap<>();
                    runMap.put("pointsummary", new Parameter.TypedValue(passSummary, JdbcType.BINARY));
                    runMap.put("passedtests", passes.length);
                    runMap.put("failedtests", failures.length);
                    runMap.put("leakedtests", leaks.length);
                    runMap.put("averagemem", avgMem);
                    Table.update(null, TestResultsSchema.getTableInfoTestRuns(), runMap, run.getId());

                    // Set all to empty array so that memory does not run out.
                    // Each run ideally has 10k test passes so server
                    // could throw a mem error pretty quick if we didn't do this.
                    if (!keepObjData) {
                        run.setPasses(new TestPassDetail[0]);
                        run.setFailures(new TestFailDetail[0]);
                        run.setLeaks(new TestLeakDetail[0]);
                        run.setHang(null);
                    }
                }
            }
            transaction.commit();
        }
    }

    public static User[] getUsers(@NotNull org.labkey.api.security.User user, @NotNull Container trainingDataContainer, @Nullable String username) {
        if (trainingDataContainer == null)
            throw new IllegalArgumentException("getUsers requires a folder; use findUserIdByName(username) for the no-folder lookup");
        // Scope the computer list to the current folder via the filtered "user" query table.
        TableInfo userTable = new TestResultsSchema(user, trainingDataContainer)
                .getTable(TestResultsSchema.TABLE_USER, ContainerFilter.current(trainingDataContainer, user));
        SimpleFilter filter = new SimpleFilter();
        if (username != null && !username.isEmpty())
            filter.addCondition(FieldKey.fromParts("username"), username);
        List<User> users = new ArrayList<>();
        new TableSelector(userTable, filter, new Sort("id")).forEach(rs -> {
            User u = new User();
            u.setId(rs.getInt("id"));
            u.setUsername(rs.getString("username"));
            users.add(u);
        });

        // Attach the training stats (userdata is keyed by container).
        SQLFragment sqlFragment = new SQLFragment();
        sqlFragment.append(
            "SELECT userid, meantestsrun, meanmemory, stddevtestsrun, stddevmemory, active " +
            "FROM testresults.userdata " +
            "WHERE container = ?");
        sqlFragment.add(trainingDataContainer.getEntityId());
        new SqlSelector(TestResultsSchema.getSchema(), sqlFragment).forEach(rs -> {
            for (User u : users)
            {
                if (u.getId() == rs.getInt("userid"))
                {
                    u.setMeantestsrun(rs.getDouble("meantestsrun"));
                    u.setMeanmemory(rs.getDouble("meanmemory"));
                    u.setStddevtestsrun(rs.getDouble("stddevtestsrun"));
                    u.setStddevmemory(rs.getDouble("stddevmemory"));
                    u.setContainer(trainingDataContainer);
                    u.setActive(rs.getBoolean("active"));
                    break;
                }
            }
        });
        return users.toArray(new User[0]);
    }

    /**
     * Looks up a computer's id by exact name across all folders, or -1 if none exists. The "user"
     * row is global (no container column), and run ingestion (ParseAndStoreXML) runs anonymously
     * and may be the computer's first post to a given folder, so this lookup must not be container-scoped.
     */
    private static int findUserIdByName(@NotNull String username)
    {
        SQLFragment sql = new SQLFragment("SELECT id FROM testresults.user WHERE username = ? ORDER BY id", username);
        List<Integer> ids = new ArrayList<>();
        new SqlSelector(TestResultsSchema.getSchema(), sql).forEach(rs -> ids.add(rs.getInt("id")));
        return ids.isEmpty() ? -1 : ids.get(0);
    }

    /**
     * action to view trainging data for each user
     */
    @RequiresPermission(ReadPermission.class)
    public static class TrainingDataViewAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            List<Integer> foundRuns = new ArrayList<>();
            SQLFragment sqlFragment = new SQLFragment();
            sqlFragment.append("SELECT * FROM " + TestResultsSchema.getTableInfoTrain());
            SqlSelector sqlSelector = new SqlSelector(TestResultsSchema.getSchema(), sqlFragment);
            sqlSelector.forEach(rs -> foundRuns.add(rs.getInt("runid")));
            SimpleFilter filter = new SimpleFilter();
            SimpleFilter.InClause in = new SimpleFilter.InClause(FieldKey.fromParts("id"), foundRuns);
            filter.addClause(in);
            filter.addCondition(FieldKey.fromParts("container"), getContainer());
            RunDetail[] runs = new TableSelector(TestResultsSchema.getTableInfoTestRuns(), filter, null).getArray(RunDetail.class);

            ensureRunDataCached(runs, false);

            User[] users = getUsers(getUser(), getContainer(), null);
            TestsDataBean bean = new TestsDataBean(runs, users);
            JspView<TestsDataBean> view = new JspView<>("/org/labkey/testresults/view/trainingdata.jsp", bean);
            view.setFrame(WebPartView.FrameType.PORTAL);
            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addModuleNavTrail(root, getContainer());
        }
    }

    // API endpoint for adding or removing a run for the training set needs parameters: runId=int&train=boolean
    @RequiresPermission(AdminPermission.class)
    public static class TrainRunAction extends MutatingApiAction<TrainRunForm> {
        @Override
        public Object execute(TrainRunForm form, BindException errors)
        {
            if (form.getRunId() == null)
            {
                return new ApiSimpleResponse(Map.of(KEY_SUCCESS, false, "error", "runId is required"));
            }
            int runId = form.getRunId();
            String trainString = form.getTrain();
            if (!Strings.CI.equals(trainString, "true") &&
                !Strings.CI.equals(trainString, "false") &&
                !Strings.CI.equals(trainString, "force"))
            {
                return new ApiSimpleResponse(Map.of(KEY_SUCCESS, false, "error", "train must be one of: true, false, force"));
            }
            boolean train = Strings.CI.equals(trainString, "true"); // true = add to training set, false = remove
            boolean force = Strings.CI.equals(trainString, "force");

            SQLFragment sqlFragment = new SQLFragment();
            sqlFragment.append("SELECT * FROM " + TestResultsSchema.getTableInfoTrain() + " WHERE runid = ?");
            sqlFragment.add(runId);
            SqlSelector sqlSelector = new SqlSelector(TestResultsSchema.getSchema(), sqlFragment);
            List<Integer> foundRuns = new ArrayList<>();
            sqlSelector.forEach(rs -> foundRuns.add(rs.getInt("runid")));
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(FieldKey.fromParts("id"), runId);
            RunDetail[] details = new TableSelector(TestResultsSchema.getTableInfoTestRuns(), filter, null).getArray(RunDetail.class);
            if (!force)
            {
                if (details.length == 0)
                    return new ApiSimpleResponse(Map.of(KEY_SUCCESS, false, "error", "run does not exist: " + runId));
                else if ((train && !foundRuns.isEmpty()) || (!train && foundRuns.isEmpty()))
                    return new ApiSimpleResponse(Map.of(KEY_SUCCESS, false, "error", "no action necessary"));
            }
            DbScope scope = TestResultsSchema.getSchema().getScope();
            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                if (!force)
                {
                    SQLFragment fragment = new SQLFragment();
                    fragment.append(train
                        ? "INSERT INTO " + TestResultsSchema.getTableInfoTrain() + " (runid) VALUES (?)"
                        : "DELETE FROM " + TestResultsSchema.getTableInfoTrain() + " WHERE runid = ?");
                    fragment.add(runId);
                    new SqlExecutor(scope).execute(fragment);
                }
                // Refresh this user's training statistics from their remaining training runs.
                recomputeUserData(scope, details[0].getUserid(), getContainer());
                transaction.commit();
            }
            return new ApiSimpleResponse(KEY_SUCCESS, true);
        }
    }

    public static class TrainRunForm
    {
        private Integer _runId;
        private String _train;

        public Integer getRunId()
        {
            return _runId;
        }
        public void setRunId(Integer runId)
        {
            _runId = runId;
        }
        public String getTrain()
        {
            return _train;
        }
        public void setTrain(String train)
        {
            _train = train;
        }
    }

    /**
     * action to view user.jsp and all run details for user in date selection
     * accepts a url parameter "username" which will be the user that the jsp displays runs for
     * accepts url parameter "start" and "end" which will be the date range of selected runs for that user to display
     */
    @RequiresPermission(ReadPermission.class)
    public static class ShowUserAction extends SimpleViewAction<ShowUserForm>
    {
        @Override
        public ModelAndView getView(ShowUserForm form, BindException errors) throws Exception
        {
            String userName = form.getUsername();
            String dataInclude = form.getDatainclude();
            Date startDate;
            Date endDate;
            try { startDate = form.getStartDate(); }
            catch (ParseException e)
            {
                errors.reject(ERROR_MSG, "Invalid start date format: " + form.getStart() + " (expected MM/dd/yyyy)");
                return new SimpleErrorView(errors);
            }
            try { endDate = form.getEndDate(); }
            catch (ParseException e)
            {
                errors.reject(ERROR_MSG, "Invalid end date format: " + form.getEnd() + " (expected MM/dd/yyyy)");
                return new SimpleErrorView(errors);
            }
            if (startDate == null)
                startDate = DateUtils.addDays(new Date(), -6); // DEFAULT TO LAST WEEK's RUNS
            endDate = endDate == null
                    ? new Date()
                    : DateUtils.addMilliseconds(DateUtils.ceiling(endDate, Calendar.DATE), 0);
            User user = null;
            if (StringUtils.isNotBlank(userName))
            {
                User[] users = getUsers(getUser(), getContainer(), userName);
                if (users.length == 1)
                    user = users[0];
            }

            RunDetail[] runs = getRunsSinceDate(startDate, endDate, getContainer(), user, dataInclude, false, false);
            if (user == null)
                populateFailures(runs);
            ensureRunDataCached(runs, false);

            TestsDataBean bean = new TestsDataBean(runs, user == null ? new User[0] : new User[]{user});
            bean.setStartDate(startDate);
            bean.setEndDate(endDate);
            bean.setUsername(userName);
            bean.setDataInclude(dataInclude);
            JspView<TestsDataBean> view = new JspView<>("/org/labkey/testresults/view/user.jsp", bean);
            view.setFrame(WebPartView.FrameType.PORTAL);
            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addModuleNavTrail(root, getContainer());
        }
    }

    public static class ShowUserForm
    {
        private String _start;
        private String _end;
        private String _username;
        private String _datainclude;

        public String getStart()
        {
            return _start;
        }
        public void setStart(String start)
        {
            _start = start;
        }
        public Date getStartDate() throws ParseException
        {
            return parseDate(_start);
        }
        public String getEnd()
        {
            return _end;
        }
        public void setEnd(String end)
        {
            _end = end;
        }
        public Date getEndDate() throws ParseException
        {
            return parseDate(_end);
        }
        public String getUsername()
        {
            return _username;
        }
        public void setUsername(String username)
        {
            _username = username;
        }
        public String getDatainclude()
        {
            return _datainclude;
        }
        public void setDatainclude(String datainclude)
        {
            _datainclude = datainclude;
        }
    }

    /**
     * action to view runDetail.jsp (detail for a single run)
     * accepts a url parameter "runId" which will be the run that the jsp displays the information of
     */
    @RequiresPermission(ReadPermission.class)
    public static class ShowRunAction extends SimpleViewAction<ShowRunForm>
    {
        @Override
        public ModelAndView getView(ShowRunForm form, BindException errors) throws Exception
        {
            if (form.getRunId() == null)
            {
                // Null bean causes runDetail.jsp to display a form prompting the user to enter a run ID
                JspView<TestsDataBean> errorView = new JspView<>("/org/labkey/testresults/view/runDetail.jsp", null);
                errorView.setFrame(WebPartView.FrameType.PORTAL);
                return errorView;
            }
            int runId = form.getRunId();
            String filterTestPassesBy = form.getFilter();

            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(FieldKey.fromParts("testrunid"), runId);

            TestFailDetail[] fails = new TableSelector(TestResultsSchema.getTableInfoTestFails(), filter, null).getArray(TestFailDetail.class);
            TestPassDetail[] passes = new TableSelector(TestResultsSchema.getTableInfoTestPasses(), filter, null).getArray(TestPassDetail.class);
            List<TestLeakDetail> leaks = new ArrayList<>();
            Collections.addAll(leaks, new TableSelector(TestResultsSchema.getTableInfoMemoryLeaks(), filter, null).getArray(TestMemoryLeakDetail.class));
            Collections.addAll(leaks, new TableSelector(TestResultsSchema.getTableInfoHandleLeaks(), filter, null).getArray(TestHandleLeakDetail.class));
            TestHangDetail[] hangs = new TableSelector(TestResultsSchema.getTableInfoHangs(), filter, null).getArray(TestHangDetail.class);

            SQLFragment sqlFragment = new SQLFragment();
            sqlFragment.append("SELECT testruns.*, u.username, EXISTS(SELECT 1 FROM testresults.trainruns WHERE runid = testruns.id) AS traindata FROM testresults.testruns ");
            sqlFragment.append("JOIN testresults.user AS u ON testruns.userid = u.id ");
            sqlFragment.append("WHERE testruns.id = ?");
            sqlFragment.add(runId);

            RunDetail[] runs = executeGetRunsSQLFragment(sqlFragment, getContainer(), false, true);
            if (runs.length == 0)
            {
                JspView<TestsDataBean> errorView = new JspView<>("/org/labkey/testresults/view/runDetail.jsp", null);
                errorView.setFrame(WebPartView.FrameType.PORTAL);
                return errorView;
            }
            RunDetail run = runs[0];
            if (run == null)
            {
                JspView<TestsDataBean> errorView = new JspView<>("/org/labkey/testresults/view/runDetail.jsp", null);
                errorView.setFrame(WebPartView.FrameType.PORTAL);
                return errorView;
            }
            if (filterTestPassesBy != null) {
                if (filterTestPassesBy.equals("duration")) {
                    List<TestPassDetail> filteredPasses = Arrays.asList(passes);
                    filteredPasses.sort((o1, o2) -> o2.getDuration() - o1.getDuration());
                    passes = filteredPasses.toArray(new TestPassDetail[passes.length]);
                } else if (filterTestPassesBy.equals("managed")) {
                    List<TestPassDetail> filteredPasses = Arrays.asList(passes);
                    filteredPasses.sort((o1, o2) -> Double.compare(o2.getManagedMemory(), o1.getManagedMemory()));
                    passes = filteredPasses.toArray(new TestPassDetail[passes.length]);

                } else if (filterTestPassesBy.equals("total")) {
                    List<TestPassDetail> filteredPasses = Arrays.asList(passes);
                    filteredPasses.sort((o1, o2) -> Double.compare(o2.getTotalMemory(), o1.getTotalMemory()));
                    passes = filteredPasses.toArray(new TestPassDetail[passes.length]);
                }
            } else {
                Arrays.sort(passes);
            }
            run.setFailures(fails);
            run.setLeaks(leaks.toArray(new TestLeakDetail[0]));
            if (hangs.length > 0)
                run.setHang(hangs[0]);
            run.setPasses(passes);
            TestsDataBean bean = new TestsDataBean(runs, new User[0]);
            JspView<TestsDataBean> view = new JspView<>("/org/labkey/testresults/view/runDetail.jsp", bean);
            view.setFrame(WebPartView.FrameType.PORTAL);
            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addModuleNavTrail(root, getContainer());
        }
    }

    public static class ShowRunForm
    {
        private Integer _runId;
        private String _filter;

        public Integer getRunId()
        {
            return _runId;
        }
        public void setRunId(Integer runId)
        {
            _runId = runId;
        }
        public String getFilter()
        {
            return _filter;
        }
        public void setFilter(String filter)
        {
            _filter = filter;
        }
    }

    /**
     * action to view longTerm.jsp
     * accepts a url parameter "viewType" of either wk(week), mo(month), or yr(year) and defaults to month
     */
    @RequiresPermission(ReadPermission.class)
    public static class LongTermAction extends SimpleViewAction<LongTermForm>
    {
        @Override
        public ModelAndView getView(LongTermForm form, BindException errors) throws Exception
        {
            String viewType = form.getViewType();

            LongTermBean bean = new LongTermBean(new RunDetail[0], new User[0]); // bean that will be handed to jsp
            viewType = getViewType(viewType, ViewType.YEAR);
            Date startDate = getStartDate(viewType, ViewType.YEAR, new Date()); // defaults to month
            bean.setViewType(viewType);
            RunDetail[] runs = getRunsSinceDate(startDate, null, getContainer(), null, false, false);
            bean.setRuns(runs);

            SimpleFilter filter = new SimpleFilter();
            filter.addClause(new CompareType.CompareClause(FieldKey.fromParts("timestamp"), CompareType.DATE_GTE, startDate));
            Sort s = new Sort("testrunid");
            TestFailDetail[] failures = new TableSelector(TestResultsSchema.getTableInfoTestFails(), filter, s).getArray(TestFailDetail.class);
            bean.setNonAssociatedFailures(failures);

            ensureRunDataCached(runs, true);
            JspView<LongTermBean> view = new JspView<>("/org/labkey/testresults/view/longTerm.jsp", bean);
            view.setFrame(WebPartView.FrameType.PORTAL);
            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addModuleNavTrail(root, getContainer());
        }
    }

    public static class LongTermForm
    {
        private String _viewType;

        public String getViewType()
        {
            return _viewType;
        }
        public void setViewType(String viewType)
        {
            _viewType = viewType;
        }
    }

    /**
     * action to view failureDetail.jsp
     * accepts parameter failedTest as name of the failed test
     * accepts parameter viewType as 'wk', 'mo', or 'yr'.  defaults to 'day'
     */
    @RequiresPermission(ReadPermission.class)
    public static class ShowFailures extends SimpleViewAction<ShowFailuresForm>
    {
        @Override
        public ModelAndView getView(ShowFailuresForm form, BindException errors) throws Exception
        {
            String failedTest = form.getFailedTest();
            String viewType = getViewType(form.getViewType(), ViewType.DAY);

            Date endParsed;
            try
            {
                endParsed = form.getEndDate();
            }
            catch (ParseException e)
            {
                errors.reject(ERROR_MSG, "Invalid date format: " + form.getEnd() + " (expected MM/dd/yyyy)");
                return new SimpleErrorView(errors);
            }
            Date endDate = setToEightAM(endParsed != null ? endParsed : new Date());
            Date startDate = getStartDate(viewType, ViewType.DAY, endDate); // defaults to day

            RunDetail[] runs = getRunsSinceDate(startDate, endDate, getContainer(), null, false, false);
            populateFailures(runs);
            populateHangs(runs);
            populateLeaks(runs);

            TestsDataBean bean = new TestsDataBean(null, new User[0]);
            bean.setViewType(viewType);
            bean.setStartDate(startDate);
            bean.setEndDate(endDate);

            if (!StringUtils.isEmpty(failedTest))
            {
                // Filter runs to be only the ones containing a failure, hang, or leak for the specified test.
                bean.setRuns(Arrays.stream(runs).filter(run ->
                        (run.getFailures() != null && Arrays.stream(run.getFailures()).anyMatch(failure -> failure.getTestName().equals(failedTest))) ||
                        (run.getHang() != null && run.getHang().getTestName().equals(failedTest)) ||
                        (run.getLeaks() != null && Arrays.stream(run.getLeaks()).anyMatch(leak -> leak.getTestName().equals(failedTest)))
                ).toArray(RunDetail[]::new));

                JspView<TestsDataBean> view = new JspView<>("/org/labkey/testresults/view/failureDetail.jsp", bean);
                view.setFrame(WebPartView.FrameType.PORTAL);
                return view;
            }

            bean.setRuns(runs);
            JspView<TestsDataBean> view = new JspView<>("/org/labkey/testresults/view/multiFailureDetail.jsp", bean);
            view.setFrame(WebPartView.FrameType.PORTAL);
            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addModuleNavTrail(root, getContainer());
        }
    }

    public static class ShowFailuresForm
    {
        private String _end;
        private String _failedTest;
        private String _viewType;

        public String getEnd()
        {
            return _end;
        }
        public void setEnd(String end)
        {
            _end = end;
        }
        public Date getEndDate() throws ParseException
        {
            return parseDate(_end);
        }
        public String getFailedTest()
        {
            return _failedTest;
        }
        public void setFailedTest(String failedTest)
        {
            _failedTest = failedTest;
        }
        public String getViewType()
        {
            return _viewType;
        }
        public void setViewType(String viewType)
        {
            _viewType = viewType;
        }
    }

    /**
     * action for deleting a run ex:'deleteRun.view?runId=x'
     */
    @RequiresPermission(AdminPermission.class)
    public static class DeleteRunAction extends MutatingApiAction<RunIdForm> {
        @Override
        public Object execute(RunIdForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            if (form.getRunId() == null)
            {
                response.put(KEY_SUCCESS, false);
                response.put("error", "runId is required");
                return response;
            }

            int rowId = form.getRunId();
            // Look up the run's owner before deleting so we can refresh that user's training
            // stats afterward: the run may have been part of their training set, so their
            // baseline can change (or disappear) once it is gone.
            RunDetail[] runs = new TableSelector(TestResultsSchema.getTableInfoTestRuns(),
                    new SimpleFilter(FieldKey.fromParts("id"), rowId), null).getArray(RunDetail.class);
            // Child tables keyed by testrunid -> testruns(id).
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(FieldKey.fromParts("testrunid"), rowId);
            // trainruns keys the run via its "runid" column, not "testrunid".
            SimpleFilter trainFilter = new SimpleFilter();
            trainFilter.addCondition(FieldKey.fromParts("runid"), rowId);
            DbScope scope = TestResultsSchema.getSchema().getScope();
            try (DbScope.Transaction transaction = scope.ensureTransaction()) {
                // Delete every child row that references this run before the parent, or the
                // testruns delete fails on a foreign key constraint. handleleaks and trainruns
                // were previously missed: deleting a run that had handle-leak records or was in
                // the training set failed (e.g. fk_memoryleaks_testruns on table handleleaks).
                Table.delete(TestResultsSchema.getTableInfoTestFails(), filter);
                Table.delete(TestResultsSchema.getTableInfoTestPasses(), filter);
                Table.delete(TestResultsSchema.getTableInfoMemoryLeaks(), filter);
                Table.delete(TestResultsSchema.getTableInfoHandleLeaks(), filter);
                Table.delete(TestResultsSchema.getTableInfoHangs(), filter);
                Table.delete(TestResultsSchema.getTableInfoTrain(), trainFilter);
                Table.delete(TestResultsSchema.getTableInfoTestRuns(), rowId); // delete run last because of foreign key
                // If the deleted run belonged to a user, refresh their training stats now that
                // it (and any trainruns row for it) is gone, so a removed training run does not
                // leave a stale UserData row behind.
                if (runs.length > 0)
                    recomputeUserData(scope, runs[0].getUserid(), getContainer());
                transaction.commit();
            } catch (Exception x) {
                response.put(KEY_SUCCESS, false);
                response.put("error", x.getMessage());
                return response;
            }
            response.put(KEY_SUCCESS, true);
            return response;
        }
    }

    public static class RunIdForm
    {
        private Integer _runId;

        public Integer getRunId()
        {
            return _runId;
        }
        public void setRunId(Integer runId)
        {
            _runId = runId;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class FlagRunAction extends MutatingApiAction<FlagRunForm> {
        @Override
        public Object execute(FlagRunForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            if (form.getRunId() == null)
            {
                response.put(KEY_SUCCESS, false);
                response.put("error", "runId is required");
                return response;
            }

            int rowId = form.getRunId();
            boolean flag = form.getFlag() != null ? form.getFlag() : false;

            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(FieldKey.fromParts("id"), rowId);
            try (DbScope.Transaction transaction = TestResultsSchema.getSchema().getScope().ensureTransaction()) {
                RunDetail detail = new TableSelector(TestResultsSchema.getTableInfoTestRuns(), filter, null).getObject(RunDetail.class);
                if (detail == null)
                {
                    response.put(KEY_SUCCESS, false);
                    response.put("error", "run not found: " + rowId);
                    return response;
                }
                if (form.getFlag() == null) // if not specified keep same
                    flag = detail.isFlagged();
                detail.setFlagged(flag);
                Table.update(null, TestResultsSchema.getTableInfoTestRuns(), detail, detail.getId());
                transaction.commit();
            } catch (Exception x) {
                response.put(KEY_SUCCESS, false);
                response.put("error", x.getMessage());
                return response;
            }
            response.put(KEY_SUCCESS, true);
            return response;
        }
    }

    public static class FlagRunForm
    {
        private Integer _runId;
        private Boolean _flag;

        public Integer getRunId()
        {
            return _runId;
        }
        public void setRunId(Integer runId)
        {
            _runId = runId;
        }
        public Boolean getFlag()
        {
            return _flag;
        }
        public void setFlag(Boolean flag)
        {
            _flag = flag;
        }
    }

    /**
     * action to show all flagged runs flagged.jsp
     */
    @RequiresNoPermission
    public static class ShowFlaggedAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(FieldKey.fromParts("flagged"), true);
            RunDetail[] details = new TableSelector(TestResultsSchema.getTableInfoTestRuns(), filter, null).getArray(RunDetail.class);
            JspView<TestsDataBean> view = new JspView<>("/org/labkey/testresults/view/flagged.jsp", new TestsDataBean(details, new User[0]));
            view.setFrame(WebPartView.FrameType.PORTAL);
            return view;
        }
        @Override
        public void addNavTrail(NavTree root)
        {
            addModuleNavTrail(root, getContainer());
        }
    }

    @RequiresSiteAdmin
    public static class ChangeBoundaries extends MutatingApiAction<BoundariesForm>
    {
        @Override
        public Object execute(BoundariesForm form, BindException errors) throws Exception
        {
            //error handling - must be numbers, and limits on the range
            Map<String, String> res = new HashMap<>();

            Integer warningB = form.getWarningb();
            Integer errorB = form.getErrorb();

            if (warningB == null) {
                res.put("Message", "fail: warning boundary must be a number");
                return new ApiSimpleResponse(res);
            }
            if (errorB == null) {
                res.put("Message", "fail: error boundary must be a number");
                return new ApiSimpleResponse(res);
            }

            if (warningB <= 0 || errorB <= 0) {
                res.put("Message", "fail: the number must be positive");
                return new ApiSimpleResponse(res);
            } else if (warningB  > errorB) {
                res.put("Message", "fail: the warning boundary must be less than the error boundary");
                return new ApiSimpleResponse(res);
            } else if (warningB > 10 || errorB > 10) {
                res.put("Message", "fail: the number must be less than or equal to 10");
                return new ApiSimpleResponse(res);
            }

            GlobalSettings settings = new GlobalSettings(warningB, errorB);
            DbScope.Transaction transaction = TestResultsSchema.getSchema().getScope().ensureTransaction();
            SQLFragment sqlFragment = new SQLFragment();
            sqlFragment.append("select exists(select 1 from " + TestResultsSchema.getTableInfoGlobalSettings() + ") ");
            SqlSelector sqlSelector = new SqlSelector(TestResultsSchema.getSchema(), sqlFragment);
            List<Boolean> values = new ArrayList<>();
            sqlSelector.forEach(rs -> values.add(rs.getBoolean(1)));

            if (values.get(0)) {
                SQLFragment sqlFragmentDelete = new SQLFragment();
                sqlFragmentDelete.append("DELETE FROM " + TestResultsSchema.getTableInfoGlobalSettings());
                new SqlExecutor(TestResultsSchema.getSchema()).execute(sqlFragmentDelete);
            }
            SQLFragment sqlFragmentInsert = new SQLFragment();
            sqlFragmentInsert.append("INSERT INTO " + TestResultsSchema.getTableInfoGlobalSettings() + " (warningb, errorb) VALUES (" + warningB + ", " + errorB +")");
            new SqlExecutor(TestResultsSchema.getSchema()).execute(sqlFragmentInsert);
            transaction.commit();
            res.put("Message", "success!");
            return new ApiSimpleResponse(res);
        }
    }

    public static class BoundariesForm
    {
        private Integer _warningb;
        private Integer _errorb;

        public Integer getWarningb()
        {
            return _warningb;
        }
        public void setWarningb(Integer warningb)
        {
            _warningb = warningb;
        }
        public Integer getErrorb()
        {
            return _errorb;
        }
        public void setErrorb(Integer errorb)
        {
            _errorb = errorb;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class ViewLogAction extends ReadOnlyApiAction<RunIdForm>
    {
        @Override
        public Object execute(RunIdForm form, BindException errors)
        {
            if (form.getRunId() == null)
                return new ApiSimpleResponse("log", null);
            int runId = form.getRunId();
            SQLFragment sqlFragment = new SQLFragment();
            sqlFragment.append("SELECT log FROM testresults.testruns WHERE id = ?");
            sqlFragment.add(runId);
            List<byte[]> logs = new ArrayList<>();
            SqlSelector sqlSelector = new SqlSelector(TestResultsSchema.getSchema(), sqlFragment);
            sqlSelector.forEach(rs -> logs.add(rs.getBytes("log")));
            if (logs.isEmpty())
                return new ApiSimpleResponse("log", null);
            return new ApiSimpleResponse("log", RunDetail.decode(logs.get(0)));
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class ViewXmlAction extends ReadOnlyApiAction<RunIdForm>
    {
        @Override
        public Object execute(RunIdForm form, BindException errors)
        {
            if (form.getRunId() == null)
                return new ApiSimpleResponse("xml", null);
            int runId = form.getRunId();
            SQLFragment sqlFragment = new SQLFragment();
            sqlFragment.append("SELECT xml FROM testresults.testruns WHERE id = ?");
            sqlFragment.add(runId);
            List<byte[]> xmls = new ArrayList<>();
            SqlSelector sqlSelector = new SqlSelector(TestResultsSchema.getSchema(), sqlFragment);
            sqlSelector.forEach(rs -> xmls.add(rs.getBytes("xml")));
            if (xmls.isEmpty())
                return new ApiSimpleResponse("xml", null);
            return new ApiSimpleResponse("xml", RunDetail.decode(xmls.get(0)));
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class SendEmailNotificationAction extends ReadOnlyApiAction<SendEmailForm>
    {
        @Override
        public Object execute(SendEmailForm form, BindException errors)
        {
            org.labkey.api.security.User from;
            try
            {
                from = UserManager.getUser(new ValidEmail(SendTestResultsEmail.DEFAULT_EMAIL.ADMIN_EMAIL));
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                return new ApiSimpleResponse("error", e.getMessage());
            }
            String to = form.getTo();
            if (to == null || to.isEmpty())
                to = SendTestResultsEmail.DEFAULT_EMAIL.RECIPIENT;
            String subject = form.getSubject();
            if (subject == null)
                subject = "";
            String message = form.getMessage();
            if (message == null)
                message = "";
            List<String> recipients = Collections.singletonList(to);
            EmailService svc = EmailService.get();
            EmailMessage emailMsg = svc.createMessage(from.getEmail(), recipients, subject);
            emailMsg.addContent(MimeMap.MimeType.PLAIN, message);
            svc.sendMessages(Collections.singletonList(emailMsg), from, ContainerManager.getHomeContainer());
            return new ApiSimpleResponse("error", null);
        }
    }

    public static class SendEmailForm
    {
        private String _to;
        private String _subject;
        private String _message;

        public String getTo()
        {
            return _to;
        }
        public void setTo(String to)
        {
            _to = to;
        }
        public String getSubject()
        {
            return _subject;
        }
        public void setSubject(String subject)
        {
            _subject = subject;
        }
        public String getMessage()
        {
            return _message;
        }
        public void setMessage(String message)
        {
            _message = message;
        }
    }

    @RequiresSiteAdmin
    public static class SetEmailCronAction extends MutatingApiAction<EmailCronForm> {

        // NOTE: user needs read permissions on development folder

        @Override
        public Object execute(EmailCronForm form, BindException errors) throws Exception
        {
            String action = form.getAction();
            String emailFrom = form.getEmailF();
            String emailTo = form.getEmailT();
            Scheduler scheduler = new StdSchedulerFactory().getScheduler();
            JobKey jobKeyEmail = new JobKey(JOB_NAME, JOB_GROUP);
            Map<String, String> res = new HashMap<>();

            if (action == null) {
                res.put("Message", "Status");
                res.put("Response", "" + scheduler.checkExists(jobKeyEmail));
                return new ApiSimpleResponse(res);
            }

            switch (action) {
                case "status":
                    res.put("Message", "Status");
                    res.put("Response", "" + scheduler.checkExists(jobKeyEmail));
                    break;
                case "start":
                    // if already started
                    if (scheduler.checkExists(jobKeyEmail)) {
                        res.put("Message", "Job already exists");
                        res.put("Response", "true");
                        return new ApiSimpleResponse(res);
                    }

                    scheduler = start(scheduler, jobKeyEmail); // start
                    // ensure job exists after started
                    if (scheduler.checkExists(jobKeyEmail)) {
                        res.put("Message", "Job Created");
                        res.put("Response", "true");
                    } else {
                        res.put("Message", "Something failed creating job, contact Yuval");
                        res.put("Response", "false");
                    }
                    break;
                case "stop":
                    scheduler.deleteJob(jobKeyEmail);
                    if (!scheduler.checkExists(jobKeyEmail)) {
                        res.put("Message", "Job successfully removed");
                        res.put("Response", "false");
                    } else {
                        res.put("Message", "Job still running, error stopping.");
                        res.put("Response", "true");
                    }
                    break;
                case SendTestResultsEmail.TEST_GET_HTML_EMAIL:
                    SendTestResultsEmail testHtml = new SendTestResultsEmail(form.getGenerateDate());
                    Pair<String,String> msg = testHtml.getHTMLEmail(getViewContext().getUser());
                    res.put("subject", msg.first);
                    res.put("HTML", msg.second);
                    res.put("Response", "true");
                    break;
                case SendTestResultsEmail.TEST_ADMIN:
                    // test target send email immedately and only to Yuval
                    SendTestResultsEmail testAdmin = new SendTestResultsEmail(form.getGenerateDate());
                    ValidEmail admin = new ValidEmail(SendTestResultsEmail.DEFAULT_EMAIL.ADMIN_EMAIL);
                    testAdmin.execute(SendTestResultsEmail.TEST_ADMIN, UserManager.getUser(admin), SendTestResultsEmail.DEFAULT_EMAIL.ADMIN_EMAIL);
                    res.put("Message", "Testing testing 123");
                    res.put("Response", "true");
                    break;
                case SendTestResultsEmail.TEST_CUSTOM:
                    SendTestResultsEmail testCustom = new SendTestResultsEmail(form.getGenerateDate());
                    String error = "";
                    ValidEmail from = null;
                    ValidEmail to = null;
                    EmailValidator validator = EmailValidator.getInstance();
                    if (validator.isValid(emailFrom))
                        from = new ValidEmail(emailFrom);
                    else
                        error += "Email 'From' not valid.";

                    if (validator.isValid(emailTo))
                        to = new ValidEmail(emailTo);
                    else
                        error += "Email 'To' not valid.";

                    if (error.isEmpty()) {
                        org.labkey.api.security.User y = UserManager.getUser(from);
                        if (y == null)
                            error += "Sender email not a registered user.";
                    }
                    if (error.isEmpty()) {
                        res.put("Message", "Email sent from " + from.getEmailAddress() + " to " + to.getEmailAddress());
                        res.put("Response", "true");
                        testCustom.execute(SendTestResultsEmail.TEST_CUSTOM, UserManager.getUser(from), to.getEmailAddress());
                    } else {
                        res.put("Message", error);
                        res.put("Response", "false");
                    }
                    break;
            }
            return new ApiSimpleResponse(res);
        }

        @NotNull
        public static Scheduler start(Scheduler scheduler, JobKey jobKeyEmail) throws SchedulerException
        {
            JobDetail jobEmail = JobBuilder.newJob(SendTestResultsEmail.class).withIdentity(jobKeyEmail).build();
            Trigger trigger1 = TriggerBuilder
                    .newTrigger()
                    .withIdentity("TestResultsEmailTrigger", "TestResultsGroup")
                    .withSchedule(CronScheduleBuilder.cronSchedule("0 0 8 1/1 * ? *"))
                    .build();
            // runs job daily at 8am 0 0 8 1/1 * ? *
            // for testing(every minute): 0 0/1 * 1/1 * ? *
            scheduler.start();
            scheduler.scheduleJob(jobEmail, trigger1);
            return scheduler;
        }

    }

    public static class EmailCronForm
    {
        private String _action;
        private String _emailF;
        private String _emailT;
        private String _generatedate;

        public String getAction()
        {
            return _action;
        }
        public void setAction(String action)
        {
            _action = action;
        }
        public String getEmailF()
        {
            return _emailF;
        }
        public void setEmailF(String emailF)
        {
            _emailF = emailF;
        }
        public String getEmailT()
        {
            return _emailT;
        }
        public void setEmailT(String emailT)
        {
            _emailT = emailT;
        }
        public String getGeneratedate()
        {
            return _generatedate;
        }
        public void setGeneratedate(String generatedate)
        {
            _generatedate = generatedate;
        }

        public Date getGenerateDate()
        {
            try
            {
                return parseDate(_generatedate);
            }
            catch (ParseException e)
            {
                return null;
            }
        }
    }

    @RequiresSiteAdmin
    public static class SetUserActive extends MutatingApiAction<SetUserActiveForm>
    {
        @Override
        public Object execute(SetUserActiveForm form, BindException errors)
        {
            Map<String, String> res = new HashMap<>();
            if (form.getUserId() == null)
            {
                res.put("Message", "userId is required");
                return new ApiSimpleResponse(res);
            }
            if (form.isActive() == null)
            {
                res.put("Message", "active parameter is required (true to activate, false to deactivate)");
                return new ApiSimpleResponse(res);
            }
            boolean isActive = form.isActive();
            int userId = form.getUserId();

            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(FieldKey.fromParts("userid"), userId);
            filter.addCondition(FieldKey.fromParts("container"), getContainer());
            User[] users = new TableSelector(TestResultsSchema.getTableInfoUserData(), filter, null).getArray(User.class);
            if (users.length == 0) {
                res.put("Message", "User not found id="+userId);
                return new ApiSimpleResponse(res);
            }
            User user = users[0];
            user.setActive(isActive);
            try {
                Map<String, Object> userData = new HashMap<>();
                userData.put("active", new Parameter.TypedValue(isActive, JdbcType.BOOLEAN));
                Table.update(getUser(), TestResultsSchema.getTableInfoUserData(), userData, user.getId());
            } catch(Exception e) {
                res.put("Message", "Unable to update user id="+userId);
                return new ApiSimpleResponse(res);
            }

            res.put("Message", "Success");

            // DEFAULT TO CHECK STATUS
            return new ApiSimpleResponse(res);
        }
    }

    public static class SetUserActiveForm
    {
        private Boolean _active;
        private Integer _userId;

        public Boolean isActive()
        {
            return _active;
        }
        public void setActive(Boolean active)
        {
            _active = active;
        }
        public Integer getUserId()
        {
            return _userId;
        }
        public void setUserId(Integer userId)
        {
            _userId = userId;
        }
    }

    @RequiresSiteAdmin
    public static class RetrainAllAction extends MutatingApiAction<RetrainAllForm>
    {
        @Override
        public Object execute(RetrainAllForm form, BindException errors)
        {
            // Validate and clamp parameters
            int maxRuns = Math.max(1, Math.min(100, form.getMaxRuns()));
            int minRuns = Math.max(1, Math.min(maxRuns, form.getMinRuns()));
            boolean incremental = form.isIncremental();

            Container c = getContainer();
            String containerPath = c.getPath();
            int expectedDuration = containerPath.toLowerCase().contains("perf") ? 720 : 540;

            // Only look back 1.5x maxRuns days to avoid ancient data
            int lookbackDays = (int) Math.ceil(maxRuns * 1.5);
            java.sql.Timestamp cutoffDate = new java.sql.Timestamp(
                System.currentTimeMillis() - (long) lookbackDays * 24 * 60 * 60 * 1000);

            TestResultsManager mgr = TestResultsManager.get();
            DbScope scope = TestResultsSchema.getSchema().getScope();

            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                List<Integer> allUserIds = mgr.getRecentUserIds(c, cutoffDate);

                if (!incremental)
                {
                    mgr.deleteTrainRunsForContainer(c);
                    mgr.deleteUserDataForContainer(c);
                }

                int usersRetrained = 0;
                int totalTrainRuns = 0;

                for (int userId : allUserIds)
                {
                    Set<Integer> existingRunIds = mgr.getExistingTrainRunIds(userId, c);
                    List<Integer> recentCleanRunIds = mgr.getCleanRunIds(userId, c, cutoffDate, expectedDuration);

                    // Determine final set of trainrun IDs
                    List<Integer> finalRunIds;
                    if (incremental && !existingRunIds.isEmpty())
                    {
                        finalRunIds = mgr.getCandidateRunIds(userId, c, existingRunIds, recentCleanRunIds, maxRuns);
                    }
                    else
                    {
                        finalRunIds = recentCleanRunIds.size() > maxRuns
                            ? new ArrayList<>(recentCleanRunIds.subList(0, maxRuns))
                            : new ArrayList<>(recentCleanRunIds);
                    }

                    // Skip if total runs is below minimum threshold
                    if (finalRunIds.size() < minRuns)
                        continue;

                    // Determine runs to add and remove
                    Set<Integer> finalRunSet = new HashSet<>(finalRunIds);
                    List<Integer> runsToAdd = new ArrayList<>();
                    List<Integer> runsToRemove = new ArrayList<>();

                    for (int runId : finalRunIds)
                    {
                        if (!existingRunIds.contains(runId))
                            runsToAdd.add(runId);
                    }
                    for (int runId : existingRunIds)
                    {
                        if (!finalRunSet.contains(runId))
                            runsToRemove.add(runId);
                    }

                    mgr.removeTrainRuns(runsToRemove);
                    mgr.addTrainRuns(runsToAdd);

                    boolean isActive = finalRunIds.size() >= maxRuns;
                    mgr.upsertUserData(userId, c, finalRunIds, isActive);

                    usersRetrained++;
                    totalTrainRuns += runsToAdd.size();
                }

                transaction.commit();

                ApiSimpleResponse response = new ApiSimpleResponse();
                response.put(KEY_SUCCESS, true);
                response.put("usersRetrained", usersRetrained);
                response.put("totalTrainRuns", totalTrainRuns);
                response.put("mode", form.getMode());
                return response;
            }
            catch (Exception e)
            {
                _log.error("Error in RetrainAllAction", e);
                ApiSimpleResponse response = new ApiSimpleResponse();
                response.put(KEY_SUCCESS, false);
                response.put("error", e.getMessage());
                return response;
            }
        }
    }

    /**
     * action for posting test output as an xml file
     */
    @RequiresNoPermission
    public static class PostAction extends MutatingApiAction<Object>
    {

        @Override
        public Object execute(Object o, BindException errors) throws Exception
        {
            // DebugRequest(getViewContext().getRequest());
            if (!(getViewContext().getRequest() instanceof MultipartRequest request))
            {
                throw new Exception("Expected a request of type MultipartRequest got " + getViewContext().getRequest().getClass());
            }

            MultipartFile file = request.getFile("xml_file");
            if (file == null)
            {
                _log.error("xml_file not found in request");
                throw new Exception("xml_file not found in request");
            }

            String xml = new String(file.getBytes(), StandardCharsets.UTF_8);
            if (xml == null)
            {
                _log.error("XML from file is null");
                throw new Exception ("XML from xml_file is null");
            }
            else if (xml.isEmpty())
            {
                _log.error("XML from file is empty");
                throw new Exception ("XML from xml_file is empty");
            }
            else
                _log.info("XML from xml_file has length: " + xml.length());

            Map<String, Object> res = new HashMap<>();

            // try to parse and store xml, if fails save xml file to server to attempt to re-post manually by user
            try {
                _log.info("Handling SkylineNightly posted results");
                NIGHTLY_POSTER.ParseAndStoreXML(xml, getContainer());
            } catch (Exception e) {
                _log.info("XML failed to parse/store");
                _log.info("Attempting to save file for a future post attempt");
                res.put(KEY_SUCCESS, false);
                res.put("Message", "Error Parsing XML attempting to save the XML file...   " + NIGHTLY_POSTER.SaveXML(file, getContainer()));
                res.put("Exception", e + NIGHTLY_POSTER.getStackTraceText(e));
                return new ApiSimpleResponse(res);
            }

            return new ApiSimpleResponse(KEY_SUCCESS, true);
        }

        private void DebugRequest(HttpServletRequest hsRequest)
        {
            _log.info("Request is " + hsRequest.getClass());
            _log.info("Content length is : "+ hsRequest.getContentLength());
            _log.info("Content type: " + hsRequest.getContentType());
            Enumeration<String> headerNames = hsRequest.getHeaderNames();
            while(headerNames.hasMoreElements())
            {
                String headerName = headerNames.nextElement();
                _log.info("Header " + headerName + ": " + hsRequest.getHeader(headerName));
            }

            if (hsRequest instanceof MultipartRequest request)
            {
                _log.info("Multi part content type for xml: " + request.getMultipartContentType("xml"));
                _log.info("Multi part content type for xml_file: " + request.getMultipartContentType("xml_file"));
            }
        }
    }


    @RequiresPermission(ReadPermission.class)
    public static class ErrorFilesAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            File local = NIGHTLY_POSTER.getLocalPath(getContainer());
            File[] files = local.listFiles();
            if (files == null)
                files = new File[0];
            JspView view = new JspView<>("/org/labkey/testresults/view/errorFiles.jsp", files);
            view.setFrame(WebPartView.FrameType.PORTAL);
            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addModuleNavTrail(root, getContainer());
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class PostErrorFilesAction extends MutatingApiAction<Object>
    {
        @Override
        public Object execute(Object o, BindException errors)
        {
            Container c = getContainer();
            File local = NIGHTLY_POSTER.getLocalPath(c);
            File[] files = local.listFiles();
            Map<String, String> res = new HashMap<>();
            for (File f: files) {
                if (f.getName().equals(".upload.log")) // LabKey system file
                    continue;
                try {
                    String xml = FileUtils.readFileToString(f, Charset.defaultCharset());
                    NIGHTLY_POSTER.ParseAndStoreXML(xml, c);
                    f.delete();
                    res.put(f.getName(), "Success!");
                } catch (Exception e) {
                    res.put(f.getName(), Arrays.toString(e.getStackTrace()));
                }
            }
            return new ApiSimpleResponse(res);
        }
    }

    public static class NIGHTLY_POSTER
    {
        // used for formatting timestamps in hh:mm format as they may roll over multiple days
        public static Date addDays(Date date, int days)
        {
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            cal.add(Calendar.DATE, days); //minus number would decrement the days
            return cal.getTime();
        }

        public static String getStackTraceText(Exception e)
        {
            StringBuilder sb = new StringBuilder();
            for (StackTraceElement el : e.getStackTrace()) {
                sb.append(el).append("\r\n");   // Use windows new lines to display correctly in NotePad
            }
            return sb.toString();
        }
        public static File getLocalPath(Container c)
        {
            return FileContentService.get().getFileRootPath(c, FileContentService.ContentType.files).toFile();
        }

        public static File makeFile(Container c, String filename)
        {
            return new File(getLocalPath(c), FileUtil.makeLegalName(filename));
        }

        private static String SaveXML(MultipartFile file, Container c) {
            String fileName = file.getOriginalFilename();
            try
            {
                File f = makeFile(c, fileName);
                if(f.exists()) {
                    _log.info("A file by the name " + fileName + " is already stored.");
                    return "File not saved - file already exists in file system.";
                }
                file.transferTo(f);
            }
            catch (IOException e)
            {
                _log.error("Failed to save " + fileName + ".");
                e.printStackTrace();
                return "Failed to save the file.";
            }
            return "File saved to system.";
        }

        private static void ParseAndStoreXML(String xml, Container c) throws Exception
        {
            final String NA = "N/A";

            // pattern for parsing test lines from the log
            // group 1: hour
            // group 2: minute
            // group 3: pass
            // group 4: test name
            // group 5: language
            final Pattern TEST_LINE_PATTERN = Pattern.compile(
                "\\[(\\d\\d):(\\d\\d)]\\s+(\\d+)\\.\\d+\\s+([A-Za-z]\\w*)\\s+\\(([A-Za-z]+)\\)");

            try (DbScope.Transaction transaction = TestResultsSchema.getSchema().getScope().ensureTransaction()) {
                DocumentBuilder dBuilder = XmlBeansUtil.DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
                InputSource is = new InputSource();
                is.setCharacterStream(new StringReader(xml));
                Document doc = dBuilder.parse(is);

                Element docElement = doc.getDocumentElement();
                // USER ID
                String username = docElement.getAttribute("id");
                if (StringUtils.isBlank(username))
                    throw new IllegalArgumentException("Posted run XML is missing the required computer name (id attribute)");
                int userid = findUserIdByName(username);
                if (userid == -1) {
                    User newUser = new User();
                    newUser.setUsername(username);
                    User u = Table.insert(null, TestResultsSchema.getTableInfoUser(), newUser);
                    if (u == null) {
                        throw new Exception();
                    }
                    userid = u.getId();
                }
                if (userid == -1)
                    throw new Exception("Issue with user/userid, may not be set");
                String os = docElement.getAttribute("os");
                String date = docElement.getAttribute("start"); // FORMAT: MM/dd/YYYY hh:mm:ss a
                SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a", Locale.US);
                Date xmlTimestamp = sdf.parse(date); // test pst z
                int duration = Integer.parseInt(docElement.getAttribute("duration")); // in minutes
                final long ONE_MINUTE_IN_MILLIS = 60000;
                long timestampMilis = xmlTimestamp.getTime();
                Date postTime = new Date(timestampMilis + (duration * ONE_MINUTE_IN_MILLIS)); // duration + timestamp is date to display test
                int revision = Integer.parseInt(docElement.getAttribute("revision"));
                String gitHash = docElement.getAttribute("git_hash");

                // Get log
                String log = null;
                TestHangDetail testHang = null;
                if (doc.getDocumentElement().getElementsByTagName("Log").getLength() > 0) {
                    Node logN = docElement.getElementsByTagName("Log").item(0);
                    log = logN.getFirstChild().getNodeValue();
                    docElement.removeChild(logN); // remove log at the end so that it doesn't get stored with the xml

                    // find the last test that was run
                    for (int endl = log.lastIndexOf('\n'); endl >= 0; endl = log.lastIndexOf('\n', endl - 1))
                    {
                        int endl2 = log.indexOf('\n', endl + 1);
                        String line = (endl2 < 0 ? log.substring(endl + 1) : log.substring(endl + 1, endl2)).trim();
                        Matcher matcher = TEST_LINE_PATTERN.matcher(line);
                        if (!matcher.find())
                        {
                            continue;
                        }

                        // Determine the start date/time of the last test.
                        // Since we only have HH:MM, initialize the date to be the same as the post date.
                        Date lastTestStart = new Date(postTime.getTime());
                        lastTestStart.setHours(Integer.parseInt(matcher.group(1)));
                        lastTestStart.setMinutes(Integer.parseInt(matcher.group(2)));

                        long lastTestStartTimestamp = lastTestStart.getTime();
                        long postTimeTimestamp = postTime.getTime();

                        // This is a hack to not subtract days in the case when the last test start time is after
                        // the post time. Occasionally this happens for some reason.
                        // Allow a short tolerance of 5 minutes for this case.
                        // In the future, a better solution might be to include date information for tests in the posted
                        // results so that we don't have to guess what the date was.
                        if (lastTestStartTimestamp > postTimeTimestamp + (5 * ONE_MINUTE_IN_MILLIS))
                        {
                            // If the start time of the last test is after the post time, it means the last test
                            // start date is not actually the same as the post date (e.g. it started before midnight
                            // and the results were posted after midnight). So subtract days until it is before
                            // the post time.
                            while (lastTestStartTimestamp > postTimeTimestamp)
                            {
                                lastTestStartTimestamp -= 24 * 60 * 60 * 1000;
                            }
                        }

                        if (postTimeTimestamp - lastTestStartTimestamp > RunDetail.HANG_MILLISECONDS)
                        {
                            testHang = new TestHangDetail(-1, Integer.parseInt(matcher.group(3)),
                                    new Date(lastTestStartTimestamp), matcher.group(4), matcher.group(5));
                        }
                        break;
                    }
                }
                // Get leaks, failures, and passes
                List<TestMemoryLeakDetail> memoryLeaks = new ArrayList<>();
                List<TestHandleLeakDetail> handleLeaks = new ArrayList<>();
                List<TestFailDetail> failures = new ArrayList<>();
                List<TestPassDetail> passes = new ArrayList<>();
                // stores leaks in database
                NodeList nListLeaks = doc.getElementsByTagName("leaks");
                NodeList nlLeak = ((Element) nListLeaks.item(0)).getElementsByTagName("leak");
                for (int leakIndex = 0; leakIndex < nlLeak.getLength(); leakIndex++) {
                    Element elLeak = (Element) nlLeak.item(leakIndex);
                    String type = elLeak.getAttribute("type");
                    if (!elLeak.getAttribute("bytes").isEmpty()) { // process memory leak
                        memoryLeaks.add(new TestMemoryLeakDetail(0, elLeak.getAttribute("name"), type, (int)Float.parseFloat(elLeak.getAttribute("bytes"))));
                    } else if (!elLeak.getAttribute("handles").isEmpty()) { // process handle leak
                        handleLeaks.add(new TestHandleLeakDetail(0, elLeak.getAttribute("name"), type, Float.parseFloat(elLeak.getAttribute("handles"))));
                    } else {
                        _log.error("Error parsing Leak " + elLeak.getAttribute("name") + ".");
                        throw new IllegalArgumentException("Leak element missing both 'bytes' and 'handles' attributes: " + elLeak.getAttribute("name"));
                    }
                }

                // parse passes
                NodeList nListPasses = doc.getElementsByTagName("pass");
                int startHour = 0;
                int lastHour = 0;
                Date timestampDay = xmlTimestamp;
                int avgMemory = 0;
                for (int i = 0; i < nListPasses.getLength(); i++) {
                    NodeList nlTests = ((Element) nListPasses.item(i)).getElementsByTagName("test");
                    int passId = Integer.parseInt(((Element) nListPasses.item(i)).getAttribute("id"));
                    for (int j = 0; j < nlTests.getLength(); j++) {
                        Element test = (Element) nlTests.item(j);
                        Date timestamp;
                        String ts = test.getAttribute("timestamp");
                        try { // try parsing xmlTimestamp as date
                            timestamp = new Date(ts);
                        } catch (IllegalArgumentException e) {
                            timestamp = null;
                        }
                        // if "xmlTimestamp" is not a proper date assume it is in hh:mm format
                        if (ts != null && timestamp == null) {
                            int hour = Integer.valueOf(ts.split(":")[0]);
                            if (hour >= lastHour) {
                                if (lastHour == 0)
                                    startHour = hour;
                            } else {
                                timestampDay = addDays(timestampDay, 1);
                            }
                            lastHour = hour;
                            String originalDate = mdyFormat().format(timestampDay);
                            try {
                                timestamp = new SimpleDateFormat("MM/dd/yyyy HH:mm").parse(originalDate + " " + ts);
                            } catch (IllegalArgumentException e) {
                                timestamp = null;
                            }
                        }
                        if (test.getAttribute("duration").equals(NA) || test.getAttribute("managed").equals(NA) || test.getAttribute("total").equals(NA))
                            continue;
                        String committedAttr = test.getAttribute("committed");
                        String usergdiAttr = test.getAttribute("user_gdi");
                        String handlesAttr = test.getAttribute("handles");
                        TestPassDetail pass = new TestPassDetail(0, passId,
                                Integer.parseInt(test.getAttribute("id")),
                                test.getAttribute("name"),
                                test.getAttribute("language"),
                                Integer.parseInt(test.getAttribute("duration")),
                                Double.parseDouble(test.getAttribute("managed")),
                                Double.parseDouble(test.getAttribute("total")),
                                // New leak tracking values
                                StringUtils.isNotBlank(committedAttr) ? Double.parseDouble(committedAttr) : 0,
                                StringUtils.isNotBlank(usergdiAttr) ? Integer.parseInt(usergdiAttr) : 0,
                                StringUtils.isNotBlank(handlesAttr) ? Integer.parseInt(handlesAttr) : 0,
                                timestamp);
                        avgMemory += pass.getTotalMemory();
                        passes.add(pass);
                    }
                }
                if (!passes.isEmpty()) {
                    avgMemory /= passes.size();
                }
                // stores failures in database
                lastHour = startHour;
                timestampDay = xmlTimestamp;
                NodeList nListFailures = doc.getElementsByTagName("failures");
                NodeList nlFailure = ((Element) nListFailures.item(0)).getElementsByTagName("failure");
                for (int failureIndex = 0; failureIndex < nlFailure.getLength(); failureIndex++) {
                    Element elFailure = (Element) nlFailure.item(failureIndex);
                    Date timestamp;
                    String ts = elFailure.getAttribute("timestamp");
                    try { // try parsing xmlTimestamp as date
                        timestamp = new Date(ts);
                    } catch (IllegalArgumentException e) {
                        timestamp = null;
                    }
                    // if "xmlTimestamp" is not a proper date assume it is in hh:mm format
                    if (ts != null && timestamp == null) {
                        int hour = Integer.valueOf(ts.split(":")[0]);
                        if (hour < lastHour) {
                            timestampDay = addDays(timestampDay, 1);
                        }
                        lastHour = hour;
                        String originalDate = mdyFormat().format(timestampDay);
                        try {
                            timestamp = new SimpleDateFormat("MM/dd/yyyy HH:mm").parse(originalDate + " " + ts);
                        } catch (IllegalArgumentException e) {
                            timestamp = null;
                        }
                    }
                    TestFailDetail fail = new TestFailDetail(0, elFailure.getAttribute("name"), Integer.parseInt(elFailure.getAttribute("pass")), Integer.parseInt(elFailure.getAttribute("test")), elFailure.getAttribute("language"), elFailure.getTextContent(), timestamp);
                    failures.add(fail);
                }
                byte[] pointSummary = encodeRunPassSummary(passes.toArray(new TestPassDetail[0]));

                // Serialize the DOM (with <Log> removed) and compress for storage
                byte[] compressedXML = null;
                if (xml != null)
                {
                    Transformer transformer = TransformerFactory.newInstance().newTransformer();
                    StringWriter xmlWriter = new StringWriter();
                    transformer.transform(new DOMSource(docElement), new StreamResult(xmlWriter));
                    compressedXML = compressString(xmlWriter.toString());
                }
                byte[] compressedLog = log != null ? compressString(log) : null;

                RunDetail run = new RunDetail(userid, duration, postTime, xmlTimestamp, os, revision, gitHash, c, false, compressedXML,
                        pointSummary, passes.size(), failures.size(), memoryLeaks.size() + handleLeaks.size(), avgMemory, compressedLog); //TODO change date AND USERID
                // stores test run in database and gets the id(foreign key)
                run = Table.insert(null, TestResultsSchema.getTableInfoTestRuns(), run);
                int runId = run.getId();

                if (testHang != null) {
                    testHang.setTestRunId(runId);
                    Table.insert(null, TestResultsSchema.getTableInfoHangs(), testHang);
                }
                for (TestHandleLeakDetail leak : handleLeaks) {
                    leak.setTestRunId(runId);
                    Table.insert(null, TestResultsSchema.getTableInfoHandleLeaks(), leak);
                }
                for (TestMemoryLeakDetail leak : memoryLeaks) {
                    leak.setTestRunId(runId);
                    Table.insert(null, TestResultsSchema.getTableInfoMemoryLeaks(), leak);
                }
                for (TestFailDetail fail : failures) {
                    fail.setTestRunId(runId);
                    Table.insert(null, TestResultsSchema.getTableInfoTestFails(), fail);
                }
                for (TestPassDetail pass : passes) {
                    pass.setTestRunId(runId);
                    Table.insert(null, TestResultsSchema.getTableInfoTestPasses(), pass);
                }
                transaction.commit();
            } catch (Exception e) {
                _log.error("Error parsing xml");
                throw e;
            }
        }

        private static byte[] compressString(String s) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                OutputStream out = new GZIPOutputStream(baos);
                out.write(s.getBytes(StandardCharsets.UTF_8));
                out.close();
            } catch (IOException e) {
                _log.error("Error compressing", e);
                return new byte[0];
            }
            return baos.toByteArray();
        }
    }

    // Encodes pass point summary
    // Format looksstarts with test# where passes change, flag -1, then intensities of all tests run
    // test#, test#, test#, test#, -1, 0.0, 100.0, 120.2, 200.4, 202.3, etc...
    static byte[] encodeRunPassSummary(TestPassDetail[] passes) throws IOException
    {
        if (passes == null || passes.length == 0 || passes[0] == null)
            return new byte[0];

        Arrays.sort(passes);
        List<Double> shortenedRunData = new ArrayList<>();
        List<Integer> passLocations = new ArrayList<>();
        int currentPass = 0;
        for (int a = 0; a < passes.length; a++) {
            TestPassDetail pass = passes[a];
            if (a % POINT_RATIO == 1)
                shortenedRunData.add(pass.getTotalMemory());
            if (pass.getPass() != currentPass) {
                currentPass = pass.getPass();
                passLocations.add(a / POINT_RATIO);
            }
        }
        passLocations.add(-1);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        for (int element : passLocations) {
            out.writeUTF(Integer.toString(element) + ',');
        }
        for (int i = 0; i < shortenedRunData.size(); i++) {
            double element = shortenedRunData.get(i);
            if (i < shortenedRunData.size() - 1) {
                out.writeUTF(Double.toString(element) + ',');
            } else {
                out.writeUTF(Double.toString(element));
            }
        }
        return baos.toByteArray();
    }

    public static RunDetail[] getRunsSinceDate(Date start, Date end, Container c, User u, boolean getXML, boolean getLog) {
        return getRunsSinceDate(start, end, c, u, null, getXML, getLog);
    }

    /*
    * Given a date will return all runs up to now
    * */
    public static RunDetail[] getRunsSinceDate(Date start, Date end, Container c, User u, String includeData, boolean getXML, boolean getLog) {
        if (end == null) {
            end = DateUtils.addDays(DateUtils.truncate(new Date(), Calendar.DAY_OF_MONTH), 1);
        }
        final String isTrain = "EXISTS(SELECT 1 FROM " + TestResultsSchema.getTableInfoTrain() + " WHERE runid = testruns.id)";
        SQLFragment sqlFragment = new SQLFragment();
        sqlFragment.append(
            "SELECT testruns.*, u.username, " + isTrain + " AS traindata " +
            "FROM "+TestResultsSchema.getTableInfoTestRuns()+" " +
            "JOIN testresults.user AS u ON testruns.userid = u.id " +
            "WHERE ");
        if (u != null)
        {
            sqlFragment.append("username = ? AND ");
            sqlFragment.add(u.getUsername());
        }
        if (includeData != null && includeData.equalsIgnoreCase("train"))
        {
            sqlFragment.append(isTrain);
        }
        else if (includeData != null && includeData.equalsIgnoreCase("both"))
        {
            sqlFragment.append("(" + isTrain + " OR (? <= posttime AND posttime < ?))");
            sqlFragment.add(start);
            sqlFragment.add(end);
        }
        else
        {
            sqlFragment.append("? <= posttime AND posttime < ?");
            sqlFragment.add(start);
            sqlFragment.add(end);
        }
        return executeGetRunsSQLFragment(sqlFragment, c, getXML, getLog);
    }

    // executes a sql fragment to get runs and return an array RunDetail[]
    public static RunDetail[] executeGetRunsSQLFragment(SQLFragment fragment, Container c, boolean getXML, boolean getLog) {
        if (c != null) {
            fragment.append(" AND container = ?");
            fragment.add(c.getEntityId());
        }
        List<RunDetail> runs = new ArrayList<>();
        SqlSelector sqlSelector = new SqlSelector(TestResultsSchema.getSchema(), fragment);
        sqlSelector.forEach(rs -> {
            RunDetail run = new RunDetail();
            run.setId(rs.getInt("id"));
            run.setUserid(rs.getInt("userid"));
            run.setUserName(rs.getString("username"));
            run.setTimestamp(rs.getTimestamp("timestamp"));
            run.setPosttime(rs.getTimestamp("posttime"));
            run.setDuration(rs.getInt("duration"));
            run.setFlagged(rs.getBoolean("flagged"));
            run.setOs(rs.getString("os"));
            run.setRevision(rs.getInt("revision"));
            run.setGitHash(rs.getString("gitHash"));
            run.setContainer(ContainerManager.getForId(rs.getString("container")));
            run.setTrainRun(rs.getBoolean("traindata"));
            byte[] ptSummary = rs.getBytes("pointsummary");
            run.setPointsummary(ptSummary);
            run.setPassedtests(rs.getInt("passedtests"));
            run.setFailedtests(rs.getInt("failedtests"));
            run.setLeakedtests(rs.getInt("leakedtests"));
            run.setAveragemem(rs.getInt("averagemem"));

            if (getXML)
                run.setXml(rs.getBytes("xml"));
            if (getLog)
                run.setLog(rs.getBytes("log"));
            if (run.getTimestamp() == null)
                run.setTimestamp(run.getPostTime());

            runs.add(run);
        });
        return runs.toArray(new RunDetail[0]);
    }

    /*
    * Given a viewType as 'wk' = week, 'mo' = month, 'yr' = year, defaults to defaultTo
    * returns a starting date based on the time frame selected and the current date
    * */
    static Date getStartDate(String viewType, String defaultTo, Date endDate) {
        Date startDate;
        long DAY_IN_MS = 1000 * 60 * 60 * 24;
        long currentTime = endDate.getTime();
        if (viewType == null)
            viewType = defaultTo;
        switch (viewType)   // all the if/else to set dates for runs based on parameters
        {
            case "wk":
            default:
                startDate = new Date(currentTime - (7 * DAY_IN_MS)); // week
                break;
            case "mo":
                startDate = new Date(currentTime - (30 * DAY_IN_MS)); // month
                break;
            case "yr":
                startDate = new Date(currentTime - (365 * DAY_IN_MS)); // year
                break;
            case "at":
                long d = 1420070400000L;
                startDate = new Date(d); // all time
                break;
            case "day":
                startDate = new Date(currentTime - (DAY_IN_MS)); // day
                break;
        }
        return startDate;
    }

    private static SimpleFilter filterByRunId(RunDetail[] runs) {
        List<Integer> allRunIds = new ArrayList<>();
        for (RunDetail run: runs) {
            allRunIds.add(run.getId());
        }
        SimpleFilter filter = new SimpleFilter();
        SimpleFilter.InClause in = new SimpleFilter.InClause(FieldKey.fromParts("testrunid"), allRunIds);
        filter.addClause(in);
        return filter;
    }

    static void populatePassesLeaksFails(RunDetail[] runs)
    {
        populatePasses(runs);
        populateLeaks(runs);
        populateFailures(runs);
        populateHangs(runs);
    }

    static TestPassDetail[] getPassesForRun(RunDetail run) {
        if (run == null)
            return null;
        RunDetail[] runs = new RunDetail[]{run};
        populatePasses(runs);
        return runs[0].getPasses();
    }
    static TestFailDetail[] getFailuresForRun(RunDetail run) {
        if (run == null)
            return null;
        RunDetail[] runs = new RunDetail[]{run};
        populateFailures(runs);
        return runs[0].getFailures();
    }
    static TestLeakDetail[] getLeaksForRun(RunDetail run) {
        if (run == null)
            return null;
        RunDetail[] runs = new RunDetail[]{run};
        populateLeaks(runs);
        return runs[0].getLeaks();
    }
    /*
    * Given a set of run details this method queries and populates each RunDetail with corresponding
    * TestFailDetails, TestPassDetails, and TestLeakDetails from the database
    */
    static void populatePasses(RunDetail[] runs) {
        if (runs == null || runs.length == 0)
            return;

        SimpleFilter filter = filterByRunId(runs);
        Sort sortById = new Sort(FieldKey.fromString("id"));

        TestPassDetail[] passes = new TableSelector(TestResultsSchema.getTableInfoTestPasses(), filter, sortById).getArray(TestPassDetail.class);

        Map<Integer, List<TestPassDetail>> testPassDetails = new IntHashMap<>();
        int id = 0;
        for (TestPassDetail pass : passes) {
            if (id != pass.getTestRunId()) {
                id = pass.getTestRunId();
                testPassDetails.put(pass.getTestRunId(), new ArrayList<>());
            }
            testPassDetails.get(pass.getTestRunId()).add(pass);
        }

        for (RunDetail run : runs) {
            List<TestPassDetail> passList = testPassDetails.get(run.getId());
            if (passList != null)
                run.setPasses(passList);
            else
                run.setPasses(new TestPassDetail[0]);
        }
    }

    static void populateLastPassForRuns(RunDetail[] runs) {
        List<Integer> runIds = new ArrayList<>();
        for (RunDetail r : runs)
            runIds.add(r.getId());

        SQLFragment sqlFragment = new SQLFragment();
//        SimpleFilter filter = new SimpleFilter();
//        filter.addCondition()
//        TestPassDetail[] passes = new TableSelector(TestResultsSchema.getInstance().getTableInfoTestFails(), filter, null).getArray(TestPassDetail.class);
        sqlFragment.append(" SELECT * FROM testresults.testpasses WHERE id = ANY(SELECT MAX(id) FROM " +
                "testresults.testpasses WHERE (testrunid = ANY(?)) GROUP BY testrunid)");
        sqlFragment.add(runIds);
        SqlSelector sqlSelector = new SqlSelector(TestResultsSchema.getSchema(), sqlFragment);
        List<TestPassDetail> passes = new ArrayList<>();
        sqlSelector.forEach(rs -> {
            TestPassDetail pass = new TestPassDetail();
            pass.setId(rs.getInt("id"));
            pass.setTestId(rs.getInt("testid"));
            pass.setTestRunId(rs.getInt("testrunid"));
            pass.setTimestamp(rs.getTimestamp("timestamp"));
            pass.setDuration(rs.getInt("duration"));
            pass.setLanguage(rs.getString("language"));
            passes.add(pass);
        });

        for (RunDetail r: runs) {
            for (TestPassDetail pass: passes) {
                if (pass.getTestRunId() == r.getId())
                    r.setPasses(new TestPassDetail[]{pass});
            }
        }
    }

    static void populateFailures(RunDetail[] runs) {
        if (runs == null || runs.length == 0)
            return;

        SimpleFilter filter = filterByRunId(runs);

        TestFailDetail[] fails = new TableSelector(TestResultsSchema.getTableInfoTestFails(), filter, null).getArray(TestFailDetail.class);
        Map<Integer, List<TestFailDetail>> testFailDetails = new IntHashMap<>();

        for (TestFailDetail fail : fails) {
            List<TestFailDetail> list = testFailDetails.get(fail.getTestRunId());
            if (null == list) {
                list = new ArrayList<>();
            }
            list.add(fail);
            testFailDetails.put(fail.getTestRunId(), list);
        }

        for (RunDetail run : runs) {
            int runId = run.getId();
            List<TestFailDetail> failList = testFailDetails.get(runId);
            run.setFailures(failList != null
                    ? failList.toArray(new TestFailDetail[0])
                    : new TestFailDetail[0]);
        }
    }

    static void populateLeaks(RunDetail[] runs) {
        if (runs == null || runs.length == 0)
            return;

        SimpleFilter filter = filterByRunId(runs);

        List<TestLeakDetail> leaks = new ArrayList<>();
        Collections.addAll(leaks, new TableSelector(TestResultsSchema.getTableInfoMemoryLeaks(), filter, null).getArray(TestMemoryLeakDetail.class));
        Collections.addAll(leaks, new TableSelector(TestResultsSchema.getTableInfoHandleLeaks(), filter, null).getArray(TestHandleLeakDetail.class));
        Map<Integer, List<TestLeakDetail>> testLeakDetails = new IntHashMap<>();

        for (TestLeakDetail leak : leaks) {
            List<TestLeakDetail> list = testLeakDetails.get(leak.getTestRunId());
            if (null == list) {
                list = new ArrayList<>();
            }
            list.add(leak);
            testLeakDetails.put(leak.getTestRunId(), list);
        }
        for (RunDetail run : runs) {
            int runId = run.getId();
            List<TestLeakDetail> leakList = testLeakDetails.get(runId);
            run.setLeaks(leakList != null ? leakList.toArray(new TestLeakDetail[0]) : new TestLeakDetail[0]);
        }
    }

    static void populateHangs(RunDetail[] runs)
    {
        if (runs == null || runs.length == 0)
            return;

        SimpleFilter filter = filterByRunId(runs);

        TestHangDetail[] hangs = new TableSelector(TestResultsSchema.getTableInfoHangs(), filter, null).getArray(TestHangDetail.class);
        Map<Integer, TestHangDetail> testHangDetails = new IntHashMap<>();

        for (TestHangDetail hang : hangs) {
            testHangDetails.put(hang.getTestRunId(), hang);
        }
        for (RunDetail run : runs) {
            TestHangDetail hang = testHangDetails.get(run.getId());
            if (hang != null)
                run.setHang(hang);
        }
    }

    public static Date setToEightAM(Date d) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(d);
        setToEightAM(cal);
        return cal.getTime();
    }

    public static void setToEightAM(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 8);
        c.set(Calendar.MINUTE, 1);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }
}
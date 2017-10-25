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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
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

import javax.servlet.http.HttpServletRequest;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.labkey.testresults.TestResultsModule.JOB_GROUP;
import static org.labkey.testresults.TestResultsModule.JOB_NAME;
import static org.labkey.testresults.TestResultsModule.TR_VIEW;
import static org.labkey.testresults.TestResultsModule.ViewType;

/**
 * User: Yuval Boss, yuval(at)uw.edu
 * Date: 1/14/2015
 */
public class TestResultsController extends SpringActionController
{
    private static final Logger _log = Logger.getLogger(TestResultsController.class);

    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(TestResultsController.class);
    public TestResultsController()
    {
        setActionResolver(_actionResolver);
    }
    public static final int POINT_RATIO = 30;

    /**
     * action to view rundown.jsp and also the landing page for module
     */
    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            TestsDataBean bean = getRunDownData(getUser(), getContainer(), getViewContext());
            return new JspView("/org/labkey/testresults/view/rundown.jsp", bean);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    // return TestDataBean specifically for rundown.jsp aka the home page of the module
    public static TestsDataBean getRunDownData(org.labkey.api.security.User user, Container c, ViewContext viewContext) throws ParseException, IOException
    {
        String end = viewContext.getRequest().getParameter("end");
        String viewType = viewContext.getRequest().getParameter("viewType");
        SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
        boolean givenDate = end != null && !end.equals("");
        Date endDate = new Date();
        if(givenDate)
            endDate = formatter.parse(end);

        Calendar cal = Calendar.getInstance();
        cal.setTime(endDate);
        cal.set(Calendar.HOUR_OF_DAY, 8);
        cal.set(Calendar.MINUTE, 1);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        endDate = cal.getTime();
        cal.add(Calendar.DATE, -1);
        Date dateBefore1Day = cal.getTime();
        viewType = getViewType(user, viewType, ViewType.MONTH, "rundown-longterm", c); // rundown-longterm view type


        Date startDate = getStartDate(viewType, ViewType.MONTH, endDate); // defaults to month

        RunDetail[] allRuns = getRunsSinceDate(startDate, endDate, c, null, false, false);
        List<RunDetail> todaysRuns = new ArrayList<>();
        List<RunDetail> monthRuns = new ArrayList<>();
        List<Integer> monthRunIds = new ArrayList<>();

        for (RunDetail run:allRuns) {
            monthRunIds.add(run.getId());
            if(run.getPostTime().getTime() > dateBefore1Day.getTime() && run.getPostTime().getTime() < endDate.getTime()) {
                todaysRuns.add(run);
            } else {
                monthRuns.add(run);
            }
        }

        // show blank page if no runs exist
        if(todaysRuns.size() == 0 && monthRuns.size() == 0)
            return new TestsDataBean(new RunDetail[0]);

        RunDetail[] today = todaysRuns.toArray(new RunDetail[todaysRuns.size()]);
        if(todaysRuns.size() > 0)
            populateLastPassForRuns(today);

        // merge todays runs with past months runs to get array of all runs this week
        allRuns = ArrayUtils.addAll(monthRuns.toArray(new RunDetail[monthRuns.size()]), today);
        // get all data for leaks and failures for runs of past month
        populateLeaks(allRuns);
        populateFailures(allRuns);

        ensureRunDataCached(allRuns, false);

        User[] users = getTrainingDataForContainer(c);
        return new TestsDataBean(allRuns, users, viewType, null, endDate);
    }

    private static String getViewType(org.labkey.api.security.User u, String viewType, String defaultViewType, String groupName, Container c) {
        PropertyManager.PropertyMap m = PropertyManager.getWritableProperties(u, c, TR_VIEW, true);
        boolean isValidView = isValidViewType(viewType);
        if(!m.containsKey(groupName)) {
            if(isValidView)
                m.put(groupName, viewType);
            else
                m.put(groupName, defaultViewType);
        } else {
            if(isValidView)
                m.put(groupName, viewType);
            else
                viewType = m.get(groupName);
        }
        m.save();
        return viewType;
    }

    // ensure all runs have necessary cached data
    // if not cache data this one time for future use
    public static void ensureRunDataCached(RunDetail[] runs, boolean keepObjData) {
        // dont want memory running out, anything over 100 gets cached then cleaned afterwards
        if(runs.length > 100)
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
                    {
                        passes = getPassesForRun(run);
                    }
                    if (failures == null)
                    {
                        failures = getFailuresForRun(run);
                    }
                    if (leaks == null)
                    {
                        leaks = getLeaksForRun(run);
                    }
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
                    Table.update(null, TestResultsSchema.getInstance().getTableInfoTestRuns(), runMap, run.getId());

                    // Set all to empty array so that memory does not run out.
                    // Each run ideally has 10k test passes so server
                    // could throw a mem error pretty quick if we didn't do this.
                    if(!keepObjData) {
                        run.setPasses(new TestPassDetail[0]);
                        run.setFailures(new TestFailDetail[0]);
                        run.setLeaks(new TestLeakDetail[0]);
                    }
                }
            }
            transaction.commit();
        }
    }


    private static  void populatePassCounts(List<Integer> runIds, List<RunDetail> runs) {
        SQLFragment sqlFragment = new SQLFragment();
        sqlFragment.append("\tSELECT testpasses.testrunid, COUNT(*)\n" +
                "    \tFROM testresults.testpasses\n" +
                "    \tWHERE\n" +
                "    \t(testrunid= ANY(?))\n" +
                "    \tGROUP BY testrunid ;\n" +
                "    \t;");
        sqlFragment.add(runIds);
        SqlSelector sqlSelector = new SqlSelector(TestResultsSchema.getSchema(), sqlFragment);
        Map<Integer, Integer> runPasses = new HashMap<>();
        sqlSelector.forEach(rs -> runPasses.put(rs.getInt("testrunid"), rs.getInt("count")));
        for(RunDetail run: runs) {
            if(run.getPasses() == null || run.getPasses().length == 0) {
                if(runPasses.get(run.getId()) != null) {
                    int passCount = runPasses.get(run.getId());
                    run.setPasses(new TestPassDetail[passCount]);
                } else {
                    run.setPasses(new TestPassDetail[0]);
                }
            }
        }
    }

    public static User[] getTrainingDataForContainer(Container c) {
        User[] users = new TableSelector(TestResultsSchema.getInstance().getTableInfoUser(), null, null).getArray(User.class);
        SQLFragment sqlFragment = new SQLFragment();
        sqlFragment.append("SELECT * FROM testresults.userdata WHERE container = ?;");
        sqlFragment.add(c.getEntityId().toString());
        SqlSelector sqlSelector = new SqlSelector(TestResultsSchema.getSchema(), sqlFragment);
        sqlSelector.forEach(rs -> {
            for(User user : users) {
                if(user.getId() == rs.getInt("userid")) {
                    user.setMeanmemory(rs.getDouble("meanmemory"));
                    user.setMeantestsrun(rs.getDouble("meantestsrun"));
                    user.setStddevtestsrun(rs.getDouble("stddevtestsrun"));
                    user.setStddevmemory(rs.getDouble("stddevmemory"));
                    user.setContainer(c);
                    user.setActive(rs.getBoolean("active"));
                }
            }
        });
        Arrays.sort(users);
        return users;
    }

    /**
     * action to view trainging data for each user
     */
    @RequiresPermission(ReadPermission.class)
    public class TrainingDataViewAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            List<Integer> foundRuns = new ArrayList<>();
            SQLFragment sqlFragment = new SQLFragment();
            sqlFragment.append("SELECT * FROM testresults.trainruns;");
            SqlSelector sqlSelector = new SqlSelector(TestResultsSchema.getSchema(), sqlFragment);
            sqlSelector.forEach(rs -> foundRuns.add(rs.getInt("runid")));
            SimpleFilter filter = new SimpleFilter();
            SimpleFilter.InClause in = new SimpleFilter.InClause(FieldKey.fromParts("id"), foundRuns);
            filter.addClause(in);
            filter.addCondition(FieldKey.fromParts("container"), getContainer());
            RunDetail[] runs = new TableSelector(TestResultsSchema.getInstance().getTableInfoTestRuns(), filter, null).getArray(RunDetail.class);

            ensureRunDataCached(runs, false);

            User[] users = getTrainingDataForContainer(getContainer());
            TestsDataBean bean = new TestsDataBean(runs, users);
            return new JspView("/org/labkey/testresults/view/trainingdata.jsp", bean);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    // API endpoint for adding or removing a run for the training set needs parameters: runId=int&train=boolean
    @RequiresPermission(AdminPermission.class)
    public class TrainRunAction extends ApiAction {
        @Override
        public Object execute(Object o, BindException errors) throws Exception
        {
            boolean somethingChanged = false;
            int rowId = Integer.parseInt(getViewContext().getRequest().getParameter("runId"));
            boolean train = Boolean.parseBoolean(getViewContext().getRequest().getParameter("train"));
            List<Integer> foundRuns = new ArrayList<>();
            SQLFragment sqlFragment = new SQLFragment();
            sqlFragment.append("SELECT * FROM testresults.trainruns ");
            sqlFragment.append("WHERE runid = ?;");
            sqlFragment.add(rowId);
            SqlSelector sqlSelector = new SqlSelector(TestResultsSchema.getSchema(), sqlFragment);
            sqlSelector.forEach(rs -> foundRuns.add(rs.getInt("runid")));
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(FieldKey.fromParts("id"), rowId);
            RunDetail[] details = new TableSelector(TestResultsSchema.getInstance().getTableInfoTestRuns(), filter, null).getArray(RunDetail.class);
            if(details.length == 0) {
                rowId = -1;  // run Does not exist
            }
            if(rowId == -1) {
                return new ApiSimpleResponse("Success", false);
            } else if(train && foundRuns.size() > 0) {
                return new ApiSimpleResponse("Success", false);
            }  else
            {
                try (DbScope.Transaction transaction = TestResultsSchema.getSchema().getScope().ensureTransaction())
                {
                    if (train && foundRuns.size() == 0)
                    {
                        SQLFragment sqlFragmentInsert = new SQLFragment();
                        sqlFragmentInsert.append("INSERT INTO testresults.trainruns (runid) VALUES (?);");
                        sqlFragmentInsert.add(rowId);
                        new SqlExecutor(TestResultsSchema.getSchema()).execute(sqlFragmentInsert);
                        somethingChanged = true;
                    }
                    else if (!train && foundRuns.size() == 1)
                    {
                        SQLFragment sqlFragmentDelete = new SQLFragment();
                        sqlFragmentDelete.append("DELETE FROM testresults.trainruns WHERE runid = ?;");
                        sqlFragmentDelete.add(rowId);
                        new SqlExecutor(TestResultsSchema.getSchema()).execute(sqlFragmentDelete);
                        somethingChanged = true;
                    }
                    if (somethingChanged)
                    {
                        // UPDATE USER TABLE CALCULATIONS
                        int userId = details[0].getUserid();
                        SQLFragment sqlFragmentUserRuns = new SQLFragment();
                        sqlFragmentUserRuns.append("SELECT testruns.*, u.username, EXISTS(SELECT 1 FROM testresults.trainruns WHERE runid = testruns.id) AS traindata FROM testresults.testruns ");
                        sqlFragmentUserRuns.append("JOIN testresults.user AS u ON testruns.userid = u.id ");
                        sqlFragmentUserRuns.append("WHERE userid = ?  ");
                        sqlFragmentUserRuns.add(userId);
                        RunDetail[] userRuns = executeGetRunsSQLFragment(sqlFragmentUserRuns, getContainer(), false, false);
                        List<RunDetail> trendDataForUser = new ArrayList<>();
                        for(RunDetail run: userRuns) {
                            if(!run.isTrainRun()) // if a run is removed will show up in set still
                                continue;
                            trendDataForUser.add(run);
                        }
                        // See if training data for a user in a container exists first
                        SQLFragment containerUserExists = new SQLFragment();
                        containerUserExists.append("SELECT id FROM testresults.userdata WHERE userid = ? AND container = ?;");
                        containerUserExists.add(userId);
                        containerUserExists.add(getContainer().getEntityId());
                        SqlSelector selector = new SqlSelector(TestResultsSchema.getSchema(), containerUserExists);
                        int foundRowId = 0; // 0 if not found
                        if(selector.getObject(Integer.class) != null)
                            foundRowId = selector.getObject(Integer.class);
                        SQLFragment sqlFragmentUpdate = new SQLFragment();
                        if(foundRowId != 0) {
                            sqlFragmentUpdate.append("UPDATE testresults.userdata SET ");
                            sqlFragmentUpdate.append("meantestsrun = ?, ");
                            sqlFragmentUpdate.append("meanmemory = ?, ");
                            sqlFragmentUpdate.append("stddevtestsrun = ?, ");
                            sqlFragmentUpdate.append("stddevmemory = ? ");
                            sqlFragmentUpdate.append("WHERE id = ? ");
                        } else {
                            sqlFragmentUpdate.append("INSERT INTO testresults.userdata (userid, container, meantestsrun, meanmemory, stddevtestsrun, stddevmemory) ");
                            sqlFragmentUpdate.append("VALUES(?, ?, ?,?,?,?);");
                            sqlFragmentUpdate.add(userId);
                            sqlFragmentUpdate.add(getContainer().getEntityId());
                        }



                        if(trendDataForUser.size() == 0) {
                            sqlFragmentUpdate.add(0.0);
                            sqlFragmentUpdate.add(0.0);
                            sqlFragmentUpdate.add(0.0);
                            sqlFragmentUpdate.add(0.0);
                        } else {
                            RunDetail[] arr = trendDataForUser.toArray(new RunDetail[trendDataForUser.size()]);
                            populatePassesLeaksFails(arr);
                            List<Double> memoryValuesList = new ArrayList<>();
                            List<Double> testsRunValuesList = new ArrayList<>();
                            for(RunDetail run: arr) {
                                TestPassDetail[] passes = run.getPasses();
                                for(TestPassDetail p: passes) {
                                    memoryValuesList.add(p.getTotalMemory());
                                }
                                testsRunValuesList.add((double)passes.length);
                            }
                            TRStats memStats = new TRStats(memoryValuesList);
                            TRStats testsRunStats = new TRStats(testsRunValuesList);
                            sqlFragmentUpdate.add(testsRunStats.getMean());
                            sqlFragmentUpdate.add(memStats.getMean());
                            sqlFragmentUpdate.add(testsRunStats.getStdDev());
                            sqlFragmentUpdate.add(memStats.getStdDev());
                        }
                        if(foundRowId != 0) {
                            sqlFragmentUpdate.add(foundRowId);
                        }
                        new SqlExecutor(TestResultsSchema.getSchema()).execute(sqlFragmentUpdate);
                    }
                    transaction.commit();
                }
            }
            return new ApiSimpleResponse("Success", true);
        }

    }

   /**
    * action to view user.jsp and all run details for user in date selection
    * accepts a url parameter "user" which will be the user that the jsp displays runs for
    * accepts url parameter "start" and "end" which will be the date range of selected runs for that user to display
    */
    @RequiresPermission(ReadPermission.class)
    public class ShowUserAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            String start = getViewContext().getRequest().getParameter("start");
            String end = getViewContext().getRequest().getParameter("end");
            String userName = getViewContext().getRequest().getParameter("user");

            SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
            Date endDate = new Date();
            Date startDate;
            if(start == null)
                startDate = DateUtils.addDays(new Date(), -6); // DEFUALT TO LAST WEEK's RUNS
            else
                startDate = formatter.parse(start);
            if(end != null)
                endDate = DateUtils.addMilliseconds(DateUtils.ceiling(formatter.parse(end), Calendar.DATE), 0);

            RunDetail[] runs;
            // If no username specified show info summary for all users
            if(userName == null || userName.equals(""))
            {
                runs = getRunsSinceDate(startDate, endDate, getContainer(), null, false, false);
                populateFailures(runs);
            } else {
                String filterString = "AND username = '" + userName + "' ";
                runs = getRunsSinceDate(startDate, endDate, getContainer(), filterString, false, false);
            }
            ensureRunDataCached(runs, false);

            TestsDataBean bean = new TestsDataBean(runs);
            return new JspView("/org/labkey/testresults/view/user.jsp", bean);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

   /**
    * action to view runDetail.jsp (detail for a single run)
    * accepts a url parameter "runId" which will be the run that the jsp displays the information of
    */
    @RequiresPermission(ReadPermission.class)
    public class ShowRunAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            int runId;
            try {
                runId = Integer.parseInt(getViewContext().getRequest().getParameter("runId"));
            } catch (Exception e) {
                return new JspView("/org/labkey/testresults/view/runDetail.jsp", null);
            }
            String filterTestPassesBy = getViewContext().getRequest().getParameter("filter");

            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(FieldKey.fromParts("testrunid"), runId);

            TestFailDetail[] fails = new TableSelector(TestResultsSchema.getInstance().getTableInfoTestFails(), filter, null).getArray(TestFailDetail.class);
            TestPassDetail[] passes = new TableSelector(TestResultsSchema.getInstance().getTableInfoTestPasses(), filter, null).getArray(TestPassDetail.class);
            TestLeakDetail[] leaks = new TableSelector(TestResultsSchema.getInstance().getTableInfoTestLeaks(), filter, null).getArray(TestLeakDetail.class);

            SQLFragment sqlFragment = new SQLFragment();
            sqlFragment.append("SELECT testruns.*, u.username, EXISTS(SELECT 1 FROM testresults.trainruns WHERE runid = testruns.id) AS traindata FROM testresults.testruns ");
            sqlFragment.append("JOIN testresults.user AS u ON testruns.userid = u.id ");
            sqlFragment.append("WHERE testruns.id = ?");
            sqlFragment.add(runId);

            RunDetail[] runs = executeGetRunsSQLFragment(sqlFragment, getContainer(), false, true);
            if(runs.length == 0)
                return new JspView("/org/labkey/testresults/view/runDetail.jsp", null);
            RunDetail run = runs[0];
            if(run == null)
                return new JspView("/org/labkey/testresults/view/runDetail.jsp", null);
            if(filterTestPassesBy != null) {
                if(filterTestPassesBy.equals("duration")) {
                    List<TestPassDetail> filteredPasses = Arrays.asList(passes);
                    filteredPasses.sort((o1, o2) -> o2.getDuration() - o1.getDuration());
                    passes = filteredPasses.toArray(new TestPassDetail[passes.length]);
                }  else if(filterTestPassesBy.equals("managed")) {
                    List<TestPassDetail> filteredPasses = Arrays.asList(passes);
                    filteredPasses.sort((o1, o2) -> Double.compare(o2.getManagedMemory(), o1.getManagedMemory()));
                    passes = filteredPasses.toArray(new TestPassDetail[passes.length]);

                } else if(filterTestPassesBy.equals("total")) {
                    List<TestPassDetail> filteredPasses = Arrays.asList(passes);
                    filteredPasses.sort((o1, o2) -> Double.compare(o2.getTotalMemory(), o1.getTotalMemory()));
                    passes = filteredPasses.toArray(new TestPassDetail[passes.length]);
                }
            } else {
                Arrays.sort(passes);
            }
            run.setFailures(fails);
            run.setLeaks(leaks);
            run.setPasses(passes);
            TestsDataBean bean = new TestsDataBean(runs);
            return new JspView("/org/labkey/testresults/view/runDetail.jsp", bean);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

   /**
    * action to view longTerm.jsp
    * accepts a url parameter "viewType" of either wk(week), mo(month), or yr(year) and defaults to month
    */
    @RequiresPermission(ReadPermission.class)
    public class LongTermAction extends SimpleViewAction
   {
       @Override
       public ModelAndView getView(Object o, BindException errors) throws Exception
       {
           String viewType = getViewContext().getRequest().getParameter("viewType");

           TestsDataBean bean = new TestsDataBean(new RunDetail[0]); // bean that will be handed to jsp
           viewType = getViewType(getViewContext().getUser(), viewType, ViewType.YEAR, "longterm", getContainer());
           Date startDate = getStartDate(viewType, ViewType.YEAR, new Date()); // defaults to month
           bean.setViewType(viewType);
           RunDetail[] runs = getRunsSinceDate(startDate, null, getContainer(), null, false, false);
           bean.setRuns(runs);

           SimpleFilter filter = new SimpleFilter();
           filter.addClause(new CompareType.CompareClause(FieldKey.fromParts("timestamp"), CompareType.DATE_GTE, startDate));
           Sort s = new Sort("testrunid");
           TestFailDetail[] failures = new TableSelector(TestResultsSchema.getInstance().getTableInfoTestFails(), filter, s).getArray(TestFailDetail.class);
           bean.setNonAssociatedFailures(failures);

             ensureRunDataCached(runs, true);
           return new JspView("/org/labkey/testresults/view/longTerm.jsp", bean);
       }

       @Override
       public NavTree appendNavTrail(NavTree root)
       {
           return root;
       }
   }

    /**
     * action to view failureDetail.jsp
     * accepts parameter failedTest as name of the failed test
     * accepts parameter viewType as 'wk', 'mo', or 'yr'.  defaults to 'day'
     */
    @RequiresPermission(ReadPermission.class)
    public class ShowFailures extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            String failedTest = getViewContext().getRequest().getParameter("failedTest");
            String viewType = getViewContext().getRequest().getParameter("viewType");

            viewType = getViewType(getViewContext().getUser(), viewType, ViewType.DAY, "failures", getContainer());
            Date startDate = getStartDate(viewType, ViewType.DAY, new Date()); // defaults to day
            RunDetail[] runs = getRunsSinceDate(startDate, null, getContainer(), null, false, false);
            populateFailures(runs);

            if(failedTest == null)
                return new JspView("/org/labkey/testresults/view/failureDetail.jsp", runs);

            List<RunDetail> failureRuns = new ArrayList<>();
            for(RunDetail run: runs) {
                for(TestFailDetail fail: run.getFailures())
                    if(fail.getTestName().equals(failedTest)) {
                        failureRuns.add(run);
                        break;
                    }
            }
            TestsDataBean bean =  new TestsDataBean(failureRuns.toArray(new RunDetail[failureRuns.size()]));
            bean.setViewType(viewType);
            return new JspView("/org/labkey/testresults/view/failureDetail.jsp", bean);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    /**
     * action for deleting a run ex:'deleteRun.view?runId=x'
     */
    @RequiresPermission(AdminPermission.class)
    public class DeleteRunAction extends BeginAction {

        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            int rowId = Integer.parseInt(getViewContext().getRequest().getParameter("runId"));
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(FieldKey.fromParts("testrunid"), rowId);
            try (DbScope.Transaction transaction = TestResultsSchema.getInstance().getSchema().getScope().ensureTransaction()) {
                Table.delete(TestResultsSchema.getInstance().getTableInfoTestFails(), filter); // delete failures
                Table.delete(TestResultsSchema.getInstance().getTableInfoTestPasses(), filter); // delete passes
                Table.delete(TestResultsSchema.getInstance().getTableInfoTestLeaks(), filter); // delete leaks
                Table.delete(TestResultsSchema.getInstance().getTableInfoTestRuns(), rowId); // delete run last because of foreign key
                transaction.commit();
            }
            return super.getView(o, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class FlagRunAction extends ShowRunAction {

        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            int rowId = Integer.parseInt(getViewContext().getRequest().getParameter("runId"));
            boolean flag = Boolean.parseBoolean(getViewContext().getRequest().getParameter("flag"));

            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(FieldKey.fromParts("id"), rowId);
            try (DbScope.Transaction transaction = TestResultsSchema.getInstance().getSchema().getScope().ensureTransaction()) {
                RunDetail[] details = new TableSelector(TestResultsSchema.getInstance().getTableInfoTestRuns(), filter, null).getArray(RunDetail.class);
                RunDetail detail = details[0];
                if(getViewContext().getRequest().getParameter("flag") == null) // if not specified keep same
                    flag = detail.isFlagged();
                detail.setFlagged(flag);
                Table.update(null, TestResultsSchema.getInstance().getTableInfoTestRuns(),detail, detail.getId());
                transaction.commit();
            }
            getViewContext().getRequest().setAttribute("runId",rowId);
            return super.getView(o, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    /**
     * action to show all flagged runs flagged.jsp
     */
    @RequiresNoPermission
    public class ShowFlaggedAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(FieldKey.fromParts("flagged"), true);
            RunDetail[] details = new TableSelector(TestResultsSchema.getInstance().getTableInfoTestRuns(), filter, null).getArray(RunDetail.class);
            return new JspView("/org/labkey/testresults/view/flagged.jsp", new TestsDataBean(details));
        }
        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    @RequiresSiteAdmin
    public static class SetEmailCronAction extends ApiAction {
        @Override
        public Object execute(Object o, BindException errors) throws Exception
        {
            String action = getViewContext().getRequest().getParameter("action");
            String emailFrom = getViewContext().getRequest().getParameter("emailF");
            String emailTo = getViewContext().getRequest().getParameter("emailT");
            Scheduler scheduler = new StdSchedulerFactory().getScheduler();
            JobKey jobKeyEmail = new JobKey(JOB_NAME, JOB_GROUP);
            Map<String, String> res = new HashMap<>();

            if(action != null && action.equals("status")) { // check status
                res.put("Message", "Status");
                res.put("Response", "" + scheduler.checkExists(jobKeyEmail));
                return new ApiSimpleResponse(res);
            }
            if(action != null && action.equals("stop")) { // stop
                scheduler.deleteJob(jobKeyEmail);
                if(!scheduler.checkExists(jobKeyEmail)) {
                    res.put("Message", "Job successfully removed");
                    res.put("Response", "false");
                } else {
                    res.put("Message", "Job still running, error stopping.");
                    res.put("Response", "true");
                }
                return new ApiSimpleResponse(res);
            }

            if(action != null && action.equals(SendTestResultsEmail.TEST_GET_HTML_EMAIL))
            {
                SendTestResultsEmail test = new SendTestResultsEmail();
                Pair<String,String> msg = test.getHTMLEmail(getViewContext().getUser());
                res.put("subject", msg.first);
                res.put("HTML", msg.second);
                res.put("Response", "true");
                return new ApiSimpleResponse(res);
            }

            // test target send email immedately and only to Yuval
            if(action != null && action.equals(SendTestResultsEmail.TEST_ADMIN))
            {
                SendTestResultsEmail test = new SendTestResultsEmail();
                ValidEmail admin = new ValidEmail(SendTestResultsEmail.DEFAULT_EMAIL.ADMIN_EMAIL);
                test.execute(SendTestResultsEmail.TEST_ADMIN, UserManager.getUser(admin), SendTestResultsEmail.DEFAULT_EMAIL.ADMIN_EMAIL);
                res.put("Message", "Testing testing 123");
                res.put("Response", "true");
                return new ApiSimpleResponse(res);
            }

            if(action != null && action.equals(SendTestResultsEmail.TEST_CUSTOM))
            {
                SendTestResultsEmail test = new SendTestResultsEmail();
                String error = "";
                ValidEmail from = null;
                ValidEmail to = null;
                EmailValidator validator = EmailValidator.getInstance();
                if(validator.isValid(emailFrom))
                    from = new ValidEmail(emailFrom);
                else
                    error += "Email 'From' not valid.";

                if(validator.isValid(emailTo))
                    to = new ValidEmail(emailTo);
                else
                    error += "Email 'To' not valid.";

                if(error.equals("")) {
                    org.labkey.api.security.User y = UserManager.getUser(from);
                    if(y == null)
                        error += "Sender email not a registered user.";
                }
                if(error.equals("")) {
                    res.put("Message", "Email sent from " + from.getEmailAddress() + " to " + to.getEmailAddress());
                    res.put("Response", "true");
                    test.execute(SendTestResultsEmail.TEST_CUSTOM, UserManager.getUser(from), to.getEmailAddress());
                } else {
                    res.put("Message", error);
                    res.put("Response", "false");
                }
                return new ApiSimpleResponse(res);
            }

            if(action != null && action.equals("start")) {
                // if already started
                if(scheduler.checkExists(jobKeyEmail)) {
                    res.put("Message", "Job already exists");
                    res.put("Response", "true");
                    return new ApiSimpleResponse(res);
                }

                scheduler =  start(scheduler, jobKeyEmail); // start

                // ensure job exists after started
                if(scheduler.checkExists(jobKeyEmail)) {
                    res.put("Message", "Job Created");
                    res.put("Response", "true");
                    return new ApiSimpleResponse(res);
                } else {
                    res.put("Message", "Something failed creating job, contact Yuval");
                    res.put("Response", "false");
                }
                return new ApiSimpleResponse(res);
            }

            // DEFALT TO CHECK STATUS
            res.put("Message", "Status");
            res.put("Response", "" + scheduler.checkExists(jobKeyEmail));
            return new ApiSimpleResponse(res);

        }

        @NotNull
        public static Scheduler start(Scheduler scheduler, JobKey jobKeyEmail) throws SchedulerException
        {

            JobDetail jobEmail = JobBuilder.newJob(SendTestResultsEmail.class)
                    .withIdentity(jobKeyEmail).build();
            Trigger trigger1 = TriggerBuilder
                    .newTrigger()
                    .withIdentity("TestResultsEmailTrigger", "TestResultsGroup")
                    .withSchedule(
                            CronScheduleBuilder.cronSchedule("0 0 8 1/1 * ? *"))
                    .build();
            // runs job daily at 8am 0 0 8 1/1 * ? *
            // for testing(every minute): 0 0/1 * 1/1 * ? *

            scheduler.start();
            scheduler.scheduleJob(jobEmail, trigger1);
            return scheduler;
        }
    }

    @RequiresSiteAdmin
    public class SetUserActive extends ApiAction {
        @Override
        public Object execute(Object o, BindException errors) throws Exception
        {
            Map<String, String> res = new HashMap<>();
            String active = getViewContext().getRequest().getParameter("active");
            String userId = getViewContext().getRequest().getParameter("userId");
            boolean isActive = Boolean.parseBoolean(active);

            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(FieldKey.fromParts("userid"), Integer.parseInt(userId));
            filter.addCondition(FieldKey.fromParts("container"), getContainer());
            User[] users = new TableSelector(TestResultsSchema.getInstance().getTableInfoUserData(), filter, null).getArray(User.class);
            if(users.length == 0) {
                res.put("Message", "User not found id="+userId);
                return new ApiSimpleResponse(res);
            }
            User user = users[0];
            user.setActive(isActive);
            try {


                Map<String, Object> userData = new HashMap<>();
                userData.put("active", new Parameter.TypedValue(isActive, JdbcType.BOOLEAN));
                Table.update(getUser(), TestResultsSchema.getInstance().getTableInfoUserData(),userData, user.getId());
            } catch(Exception e) {
                res.put("Message", "Unable to update user id="+userId);
                return new ApiSimpleResponse(res);
            }


            res.put("Message", "Success");

            // DEFALT TO CHECK STATUS
            return new ApiSimpleResponse(res);

        }
    }

    /**
     * action for posting test output as an xml file
     */
    @RequiresNoPermission
    public class PostAction extends SimpleViewAction {

        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            String NA = "N/A";
            _log.info("Handling SkylineNightly posted results");

            // DebugRequest(getViewContext().getRequest());

            if(!(getViewContext().getRequest() instanceof MultipartRequest))
            {
                throw new Exception("Expected a request of type MultipartRequest got " + getViewContext().getRequest().getClass().toString());
            }

            MultipartRequest request = (MultipartRequest) getViewContext().getRequest();

            MultipartFile file = request.getFile("xml_file");
            String xml;
            if(file != null)
            {
                xml = new String(file.getBytes(), "UTF-8");

                if (xml == null)
                {
                    _log.error("XML from file is null");
                    throw new Exception ("XML from xml_file is null");
                }
                else if (xml.length() == 0)
                {
                    _log.error("XML from file is empty");
                    throw new Exception ("XML from xml_file is empty");
                }
                else
                    _log.info("XML from xml_file has length: " + xml.length());
            }
            else
            {
                _log.error("xml_file not found in request");
                throw new Exception("xml_file not found in request");
            }


            try (DbScope.Transaction transaction = TestResultsSchema.getSchema().getScope().ensureTransaction()) {

                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                InputSource is = new InputSource();
                is.setCharacterStream(new StringReader(xml));
                Document doc = dBuilder.parse(is);

                Element docElement = doc.getDocumentElement();
                // USER ID
                int userid = -1;
                String username = docElement.getAttribute("id");
                SimpleFilter filter = new SimpleFilter();
                filter.addCondition(FieldKey.fromParts("username"), username);
                User[] details = new TableSelector(TestResultsSchema.getInstance().getTableInfoUser(), filter, null).getArray(User.class);
                if(details.length == 0) {
                    User newUser =  new User();
                    newUser.setUsername(username);
                    User u = Table.insert(null, TestResultsSchema.getInstance().getTableInfoUser(),newUser);
                    if(u == null) {
                        throw new Exception();
                    } else{
                        userid = u.getId();
                    }

                } else {
                   userid = details[0].getId();
                }
                if(userid == -1)
                    throw new Exception("Issue with user/userid, may not be set");
                String os = docElement.getAttribute("os");
                String date = docElement.getAttribute("start"); // FORMAT: MM/dd/YYYY hh:mm:ss a
                SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a", Locale.US);
                Date xmlTimestamp = sdf.parse(date); // test pst z
                int duration = Integer.parseInt(docElement.getAttribute("duration")); // in minutes
                long ONE_MINUTE_IN_MILLIS=60000;  //millisecs
                long timestampMilis = xmlTimestamp.getTime();
                Date postTime = new Date(timestampMilis + (duration * ONE_MINUTE_IN_MILLIS)); // duration + timestamp is date to display test
                if(postTime == null)
                    postTime = new Date();
                int revision = Integer.parseInt(docElement.getAttribute("revision"));

                // Get log
                String log = null;
                if(doc.getDocumentElement().getElementsByTagName("Log").getLength() > 0) {
                    Node logN = docElement.getElementsByTagName("Log").item(0);
                    log = logN.getFirstChild().getNodeValue();
                    docElement.removeChild(logN); // remove log at the end so that it doesn't get stored with the xml
                }
                // Get leaks, failures, and passes
                List<TestLeakDetail> leaks = new ArrayList<>();
                List<TestFailDetail> failures = new ArrayList<>();
                List<TestPassDetail> passes = new ArrayList<>();
                // stores leaks in database
                NodeList nListLeaks = doc.getElementsByTagName("leaks");
                NodeList nlLeak = ((Element) nListLeaks.item(0)).getElementsByTagName("leak");
                for(int leakIndex = 0; leakIndex < nlLeak.getLength(); leakIndex++) {
                    Element elLeak = (Element) nlLeak.item(leakIndex);
                    TestLeakDetail leak = new TestLeakDetail(0, elLeak.getAttribute("name"), Integer.parseInt(elLeak.getAttribute("bytes")));
                    leaks.add(leak);
                }

                // parse passes
                NodeList nListPasses = doc.getElementsByTagName("pass");
                int startHour = 0;
                int lastHour = 0;
                Date timestampDay = xmlTimestamp;
                int avgMemory = 0;
                for(int i = 0; i < nListPasses.getLength(); i++) {
                    NodeList nlTests = ((Element) nListPasses.item(i)).getElementsByTagName("test");
                    int passId = Integer.parseInt(((Element) nListPasses.item(i)).getAttribute("id"));
                     for(int j = 0; j < nlTests.getLength(); j++) {
                        Element test = (Element) nlTests.item(j);
                        Date timestamp;
                        String ts = test.getAttribute("timestamp");
                        try { // try parsing xmlTimestamp as date
                            timestamp = new Date(ts);
                        } catch (IllegalArgumentException e) {
                            timestamp = null;
                        }
                        // if "xmlTimestamp" is not a proper date assume it is in hh:mm format
                         if(ts!=null && timestamp == null) {
                            int hour = new Integer(ts.split(":")[0]);
                            if(hour >= lastHour) {
                                if (lastHour == 0)
                                    startHour = hour;
                                lastHour = hour;
                            } else {
                                timestampDay = addDays(timestampDay, 1);
                                lastHour = hour;
                            }
                            DateFormat df = new SimpleDateFormat("MM/dd/yyyy");
                            String originalDate = df.format(timestampDay);
                            try {
                                timestamp = new SimpleDateFormat("MM/dd/yyyy HH:mm").parse(originalDate + " " + ts);
                            } catch (IllegalArgumentException e) {
                                timestamp = null;
                            }
                        }
                        if(test.getAttribute("duration").equals(NA) || test.getAttribute("managed").equals(NA) || test.getAttribute("total").equals(NA))
                            continue;
                        TestPassDetail pass = new TestPassDetail(0, passId, Integer.parseInt(test.getAttribute("id")), test.getAttribute("name"),
                                test.getAttribute("language"), Integer.parseInt(test.getAttribute("duration")), Double.parseDouble(test.getAttribute("managed")), Double.parseDouble(test.getAttribute("total")), timestamp);
                         avgMemory += pass.getTotalMemory();
                         passes.add(pass);
                    }
                }
                if(passes.size() != 0)
                    avgMemory = avgMemory/passes.size();
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
                    if(ts!=null && timestamp == null) {
                        int hour = new Integer(ts.split(":")[0]);
                        if(hour >= lastHour)
                            lastHour = hour;
                        else {
                            timestampDay = addDays(timestampDay, 1);
                            lastHour = hour;
                        }
                        DateFormat df = new SimpleDateFormat("MM/dd/yyyy");
                        String originalDate = df.format(timestampDay);
                        try {
                            timestamp = new SimpleDateFormat("MM/dd/yyyy HH:mm").parse(originalDate + " " + ts);
                        } catch (IllegalArgumentException e) {
                            timestamp = null;
                        }
                    }
                    TestFailDetail fail = new TestFailDetail(0, elFailure.getAttribute("name"), Integer.parseInt(elFailure.getAttribute("pass")), Integer.parseInt(elFailure.getAttribute("test")), elFailure.getAttribute("language"), elFailure.getTextContent(), timestamp);
                    failures.add(fail);
                }
                byte[] pointSummary = encodeRunPassSummary(passes.toArray(new TestPassDetail[passes.size()]));

                // Compress xml, will be stored in testresults.testruns, column xml
                byte[] compressedXML = null;
                byte[] compressedLog = null;
                if(xml != null)
                     compressString(docElement.toString());
                if(log != null)
                    compressedLog = compressString(log);

                RunDetail run = new RunDetail(userid, duration, postTime, xmlTimestamp, os, revision, getViewContext().getContainer(), false, compressedXML,
                        pointSummary, passes.size(), failures.size(), leaks.size(), avgMemory, compressedLog); //TODO change date AND USERID
                // stores test run in database and gets the id(foreign key)
                run = Table.insert(null, TestResultsSchema.getInstance().getTableInfoTestRuns(), run);
                int runId = run.getId();
                for(TestLeakDetail leak : leaks) {
                    leak.setTestRunId(runId);
                    Table.insert(null, TestResultsSchema.getInstance().getTableInfoTestLeaks(), leak);
                }
                for(TestFailDetail fail: failures) {
                    fail.setTestRunId(runId);
                    Table.insert(null, TestResultsSchema.getInstance().getTableInfoTestFails(), fail);
                }
                for(TestPassDetail pass: passes) {
                    pass.setTestRunId(runId);
                    Table.insert(null, TestResultsSchema.getInstance().getTableInfoTestPasses(), pass);
                }

                transaction.commit();
            } catch (Exception e) {
                _log.error("Error parsing xml", e);
                // e.getStackTrace();
                throw e;
            }
            return null;
        }

        private byte[] compressString(String s) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                OutputStream out = new GZIPOutputStream(baos);
                out.write(s.getBytes("UTF-8"));
                out.close();
            } catch (IOException e) {
                _log.error("Error compressing", e);
                return new byte[0];
            }
            return baos.toByteArray();
        }

        private void DebugRequest(HttpServletRequest hsRequest)
        {
            _log.info("Request is " + hsRequest.getClass().toString());
            _log.info("Content length is : "+ hsRequest.getContentLength());
            _log.info("Content type: " + hsRequest.getContentType());
            Enumeration<String> headerNames = hsRequest.getHeaderNames();
            while(headerNames.hasMoreElements())
            {
                String headerName = headerNames.nextElement();
                _log.info("Header " + headerName + ": " + hsRequest.getHeader(headerName));
            }

            if(hsRequest instanceof MultipartRequest)
            {
                MultipartRequest request = (MultipartRequest) hsRequest;
                _log.info("Multi part content type for xml: " + request.getMultipartContentType("xml"));
                _log.info("Multi part content type for xml_file: " + request.getMultipartContentType("xml_file"));
            }
        }

        // used for formatting timestamps in hh:mm format as they may roll over multiple days
        public Date addDays(Date date, int days)
        {
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            cal.add(Calendar.DATE, days); //minus number would decrement the days
            return cal.getTime();
        }
        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    // Encodes pass point summary
    // Format looksstarts with test# where passes change, flag -1, then intensities of all tests run
    // test#, test#, test#, test#, -1, 0.0, 100.0, 120.2, 200.4, 202.3, etc...
    static byte[] encodeRunPassSummary(TestPassDetail[] passes) throws IOException
    {
        if(passes == null || passes.length == 0 || passes[0] == null)
            return new byte[0];

        Arrays.sort(passes);
        List<Double> shortenedRunData = new ArrayList<>();
        List<Integer> passLocations = new ArrayList<>();
        int currentPass = 0;
        for(int a = 0; a < passes.length; a++) {
            TestPassDetail pass = passes[a];
            if(a%POINT_RATIO == 1)
                shortenedRunData.add(pass.getTotalMemory());
            if(pass.getPass() != currentPass) {
                currentPass = pass.getPass();
                passLocations.add(a/POINT_RATIO);
            }
        }
        passLocations.add(-1);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        for (int element : passLocations) {
            out.writeUTF(Integer.toString(element)+',');
        }
        for (int i = 0; i < shortenedRunData.size(); i++) {
            double element = shortenedRunData.get(i);
            if(i < shortenedRunData.size()-1) {
                out.writeUTF(Double.toString(element)+',');
            } else {
                out.writeUTF(Double.toString(element));
            }
        }
        return baos.toByteArray();
    }

    /*
    * Given a date will return all runs up to now
    * */
    public static RunDetail[] getRunsSinceDate(Date startDate, Date end, Container c,String filterString, boolean getXML, boolean getLog) {
        if(end == null) {
            Date start = DateUtils.truncate(new Date(), Calendar.DAY_OF_MONTH);
            end = DateUtils.addDays(start, 1);
        }
        SQLFragment sqlFragment = new SQLFragment();
        sqlFragment.append("SELECT testruns.*, u.username, EXISTS(SELECT 1 FROM testresults.trainruns WHERE runid = testruns.id) AS traindata ");
        sqlFragment.append("FROM testresults.testruns ");
        sqlFragment.append("JOIN testresults.user AS u ON testruns.userid = u.id ");
        sqlFragment.append("WHERE posttime >= ? ");
        sqlFragment.append("AND posttime < ? ");
        if(filterString != null)
            sqlFragment.append(filterString);
        sqlFragment.add(startDate);
        sqlFragment.add(end);

        return  executeGetRunsSQLFragment(sqlFragment, c, getXML, getLog);
    }

    // executes a sql fragment to get runs and return an array RunDetail[]
    public static RunDetail[] executeGetRunsSQLFragment(SQLFragment fragment, Container c, boolean getXML, boolean getLog) {
        if(c != null)
        {
            fragment.append(" AND container = ?;");
            fragment.add(c.getEntityId());
        } else {
            fragment.append(";");
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
            run.setContainer(ContainerManager.getForId(rs.getString("container")));
            run.setTrainRun(rs.getBoolean("traindata"));
            byte[] ptSummary = rs.getBytes("pointsummary");
            run.setPointsummary(ptSummary);
            run.setPassedtests(rs.getInt("passedtests"));
            run.setFailedtests(rs.getInt("failedtests"));
            run.setLeakedtests(rs.getInt("leakedtests"));
            run.setAveragemem(rs.getInt("averagemem"));

            if(getXML)
                run.setXml(rs.getBytes("xml"));
            if(getLog)
                run.setLog(rs.getBytes("log"));

            if(run.getTimestamp() == null)
                run.setTimestamp(run.getPostTime());
            runs.add(run);
        });
        return runs.toArray(new RunDetail[runs.size()]);
    }

    static Boolean isValidViewType(String ds) {
        if(ds == null)
            return false;
        switch (ds) {
            case ViewType.DAY:
               return true;
            case ViewType.WEEK:
                return true;
            case ViewType.MONTH:
                return true;
            case ViewType.YEAR:
                return true;
            case ViewType.ALLTIME:
                return true;
            default:
                return false;
        }
    }

    /*
    * Given a viewType as 'wk' = week, 'mo' = month, 'yr' = year, defaults to defaultTo
    * returns a starting date based on the time frame selected and the current date
    * */
    static Date getStartDate(String viewType, String defaultTo, Date endDate) {
        Date startDate;
        long DAY_IN_MS = 1000 * 60 * 60 * 24;
        long currentTime = endDate.getTime();
        if(viewType == null)
            viewType = defaultTo;
        switch (viewType)   // all the if/else to set dates for runs based on parameters
        {
            case "wk":
                startDate = new Date(currentTime - (7 * DAY_IN_MS)); // week
                break;
            case "mo":
                startDate = new Date(currentTime - (30 * DAY_IN_MS)); // month
                break;
            case "yr":
                startDate = new Date(currentTime - (365 * DAY_IN_MS)); // year
                break;
            case "at":
                long d = 1420070400000l;
                startDate = new Date(d); // all time
                break;
            case "day":
                startDate = new Date(currentTime - (DAY_IN_MS)); // day
                break;
            default:
                startDate = new Date(currentTime - (7 * DAY_IN_MS)); // week
                break;
        }
        return startDate;
    }

    private static SimpleFilter filterByRunId(RunDetail[] runs) {
        List<Integer> allRunIds = new ArrayList<>();
        for(RunDetail run: runs) {
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
    }

    static TestPassDetail[] getPassesForRun(RunDetail run) {
        if(run == null)
            return null;
        RunDetail[] runs = new RunDetail[]{run};
        populatePasses(runs);
        return runs[0].getPasses();
    }
    static TestFailDetail[] getFailuresForRun(RunDetail run) {
        if(run == null)
            return null;
        RunDetail[] runs = new RunDetail[]{run};
        populateFailures(runs);
        return runs[0].getFailures();
    }
    static TestLeakDetail[] getLeaksForRun(RunDetail run) {
        if(run == null)
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
        SimpleFilter filter = filterByRunId(runs);
        Sort sortById = new Sort(FieldKey.fromString("id"));

        TestPassDetail[] passes = new TableSelector(TestResultsSchema.getInstance().getTableInfoTestPasses(), filter, sortById).getArray(TestPassDetail.class);


        Map<Integer, List<TestPassDetail>> testPassDetails = new HashMap<>();
        int id = 0;
        for (TestPassDetail pass : passes) {
            if(id != pass.getTestRunId()) {
                id = pass.getTestRunId();
                testPassDetails.put(pass.getTestRunId(), new ArrayList<>());
            }
            testPassDetails.get(pass.getTestRunId()).add(pass);
        }


        for (RunDetail run : runs) {
            List<TestPassDetail> passList = testPassDetails.get(run.getId());
            if(passList != null)
                run.setPasses(passList);
            else
                run.setPasses(new TestPassDetail[0]);
        }
    }

    static void populateLastPassForRuns(RunDetail[] runs) {
        List<Integer> runIds = new ArrayList<>();
        for(RunDetail r: runs)
            runIds.add(r.getId());

        SQLFragment sqlFragment = new SQLFragment();
        sqlFragment.append(" SELECT * FROM testresults.testpasses WHERE id = ANY(SELECT MAX(id) FROM " +
                "testresults.testpasses WHERE (testrunid = ANY(?)) GROUP BY testrunid);");
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

        for(RunDetail r: runs) {
            for(TestPassDetail pass: passes) {
                if(pass.getTestRunId() == r.getId())
                    r.setPasses(new TestPassDetail[]{pass});
            }
        }

    }

    static void populateFailures(RunDetail[] runs) {
        SimpleFilter filter = filterByRunId(runs);

        TestFailDetail[] fails = new TableSelector(TestResultsSchema.getInstance().getTableInfoTestFails(), filter, null).getArray(TestFailDetail.class);
        Map<Integer, List<TestFailDetail>> testFailDetails = new HashMap<>();

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

            if (failList != null)
                run.setFailures(failList.toArray(new TestFailDetail[failList.size()]));
            else
                run.setFailures(new TestFailDetail[0]);

        }
    }

    static void populateLeaks(RunDetail[] runs) {
        SimpleFilter filter = filterByRunId(runs);

        TestLeakDetail[] leaks = new TableSelector(TestResultsSchema.getInstance().getTableInfoTestLeaks(), filter, null).getArray(TestLeakDetail.class);
        Map<Integer, List<TestLeakDetail>> testLeakDetails = new HashMap<>();

        for (TestLeakDetail leak : leaks) {
            List<TestLeakDetail> list = testLeakDetails.get(leak.getTestRunId());
            if (null == list) {
                list = new ArrayList<>();
            }
            list.add(leak);
            testLeakDetails.put(leak.getTestRunId(), list);
        }
        for (RunDetail run : runs)
        {
            int runId = run.getId();
            List<TestLeakDetail> leakList = testLeakDetails.get(runId);

            if (leakList != null)
                run.setLeaks(leakList.toArray(new TestLeakDetail[leakList.size()]));
            else
                run.setLeaks(new TestLeakDetail[0]);
        }
    }
}
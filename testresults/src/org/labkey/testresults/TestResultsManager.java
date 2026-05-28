/*
 * Copyright (c) 2017-2026 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: Yuval Boss, yuval(at)uw.edu
 * Date: 1/14/2015
 */
public class TestResultsManager
{
    private static final TestResultsManager _instance = new TestResultsManager();

    private TestResultsManager()
    {
        // prevent external construction with a private default constructor
    }

    public static TestResultsManager get()
    {
        return _instance;
    }

    /**
     * Get distinct user IDs from recent test runs in a container.
     */
    public List<Integer> getRecentUserIds(Container container, Timestamp cutoffDate)
    {
        DbScope scope = TestResultsSchema.getSchema().getScope();
        SQLFragment sql = new SQLFragment();
        sql.append(
            "SELECT DISTINCT userid FROM " + TestResultsSchema.getTableInfoTestRuns() +
            " WHERE container = ? AND posttime >= ?");
        sql.add(container.getEntityId());
        sql.add(cutoffDate);

        List<Integer> userIds = new ArrayList<>();
        new SqlSelector(scope, sql).forEach(rs -> userIds.add(rs.getInt("userid")));
        return userIds;
    }

    /**
     * Get trainrun counts per user for a container.
     */
    public Map<Integer, Integer> getTrainRunCounts(Container container)
    {
        DbScope scope = TestResultsSchema.getSchema().getScope();
        SQLFragment sql = new SQLFragment();
        sql.append(
            "SELECT r.userid, COUNT(tr.runid) as traincount" +
            " FROM " + TestResultsSchema.getTableInfoTrain() + " tr" +
            " JOIN " + TestResultsSchema.getTableInfoTestRuns() + " r ON tr.runid = r.id" +
            " WHERE r.container = ?" +
            " GROUP BY r.userid");
        sql.add(container.getEntityId());

        Map<Integer, Integer> counts = new HashMap<>();
        new SqlSelector(scope, sql).forEach(rs ->
            counts.put(rs.getInt("userid"), rs.getInt("traincount")));
        return counts;
    }

    /**
     * Delete all trainruns for a container.
     */
    public void deleteTrainRunsForContainer(Container container)
    {
        DbScope scope = TestResultsSchema.getSchema().getScope();
        SQLFragment sql = new SQLFragment();
        sql.append(
            "DELETE FROM " + TestResultsSchema.getTableInfoTrain() +
            " WHERE runid IN (SELECT id FROM " + TestResultsSchema.getTableInfoTestRuns() +
            " WHERE container = ?)");
        sql.add(container.getEntityId());
        new SqlExecutor(scope).execute(sql);
    }

    /**
     * Delete all userdata for a container.
     */
    public void deleteUserDataForContainer(Container container)
    {
        DbScope scope = TestResultsSchema.getSchema().getScope();
        SQLFragment sql = new SQLFragment();
        sql.append("DELETE FROM " + TestResultsSchema.getTableInfoUserData() + " WHERE container = ?");
        sql.add(container.getEntityId());
        new SqlExecutor(scope).execute(sql);
    }

    /**
     * Get existing trainrun IDs for a user in a container.
     */
    public Set<Integer> getExistingTrainRunIds(int userId, Container container)
    {
        DbScope scope = TestResultsSchema.getSchema().getScope();
        SQLFragment sql = new SQLFragment();
        sql.append(
            "SELECT tr.runid FROM " + TestResultsSchema.getTableInfoTrain() + " tr" +
            " JOIN " + TestResultsSchema.getTableInfoTestRuns() + " r ON tr.runid = r.id" +
            " WHERE r.userid = ? AND r.container = ?");
        sql.add(userId);
        sql.add(container.getEntityId());

        Set<Integer> runIds = new HashSet<>();
        new SqlSelector(scope, sql).forEach(rs -> runIds.add(rs.getInt("runid")));
        return runIds;
    }

    /**
     * Get clean run IDs for a user within lookback period.
     * Clean = 0 failures, 0 leaks, passedtests > 0, not flagged, full duration, no hangs.
     */
    public List<Integer> getCleanRunIds(int userId, Container container, Timestamp cutoffDate, int expectedDuration)
    {
        DbScope scope = TestResultsSchema.getSchema().getScope();
        SQLFragment sql = new SQLFragment();
        sql.append(
            "SELECT tr.id FROM " + TestResultsSchema.getTableInfoTestRuns() + " tr" +
            " WHERE tr.userid = ? AND tr.container = ?" +
            " AND tr.posttime >= ?" +
            " AND tr.failedtests = 0 AND tr.leakedtests = 0" +
            " AND tr.passedtests > 0 AND tr.flagged = false" +
            " AND tr.duration >= ?" +
            " AND NOT EXISTS (SELECT 1 FROM " + TestResultsSchema.getTableInfoHangs() +
            " h WHERE h.testrunid = tr.id)" +
            " ORDER BY tr.posttime DESC");
        sql.add(userId);
        sql.add(container.getEntityId());
        sql.add(cutoffDate);
        sql.add(expectedDuration);

        List<Integer> runIds = new ArrayList<>();
        new SqlSelector(scope, sql).forEach(rs -> runIds.add(rs.getInt("id")));
        return runIds;
    }

    /**
     * Get combined candidate run IDs (existing + recent) sorted by posttime, limited to maxRuns.
     */
    public List<Integer> getCandidateRunIds(int userId, Container container,
                                            Set<Integer> existingIds, List<Integer> recentIds, int maxRuns)
    {
        if (existingIds.isEmpty() && recentIds.isEmpty())
            return new ArrayList<>();

        DbScope scope = TestResultsSchema.getSchema().getScope();
        SQLFragment sql = new SQLFragment();
        sql.append(
            "SELECT id FROM " + TestResultsSchema.getTableInfoTestRuns() +
            " WHERE userid = ? AND container = ?" +
            " AND (id = ANY(?) OR id = ANY(?))" +
            " ORDER BY posttime DESC");
        sql.add(userId);
        sql.add(container.getEntityId());
        sql.add(existingIds.toArray(new Integer[0]));
        sql.add(recentIds.toArray(new Integer[0]));

        List<Integer> result = new ArrayList<>();
        new SqlSelector(scope, sql).forEach(rs -> {
            if (result.size() < maxRuns)
                result.add(rs.getInt("id"));
        });
        return result;
    }

    /**
     * Delete trainruns by run IDs.
     */
    public void removeTrainRuns(List<Integer> runIds)
    {
        if (runIds.isEmpty())
            return;

        DbScope scope = TestResultsSchema.getSchema().getScope();
        for (int runId : runIds)
        {
            SQLFragment sql = new SQLFragment();
            sql.append("DELETE FROM " + TestResultsSchema.getTableInfoTrain() + " WHERE runid = ?");
            sql.add(runId);
            new SqlExecutor(scope).execute(sql);
        }
    }

    /**
     * Insert trainruns by run IDs.
     */
    public void addTrainRuns(List<Integer> runIds)
    {
        if (runIds.isEmpty())
            return;

        DbScope scope = TestResultsSchema.getSchema().getScope();
        for (int runId : runIds)
        {
            SQLFragment sql = new SQLFragment();
            sql.append("INSERT INTO " + TestResultsSchema.getTableInfoTrain() + " (runid) VALUES (?)");
            sql.add(runId);
            new SqlExecutor(scope).execute(sql);
        }
    }

    /**
     * Upsert userdata with calculated stats from specified run IDs.
     */
    public void upsertUserData(int userId, Container container, List<Integer> runIds, boolean active)
    {
        if (runIds.isEmpty())
            return;

        DbScope scope = TestResultsSchema.getSchema().getScope();
        SQLFragment sql = new SQLFragment();
        sql.append(
            "INSERT INTO " + TestResultsSchema.getTableInfoUserData() +
            " (userid, container, meantestsrun, meanmemory, stddevtestsrun, stddevmemory, active)" +
            " SELECT ?, ?, avg(passedtests), avg(averagemem)," +
            " stddev_pop(passedtests), stddev_pop(averagemem), ?" +
            " FROM " + TestResultsSchema.getTableInfoTestRuns() +
            " WHERE id = ANY(?)" +
            " ON CONFLICT(userid, container) DO UPDATE SET" +
            " meantestsrun = excluded.meantestsrun," +
            " meanmemory = excluded.meanmemory," +
            " stddevtestsrun = excluded.stddevtestsrun," +
            " stddevmemory = excluded.stddevmemory," +
            " active = excluded.active");
        sql.add(userId);
        sql.add(container.getEntityId());
        sql.add(active);
        sql.add(runIds.toArray(new Integer[0]));
        new SqlExecutor(scope).execute(sql);
    }
}

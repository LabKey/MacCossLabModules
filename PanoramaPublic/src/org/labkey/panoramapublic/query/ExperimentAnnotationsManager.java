/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
package org.labkey.targetedms.query;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.util.GUID;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.targetedms.TargetedMSManager;
import org.labkey.targetedms.TargetedMSRun;
import org.labkey.targetedms.model.ExperimentAnnotations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * User: vsharma
 * Date: 2/18/13
 * Time: 3:41 PM
 */
public class ExperimentAnnotationsManager
{
    private ExperimentAnnotationsManager() {}

    public static ExperimentAnnotations get(int experimentAnnotationsId)
    {
        return new TableSelector(TargetedMSManager.getTableInfoExperimentAnnotations(),null, null).getObject(experimentAnnotationsId, ExperimentAnnotations.class);
    }

    public static ExperimentAnnotations getForExperiment(int experimentId)
    {
        return new TableSelector(TargetedMSManager.getTableInfoExperimentAnnotations(),
                new SimpleFilter(FieldKey.fromParts("ExperimentId"), experimentId), null).getObject(ExperimentAnnotations.class);
    }

    public static ExperimentAnnotations save(ExperimentAnnotations annotations, User user)
    {
        ExperimentAnnotations toReturn;
        if(annotations.getId() != 0)
        {
            ExperimentAnnotations existingAnnotations = get(annotations.getId());
            if(existingAnnotations == null)
            {
                throw new NotFoundException("ExperimentAnnotations not found for Id "+annotations.getId());
            }
            else
            {
                toReturn = Table.update(user, TargetedMSManager.getTableInfoExperimentAnnotations(), annotations, annotations.getId());
            }
        }
        else
        {
            toReturn = Table.insert(user, TargetedMSManager.getTableInfoExperimentAnnotations(), annotations);
        }
        return toReturn;
    }

    public static void excludeSubfoldersFromExperiment(ExperimentAnnotations expAnnotations, User user)
    {
        ExpExperiment experiment = expAnnotations.getExperiment();
        if(experiment == null)
        {
            throw new NotFoundException("Could not find experiment with rowId " + expAnnotations.getExperimentId());
        }

        // Get all the runs in the experiment.
        List<? extends ExpRun> runs = experiment.getRuns();
        List<Integer> rowIdsToRemove = new ArrayList<>();
        Container expContainer = experiment.getContainer();
        // Get a list of runs that are not in the folder where the experiment is defined.
        for(ExpRun run: runs)
        {
            if(!run.getContainer().equals(expContainer))
            {
                rowIdsToRemove.add(run.getRowId());
            }
        }

        int[] rowIds = new int[rowIdsToRemove.size()];
        int i = 0;
        for(Integer rowId: rowIdsToRemove)
        {
            rowIds[i++] = rowId;
        }

        try(DbScope.Transaction transaction = TargetedMSManager.getSchema().getScope().ensureTransaction())
        {
            removeRunIds(experiment, rowIds, user);

            expAnnotations.setIncludeSubfolders(false);
            save(expAnnotations, user);

            transaction.commit();
        }
    }

    private static void removeRunIds(ExpExperiment experiment, int[] rowIds, User user)
    {
        ExperimentService expService = ExperimentService.get();

        for(int rowId: rowIds)
        {
            ExpRun run = expService.getExpRun(rowId);
            if(run != null)
            {
                TargetedMSRun tmsRun = TargetedMSManager.getRunByLsid(run.getLSID(), run.getContainer());
                if(tmsRun != null)
                {
                    experiment.removeRun(user, run);
                }
            }
        }
    }

    public static void includeSubfoldersInExperiment(ExperimentAnnotations expAnnotations, User user)
    {
        ExpExperiment experiment = expAnnotations.getExperiment();

        ExperimentService expService = ExperimentService.get();

        // Get all the runs contained in the folder and its subfolders.
        List<ExpRun> runs = new ArrayList<>();
        // 'children' includes the root container.
        List<Container> children = ContainerManager.getAllChildren(experiment.getContainer(), user, InsertPermission.class);
        for(Container child: children)
        {
            runs.addAll(expService.getExpRuns(child, null, null));
        }

        // Get a list of runs that already belong to the experiment.
        List<? extends ExpRun> existingRuns = experiment.getRuns();
        Set<Integer> existingRunRowIds = new HashSet<>();
        for(ExpRun run: existingRuns)
        {
            existingRunRowIds.add(run.getRowId());
        }

        // Keep runs that do not already belong to the experiment.
        Iterator<ExpRun> runIterator = runs.iterator();
        while(runIterator.hasNext())
        {
            ExpRun run = runIterator.next();
            if(existingRunRowIds.contains(run.getRowId()))
                runIterator.remove();
        }
        int[] rowIds = new int[runs.size()];
        int i = 0;
        for(ExpRun run: runs)
        {
            rowIds[i++] = run.getRowId();
        }

        try(DbScope.Transaction transaction = TargetedMSManager.getSchema().getScope().ensureTransaction())
        {
            addSelectedRunsToExperiment(experiment, rowIds, user);

            expAnnotations.setIncludeSubfolders(true);
            save(expAnnotations, user);

            transaction.commit();
        }
    }

    public static void addSelectedRunsToExperiment(ExpExperiment experiment, int[] rowIds, User user)
    {
        List<ExpRun> runs = new ArrayList<>();
        for (int rowId : rowIds)
        {
            ExpRun run = ExperimentService.get().getExpRun(rowId);
            if (run != null)
            {
                TargetedMSRun tmsRun = TargetedMSManager.getRunByLsid(run.getLSID(), run.getContainer());
                validateRun(tmsRun, experiment.getContainer());

                if(tmsRun != null)
                {
                    runs.add(run);
                }
            }
        }
        experiment.addRuns(user, runs.toArray(new ExpRun[runs.size()]));
    }

    private static void validateRun(TargetedMSRun run, Container c)
    {
        Container container = run.getContainer();

        if (container == null || (!container.equals(c) && !container.isDescendant(c)))
        {
            throw new NotFoundException("TargetedMS run with Id " + run.getId() + " does not exist in container "+c.getPath() + " or one of its descendents");
        }
    }

    public static Set<Container> getExperimentFolders(ExperimentAnnotations experimentAnnotations, User user)
    {
        if(experimentAnnotations.isIncludeSubfolders())
        {
            return new HashSet<>(ContainerManager.getAllChildren(experimentAnnotations.getContainer(), user));
        }
        else
        {
            return Collections.singleton(experimentAnnotations.getContainer());
        }
    }

    public static void beforeDeleteExpExperiment(ExpExperiment experiment, User user)
    {
        if(experiment == null)
            return;
        ExperimentAnnotations experimentAnnotations = getForExperiment(experiment.getRowId());
        if(experimentAnnotations != null)
        {
            deleteExperiment(experimentAnnotations, user);
        }
    }

    private static void deleteExperiment(ExperimentAnnotations expAnnotations, User user)
    {
        // If any journal were given access to this experiment, remove the access and delete entries from the JournalExperiment table.
        JournalManager.beforeDeleteTargetedMSExperiment(expAnnotations, user);

        Table.delete(TargetedMSManager.getTableInfoExperimentAnnotations(), expAnnotations.getId());

        if(expAnnotations.isJournalCopy() && expAnnotations.getShortUrl() != null)
        {
            // Delete the short access URL
            JournalManager.tryDeleteShortUrl(expAnnotations.getShortUrl(), user);
        }
    }

    /**
     *
     * @return A list of targeted MS experiments in the given container and its subfolders
     */
    public static List<ExperimentAnnotations> getAllExperiments(Container container, User user)
    {
        SimpleFilter filter = new SimpleFilter();
        ContainerFilter containerFilter = new ContainerFilter.CurrentAndSubfolders(user);
        filter.addCondition(containerFilter.createFilterClause(TargetedMSManager.getSchema(), FieldKey.fromParts("Container"), container));

        return new TableSelector(TargetedMSManager.getTableInfoExperimentAnnotations(), filter, null).getArrayList(ExperimentAnnotations.class);
    }

    /**
     * @param container container
     * @return A TargetedMS experiment that includes data in the given container.  This could be an experiment defined
     * in the given container, or in an ancestor container that has 'IncludeSubfolders' set to true.
     */
    public static ExperimentAnnotations getExperimentIncludesContainer(Container container)
    {
        Container leaf = container;
        while(container != null && !container.isRoot())
        {
            ExperimentAnnotations expAnnotations = ExperimentAnnotationsManager.get(container);
            if(expAnnotations == null)
            {
                container = container.getParent();
                continue;
            }

            if(container.equals(leaf) || expAnnotations.isIncludeSubfolders())
            {
                return expAnnotations;
            }
            else
            {
                // We found an experiment in this folder, but it is either not in the container we started with
                // or this experiment has not been configured to include subfolders.  We don't need to look any
                // further up in the tree.
                break;
            }
        }
        return null;
    }

    private static ExperimentAnnotations get(Container container)
    {
        SimpleFilter filter = container != null ? SimpleFilter.createContainerFilter(container) : null;
        List<ExperimentAnnotations> expAnnotations = new TableSelector(TargetedMSManager.getTableInfoExperimentAnnotations(),
                filter, null).getArrayList(ExperimentAnnotations.class);
        if(expAnnotations.size() > 0)
        {
            // 09.04.14
            // Return the first experiment in the container.
            // We are now enforcing a single experiment per container, but there may already be
            // containers with multiple experiments.
            return expAnnotations.get(0);
        }
        return null;
    }

    public static boolean hasExperimentsInSubfolders(Container container, User user)
    {
        Collection<GUID> containerIds = new ContainerFilter.CurrentAndSubfolders(user).getIds(container);
        if(containerIds == null || containerIds.size() == 0)
        {
            return false;
        }

        Collection<GUID> subfolderIds = new ArrayList<>();
        for(GUID containerId: containerIds)
        {
            if(!container.getEntityId().equals(containerId))
            {
                subfolderIds.add(containerId);
            }
        }

        SimpleFilter filter = new SimpleFilter();
        filter.addInClause(FieldKey.fromParts("Container"), subfolderIds);

        List<ExperimentAnnotations> expAnnotations = new TableSelector(TargetedMSManager.getTableInfoExperimentAnnotations(),
                filter, null).getArrayList(ExperimentAnnotations.class);

        return expAnnotations.size() > 0;
    }

    public static ExperimentAnnotations getExperimentForShortUrl(ShortURLRecord shortUrl)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("shortUrl"), shortUrl);
        // There should be at most 1 record associated with a shortURL in the ExperimentAnnotations table.
        return new TableSelector(TargetedMSManager.getTableInfoExperimentAnnotations(),
                filter, null).getObject(ExperimentAnnotations.class);
    }

    public static void removeShortUrl(int sourceExperimentId, ShortURLRecord shortAccessUrl, User user)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("sourceExperimentId"), sourceExperimentId);
        filter.addCondition(FieldKey.fromParts("shortUrl"), shortAccessUrl);
        TableInfo tInfo = TargetedMSManager.getTableInfoExperimentAnnotations();

        ExperimentAnnotations expAnnot = new TableSelector(tInfo, filter, null).getObject(ExperimentAnnotations.class);

        if(expAnnot != null)
        {
            expAnnot.setShortUrl(null);
            Table.update(user, tInfo, expAnnot, expAnnot.getId());
        }
    }

    public static List<TargetedMSRun> getTargetedMSRuns(ExperimentAnnotations expAnnotations)
    {
        List<TargetedMSRun> runs = new ArrayList<>();
        ExpExperiment exp = ExperimentService.get().getExpExperiment(expAnnotations.getExperimentId());
        if(exp != null)
        {
            List<? extends ExpRun> expRuns = exp.getRuns();
            for (ExpRun run : expRuns)
            {
                TargetedMSRun tRun = TargetedMSManager.getRunByLsid(run.getLSID(), run.getContainer());
                if (run != null)
                {
                    runs.add(tRun);
                }
            }
        }
        return runs;
    }

    public static void updatePxId(ExperimentAnnotations expAnnotations, String pxId)
    {
        new SqlExecutor(TargetedMSManager.getSchema()).execute("UPDATE " + TargetedMSManager.getTableInfoExperimentAnnotations() +
                        " SET pxId = ? WHERE Id = ?", pxId, expAnnotations.getId());
    }
}

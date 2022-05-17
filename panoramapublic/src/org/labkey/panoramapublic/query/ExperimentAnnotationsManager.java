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
package org.labkey.panoramapublic.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.GUID;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.Journal;
import org.labkey.panoramapublic.model.JournalSubmission;
import org.labkey.panoramapublic.model.Submission;
import org.labkey.panoramapublic.proteomexchange.PsiInstrumentParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User: vsharma
 * Date: 2/18/13
 * Time: 3:41 PM
 */
public class ExperimentAnnotationsManager
{
    private ExperimentAnnotationsManager() {}

    @Nullable
    public static ExperimentAnnotations get(@Nullable Integer experimentAnnotationsId)
    {
        return experimentAnnotationsId == null ? null : new TableSelector(PanoramaPublicManager.getTableInfoExperimentAnnotations(),null, null).getObject(experimentAnnotationsId, ExperimentAnnotations.class);
    }

    @Nullable
    public static ExperimentAnnotations get(int experimentAnnotationsId, Container container)
    {
        var expAnnotations = get(experimentAnnotationsId);
        return expAnnotations != null && expAnnotations.getContainer().equals(container) ? expAnnotations : null;
    }

    /**
     * @param experimentId FK -> exp.experiment.rowId
     * @return ExperimentAnnotations object with the given experimentId
     */
    private static ExperimentAnnotations getForExperimentId(int experimentId)
    {
        return new TableSelector(PanoramaPublicManager.getTableInfoExperimentAnnotations(),
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
                toReturn = Table.update(user, PanoramaPublicManager.getTableInfoExperimentAnnotations(), annotations, annotations.getId());
            }
        }
        else
        {
            toReturn = Table.insert(user, PanoramaPublicManager.getTableInfoExperimentAnnotations(), annotations);
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

        try(DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
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
                ITargetedMSRun tmsRun = PanoramaPublicManager.getRunByLsid(run.getLSID(), run.getContainer());
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
        List<Container> children = ContainerManager.getAllChildren(experiment.getContainer(), user, ReadPermission.class);
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
        runs.removeIf(run -> existingRunRowIds.contains(run.getRowId()));
        int[] rowIds = new int[runs.size()];
        int i = 0;
        for(ExpRun run: runs)
        {
            rowIds[i++] = run.getRowId();
        }

        try(DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
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
                ITargetedMSRun tmsRun = PanoramaPublicManager.getRunByLsid(run.getLSID(), run.getContainer());
                if(tmsRun != null)
                {
                    validateRun(tmsRun, experiment.getContainer());
                    runs.add(run);
                }
            }
        }
        experiment.addRuns(user, runs.toArray(new ExpRun[0]));
    }

    private static void validateRun(ITargetedMSRun run, Container c)
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
        ExperimentAnnotations experimentAnnotations = getForExperimentId(experiment.getRowId());
        if(experimentAnnotations != null)
        {
            if (!experimentAnnotations.getContainer().hasPermission(user, DeletePermission.class))
            {
                throw new UnauthorizedException("You are not authorized to delete experiments in the folder '" + experimentAnnotations.getContainer().getPath() + "'");
            }
            deleteExperiment(experimentAnnotations, user);
        }
    }

    private static void deleteExperiment(ExperimentAnnotations expAnnotations, User user)
    {
        if (!expAnnotations.isJournalCopy())
        {
            // If any journal were given access to this experiment, remove the access and delete entries from the JournalExperiment table.
            List<Journal> journals = JournalManager.getJournalsForExperiment(expAnnotations.getId());
            for (Journal journal: journals)
            {
                SubmissionManager.beforeSubmittedExperimentDeleted(expAnnotations, journal, user);
                JournalManager.removeJournalPermissions(expAnnotations, journal, user);
            }
        }
        else
        {
            // This experiment is a journal copy (i.e. in the Panorama Public project on PanoramaWeb)
            SubmissionManager.beforeCopiedExperimentDeleted(expAnnotations, user);
        }

        // Delete any rows in the panoramapublic.speclibinfo table associated with this experiment
        Table.delete(PanoramaPublicManager.getTableInfoSpecLibInfo(),
                new SimpleFilter().addCondition(FieldKey.fromParts("ExperimentAnnotationsId"), expAnnotations.getId()));

        // Delete any rows in the panoramapublic.ExperimentStructuralModInfo and panoramapublic.ExperimentIsotopeModInfo tables
        ModificationInfoManager.deleteStructuralModInfoForExperiment(expAnnotations);
        ModificationInfoManager.deleteIsotopeModInfoForExperiment(expAnnotations);

        // Delete any data validation rows for this experiment
        DataValidationManager.deleteValidations(expAnnotations.getId(), expAnnotations.getContainer());

        Table.delete(PanoramaPublicManager.getTableInfoExperimentAnnotations(), expAnnotations.getId());

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
        ContainerFilter containerFilter = ContainerFilter.Type.CurrentAndSubfolders.create(container, user);
        filter.addCondition(containerFilter.createFilterClause(PanoramaPublicManager.getSchema(), FieldKey.fromParts("Container")));

        return new TableSelector(PanoramaPublicManager.getTableInfoExperimentAnnotations(), filter, null).getArrayList(ExperimentAnnotations.class);
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
            ExperimentAnnotations expAnnotations = ExperimentAnnotationsManager.getExperimentInContainer(container);
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

    public static ExperimentAnnotations getExperimentInContainer(Container container)
    {
        SimpleFilter filter = container != null ? SimpleFilter.createContainerFilter(container) : null;
        List<ExperimentAnnotations> expAnnotations = new TableSelector(PanoramaPublicManager.getTableInfoExperimentAnnotations(),
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
        Collection<GUID> containerIds = ContainerFilter.Type.CurrentAndSubfolders.create(container, user).getIds();
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

        List<ExperimentAnnotations> expAnnotations = new TableSelector(PanoramaPublicManager.getTableInfoExperimentAnnotations(),
                filter, null).getArrayList(ExperimentAnnotations.class);

        return expAnnotations.size() > 0;
    }

    public static ExperimentAnnotations getExperimentForShortUrl(ShortURLRecord shortUrl)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("shortUrl"), shortUrl);
        // There should be at most 1 record associated with a shortURL in the ExperimentAnnotations table.
        return new TableSelector(PanoramaPublicManager.getTableInfoExperimentAnnotations(),
                filter, null).getObject(ExperimentAnnotations.class);
    }

    public static List<ITargetedMSRun> getTargetedMSRuns(ExperimentAnnotations expAnnotations)
    {
        List<ITargetedMSRun> runs = new ArrayList<>();
        ExpExperiment exp = ExperimentService.get().getExpExperiment(expAnnotations.getExperimentId());
        if(exp != null)
        {
            List<? extends ExpRun> expRuns = exp.getRuns();
            for (ExpRun run : expRuns)
            {
                ITargetedMSRun tRun = PanoramaPublicManager.getRunByLsid(run.getLSID(), run.getContainer());
                if (tRun != null)
                {
                    runs.add(tRun);
                }
            }
        }
        return runs;
    }

    public static void updatePxId(ExperimentAnnotations expAnnotations, String pxId)
    {
        new SqlExecutor(PanoramaPublicManager.getSchema()).execute("UPDATE " + PanoramaPublicManager.getTableInfoExperimentAnnotations() +
                        " SET pxId = ? WHERE Id = ?", pxId, expAnnotations.getId());
    }

    public static void updateDoi(ExperimentAnnotations expAnnotations)
    {
        new SqlExecutor(PanoramaPublicManager.getSchema()).execute("UPDATE " + PanoramaPublicManager.getTableInfoExperimentAnnotations() +
                " SET doi = ? WHERE Id = ?", expAnnotations.getDoi(), expAnnotations.getId());
    }

    public static boolean canSubmitExperiment(int expAnnotationsId, JournalSubmission journalSubmission)
    {
        ExperimentAnnotations expAnnotations = ExperimentAnnotationsManager.get(expAnnotationsId);
        return expAnnotations != null && canSubmitExperiment(expAnnotations, journalSubmission);
    }

    /**
     * Returns true if
     * 1. this is a NOT journal copy (i.e. a folder in the Panorama Public project)
     * 2. AND if this experiment has been copied to Panorama Public, the copy is not final (paper published and data public).
     */
    public static boolean canSubmitExperiment(@NotNull ExperimentAnnotations expAnnotations, @Nullable JournalSubmission journalSubmission)
    {
        if(expAnnotations.isJournalCopy())
        {
            return false;
        }

        if (journalSubmission != null)
        {
            if (journalSubmission.hasPendingSubmission())
            {
                return false;
            }
            // If this experiment has already been copied and the journal copy is final (paper published and data public)
            // then the user should not be able to re-submit this data.
            Submission lastCopiedSubmission = journalSubmission.getLatestCopiedSubmission();
            if (lastCopiedSubmission != null)
            {
                ExperimentAnnotations journalCopy = ExperimentAnnotationsManager.get(lastCopiedSubmission.getCopiedExperimentId());
                return journalCopy == null || !journalCopy.isFinal();
            }
        }

        return true;
    }

    public static boolean hasProteomicData(ExperimentAnnotations experimentAnnotations, User user)
    {
        // CONSIDER: Add this method to TargetedMSService?
        TargetedMSService svc = TargetedMSService.get();
        UserSchema targetedmsSchema = svc.getUserSchema(user, experimentAnnotations.getContainer());

        SQLFragment sql = new SQLFragment("SELECT Id FROM ").append(svc.getTableInfoRuns(), "r")
                .append(" WHERE PeptideCount > 0 ")
                .append(" AND Deleted = ? " ).add(Boolean.FALSE)
                .append(" AND Container IN ");
        if(experimentAnnotations.isIncludeSubfolders())
        {
            List<Container> containers = ContainerManager.getAllChildren(experimentAnnotations.getContainer(), user);
            sql.append(ContainerManager.getIdsAsCsvList(new HashSet<>(containers)));
        }
        else
        {
            sql.append("('");
            sql.append(experimentAnnotations.getContainer().getId());
            sql.append("')");
        }

        return new SqlSelector(targetedmsSchema.getDbSchema(), sql).exists();
    }

    /**
     * @return the short URL associated with an experiment rendered as a string. If the experiment is not a journal
     * copy, the short URL associated with the latest journal copy of this experiment is returned.
     */
    public static @Nullable String getExperimentShortUrl(ExperimentAnnotations expAnnotations)
    {
        if(expAnnotations.isJournalCopy())
        {
            if(expAnnotations.getShortUrl() != null)
            {
                return expAnnotations.getShortUrl().renderShortURL();
            }
        }
        else
        {
            JournalSubmission js = SubmissionManager.getNewestJournalSubmission(expAnnotations);
            return js == null ? null : js.getShortAccessUrl().renderShortURL();
        }
        return null;
    }

    /**
     * @return the latest ExperimentAnnotations copied to the journal project for the given submission request.
     */
    public static @Nullable ExperimentAnnotations getLatestCopyForSubmission(JournalSubmission journalSubmission)
    {
        if(journalSubmission != null)
        {
            Submission previousSubmission = journalSubmission.getLatestCopiedSubmission();
            return previousSubmission != null ? ExperimentAnnotationsManager.get(previousSubmission.getCopiedExperimentId()) : null;
        }
        return null;
    }

    /**
     * @return the maximum value of DataVersion associated with ExperimentAnnotation copies of the
     * given source experimentAnnotationsId, or null if there are no copies of the experiment.
     */
    public static @Nullable Integer getMaxVersionForExperiment(int experimentAnnotationsId)
    {
        SQLFragment sql = new SQLFragment("SELECT MAX(DataVersion) FROM ")
                .append(PanoramaPublicManager.getTableInfoExperimentAnnotations(), "")
                .append(" WHERE DataVersion IS NOT NULL AND SourceExperimentId = ? ").add(experimentAnnotationsId);
        return new SqlSelector(PanoramaPublicManager.getSchema(), sql).getObject(Integer.class);
    }

    /**
     * @return a list of ExperimentAnnotation copies for the given sourceExperimentId.
     */
    public static List<ExperimentAnnotations> getPublishedVersionsOfExperiment(int sourceExperimentId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("SourceExperimentId"), sourceExperimentId);
        Sort sort = new Sort();
        sort.appendSortColumn(FieldKey.fromParts("Created"), Sort.SortDirection.DESC, true);
        return new TableSelector(PanoramaPublicManager.getTableInfoExperimentAnnotations(), filter, sort).getArrayList(ExperimentAnnotations.class);
    }

    /**
     * @param container
     * @param user
     * @return List of instruments that were used to acquire the data for the Skyline documents in the given container.
     * The list will only include instrument model names that have a match in the PSI-MS controlled vocabulary. We are not able
     * to get specific instrument model names from Bruker, Agilent or Waters raw data. The instrument name for data from these
     * vendors is usually reported in the Skyline documents as - e.g. "Waters instrument model".
     */
    public static @NotNull List<PsiInstrumentParser.PsiInstrument> getContainerInstruments(@NotNull Container container, User user)
    {
        List<ITargetedMSRun> runs = TargetedMSService.get().getRuns(container);
        List<Long> runIds = runs.stream().map(ITargetedMSRun::getId).collect(Collectors.toList());
        List<String> modelNames = getInstrumentModelNames(runIds, user, container);

        PsiInstrumentParser parser = new PsiInstrumentParser();
        return modelNames.stream().map(parser::tryGetInstrument).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private static List<String> getInstrumentModelNames(List<Long> runIds, User user, Container container)
    {
        if (runIds.size() > 0)
        {
            SimpleFilter filter = new SimpleFilter().addInClause(FieldKey.fromParts("runId"), runIds);
            return new TableSelector(TargetedMSService.get().getUserSchema(user, container).getTable("instrument"),
                    Collections.singleton("model"), filter, null).getArrayList(String.class);
        }
        return Collections.emptyList();
    }
}

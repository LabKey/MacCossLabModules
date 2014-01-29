package org.labkey.targetedms.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.view.NotFoundException;
import org.labkey.targetedms.SkylineDocImporter;
import org.labkey.targetedms.TargetedMSManager;
import org.labkey.targetedms.TargetedMSRun;
import org.labkey.targetedms.model.ExperimentAnnotations;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public static List<ExperimentAnnotations> get(Container container)
    {
        SimpleFilter filter = container != null ? SimpleFilter.createContainerFilter(container) : null;
        return new TableSelector(TargetedMSManager.getTableInfoExperimentAnnotations(),
                                                                   filter, null).getArrayList(ExperimentAnnotations.class);
    }

    public static ExperimentAnnotations save(Container container, ExperimentAnnotations annotations, User user)
    {
        ExperimentAnnotations toReturn = null;
        try
        {
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
                annotations.setContainer(container);
                ExperimentAnnotations existingAnnotations = get(container, annotations);
                if(existingAnnotations == null)
                {
                    toReturn = Table.insert(user, TargetedMSManager.getTableInfoExperimentAnnotations(), annotations);
                }
                else
                {
                    toReturn = existingAnnotations;
                }
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        return toReturn;
    }

    private static ExperimentAnnotations get(Container container, ExperimentAnnotations experimentAnnotations)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Container"), container);
        filter.addCondition(FieldKey.fromParts("Title"), experimentAnnotations.getTitle(),
                experimentAnnotations.getTitle() == null ? CompareType.ISBLANK : CompareType.EQUAL);
        filter.addCondition(FieldKey.fromParts("Organism"), experimentAnnotations.getOrganism(),
                experimentAnnotations.getOrganism() == null ? CompareType.ISBLANK : CompareType.EQUAL);
        filter.addCondition(FieldKey.fromParts("ExperimentDescription"), experimentAnnotations.getExperimentDescription(),
                experimentAnnotations.getExperimentDescription() == null ? CompareType.ISBLANK : CompareType.EQUAL);
        filter.addCondition(FieldKey.fromParts("SampleDescription"), experimentAnnotations.getSampleDescription(),
                experimentAnnotations.getSampleDescription() == null ? CompareType.ISBLANK : CompareType.EQUAL);
        filter.addCondition(FieldKey.fromParts("Instrument"), experimentAnnotations.getInstrument(),
                experimentAnnotations.getInstrument() == null ? CompareType.ISBLANK : CompareType.EQUAL);
        filter.addCondition(FieldKey.fromParts("SpikeIn"), experimentAnnotations.getSpikeIn(),
                experimentAnnotations.getSpikeIn() == null ? CompareType.ISBLANK : CompareType.EQUAL);
        filter.addCondition(FieldKey.fromParts("Citation"), experimentAnnotations.getCitation(),
                experimentAnnotations.getCitation() == null ? CompareType.ISBLANK : CompareType.EQUAL);
        filter.addCondition(FieldKey.fromParts("Abstract"), experimentAnnotations.getAbstract(),
                experimentAnnotations.getAbstract() == null ? CompareType.ISBLANK : CompareType.EQUAL);
        filter.addCondition(FieldKey.fromParts("PublicationLink"), experimentAnnotations.getPublicationLink(),
                experimentAnnotations.getPublicationLink() == null ? CompareType.ISBLANK : CompareType.EQUAL);

        return  new TableSelector(TargetedMSManager.getTableInfoExperimentAnnotations(), filter, null).getObject(ExperimentAnnotations.class);
    }

    public static void deleteExperimentAnnotations(int... experimentAnnotationIds)
    {

        if(experimentAnnotationIds == null || experimentAnnotationIds.length == 0)
            return;

        try(DbScope.Transaction transaction = TargetedMSManager.getSchema().getScope().ensureTransaction()) {
            for(int experimentAnnotationId: experimentAnnotationIds)
            {
                ExperimentAnnotations annotations = get(experimentAnnotationId);
                if(annotations != null)
                {
                    Table.delete(TargetedMSManager.getTableInfoExperimentAnnotationsRun(),
                                 new SimpleFilter(FieldKey.fromParts("ExperimentAnnotationsId"), experimentAnnotationId));

                    Table.delete(TargetedMSManager.getTableInfoExperimentAnnotations(), experimentAnnotationId);
                }
            }
            transaction.commit();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public static List<Integer> getRunIds(Container container, int experimentAnnotationsId)
    {
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT ear.RunId");
        sql.append(" FROM ");
        sql.append(TargetedMSManager.getTableInfoExperimentAnnotations(), "ea");
        sql.append(", ");
        sql.append(TargetedMSManager.getTableInfoExperimentAnnotationsRun(), "ear");
        sql.append(" WHERE");
        sql.append(" ea.id = ear.experimentAnnotationsId");
        sql.append(" AND");
        sql.append(" ea.container = ?");
        sql.append(" AND");
        sql.append(" ea.Id = ?");
        sql.add(container);
        sql.add(experimentAnnotationsId);

        return new SqlSelector(TargetedMSManager.getSchema(), sql).getArrayList(Integer.class);
    }

    public static void addRunIds(ExperimentAnnotations expAnnotations, List<TargetedMSRun> runs, Container container, User user)
    {
        if(runs == null || runs.size() == 0)
            return;

        List<Integer> existingRunIds = getRunIds(container, expAnnotations.getId());
        List<Integer> newRunIds = new ArrayList<>();

        for(TargetedMSRun run: runs)
        {
            validateRun(run.getId(), container);
            newRunIds.add(run.getId());
        }
        newRunIds.removeAll(existingRunIds);

        try (DbScope.Transaction transaction = TargetedMSManager.getSchema().getScope().ensureTransaction()) {
            for(Integer runId: newRunIds)
            {
                Map<String, Object> map = new HashMap();
                map.put("experimentAnnotationsId", expAnnotations.getId());
                map.put("runId", runId);
                Table.insert(user, TargetedMSManager.getTableInfoExperimentAnnotationsRun(), map);
            }
            transaction.commit();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public static void removeRunIds(ExperimentAnnotations expAnnotations, List<TargetedMSRun> runs, Container container)
    {
        try (DbScope.Transaction transaction = TargetedMSManager.getSchema().getScope().ensureTransaction())
        {
            for(TargetedMSRun run: runs)
            {
                validateRun(run.getId(), container);

                SimpleFilter filter = new SimpleFilter();
                filter.addCondition(FieldKey.fromParts("experimentAnnotationsId"), expAnnotations.getId());
                filter.addCondition(FieldKey.fromParts("runID"), run.getId());

                Table.delete(TargetedMSManager.getTableInfoExperimentAnnotationsRun(), filter);
            }

            transaction.commit();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    @NotNull
    private static TargetedMSRun validateRun(int runId, Container c)
    {
        TargetedMSRun run = TargetedMSManager.getRun(runId);

        if (null == run)
            throw new NotFoundException("TargetedMS Run " + runId + " not found");
        if (run.isDeleted())
            throw new NotFoundException("Run has been deleted.");
        if (run.getStatusId() == SkylineDocImporter.STATUS_RUNNING)
            throw new NotFoundException("Targeted MS Run is still loading.  Current status: " + run.getStatus());
        if (run.getStatusId() == SkylineDocImporter.STATUS_FAILED)
            throw new NotFoundException("TargetedMS Run failed loading.  Status: " + run.getStatus());

        Container container = run.getContainer();

        if (container == null || (!container.equals(c) && !container.isDescendant(c)))
        {
            throw new NotFoundException("TargetedMS run " + runId + " does not exist in container "+c.getPath() + " or one of its descendents");
        }

        return run;
    }
}

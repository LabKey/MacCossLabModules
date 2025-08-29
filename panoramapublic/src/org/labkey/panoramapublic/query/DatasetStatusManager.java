package org.labkey.panoramapublic.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.model.DatasetStatus;
import org.labkey.panoramapublic.model.ExperimentAnnotations;

public class DatasetStatusManager
{
    public static DatasetStatus get(int datasetStatusId)
    {
        return new TableSelector(PanoramaPublicManager.getTableInfoDatasetStatus(),null, null).getObject(datasetStatusId, DatasetStatus.class);
    }

    public static @Nullable DatasetStatus getForExperiment(ExperimentAnnotations experimentAnnotations)
    {
        if (experimentAnnotations != null)
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(FieldKey.fromParts("ExperimentAnnotationsId"), experimentAnnotations.getId());
            return new TableSelector(PanoramaPublicManager.getTableInfoDatasetStatus(), filter, null).getObject(DatasetStatus.class);
        }
        return null;
    }

    public static void save(DatasetStatus datasetStatus, User user)
    {
        Table.insert(user, PanoramaPublicManager.getTableInfoDatasetStatus(), datasetStatus);
    }

    public static void update(DatasetStatus datasetStatus, User user)
    {
        Table.update(user, PanoramaPublicManager.getTableInfoDatasetStatus(), datasetStatus, datasetStatus.getId());
    }

    public static void deleteStatusForExperiment(ExperimentAnnotations expAnnotations)
    {
        if (expAnnotations == null) return;

        try(DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
        {
            Table.delete(PanoramaPublicManager.getTableInfoDatasetStatus(), new SimpleFilter(FieldKey.fromParts("ExperimentAnnotationsId"), expAnnotations.getId()));
            transaction.commit();
        }
    }
}

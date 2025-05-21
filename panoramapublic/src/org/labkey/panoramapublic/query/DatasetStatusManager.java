package org.labkey.panoramapublic.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.model.DatasetStatus;

public class DatasetStatusManager
{
    public static DatasetStatus get(int datasetStatusId)
    {
        return new TableSelector(PanoramaPublicManager.getTableInfoDatasetStatus(),null, null).getObject(datasetStatusId, DatasetStatus.class);
    }

    public static @Nullable DatasetStatus getForShortUrl(ShortURLRecord shortUrl)
    {
        if (shortUrl != null)
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(FieldKey.fromParts("ShortUrl"), shortUrl.getEntityId());
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
}

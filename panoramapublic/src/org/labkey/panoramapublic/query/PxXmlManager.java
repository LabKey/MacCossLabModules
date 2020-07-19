package org.labkey.panoramapublic.query;

import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.model.PxXml;

import java.util.Collections;
import java.util.List;

public class PxXmlManager
{

    public static int getNextVersion(int journalExperimentId)
    {
        List<Integer> versions =  new TableSelector(PanoramaPublicManager.getTableInfoPxXml(),
                Collections.singleton("Version"),
                new SimpleFilter(FieldKey.fromParts("JournalExperimentId"), journalExperimentId),
                new Sort("-Version")).getArrayList(Integer.class);

        return versions.size() == 0 ? 1 : versions.get(0) + 1;
    }

    public static PxXml save(PxXml pxXml, User user)
    {
        return Table.insert(user, PanoramaPublicManager.getTableInfoPxXml(), pxXml);
    }
}

package org.labkey.panoramapublic.query;

import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.PanoramaPublicSchema;
import org.labkey.panoramapublic.model.SpecLibInfo;
import org.labkey.panoramapublic.model.SpecLibInfoRun;

public class SpecLibInfoManager
{
    private SpecLibInfoManager() {}

    public static SpecLibInfo[] getByRun(int runId)
    {
        SQLFragment sql = new SQLFragment(
            "SELECT sli.* FROM panoramapublic.speclibinforun slir " +
            "JOIN panoramapublic.speclibinfo sli ON sli.id = slir.speclibinfoid " +
            "WHERE runid = ?", runId);
        return new SqlSelector(PanoramaPublicSchema.getSchema(), sql).getArray(SpecLibInfo.class);
    }

    public static SpecLibInfo[] get(Container container)
    {
        SQLFragment sql = new SQLFragment(
        "SELECT sli.* FROM panoramapublic.speclibinforun slir " +
            "JOIN panoramapublic.speclibinfo sli ON sli.id = slir.speclibinfoid " +
            "JOIN targetedms.runs r ON r.id = slir.runid " +
            "WHERE container = ?", container);
        return new SqlSelector(PanoramaPublicSchema.getSchema(), sql).getArray(SpecLibInfo.class);
    }

    public static SpecLibInfo get(int id)
    {
        Filter filter = new SimpleFilter(new FieldKey(null, "id"), id);
        return new TableSelector(PanoramaPublicManager.getTableInfoSpecLibInfo(), filter, null).getObject(SpecLibInfo.class);
    }

    public static SpecLibInfo update(SpecLibInfo specLibInfo, User user)
    {
        return Table.update(user, PanoramaPublicManager.getTableInfoSpecLibInfo(), specLibInfo, specLibInfo.getId());
    }

    public static SpecLibInfo addInfo(SpecLibInfo specLibInfo, User user)
    {
        return Table.insert(user, PanoramaPublicManager.getTableInfoSpecLibInfo(), specLibInfo);
    }

    public static void addRun(ITargetedMSRun run, SpecLibInfo specLibInfo, User user)
    {
        Table.insert(user, PanoramaPublicManager.getTableInfoSpecLibInfoRun(), new SpecLibInfoRun(run.getId(), specLibInfo.getId()));
    }

    public static void deleteRuns(int runId)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("runid"), runId);
        Table.delete(PanoramaPublicManager.getTableInfoSpecLibInfoRun(), filter);

        // delete SpecLibInfos without any SpecLibInfoRuns referencing it
        SQLFragment sql = new SQLFragment(
            "DELETE FROM panoramapublic.speclibinfo sli " +
                "WHERE NOT EXISTS (SELECT id FROM panoramapublic.speclibinforun slir WHERE slir.speclibinfoid = sli.id)");
        new SqlExecutor(DbScope.getDbScope("panoramapublic")).execute(sql);
    }
}

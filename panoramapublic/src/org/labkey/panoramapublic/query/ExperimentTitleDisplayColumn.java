package org.labkey.targetedms.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.view.ActionURL;
import org.labkey.targetedms.TargetedMSController;
import org.labkey.targetedms.TargetedMSManager;

public class ExperimentTitleDisplayColumn extends ExprColumn
{
    public ExperimentTitleDisplayColumn(TableInfo table, Container container, SQLFragment whereSql, String runsTableAlias)
    {
        super(table, "Experiment", colSql(whereSql, runsTableAlias), JdbcType.INTEGER);

        setTextAlign("left");
        setFacetingBehaviorType(FacetingBehaviorType.ALWAYS_OFF);
        setFk(new LookupForeignKey(new ActionURL(TargetedMSController.ShowExperimentAnnotationsAction.class, container),
                "id", "Id", "Title")
        {
            @Override
            public @Nullable TableInfo getLookupTableInfo()
            {
                return TargetedMSManager.getTableInfoExperimentAnnotations();
            }
        });
    }

    private static SQLFragment colSql(SQLFragment whereSql, String runsTableAlias)
    {
        ExperimentService service = ExperimentService.get();
        SQLFragment sql = new SQLFragment();
        sql.append(" (SELECT expannot.Id FROM ");
        sql.append(TargetedMSManager.getTableInfoExperimentAnnotations(), "expannot");
        sql.append(" INNER JOIN ");
        sql.append(service.getTinfoRunList(), "rlist");
        sql.append(" ON ");
        sql.append("expannot.experimentId = rlist.experimentId");
        sql.append(" INNER JOIN ");
        sql.append(service.getTinfoExperimentRun(), "exprun");
        sql.append(" ON ");
        sql.append("exprun.rowId = rlist.experimentRunId");
        sql.append(" INNER JOIN ");
        sql.append(TargetedMSManager.getTableInfoRuns(), runsTableAlias);
        sql.append(" ON ");
        sql.append(runsTableAlias + ".experimentRunLsid = exprun.lsid");
        sql.append(whereSql);
        sql.append(") ");
        return sql;
    }

}

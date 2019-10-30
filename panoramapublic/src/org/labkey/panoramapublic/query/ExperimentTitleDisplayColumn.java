/*
 * Copyright (c) 2018-2019 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.targetedms.SearchResultColumnInfo;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.view.ActionURL;
import org.labkey.panoramapublic.PanoramaPublicController;
import org.labkey.panoramapublic.PanoramaPublicManager;

public class ExperimentTitleDisplayColumn
{
    public static SearchResultColumnInfo getModSearchExpColInfo()
    {
        SQLFragment sql = new SQLFragment();
        sql.append(" INNER JOIN ").append(TargetedMSService.get().getTableInfoPeptideGroup(), "pg");
        sql.append(" ON ").append("pg.runId = runs.id");
        sql.append(" INNER JOIN ").append(TargetedMSService.get().getTableInfoGeneralMolecule(), "gm");
        sql.append(" ON ").append("gm.peptideGroupId = pg.id");
        sql.append(" WHERE gm.Id = ").append(ExprColumn.STR_TABLE_ALIAS).append(".generalMoleculeId");

        return getExperimentColInfo(sql);
    }

    public static SearchResultColumnInfo getPeptideSearchExpColInfo()
    {
        SQLFragment sql = new SQLFragment();
        sql.append(" INNER JOIN ");
        sql.append(TargetedMSService.get().getTableInfoPeptideGroup(), "pg");
        sql.append(" ON ");
        sql.append("pg.runId = runs.id");
        sql.append(" WHERE pg.Id = ").append(ExprColumn.STR_TABLE_ALIAS).append(".peptideGroupId");

        return getExperimentColInfo(sql);
    }

    public static SearchResultColumnInfo getProteinSearchExpColInfo()
    {
        SQLFragment sql = new SQLFragment(" WHERE runs.Id = ").append(ExprColumn.STR_TABLE_ALIAS).append(".runId");
        return getExperimentColInfo(sql);
    }

    private static SQLFragment colSql(SQLFragment whereSql)
    {
        ExperimentService service = ExperimentService.get();
        SQLFragment sql = new SQLFragment();
        sql.append(" (SELECT expannot.Id FROM ");
        sql.append(PanoramaPublicManager.getTableInfoExperimentAnnotations(), "expannot");
        sql.append(" INNER JOIN ");
        sql.append(service.getTinfoRunList(), "rlist");
        sql.append(" ON ");
        sql.append("expannot.experimentId = rlist.experimentId");
        sql.append(" INNER JOIN ");
        sql.append(service.getTinfoExperimentRun(), "exprun");
        sql.append(" ON ");
        sql.append("exprun.rowId = rlist.experimentRunId");
        sql.append(" INNER JOIN ");
        sql.append(TargetedMSService.get().getTableInfoRuns(), "runs");
        sql.append(" ON ");
        sql.append("runs" + ".experimentRunLsid = exprun.lsid");
        sql.append(whereSql);
        sql.append(") ");
        return sql;
    }

    private static SearchResultColumnInfo getExperimentColInfo(SQLFragment whereSql)
    {
        return new SearchResultColumnInfo("Experiment", colSql(whereSql), JdbcType.INTEGER)
        {
            @Override
            public boolean showInContainer(Container container)
            {
                return container.isProject() && JournalManager.isJournalProject(container);
            }

            @Override
            public void setupColumn(ExprColumn col, Container container)
            {
                col.setTextAlign("left");
                col.setFacetingBehaviorType(FacetingBehaviorType.ALWAYS_OFF);
                col.setFk(new LookupForeignKey(col.getParentTable().getContainerFilter(), new ActionURL(PanoramaPublicController.ShowExperimentAnnotationsAction.class, container),
                        "id", "Id", "Title")
                {
                    @Override
                    public @Nullable TableInfo getLookupTableInfo()
                    {
                        return PanoramaPublicManager.getTableInfoExperimentAnnotations();
                    }
                });
            }
        };
    }
}

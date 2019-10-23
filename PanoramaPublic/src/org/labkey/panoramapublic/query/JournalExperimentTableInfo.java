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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.PanoramaPublicSchema;
import org.labkey.panoramapublic.PanoramaPublicController;
import org.labkey.panoramapublic.view.publish.ShortUrlDisplayColumnFactory;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * User: vsharma
 * Date: 9/12/2014
 * Time: 4:07 PM
 */
public class JournalExperimentTableInfo extends FilteredTable<PanoramaPublicSchema>
{
    public JournalExperimentTableInfo(final PanoramaPublicSchema schema, ContainerFilter cf)
    {
        super(PanoramaPublicManager.getTableInfoJournalExperiment(), schema, cf);

        wrapAllColumns(true);

        var editColumn = wrapColumn("Edit", getRealTable().getColumn("ExperimentAnnotationsId"));
        editColumn.setLabel("");
        editColumn.setDisplayColumnFactory(new EditUrlDisplayColumnFactory(getContainer()));
        addColumn(editColumn);

        var deleteColumn = wrapColumn("Delete", getRealTable().getColumn("ExperimentAnnotationsId"));
        deleteColumn.setLabel("");
        deleteColumn.setDisplayColumnFactory(new DeleteUrlDisplayColumnFactory(getContainer()));
        addColumn(deleteColumn);

        var accessUrlCol = getMutableColumn(FieldKey.fromParts("ShortAccessUrl"));
        accessUrlCol.setDisplayColumnFactory(new ShortUrlDisplayColumnFactory());
        var copyUrlCol = getMutableColumn(FieldKey.fromParts("ShortCopyUrl"));
        copyUrlCol.setDisplayColumnFactory(new ShortUrlDisplayColumnFactory());

        List<FieldKey> columns = new ArrayList<>();
        columns.add(FieldKey.fromParts("CreatedBy"));
        columns.add(FieldKey.fromParts("Created"));
        columns.add(FieldKey.fromParts("JournalId"));
        columns.add(FieldKey.fromParts("ShortAccessURL"));
        columns.add(FieldKey.fromParts("Copied"));
        columns.add(FieldKey.fromParts("Edit"));
        columns.add(FieldKey.fromParts("Delete"));
        setDefaultVisibleColumns(columns);
    }

    @Override
    protected void applyContainerFilter(ContainerFilter filter)
    {
        // Don't apply the container filter normally, let us apply it in our wrapper around the normally generated SQL
    }

    @Override
    @NotNull
    public SQLFragment getFromSQL(String alias)
    {
        SQLFragment sql = new SQLFragment("(SELECT X.* FROM ");
        sql.append(super.getFromSQL("X"));
        sql.append(" ");

        if (getContainerFilter() != ContainerFilter.EVERYTHING)
        {
            SQLFragment joinToExpAnnotSql = new SQLFragment("INNER JOIN ");
            joinToExpAnnotSql.append(PanoramaPublicManager.getTableInfoExperimentAnnotations(), "exp");
            joinToExpAnnotSql.append(" ON ( ");
            joinToExpAnnotSql.append("exp.id");
            joinToExpAnnotSql.append(" = ");
            joinToExpAnnotSql.append("ExperimentAnnotationsId");
            joinToExpAnnotSql.append(" ) ");

            sql.append(joinToExpAnnotSql);

            sql.append(" WHERE ");
            sql.append(getContainerFilter().getSQLFragment(getSchema(), new SQLFragment("exp.Container"), getContainer()));
        }
        sql.append(") ");
        sql.append(alias);

        return sql;
    }

    public static class DeleteUrlDisplayColumnFactory implements DisplayColumnFactory
    {
        private final ActionURL _url;
        private final String _linkText;

        DeleteUrlDisplayColumnFactory(Container container)
        {
            _url = new ActionURL(PanoramaPublicController.DeleteJournalExperimentAction.class, container);
            _linkText = "Delete";
        }

        @Override
        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            return new DataColumn(colInfo)
            {
                @Override
                public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                {
                    String experimentAnnotationsId = String.valueOf(ctx.get("ExperimentAnnotationsId"));
                    String journalId = String.valueOf(ctx.get("JournalId"));
                    if(ctx.get("Copied") != null)
                    {
                        // Do not show the delete link if the experiment has already been copied by a journal
                        out.write("");
                    }
                    else
                    {
                        _url.replaceParameter("id", experimentAnnotationsId);
                        _url.replaceParameter("journalId", journalId);
                        out.write(PageFlowUtil.link(_linkText).href(_url).toString());
                    }
                }

                @Override
                public void addQueryFieldKeys(Set<FieldKey> keys)
                {
                    super.addQueryFieldKeys(keys);
                    keys.add(FieldKey.fromParts("Copied"));
                }
            };
        }
    }

    public static class EditUrlDisplayColumnFactory implements DisplayColumnFactory
    {
        private final ActionURL _editUrl;
        private final String _editLinkText;
        private final ActionURL _resetUrl;
        private final String _republishLinkText;

        EditUrlDisplayColumnFactory(Container container)
        {
            _editUrl = new ActionURL(PanoramaPublicController.ViewPublishExperimentFormAction.class, container);
            _editUrl.addParameter("update", true);
            _editLinkText = "Edit";
            _resetUrl = new ActionURL(PanoramaPublicController.RepublishJournalExperimentAction.class, container);
            _republishLinkText = "Resubmit";
        }

        @Override
        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            return new DataColumn(colInfo)
            {
                @Override
                public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                {
                    String experimentAnnotationsId = String.valueOf(ctx.get("ExperimentAnnotationsId"));
                    String journalId = String.valueOf(ctx.get("JournalId"));
                    if(ctx.get("Copied") != null)
                    {
                        // Show the reset link if the experiment has already been copied by a journal
                        _resetUrl.replaceParameter("id", experimentAnnotationsId);
                        _resetUrl.replaceParameter("journalId", journalId);
                        out.write(PageFlowUtil.link(_republishLinkText).href(_resetUrl).toString());
                    }
                    else
                    {
                        // Otherwise show the edit link
                        _editUrl.replaceParameter("id", experimentAnnotationsId);
                        _editUrl.replaceParameter("journalId", journalId);
                        out.write(PageFlowUtil.link(_editLinkText).href(_editUrl).toString());
                    }
                }

                @Override
                public void addQueryFieldKeys(Set<FieldKey> keys)
                {
                    super.addQueryFieldKeys(keys);
                    keys.add(FieldKey.fromParts("Copied"));
                }
            };
        }
    }
}

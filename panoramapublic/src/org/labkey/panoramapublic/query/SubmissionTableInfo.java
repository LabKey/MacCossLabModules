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
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.panoramapublic.PanoramaPublicController;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.PanoramaPublicSchema;
import org.labkey.panoramapublic.model.DataLicense;
import org.labkey.panoramapublic.model.JournalSubmission;
import org.labkey.panoramapublic.model.Submission;
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
public class SubmissionTableInfo extends FilteredTable<PanoramaPublicSchema>
{
    public SubmissionTableInfo(final PanoramaPublicSchema schema, ContainerFilter cf)
    {
        super(PanoramaPublicManager.getTableInfoSubmission(), schema, cf);

        wrapAllColumns(true);

        var editColumn = wrapColumn("Edit", getRealTable().getColumn("Id"));
        editColumn.setLabel("");
        editColumn.setDisplayColumnFactory(new EditUrlDisplayColumnFactory(getContainer()));
        addColumn(editColumn);

        var deleteColumn = wrapColumn("Delete", getRealTable().getColumn("Id"));
        deleteColumn.setLabel("");
        deleteColumn.setDisplayColumnFactory(new DeleteUrlDisplayColumnFactory(getContainer()));
        addColumn(deleteColumn);

        var accessUrlCol = getMutableColumn(FieldKey.fromParts("ShortAccessUrl"));
        accessUrlCol.setDisplayColumnFactory(new ShortUrlDisplayColumnFactory());
//
//
//        var copyUrlCol = getMutableColumn(FieldKey.fromParts("ShortCopyUrl"));
//        copyUrlCol.setDisplayColumnFactory(new ShortUrlDisplayColumnFactory());

        var licenseCol = getMutableColumn(FieldKey.fromParts("DataLicense"));
        licenseCol.setURLTargetWindow("_blank");
        licenseCol.setDisplayColumnFactory(colInfo -> new DataColumn(colInfo){
            @Override
            public DataLicense getValue(RenderContext ctx)
            {
                return ctx.get(FieldKey.fromParts("DataLicense"), DataLicense.class);
            }

            @Override
            public Object getDisplayValue(RenderContext ctx)
            {
                DataLicense license = getValue(ctx);
                return license != null ? license.getDisplayName() : super.getDisplayValue(ctx);
            }

            @Override
            public @NotNull HtmlString getFormattedHtml(RenderContext ctx)
            {
                DataLicense license = getValue(ctx);
                return license != null ? HtmlString.of(license.getDisplayName()) : super.getFormattedHtml(ctx);
            }

            @Override
            public String renderURL(RenderContext ctx)
            {
                DataLicense license = getValue(ctx);
                return license != null ? license.getUrl() : super.renderURL(ctx);
            }
        });

        List<FieldKey> columns = new ArrayList<>();
        columns.add(FieldKey.fromParts("Id"));
        columns.add(FieldKey.fromParts("CreatedBy"));
        columns.add(FieldKey.fromParts("Created"));
        columns.add(FieldKey.fromParts("Version"));
        columns.add(FieldKey.fromParts("JournalExperimentId/JournalId"));
        columns.add(FieldKey.fromParts("JournalExperimentId/ShortAccessURL"));
        columns.add(FieldKey.fromParts("Edit"));
        columns.add(FieldKey.fromParts("Delete"));
        columns.add(FieldKey.fromParts("DataLicense"));
        columns.add(FieldKey.fromParts("PxidRequested"));
        columns.add(FieldKey.fromParts("CopiedExperimentId"));
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
            SQLFragment joinSql = new SQLFragment("INNER JOIN ");
            joinSql.append(PanoramaPublicManager.getTableInfoJournalExperiment(), "je")
                    .append(" ON ( je.Id = JournalExperimentId ) ");
            joinSql.append(" INNER JOIN ");
            joinSql.append(PanoramaPublicManager.getTableInfoExperimentAnnotations(), "exp");
            joinSql.append(" ON ( ");
            // JournalExperiment table contains two experiment id (table ExperimentAnnotations) columns. One for the source experiment and another for the
            // experiment copied to Panorama Public. We want to see the JournalExperiment row in the containers of both the source experiment and the
            // Panorama Public copy so we are filtering on the Container columns of both experiments.
            // This means, however, that when the container filter is changed to "All Folders", the user will see duplicate rows for a row in JournalExperiment
            // if they have read permissions in both containers.
            joinSql.append("exp.id = ").append("je.ExperimentAnnotationsId");
            joinSql.append(" OR exp.id = ").append("X.CopiedExperimentId");
            joinSql.append(" ) ");

            sql.append(joinSql);

            sql.append(" WHERE ");
            sql.append(getContainerFilter().getSQLFragment(getSchema(), new SQLFragment("exp.Container")));
        }
        sql.append(") ");
        sql.append(alias);

        return sql;
    }

    public static class DeleteUrlDisplayColumnFactory implements DisplayColumnFactory
    {
        private final ActionURL _url;

        DeleteUrlDisplayColumnFactory(Container container)
        {
            _url = new ActionURL(PanoramaPublicController.DeleteSubmissionAction.class, container);
        }

        @Override
        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            return new DataColumn(colInfo)
            {
                @Override
                public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                {
                    Integer id = ctx.get(colInfo.getFieldKey(), Integer.class);
                    Submission s = SubmissionManager.getSubmission(id);
                    if(s != null && s.getVersion() == null)
                    {
                        Integer copiedExperimentId = ctx.get(FieldKey.fromParts("CopiedExperimentId"), Integer.class);
                        if (copiedExperimentId == null)
                        {
                            // Show the delete link only if the experiment has not yet been copied
                            _url.replaceParameter("id", id);
                            out.write(PageFlowUtil.link("Delete").href(_url).toString());
                        }
                    }
                }

                @Override
                public void addQueryFieldKeys(Set<FieldKey> keys)
                {
                    super.addQueryFieldKeys(keys);
                    keys.add(FieldKey.fromParts("CopiedExperimentId"));
                }
            };
        }
    }

    public static class EditUrlDisplayColumnFactory implements DisplayColumnFactory
    {
        private final Container _container;

        EditUrlDisplayColumnFactory(Container container)
        {
            _container = container;
        }

        @Override
        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            return new DataColumn(colInfo)
            {
                @Override
                public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                {
                    Integer id = ctx.get(colInfo.getFieldKey(), Integer.class);
                    Submission s = SubmissionManager.getSubmission(id);
                    if(s != null && s.getVersion() == null)
                    {
                        //Integer journalId = ctx.get(FieldKey.fromParts("JournalId"), Integer.class);
                        //Integer experimentAnnotationsId = ctx.get(FieldKey.fromParts("ExperimentAnnotationsId"), Integer.class);

                        JournalSubmission je = SubmissionManager.getJournalSubmission(s.getJournalExperimentId());
                        if(je.isNewestSubmission(s.getId()))
                        {
                            // Show the "Resubmit" or "Edit" links only if this is the most recent submission request for the experiment.
                            if (s.getCopiedExperimentId() != null)
                            {
                                // Show the resubmit link if the experiment has already been copied by a journal
                                // but NOT if the journal copy is final.
                                if (ExperimentAnnotationsManager.canSubmitExperiment(je.getExperimentAnnotationsId()))
                                {
                                    ActionURL resubmitUrl = PanoramaPublicController.getRePublishExperimentURL(je.getExperimentAnnotationsId(), je.getJournalId(), _container, s.isKeepPrivate(),
                                            true /*check if data is valid for PXD. Always do this check on a resubmit.*/);
                                    out.write(PageFlowUtil.link("Resubmit").href(resubmitUrl).toString());
                                }
                            }
                            else
                            {
                                ActionURL ediUrl = PanoramaPublicController.getUpdateJournalExperimentURL(je.getExperimentAnnotationsId(), je.getJournalId(), _container, s.isKeepPrivate(), true);
                                out.write(PageFlowUtil.link("Edit").href(ediUrl).toString());
                            }
                        }
                    }
                }

                @Override
                public void addQueryFieldKeys(Set<FieldKey> keys)
                {
                    super.addQueryFieldKeys(keys);
                    keys.add(FieldKey.fromParts("JournalId"));
                    keys.add(FieldKey.fromParts("ExperimentAnnotationsId"));
                    keys.add(FieldKey.fromParts("Version"));
                }
            };
        }
    }
}

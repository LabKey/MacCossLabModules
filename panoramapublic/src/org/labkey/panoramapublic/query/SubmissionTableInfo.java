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

        var accessUrlCol = wrapColumn("ShortURL", getRealTable().getColumn("CopiedExperimentId"));
        accessUrlCol.setDisplayColumnFactory(new ShortUrlDisplayColumnFactory(FieldKey.fromParts("ShortUrl")));
        addColumn(accessUrlCol);

        var licenseCol = getMutableColumn(FieldKey.fromParts("DataLicense"));
        if (licenseCol != null)
        {
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
        }

        List<FieldKey> columns = new ArrayList<>();
        columns.add(FieldKey.fromParts("Id"));
        columns.add(FieldKey.fromParts("CreatedBy"));
        columns.add(FieldKey.fromParts("Created"));
        columns.add(FieldKey.fromParts("JournalExperimentId", "JournalId"));
        columns.add(FieldKey.fromParts("DataLicense"));
        columns.add(FieldKey.fromParts("PxidRequested"));
        columns.add(FieldKey.fromParts("ShortURL"));
        columns.add(FieldKey.fromParts("CopiedExperimentId", "DataVersion"));
        columns.add(FieldKey.fromParts("Copied"));
        columns.add(FieldKey.fromParts("Edit"));
        columns.add(FieldKey.fromParts("Delete"));
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
            // JournalExperiment table contains the experiment id (table ExperimentAnnotations) column of the source experiment.
            // Submission table contains the experiment id of the Panorama Public copy of the experiment.
            // We are filtering on the Container columns of both experiments so that in the folder containing the source experiment
            // we can see all the rows from the Submission table that correspond to the source experiment.
            // In the folder containing the Panorama Public copy we will see the row corresponding to the Panorama Public
            // copy of the experiment.
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
                    if (s != null && s.isPending())
                    {
                        // Show the delete link only if the experiment has not yet been copied
                        _url.replaceParameter("id", id);
                        out.write(PageFlowUtil.link("Delete").href(_url).toString());
                    }
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
                    if (s != null)
                    {
                        JournalSubmission js = SubmissionManager.getJournalSubmission(s.getJournalExperimentId());
                        if (js != null && js.isLatestSubmission(s.getId()))
                        {
                            // Show the "Resubmit" or "Edit" links only if this is the most recent submission request for the experiment.
                            if (s.hasCopy())
                            {
                                // Show the resubmit link if the experiment has already been copied by a journal
                                // but NOT if the journal copy is final.
                                if (ExperimentAnnotationsManager.canSubmitExperiment(js.getExperimentAnnotationsId(), js))
                                {
                                    ActionURL resubmitUrl = PanoramaPublicController.getRePublishExperimentURL(js.getExperimentAnnotationsId(), js.getJournalId(), _container, s.isKeepPrivate(),
                                            true /*check if data is valid for PXD. Always do this check on a resubmit.*/);
                                    out.write(PageFlowUtil.link("Resubmit").href(resubmitUrl).toString());
                                }
                            }
                            else
                            {
                                ActionURL ediUrl = PanoramaPublicController.getUpdateSubmissionURL(js.getExperimentAnnotationsId(), js.getJournalId(), _container, s.isKeepPrivate(), true);
                                out.write(PageFlowUtil.link("Edit").href(ediUrl).toString());
                            }
                        }
                    }
                }
            };
        }
    }
}

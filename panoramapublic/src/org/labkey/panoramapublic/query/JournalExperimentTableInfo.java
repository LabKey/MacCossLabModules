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
package org.labkey.targetedms.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.targetedms.PublishTargetedMSExperimentsController;
import org.labkey.targetedms.TargetedMSManager;
import org.labkey.targetedms.TargetedMSSchema;
import org.labkey.targetedms.view.publish.ShortUrlDisplayColumnFactory;

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
public class JournalExperimentTableInfo extends TargetedMSTable
{

    public JournalExperimentTableInfo(final TargetedMSSchema schema, ContainerFilter cf, Container container)
    {
        super(TargetedMSManager.getTableInfoJournalExperiment(), schema, cf, TargetedMSSchema.ContainerJoinType.ExperimentAnnotationsFK);

        var editColumn = wrapColumn("Edit", getRealTable().getColumn("ExperimentAnnotationsId"));
        editColumn.setLabel("");
        editColumn.setDisplayColumnFactory(new EditUrlDisplayColumnFactory(container));
        addColumn(editColumn);

        var deleteColumn = wrapColumn("Delete", getRealTable().getColumn("ExperimentAnnotationsId"));
        deleteColumn.setLabel("");
        deleteColumn.setDisplayColumnFactory(new DeleteUrlDisplayColumnFactory(container));
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

    public static class DeleteUrlDisplayColumnFactory implements DisplayColumnFactory
    {
        private final ActionURL _url;
        private final String _linkText;

        DeleteUrlDisplayColumnFactory(Container container)
        {
            _url = new ActionURL(PublishTargetedMSExperimentsController.DeleteJournalExperimentAction.class, container);
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
                        out.write(PageFlowUtil.textLink(_linkText, _url));
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
            _editUrl = new ActionURL(PublishTargetedMSExperimentsController.ViewPublishExperimentFormAction.class, container);
            _editUrl.addParameter("update", true);
            _editLinkText = "Edit";
            _resetUrl = new ActionURL(PublishTargetedMSExperimentsController.RepublishJournalExperimentAction.class, container);
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
                        out.write(PageFlowUtil.textLink(_republishLinkText, _resetUrl));
                    }
                    else
                    {
                        // Otherwise show the edit link
                        _editUrl.replaceParameter("id", experimentAnnotationsId);
                        _editUrl.replaceParameter("journalId", journalId);
                        out.write(PageFlowUtil.textLink(_editLinkText, _editUrl));
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

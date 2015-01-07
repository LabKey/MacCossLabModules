/*
 * Copyright (c) 2014 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerDisplayColumn;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.targetedms.TargetedMSController;
import org.labkey.targetedms.TargetedMSManager;
import org.labkey.targetedms.TargetedMSSchema;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * User: vsharma
 * Date: 12/19/13
 * Time: 2:29 PM
 */
public class ExperimentAnnotationsTableInfo extends FilteredTable
{

    public ExperimentAnnotationsTableInfo(final TargetedMSSchema schema, User user)
    {
        this(TargetedMSManager.getTableInfoExperimentAnnotations(), schema, user);
    }

    public ExperimentAnnotationsTableInfo(TableInfo tableInfo, UserSchema schema, User user)
    {
        super(tableInfo, schema, new ContainerFilter.CurrentAndSubfolders(user));

        wrapAllColumns(true);
        setDetailsURL(new DetailsURL(new ActionURL(TargetedMSController.ShowExperimentAnnotationsAction.class,
                getContainer()), "id", FieldKey.fromParts("Id")));

        ColumnInfo citationCol = getColumn(FieldKey.fromParts("Citation"));
        citationCol.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new PublicationLinkDisplayColumn(colInfo);
            }
        });
        citationCol.setURLTargetWindow("_blank");
        citationCol.setLabel("Publication");

        ColumnInfo spikeInColumn = getColumn(FieldKey.fromParts("SpikeIn"));
        spikeInColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new YesNoDisplayColumn(colInfo);
            }
        });

        ColumnInfo titleCol =  getColumn(FieldKey.fromParts("Title"));
        titleCol.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {

                return new DataColumn(colInfo, true)
                {
                    private boolean _renderedCSS = false;

                    @Override
                    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                    {
                        int id = (Integer)ctx.get("id");
                        if (!_renderedCSS)
                        {
                            out.write("<script type=\"text/javascript\">\n" +
                                    "LABKEY.requiresCss(\"/TargetedMS/css/dropDown.css\");\n" +
                                    "LABKEY.requiresScript(\"/TargetedMS/js/dropDownUtil.js\");\n" +
                                    "</script>");
                            out.write("\n<script src=\"//ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js\"></script>\n");

                            _renderedCSS = true;
                        }

                        ActionURL detailsPage = TargetedMSController.getViewExperimentDetailsURL(id, getContainer());

                        out.write("<span active=\"false\" loaded=\"false\" onclick=\"viewExperimentDetails(this,'"+id+"','"+detailsPage+"')\"><img id=\"expandcontract-"+id+"\" src=\"/labkey/_images/plus.gif\">&nbsp;");
                        out.write("</span>");
                        super.renderGridCellContents(ctx, out);
                    }
                };
            }
        });

        ColumnInfo containerCol = getColumn(FieldKey.fromParts("Container"));
        ContainerForeignKey.initColumn(containerCol, getUserSchema());

        SQLFragment runCountSQL = new SQLFragment("(SELECT COUNT(r.ExperimentRunId) FROM ");
        runCountSQL.append(ExperimentService.get().getTinfoRunList(), "r");
        runCountSQL.append(" WHERE r.ExperimentId = ");
        runCountSQL.append(ExprColumn.STR_TABLE_ALIAS);
        runCountSQL.append(".ExperimentId)");
        ExprColumn runCountColumn = new ExprColumn(this, "Runs", runCountSQL, JdbcType.INTEGER);
        addColumn(runCountColumn);

        List<FieldKey> visibleColumns = new ArrayList<>();
        visibleColumns.add(FieldKey.fromParts("Title"));
        visibleColumns.add(FieldKey.fromParts("Organism"));
        visibleColumns.add(FieldKey.fromParts("Instrument"));
        visibleColumns.add(FieldKey.fromParts("SpikeIn"));
        visibleColumns.add(FieldKey.fromParts("Citation"));
        visibleColumns.add(FieldKey.fromParts("Runs"));
        visibleColumns.add(FieldKey.fromParts("Container"));

        setDefaultVisibleColumns(visibleColumns);
    }

    @Override
    public String getName()
    {
        return TargetedMSSchema.TABLE_EXPERIMENT_ANNOTATIONS;
    }

    public static  class PublicationLinkDisplayColumn extends DataColumn
    {
        public PublicationLinkDisplayColumn(ColumnInfo colInfo)
        {
            super(colInfo);
        }

        @Override
        public Object getValue(RenderContext ctx)
        {
            Object citation = ctx.get("Citation");
            Object publicationLink = ctx.get("PublicationLink");

            if(citation != null)
            {
                if(publicationLink != null)
                {
                    setURL((String)publicationLink);
                }
                return citation;
            }
            return "";
        }

        @Override
        public Object getDisplayValue(RenderContext ctx)
        {
            return getValue(ctx);
        }

        @NotNull
        @Override
        public String getFormattedValue(RenderContext ctx)
        {
            return h(getValue(ctx));
        }

        @Override
        public void addQueryFieldKeys(Set<FieldKey> keys)
        {
            super.addQueryFieldKeys(keys);
            keys.add(FieldKey.fromParts("PublicationLink"));
        }
    }

    public static  class YesNoDisplayColumn extends DataColumn
    {
        public YesNoDisplayColumn(ColumnInfo colInfo)
        {
            super(colInfo);
        }

        @Override
        public Object getValue(RenderContext ctx)
        {
            Object value =  super.getValue(ctx);
            if(value != null)
            {
                return (Boolean)value ? "Yes" : "No";
            }
            return "";
        }

        @Override
        public Object getDisplayValue(RenderContext ctx)
        {
            return getValue(ctx);
        }

        @NotNull
        @Override
        public String getFormattedValue(RenderContext ctx)
        {
            return h(getValue(ctx));
        }
    }
}

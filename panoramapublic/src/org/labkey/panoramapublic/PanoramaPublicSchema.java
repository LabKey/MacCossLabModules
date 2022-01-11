/*
 * Copyright (c) 2019 LabKey Corporation
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

package org.labkey.panoramapublic;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.column.BuiltInColumnTypes;
import org.labkey.api.security.User;
import org.labkey.panoramapublic.query.ExperimentAnnotationsTableInfo;
import org.labkey.panoramapublic.query.JournalExperimentTableInfo;
import org.labkey.panoramapublic.query.SubmissionTableInfo;

import java.util.Set;

public class PanoramaPublicSchema extends UserSchema
{
    public static final String SCHEMA_NAME = "panoramapublic";
    public static final String SCHEMA_DESCR = "Contains data for Panorama Public";

    public static final String TABLE_JOURNAL = "Journal";
    public static final String TABLE_JOURNAL_EXPERIMENT = "JournalExperiment";
    public static final String TABLE_SUBMISSION = "Submission";
    public static final String TABLE_EXPERIMENT_ANNOTATIONS = "ExperimentAnnotations";
    public static final String TABLE_PX_XML = "PxXml";

    public PanoramaPublicSchema(User user, Container container)
    {
        super(SCHEMA_NAME, SCHEMA_DESCR, user, container, getSchema());
    }

    static public void register(Module module)
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider(module)
        {
            @Override
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new PanoramaPublicSchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public static DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME, DbSchemaType.Module);
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    @Override
    public TableInfo createTable(String name, ContainerFilter cf)
    {
        if (TABLE_EXPERIMENT_ANNOTATIONS.equalsIgnoreCase(name))
        {
            return new ExperimentAnnotationsTableInfo(this, cf);
        }
        if (TABLE_JOURNAL_EXPERIMENT.equalsIgnoreCase(name))
        {
            return new JournalExperimentTableInfo(this, cf);
        }
        if (TABLE_SUBMISSION.equalsIgnoreCase(name))
        {
            return new SubmissionTableInfo(this, cf);
        }

        if (TABLE_JOURNAL.equalsIgnoreCase(name))
        {
            FilteredTable<PanoramaPublicSchema> result = new FilteredTable<>(getSchema().getTable(name), this, cf);
            result.wrapAllColumns(true);
            var projectCol = result.getMutableColumn(FieldKey.fromParts("Project"));
            projectCol.setConceptURI(BuiltInColumnTypes.CONTAINERID_CONCEPT_URI);
            var supportContainerCol = result.getMutableColumn(FieldKey.fromParts("SupportContainer"));
            supportContainerCol.setConceptURI(BuiltInColumnTypes.CONTAINERID_CONCEPT_URI);
            return result;
        }

        if(TABLE_PX_XML.equalsIgnoreCase(name))
        {
            return getFilteredPxXmlTable(name, cf);
        }
        return null;
    }

    @NotNull
    private TableInfo getFilteredPxXmlTable(String name, ContainerFilter cf)
    {
        FilteredTable<PanoramaPublicSchema> result = new FilteredTable<>(getSchema().getTable(name), this, cf)
        {
            @Override
            protected void applyContainerFilter(ContainerFilter filter)
            {
                // Don't apply the container filter normally, let us apply it in our wrapper around the normally generated SQL
            }

            @Override
            public SQLFragment getFromSQL(String alias)
            {
                // This table does not have a Container column so we will join it to the JournalExperiment and ExperimentAnnotations
                // tables to filter by the Container of the copied experiment.
                SQLFragment sql = new SQLFragment("(SELECT X.* FROM ");
                sql.append(super.getFromSQL("X"));
                sql.append(" ");

                if (getContainerFilter() != ContainerFilter.EVERYTHING)
                {
                    SQLFragment joinToExpAnnotSql = new SQLFragment("INNER JOIN ");
                    joinToExpAnnotSql.append(PanoramaPublicManager.getTableInfoSubmission(), "s");
                    joinToExpAnnotSql.append(" ON (s.journalExperimentId = X.JournalExperimentId) ");
                    joinToExpAnnotSql.append(" INNER JOIN ");
                    joinToExpAnnotSql.append(PanoramaPublicManager.getTableInfoExperimentAnnotations(), "exp");
                    joinToExpAnnotSql.append(" ON (exp.id = s.CopiedExperimentId) ");

                    sql.append(joinToExpAnnotSql);

                    sql.append(" WHERE ");
                    sql.append(getContainerFilter().getSQLFragment(getSchema(), new SQLFragment("exp.Container")));
                }
                sql.append(") ");
                sql.append(alias);

                return sql;
            }
        };
        result.wrapAllColumns(true);
        return result;
    }

    @Override
    public Set<String> getTableNames()
    {
        CaseInsensitiveHashSet hs = new CaseInsensitiveHashSet();
        hs.add(TABLE_JOURNAL);
        hs.add(TABLE_JOURNAL_EXPERIMENT);
        hs.add(TABLE_SUBMISSION);
        hs.add(TABLE_EXPERIMENT_ANNOTATIONS);
        hs.add(TABLE_PX_XML);

        return hs;
    }
}

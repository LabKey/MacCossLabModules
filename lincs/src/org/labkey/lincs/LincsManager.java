/*
 * Copyright (c) 2015-2017 LabKey Corporation
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

package org.labkey.lincs;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.resource.FileResource;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.util.Path;
import org.labkey.lincs.psp.LincsPspJob;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LincsManager
{
    private static final LincsManager _instance = new LincsManager();

    private static Logger _log = Logger.getLogger(LincsManager.class);

    private LincsManager()
    {
        // prevent external construction with a private default constructor
    }

    public static LincsManager get()
    {
        return _instance;
    }

    public String getSchemaName()
    {
        return LincsSchema.SCHEMA_NAME;
    }

    public static DbSchema getSchema()
    {
        return DbSchema.get(LincsSchema.SCHEMA_NAME, DbSchemaType.Module);
    }

    public List<LincsAnnotation> getReplicateAnnotations(User user, Container container)
    {
        String listName = "LincsReplicateAnnotation";
        // If this folder has a list "LincsReplicateAnnotation" read data from that list
        TableInfo tableInfo = QueryService.get().getUserSchema(user, container, "lists").getTable(listName);
        if (tableInfo != null)
        {
            return new TableSelector(tableInfo).getArrayList(LincsAnnotation.class);
        }
        else
        {
            // Otherwise, read from the lincs_replicate_annotations.txt file in the module's resources directory
            _log.info("Could not find table " + listName + " in schema 'lists'. Trying to read from file.");
            return readFromFile("lincs_replicate_annotations.txt");
        }
    }

    public List<LincsAnnotation> getPeptideAnnotations(User user, Container container)
    {
        String listName = "LincsPeptideAnnotation";
        // If this folder has a list "LincsPeptideAnnotation" read data from that list
        TableInfo tableInfo = QueryService.get().getUserSchema(user, container, "lists").getTable(listName);
        if (tableInfo != null)
        {
            return new TableSelector(tableInfo).getArrayList(LincsAnnotation.class);
        }
        else
        {
            // Otherwise, read from the lincs_peptide_annotations.txt file in the module's resources directory
            _log.info("Could not find table " + listName + " in schema 'lists'. Trying to read from file.");
            return readFromFile("lincs_peptide_annotations.txt");
        }
    }

    private List<LincsAnnotation> readFromFile(String filename)
    {
        Module module = ModuleLoader.getInstance().getModule(LincsModule.class);
        FileResource resource = (FileResource)module.getModuleResolver().lookup(Path.parse(filename));
        File txt = resource.getFile();
        try (BufferedReader reader = new BufferedReader(new FileReader(txt)))
        {
            String line = reader.readLine(); // Read header
            int nameCol = -1;
            int displayNameCol = -1;
            int advancedCol = -1;
            int ignoredCol = -1;
            String[] headers = line.split("\\t");
            for(int i = 0; i < headers.length; i++)
            {
                if(headers[i].toLowerCase().equals("name")) {nameCol = i;}
                else if(headers[i].toLowerCase().equals("displayname")) {displayNameCol = i;}
                else if(headers[i].toLowerCase().equals("advanced")) {advancedCol = i;}
                else if(headers[i].toLowerCase().equals("ignored")) {ignoredCol = i;}
            }

            List<LincsAnnotation> annotations = new ArrayList<>();
            while((line = reader.readLine()) != null)
            {
                String[] parts = line.split("\\t");
                String name = nameCol == -1 ? "NO_NAME" : parts[nameCol];
                LincsAnnotation annotation = new LincsAnnotation(
                        name,  // Name
                        displayNameCol == -1 ? name : parts[displayNameCol],    // Display Name
                        advancedCol == -1 ? false : Boolean.valueOf(parts[advancedCol]),  // Advanced
                        ignoredCol == -1 ? false : Boolean.valueOf(parts[ignoredCol]));  // Ignored
                annotations.add(annotation);
            }
            return annotations;
        }
        catch (IOException e)
        {
            _log.error("Could not read file " + txt.getPath(), e);
            return Collections.emptyList();
        }
    }

    public LincsPspJob saveNewLincsPspJob(ITargetedMSRun run, User user)
    {
        LincsPspJob job = new LincsPspJob(run.getContainer(), run.getId());
        return Table.insert(user, getTableInfoLincsPspJob(), job);
    }

    public void updateLincsPspJob(LincsPspJob job, User user)
    {
        Table.update(user, getTableInfoLincsPspJob(), job, job.getId());
    }

    public void updatePipelineJobId(LincsPspJob job)
    {
        new SqlExecutor(getSchema()).execute("UPDATE " + getTableInfoLincsPspJob() + " SET PipelineJobId=? WHERE Id = ? AND PipelineJobId IS NULL",
                job.getPipelineJobId(), job.getId());
    }

    public LincsPspJob getLincsPspJobForRun(int runId)
    {
        // Get the most recent PSP job details
        Sort sort = new Sort();
        sort.appendSortColumn(FieldKey.fromParts("Modified"), Sort.SortDirection.DESC, true);
        TableSelector ts = new TableSelector(getTableInfoLincsPspJob(), new SimpleFilter(FieldKey.fromParts("RunId"), runId), sort);
        ts.setMaxRows(1);
        return ts.getObject(LincsPspJob.class);
    }

    public LincsPspJob getLincsPspJob(int id)
    {
        return new TableSelector(getTableInfoLincsPspJob(), new SimpleFilter(FieldKey.fromParts("Id"), id), null).getObject(LincsPspJob.class);
    }

    public void deleteLincsPspJobsForRun(int runId)
    {
        Table.delete(getTableInfoLincsPspJob(), new SimpleFilter(FieldKey.fromParts("runId"), runId));
    }

    public void deleteLincsPspJob(LincsPspJob job)
    {
        Table.delete(getTableInfoLincsPspJob(), new SimpleFilter(FieldKey.fromParts("Id"), job.getId()));
    }

    public static TableInfo getTableInfoLincsPspJob()
    {
        return getSchema().getTable(LincsSchema.TABLE_LINCS_PSP_JOB);
    }
}
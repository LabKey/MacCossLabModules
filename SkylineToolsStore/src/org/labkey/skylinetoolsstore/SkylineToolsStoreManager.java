/*
 * Copyright (c) 2013 LabKey Corporation
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

package org.labkey.skylinetoolsstore;

import org.labkey.api.data.Container;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.skylinetoolsstore.model.SkylineTool;

import java.sql.SQLException;

public class SkylineToolsStoreManager
{
    private static final SkylineToolsStoreManager _instance = new SkylineToolsStoreManager();

    private SkylineToolsStoreManager()
    {
        // prevent external construction with a private default constructor
    }

    public static SkylineToolsStoreManager get()
    {
        return _instance;
    }

    public void deleteAllData(Container c) throws SQLException
    {
        // delete all tools when the container is deleted
        Filter containerFilter = SimpleFilter.createContainerFilter(c);
        Table.delete(SkylineToolsStoreSchema.getInstance().getTableInfoSkylineTool(), containerFilter);
    }

    public SkylineTool getTool(int rowId)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("RowId"), rowId);
        SkylineTool[] tools = getTools(filter);
        return (tools != null && tools.length == 1) ? tools[0] : null;
    }

    public SkylineTool[] getTools(Container c)
    {
        Filter containerFilter = SimpleFilter.createContainerFilter(c);
        return getTools(containerFilter);
    }

    public SkylineTool[] getTools(Filter filter)
    {
        return new TableSelector(SkylineToolsStoreSchema.getInstance().getTableInfoSkylineTool(),
                                 filter, new Sort("Name")).getArray(SkylineTool.class);
    }

    public SkylineTool[] getToolsLatest()
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("Latest"), true);
        return getTools(filter);
    }

    public SkylineTool[] getToolsByIdentifier(String identifier)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("Identifier"), identifier);
        return getTools(filter);
    }

    public SkylineTool getLatestTool(String name)
    {
        SkylineTool tool = getToolLatestByName(name);
        if (tool == null)
        {
            // The name of the tool may have changed.  Loook for the latest tool by lsid.
            SkylineTool[] tools = getTools(new SimpleFilter().addCondition(FieldKey.fromParts("Name"), name));
            if (tools != null && tools.length > 0)
                return getToolLatestByIdentifier(tools[0].getIdentifier());
        }
        return tool;
    }

    public SkylineTool getToolLatestByIdentifier(String identifier)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("Identifier"), identifier)
              .addCondition(FieldKey.fromParts("Latest"), true);
        SkylineTool[] tools = getTools(filter);
        return (tools != null && tools.length == 1) ? tools[0] : null;
    }

    private SkylineTool getToolLatestByName(String name)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("Name"), name)
              .addCondition(FieldKey.fromParts("Latest"), true);
        SkylineTool[] tools = getTools(filter);

        return (tools != null && tools.length == 1) ? tools[0] : null;
    }

    public SkylineTool getToolByNameAndVersion(String name, String version)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("Name"), name)
              .addCondition(FieldKey.fromParts("Version"), version);
        SkylineTool[] tools = getTools(filter);
        return (tools != null && tools.length > 0) ? tools[0] : null;
    }

    public void deleteTool(int rowId) throws SQLException
    {
        Table.delete(SkylineToolsStoreSchema.getInstance().getTableInfoSkylineTool(), rowId);
    }

    public SkylineTool insertTool(Container c, User user, SkylineTool tool) throws SQLException
    {
        tool.setContainer(c.getId());
        return Table.insert(user, SkylineToolsStoreSchema.getInstance().getTableInfoSkylineTool(), tool);
    }

    public SkylineTool updateTool(Container c, User user, SkylineTool tool) throws SQLException
    {
        if (tool.getContainerId() == null)
            tool.setContainerId(c.getId());
        if (tool.getRowId() == null)
        {
            throw new IllegalStateException("Can't update a row with a null rowId");
        }
        if(!tool.getContainerId().equals(c.getId()))
        {
            throw new IllegalStateException("Container mismatch. Container associated with tool is " + tool.lookupContainer()
            + ". updateTool called with " + c);
        }

        return Table.update(user, SkylineToolsStoreSchema.getInstance().getTableInfoSkylineTool(),
                            tool, tool.getRowId());
    }

    public SkylineTool recordToolDownload(SkylineTool tool) throws SQLException
    {
        tool.setDownloads(tool.getDownloads() + 1);
        return Table.update(null, SkylineToolsStoreSchema.getInstance().getTableInfoSkylineTool(),
                            tool, tool.getRowId());
    }
}
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
import org.labkey.skylinetoolsstore.model.Rating;
import org.labkey.skylinetoolsstore.model.SkylineTool;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RatingManager
{
    private static final RatingManager _instance = new RatingManager();

    private RatingManager()
    {
        // prevent external construction with a private default constructor
    }

    public static RatingManager get()
    {
        return _instance;
    }

    public void deleteAllData(Container c) throws SQLException
    {
        // delete all tools when the container is deleted
        Filter containerFilter = SimpleFilter.createContainerFilter(c);
        Table.delete(SkylineToolsStoreSchema.getInstance().getTableInfoSkylineTool(), containerFilter);
    }

    public Rating[] getRatings(Filter filter)
    {
        Sort sort = new Sort();
        FieldKey fieldKey = FieldKey.fromParts("modified");
        sort.insertSortColumn(fieldKey, Sort.SortDirection.DESC);
        return new TableSelector(SkylineToolsStoreSchema.getInstance().getTableInfoRating(),
                filter, sort).getArray(Rating.class);
    }
    public Rating getRatingById(Integer rowId)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("rowId"), rowId);
        Rating[] ratings = getRatings(filter);
        return (ratings != null && ratings.length > 0) ? ratings[0] : null;
    }

    public Rating[] getRatingsByToolId(Integer toolId)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("toolId"), toolId);
        return getRatings(filter);
    }

    public Rating[] getRatingsByToolAllVersions(String toolLsid)
    {
        List<Rating> ratings = new ArrayList<>();
        SkylineTool[] tools = SkylineToolsStoreManager.get().getToolsByIdentifier(toolLsid);
        if (tools != null)
        {
            Collections.reverse(Arrays.asList(tools));
            for (SkylineTool tool : tools)
                ratings.addAll(Arrays.asList(getRatingsByToolId(tool.getRowId())));
        }
        return ratings.toArray(new Rating[ratings.size()]);
    }

    public boolean userLeftRating(String toolLsid, User user)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("toolId"), SkylineToolsStoreManager.get().getToolLatestByIdentifier(toolLsid).getRowId());
        filter.addCondition(FieldKey.fromParts("createdBy"), user.getUserId());
        Rating[] ratings = getRatings(filter);
        return ratings != null && ratings.length > 0;
    }

    public void deleteRating(int rowId) throws SQLException
    {
        Table.delete(SkylineToolsStoreSchema.getInstance().getTableInfoRating(), rowId);
    }

    public Rating editRating(Rating rating, User user) throws SQLException
    {
        return Table.update(user, SkylineToolsStoreSchema.getInstance().getTableInfoRating(),
                rating, rating.getRowId());
    }

    public void deleteRatings(Filter filter) throws SQLException
    {
        Table.delete(SkylineToolsStoreSchema.getInstance().getTableInfoRating(), filter);
    }

    public void deleteRatingsByToolId(int toolId) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("toolId"), toolId);
        deleteRatings(filter);
    }

    public Rating insertRating(User user, Rating rating) throws SQLException
    {
        return Table.insert(user, SkylineToolsStoreSchema.getInstance().getTableInfoRating(), rating);
    }
}
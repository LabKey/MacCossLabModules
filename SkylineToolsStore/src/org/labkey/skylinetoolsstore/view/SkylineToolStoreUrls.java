/*
 * Copyright (c) 2017-2026 LabKey Corporation
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
package org.labkey.skylinetoolsstore.view;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.skylinetoolsstore.SkylineToolsStoreController;
import org.labkey.skylinetoolsstore.model.SkylineTool;

/**
 * User: vsharma
 * Date: 12/15/13
 * Time: 11:24 PM
 */
public class SkylineToolStoreUrls
{
    private SkylineToolStoreUrls() {}

    public static ActionURL getToolStoreHomeUrl(Container container, User user)
    {
        return container.getStartURL(user);
    }

    public static ActionURL getToolDetailsUrl(SkylineTool tool)
    {
        ActionURL url = new ActionURL(SkylineToolsStoreController.DetailsAction.class, tool.getContainerParent()).addParameter("name", tool.getName());
        if (!tool.getLatest())
            url.addParameter("version", tool.getVersion());
        return url;
    }

    public static ActionURL getToolDetailsLatestUrl(SkylineTool tool)
    {
        return new ActionURL(SkylineToolsStoreController.DetailsAction.class, tool.getContainerParent()).addParameter("name", tool.getName());
    }
}

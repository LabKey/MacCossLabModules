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

import org.labkey.api.action.PermissionCheckableAction;
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

    /** Adding a new tool happens in the store folder, so this takes a container rather than a tool. */
    public static ActionURL getInsertToolUrl(Container storeContainer)
    {
        return new ActionURL(SkylineToolsStoreController.InsertToolAction.class, storeContainer);
    }

    public static ActionURL getUpdateToolUrl(SkylineTool tool)
    {
        return getToolActionUrl(SkylineToolsStoreController.UpdateToolAction.class, tool);
    }

    public static ActionURL getInsertSupplementUrl(SkylineTool tool)
    {
        return getToolActionUrl(SkylineToolsStoreController.InsertSupplementAction.class, tool);
    }

    public static ActionURL getDeleteSupplementUrl(SkylineTool tool)
    {
        return getToolActionUrl(SkylineToolsStoreController.DeleteSupplementAction.class, tool);
    }

    public static ActionURL getUpdatePropertyUrl(SkylineTool tool)
    {
        return getToolActionUrl(SkylineToolsStoreController.UpdatePropertyAction.class, tool);
    }

    /**
     * Takes the newest version rather than the tool being viewed, because that is the folder the
     * action removes and so the folder its permission has to be checked against.
     */
    public static ActionURL getDeleteLatestUrl(SkylineTool latestVersion)
    {
        return getToolActionUrl(SkylineToolsStoreController.DeleteLatestAction.class, latestVersion);
    }

    /**
     * URL for an action targeting the tool's own container rather than the tool store container.
     *
     * Actions that operate on a single tool are annotated with the permission they need, and the
     * annotation is checked against the container in the URL. Since a tool lives in its own child
     * folder, the URL has to name that folder for the annotation to check the right thing.
     */
    private static ActionURL getToolActionUrl(Class<? extends PermissionCheckableAction> action, SkylineTool tool)
    {
        return new ActionURL(action, tool.lookupContainer());
    }
}

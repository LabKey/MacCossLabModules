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

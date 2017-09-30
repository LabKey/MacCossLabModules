package org.labkey.skylinetoolsstore.view;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.JspView;
import org.labkey.skylinetoolsstore.SkylineToolsStoreManager;
import org.labkey.skylinetoolsstore.model.SkylineTool;

import java.util.Arrays;
import java.util.List;

public class SkylineToolsStoreWebPart extends JspView<List<SkylineTool>>
{
    public SkylineToolsStoreWebPart()
    {
        super("/org/labkey/skylinetoolsstore/view/SkylineToolsStoreWebPart.jsp", null);
        setModelBean(Arrays.asList(SkylineToolsStoreManager.get().getToolsLatest()));
        setTitle("Skyline Tool Store");
        Container container = getViewContext().getContainer();
        User user = getViewContext().getUser();
        setTitleHref(SkylineToolStoreUrls.getToolStoreHomeUrl(container, user));
    }
}

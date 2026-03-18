package org.labkey.skylinetoolsstore.view;

import org.labkey.api.view.JspView;

import org.labkey.skylinetoolsstore.model.SkylineTool;

public class SkylineToolDetails extends JspView<SkylineTool>
{
    public SkylineToolDetails(SkylineTool tool)
    {
        super("/org/labkey/skylinetoolsstore/view/SkylineToolDetails.jsp", null);
        setModelBean(tool);
        setTitle(tool.getName());
        setTitleHref(SkylineToolStoreUrls.getToolDetailsUrl(tool));
    }
}

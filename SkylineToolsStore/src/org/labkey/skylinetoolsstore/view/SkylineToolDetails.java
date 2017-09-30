package org.labkey.skylinetoolsstore.view;

import org.labkey.api.view.JspView;

import org.labkey.skylinetoolsstore.RatingManager;

import org.labkey.skylinetoolsstore.model.SkylineTool;

import java.util.Arrays;

public class SkylineToolDetails extends JspView<SkylineTool>
{
    public SkylineToolDetails(SkylineTool tool)
    {
        super("/org/labkey/skylinetoolsstore/view/SkylineToolDetails.jsp", null);
        setModelBean(tool);
        getViewContext().getRequest().setAttribute("ratings", Arrays.asList(RatingManager.get().getRatingsByToolId(tool.getRowId())));
        setTitle(tool.getName());
        setTitleHref(SkylineToolStoreUrls.getToolDetailsUrl(tool));
    }
}

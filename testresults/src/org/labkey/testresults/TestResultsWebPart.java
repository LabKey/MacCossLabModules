package org.labkey.testresults;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartConfigurationException;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.testresults.view.TestsDataBean;

import java.io.IOException;
import java.text.ParseException;

public class TestResultsWebPart extends BaseWebPartFactory
{
    private static final Logger LOG = LogHelper.getLogger(TestResultsWebPart.class, "Test results web part");

    public TestResultsWebPart()
    {
        super("Test Results", true, false, WebPartFactory.LOCATION_BODY);
    }

    @Override
    public WebPartView<?> getWebPartView(@NotNull ViewContext portalCtx, Portal.@NotNull WebPart webPart) throws WebPartConfigurationException
    {
        Container c =portalCtx.getContainer();
        TestsDataBean bean = null;
        try
        {
            bean = TestResultsController.getRunDownBean(portalCtx.getUser(), c);
        }
        catch (ParseException | IOException e)
        {
            LOG.error("Failed to build the test results web part data", e);
        }
        JspView<TestsDataBean> view = new JspView<>("/org/labkey/testresults/view/rundown.jsp", bean);
        view.setTitle("Test Results");
        view.setFrame(WebPartView.FrameType.PORTAL);
        return view;
    }
}

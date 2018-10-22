package org.labkey.testresults;

import org.labkey.api.data.Container;
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
    public TestResultsWebPart()
    {
        super("Test Results", true, false, WebPartFactory.LOCATION_BODY);
    }

    @Override
    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws WebPartConfigurationException
    {
        Container c =portalCtx.getContainer();
        TestsDataBean bean = null;
        try
        {
            bean = TestResultsController.getRunDownBean(portalCtx.getUser(), c, portalCtx);
        }
        catch (ParseException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        JspView<TestsDataBean> view = new JspView<>("/org/labkey/testresults/view/rundown.jsp", bean);
        view.setTitle("Test Results");
        view.setFrame(WebPartView.FrameType.PORTAL);
        return view;
    }
}

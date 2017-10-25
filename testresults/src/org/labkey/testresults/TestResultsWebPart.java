package org.labkey.testresults;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartConfigurationException;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.labkey.testresults.TestResultsController.getRunsSinceDate;
import static org.labkey.testresults.TestResultsController.getTrainingDataForContainer;
import static org.labkey.testresults.TestResultsController.populateFailures;
import static org.labkey.testresults.TestResultsController.populateLeaks;
import static org.labkey.testresults.TestResultsController.populatePassesLeaksFails;

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
            bean = TestResultsController.getRunDownData(portalCtx.getUser(), c, portalCtx);
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

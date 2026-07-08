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
package org.labkey.testresults;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
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
    public WebPartView<?> getWebPartView(@NotNull ViewContext portalCtx, Portal.@NotNull WebPart webPart)
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

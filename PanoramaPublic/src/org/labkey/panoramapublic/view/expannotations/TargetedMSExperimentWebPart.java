/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
package org.labkey.targetedms.view.expannotations;

import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.targetedms.TargetedMSController;
import org.labkey.targetedms.model.ExperimentAnnotations;
import org.labkey.targetedms.query.ExperimentAnnotationsManager;
import org.labkey.targetedms.view.TargetedMsRunListView;

/**
 * User: vsharma
 * Date: 10/2/13
 * Time: 11:39 AM
 */
public class TargetedMSExperimentWebPart extends VBox
{
    public static final String WEB_PART_NAME = "Targeted MS Experiment";

    public TargetedMSExperimentWebPart(ViewContext portalCtx)
    {
        Container container = portalCtx.getContainer();

        // Get an experiment that includes data in this container.
        ExperimentAnnotations expAnnotations = ExperimentAnnotationsManager.getExperimentIncludesContainer(container);
        if(expAnnotations == null)
        {
            // There is no experiment defined in this container, or in a parent container that is configured
            // to include subfolders.
            StringBuilder html = new StringBuilder("<div>This folder does not contain an experiment.</div>");
            ActionURL url = new ActionURL(TargetedMSController.ShowNewExperimentAnnotationFormAction.class, container);
            html.append("<div style=\"margin-top: 20px;\">");
            html.append("<a href=\"").append(url).append("\">Create New Experiment</a>");
            html.append("</div>");
            HtmlView view = new HtmlView(html.toString());
            addView(view);
        }
        else if(expAnnotations.getContainer().equals(container))
        {
            // There is already an experiment defined in this container.
            TargetedMSController.ExperimentAnnotationsDetails experimentDetails = new TargetedMSController.ExperimentAnnotationsDetails(getViewContext().getUser(), expAnnotations, false);
            JspView<TargetedMSController.ExperimentAnnotationsDetails> view = new JspView<>("/org/labkey/targetedms/view/expannotations/experimentDetails.jsp", experimentDetails);
            addView(view);
            ActionURL url = TargetedMSController.getViewExperimentDetailsURL(expAnnotations.getId(), container);
            setTitleHref(url);
        }
        else
        {
            // There is an experiment defined in a parent container that is configured to include subfolders.
            StringBuilder html = new StringBuilder("<div>A parent folder contains an experiment that includes data in this folder.</div>");
            ActionURL url = TargetedMSController.getViewExperimentDetailsURL(expAnnotations.getId(), container);
            html.append("<div style=\"margin-top: 20px;\">");
            html.append("<a href=\"").append(url.getEncodedLocalURIString()).append("\">View Experiment Details</a>");
            html.append("</div>");
            HtmlView view = new HtmlView(html.toString());
            addView(view);
        }
        setTitle("Targeted MS Experiment");
        setFrame(FrameType.PORTAL);
    }
}
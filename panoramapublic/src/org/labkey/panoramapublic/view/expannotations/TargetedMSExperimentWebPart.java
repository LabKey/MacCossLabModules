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
package org.labkey.panoramapublic.view.expannotations;

import org.labkey.api.data.Container;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.util.Link;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.panoramapublic.PanoramaPublicController;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.JournalSubmission;
import org.labkey.panoramapublic.query.ExperimentAnnotationsManager;
import org.labkey.panoramapublic.query.SubmissionManager;

import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.at;

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
        this(ExperimentAnnotationsManager.getExperimentIncludesContainer(portalCtx.getContainer()), // TargetedMSExperiment in this container.
                portalCtx, false);
    }

    public TargetedMSExperimentWebPart(ExperimentAnnotations expAnnotations, ViewContext portalCtx, boolean fullDetails)
    {
        Container container = portalCtx.getContainer();

        if(expAnnotations == null)
        {
            // There is no experiment defined in this container, or in a parent container that is configured
            // to include subfolders.
            HtmlView view = new HtmlView(DIV(
                    DIV("This folder does not contain an experiment."),
                    DIV(at(style, "margin-top:20px;"),
                            new Link.LinkBuilder("Create New Experiment")
                                    .href(new ActionURL(PanoramaPublicController.ShowNewExperimentAnnotationFormAction.class, container))
                                    .build())));
            addView(view);
        }
        else if(expAnnotations.getContainer().equals(container))
        {
            // There is already an experiment defined in this container.
            PanoramaPublicController.ExperimentAnnotationsDetails experimentDetails = new PanoramaPublicController.ExperimentAnnotationsDetails(getViewContext().getUser(), expAnnotations, fullDetails);
            JspView<PanoramaPublicController.ExperimentAnnotationsDetails> view = new JspView<>("/org/labkey/panoramapublic/view/expannotations/experimentDetails.jsp", experimentDetails);
            addView(view);
            ActionURL url = PanoramaPublicController.getViewExperimentDetailsURL(expAnnotations.getId(), container);
            setTitleHref(url);
            if (portalCtx.hasPermission(AdminOperationsPermission.class))
            {
                NavTree navTree = new NavTree();
                navTree.addChild("ProteomeXchange", new ActionURL(PanoramaPublicController.GetPxActionsAction.class, container).addParameter("id", expAnnotations.getId()));
                navTree.addChild("Data Validation", new ActionURL(PanoramaPublicController.ViewPxValidationsAction.class, container).addParameter("id", expAnnotations.getId()));
                navTree.addChild("DOI", new ActionURL(PanoramaPublicController.DoiOptionsAction.class, container).addParameter("id", expAnnotations.getId()));
                navTree.addChild("Make Data Public", new ActionURL(PanoramaPublicController.MakePublicAction.class, container).addParameter("id", expAnnotations.getId()));

                if (expAnnotations.isJournalCopy())
                {
                    JournalSubmission submission = SubmissionManager.getSubmissionForJournalCopy(expAnnotations);
                    ExperimentAnnotations sourceExpt = submission != null ? ExperimentAnnotationsManager.get(submission.getExperimentAnnotationsId()) : null;
                    if (sourceExpt != null)
                    {
                        navTree.addChild("Source Experiment", PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(sourceExpt.getContainer()));
                    }
                }
                setNavMenu(navTree);
            }
        }
        else
        {
            // There is an experiment defined in a parent container that is configured to include subfolders.
            HtmlView view = new HtmlView(DIV(
                    DIV("A parent folder contains an experiment that includes data in this folder."),
                    DIV(at(style, "margin-top: 20px;"),
                            new Link.LinkBuilder("View Experiment Details")
                                    .href(PanoramaPublicController.getViewExperimentDetailsURL(expAnnotations.getId(), container))
                                    .build() )
            ));
            addView(view);
        }
        setTitle("Targeted MS Experiment");
        setFrame(FrameType.PORTAL);
    }
}
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

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataRegion;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.targetedms.TargetedMSController;
import org.labkey.targetedms.TargetedMSSchema;

/**
 * User: vsharma
 * Date: 10/2/13
 * Time: 11:39 AM
 */
public class TargetedMSExperimentsWebPart extends QueryView
{
    public static final String WEB_PART_NAME = "Targeted MS Experiment List";

    public TargetedMSExperimentsWebPart(ViewContext portalCtx)
    {
        super(new TargetedMSSchema(portalCtx.getUser(), portalCtx.getContainer()));

        setTitle(WEB_PART_NAME);

        setSettings(createQuerySettings(portalCtx, WEB_PART_NAME));

        setShowDetailsColumn(false);
        setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);
        setShowExportButtons(false);
        setShowBorders(true);
        setShadeAlternatingRows(true);

        setAllowableContainerFilterTypes(ContainerFilter.Type.Current,
                                         ContainerFilter.Type.CurrentAndSubfolders);

        setFrame(FrameType.PORTAL);
    }

    private QuerySettings createQuerySettings(ViewContext portalCtx, String dataRegionName)
    {
        QuerySettings settings = getSchema().getSettings(portalCtx, dataRegionName, TargetedMSSchema.TABLE_EXPERIMENT_ANNOTATIONS);
        if(settings.getContainerFilterName() == null)
        {
            settings.setContainerFilterName(ContainerFilter.Type.CurrentAndSubfolders.name());
        }
        return settings;
    }

    protected void populateButtonBar(DataView view, ButtonBar bb)
    {
        super.populateButtonBar(view, bb);
        ActionURL deleteExpAnnotUrl = new ActionURL(TargetedMSController.DeleteSelectedExperimentAnnotationsAction.class, getContainer());
        ActionButton deleteExperimentAnnotation = new ActionButton(deleteExpAnnotUrl, "Delete");
        deleteExperimentAnnotation.setActionType(ActionButton.Action.GET);
        deleteExperimentAnnotation.setDisplayPermission(DeletePermission.class);
        deleteExperimentAnnotation.setRequiresSelection(true);
        bb.add(deleteExperimentAnnotation);
    }
}
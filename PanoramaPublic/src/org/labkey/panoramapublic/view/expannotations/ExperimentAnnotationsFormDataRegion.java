/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.targetedms.TargetedMSController;
import org.labkey.targetedms.TargetedMSManager;

/**
 * User: vsharma
 * Date: 12/18/13
 * Time: 4:31 PM
 */
public class ExperimentAnnotationsFormDataRegion extends DataRegion
{
    public ExperimentAnnotationsFormDataRegion(ViewContext viewContext, TargetedMSController.ExperimentAnnotationsForm form, int mode)
    {
        super();

        addColumns(TargetedMSManager.getTableInfoExperimentAnnotations(), "Id,Title,Organism,Instrument,SpikeIn,Abstract,ExperimentDescription,SampleDescription, Citation,PublicationLink,");

        DisplayColumn idCol = getDisplayColumn("Id");
        idCol.setVisible(false);

        ButtonBar bb = new ButtonBar();
        bb.setStyle(ButtonBar.Style.separateButtons);

        ActionButton cancelButton = new ActionButton(form.getReturnActionURL(viewContext.getContainer().getStartURL(viewContext.getUser())), "Cancel");
            cancelButton.setActionType(ActionButton.Action.LINK);

        switch(mode)
        {
            case DataRegion.MODE_INSERT:
                ActionURL submitUrl = new ActionURL(TargetedMSController.SaveNewExperimentAnnotationAction.class, viewContext.getContainer());
                ActionButton insertButton = new ActionButton(submitUrl, "Submit");
                insertButton.setDisplayPermission(InsertPermission.class);
                insertButton.setActionType(ActionButton.Action.POST);

                bb.add(insertButton);
                bb.add(cancelButton);
                break;

            case DataRegion.MODE_UPDATE:
                ActionURL updateUrl = new ActionURL(TargetedMSController.UpdateExperimentAnnotationsAction.class, viewContext.getContainer());
                ActionButton updateButton = new ActionButton(updateUrl, "Update");
                updateButton.setDisplayPermission(UpdatePermission.class);
                updateButton.setActionType(ActionButton.Action.POST);

                bb.add(updateButton);
                bb.add(cancelButton);
                break;
        }

        setButtonBar(bb);
    }
}

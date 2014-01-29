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

        addColumns(TargetedMSManager.getTableInfoExperimentAnnotations(), "Id,Title,Organism,Instrument,SpikeIn,Abstract,Citation,PublicationLink,ExperimentDescription,SampleDescription");

        setFixedWidthColumns(false);

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

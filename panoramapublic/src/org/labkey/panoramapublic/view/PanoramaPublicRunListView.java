package org.labkey.panoramapublic.view;

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataRegion;
import org.labkey.api.exp.ExperimentRunListView;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.panoramapublic.PanoramaPublicController;
import org.labkey.panoramapublic.model.ExperimentAnnotations;

public class PanoramaPublicRunListView extends ExperimentRunListView
{
    private ExperimentAnnotations _expAnnotations;

    public PanoramaPublicRunListView(ExperimentAnnotations expAnnotations, UserSchema schema, QuerySettings settings)
    {
        super(schema, settings, TargetedMSService.get().getExperimentRunType());

        _expAnnotations = expAnnotations;

        if(expAnnotations.isIncludeSubfolders())
        {
            // We are looking at the details of an experiment that includes sub-folders. Set the container filter
            // to CurrentAndSubfolders so that runs in subfolders are visible
            settings.setContainerFilterName(ContainerFilter.Type.CurrentAndSubfolders.name());
        }

        setShowMoveRunsButton(false);
        setShowExportButtons(false);
        if(expAnnotations.isJournalCopy())
        {
            setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);
            setShowRecordSelectors(false);
        }
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        if(!_expAnnotations.isJournalCopy())
        {
            String buttonText = _expAnnotations.isIncludeSubfolders() ? "Exclude Subfolders" : "Include Subfolders";
            ActionURL url = _expAnnotations.isIncludeSubfolders() ?
                    PanoramaPublicController.getExcludeSubfoldersInExperimentURL(_expAnnotations.getId(), getViewContext().getContainer(), getReturnURL()) :
                    PanoramaPublicController.getIncludeSubfoldersInExperimentURL(_expAnnotations.getId(), getViewContext().getContainer(), getReturnURL());

            ActionButton includeSubfoldersBtn = new ActionButton(buttonText, url);
            includeSubfoldersBtn.setDisplayPermission(InsertPermission.class);
            includeSubfoldersBtn.setActionType(ActionButton.Action.POST);
            bar.add(includeSubfoldersBtn);
        }
    }

    public static PanoramaPublicRunListView createView(ViewContext model, ExperimentAnnotations expAnnotations)
    {
        UserSchema schema = TargetedMSService.get().getUserSchema(model.getUser(), model.getContainer());
        QuerySettings querySettings = getRunListQuerySettings(schema, model, TargetedMSService.get().getExperimentRunType().getTableName(), true);

        PanoramaPublicRunListView view = new PanoramaPublicRunListView(expAnnotations, schema, querySettings);
        view.setTitle("Targeted MS Runs");
        view.setFrame(WebPartView.FrameType.PORTAL);

        return view;
    }
}

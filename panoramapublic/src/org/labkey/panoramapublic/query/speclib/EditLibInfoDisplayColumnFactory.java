package org.labkey.panoramapublic.query.speclib;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.HtmlWriter;
import org.labkey.panoramapublic.PanoramaPublicController;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.query.ExperimentAnnotationsManager;

import java.util.Set;

public class EditLibInfoDisplayColumnFactory implements DisplayColumnFactory
{
    private static final FieldKey SPECLIB_INFO_ID = FieldKey.fromParts("specLibInfoId");
    private static final FieldKey EXPT_ANNOT_ID = FieldKey.fromParts("specLibInfoId", "experimentAnnotationsId");

    public EditLibInfoDisplayColumnFactory() {}

    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new DataColumn(colInfo)
        {
            @Override
            public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
            {
                if (ctx.getContainer().hasPermission(ctx.getViewContext().getUser(), UpdatePermission.class))
                {
                    Long specLibId = ctx.get(colInfo.getFieldKey(), Long.class);
                    if (specLibId != null)
                    {
                        Integer specLibInfoId = ctx.get(SPECLIB_INFO_ID, Integer.class);
                        Integer experimentAnnotationsId;
                        if (specLibInfoId != null)
                        {
                            experimentAnnotationsId = ctx.get(EXPT_ANNOT_ID, Integer.class);
                        }
                        else
                        {
                            ExperimentAnnotations exptAnnotations = ExperimentAnnotationsManager.getExperimentInContainer(ctx.getContainer());
                            experimentAnnotationsId = exptAnnotations != null ? exptAnnotations.getId() : null;
                        }
                        if (experimentAnnotationsId != null)
                        {
                            URLHelper returnUrl = ctx.getViewContext().getActionURL().getReturnUrl();
                            if (returnUrl == null)
                            {
                                returnUrl = ctx.getViewContext().getActionURL();
                            }
                            ActionURL editUrl = PanoramaPublicController.getEditSpecLibInfoURL(experimentAnnotationsId, specLibId, specLibInfoId, ctx.getContainer());
                            editUrl.addReturnUrl(returnUrl);
                            out.write(LinkBuilder.labkeyLink(specLibInfoId != null ? "Edit" : "Add", editUrl));
                            if (specLibInfoId != null)
                            {
                                ActionURL deleteUrl = PanoramaPublicController.getDeleteSpecLibInfoURL(experimentAnnotationsId, specLibInfoId, ctx.getContainer());
                                deleteUrl.addReturnUrl(returnUrl);
                                out.write(LinkBuilder.labkeyLink("Delete", deleteUrl).usePost("Are you sure you want to delete the spectral library information?"));
                            }
                        }
                    }
                }
            }

            @Override
            public void addQueryFieldKeys(Set<FieldKey> keys)
            {
                super.addQueryFieldKeys(keys);
                keys.add(SPECLIB_INFO_ID);
                keys.add(EXPT_ANNOT_ID);
            }
        };
    }
}

package org.labkey.panoramapublic.query.speclib;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.panoramapublic.PanoramaPublicController;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.query.ExperimentAnnotationsManager;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

public class EditLibraryDisplayColumnFactory implements DisplayColumnFactory
{
    private static final FieldKey SPECLIB_INFO_ID = FieldKey.fromParts("specLibInfoId");
    private static final FieldKey EXPT_ANNOT_ID = FieldKey.fromParts("specLibInfoId", "experimentAnnotationsId");

    public EditLibraryDisplayColumnFactory() {}

    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new DataColumn(colInfo)
        {
            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                // Show the link only to users that have have admin permissions in the container
                if (ctx.getContainer().hasPermission(ctx.getViewContext().getUser(), AdminPermission.class))
                {
                    Long specLibId = ctx.get(colInfo.getFieldKey(), Long.class);
                    Integer specLibInfoId = ctx.get(SPECLIB_INFO_ID, Integer.class);
                    int experimentAnnotationsId;
                    if (specLibInfoId != null)
                    {
                        experimentAnnotationsId = ctx.get(EXPT_ANNOT_ID, Integer.class);
                    }
                    else
                    {
                        ExperimentAnnotations exptAnnotations = ExperimentAnnotationsManager.getInContainer(ctx.getContainer());
                        experimentAnnotationsId = exptAnnotations != null ? exptAnnotations.getId() : 0;
                    }
                    if (experimentAnnotationsId != 0)
                    {
                        ActionURL editUrl = PanoramaPublicController.getEditSpecLibInfoURL(experimentAnnotationsId, specLibId, specLibInfoId, ctx.getContainer());
                        editUrl.addReturnURL(ctx.getViewContext().getActionURL());
                        out.write(PageFlowUtil.link(specLibInfoId != null ? "Edit" : "Add").href(editUrl).toString());
                        return;
                    }

                }
                out.write("<em>Not Editable</em>");
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

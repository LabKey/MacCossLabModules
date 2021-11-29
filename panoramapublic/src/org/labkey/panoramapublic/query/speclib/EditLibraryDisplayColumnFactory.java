package org.labkey.panoramapublic.query.speclib;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.permissions.UpdatePermission;
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
                            ActionURL editUrl = PanoramaPublicController.getEditSpecLibInfoURL(experimentAnnotationsId, specLibId, specLibInfoId, ctx.getContainer());
                            editUrl.addReturnURL(ctx.getViewContext().getActionURL());
                            out.write(PageFlowUtil.link(specLibInfoId != null ? "Edit" : "Add").href(editUrl).toString());
                            return;
                        }
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

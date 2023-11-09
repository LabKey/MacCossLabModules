package org.labkey.panoramapublic.view.publish;

import org.labkey.api.data.Container;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.Button;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.WebPartView;
import org.labkey.panoramapublic.PanoramaPublicController;
import org.labkey.panoramapublic.catalog.CatalogEntrySettings;
import org.labkey.panoramapublic.model.CatalogEntry;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.query.CatalogEntryManager;
import org.labkey.panoramapublic.query.ExperimentAnnotationsManager;
import org.labkey.panoramapublic.security.PanoramaPublicSubmitterPermission;

import java.util.Set;

import static org.labkey.api.util.DOM.Attribute.height;
import static org.labkey.api.util.DOM.Attribute.src;
import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.Attribute.width;
import static org.labkey.api.util.DOM.BR;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.IMG;
import static org.labkey.api.util.DOM.TABLE;
import static org.labkey.api.util.DOM.TD;
import static org.labkey.api.util.DOM.TR;
import static org.labkey.api.util.DOM.U;
import static org.labkey.api.util.DOM.at;

public class CatalogEntryWebPart extends VBox
{
    public CatalogEntryWebPart(ExperimentAnnotations expAnnotations, User user)
    {
        this(expAnnotations, user, false);
    }

    public CatalogEntryWebPart(ExperimentAnnotations expAnnotations, User user, boolean standalone)
    {
        Container container = expAnnotations.getContainer();
        setTitle("Panorama Public Catalog Entry");
        setFrame(WebPartView.FrameType.PORTAL);

        CatalogEntry entry = CatalogEntryManager.getEntryForExperiment(expAnnotations);

        if (entry == null)
        {
            addView(new HtmlView(DIV("This dataset does not have an entry in the Panorama Public data catalog. " +
                            "Click the button below to add an entry.",
                    BR(),
                    new Button.ButtonBuilder("Add Catalog Entry").href(PanoramaPublicController.getAddCatalogEntryUrl(expAnnotations)
                            .addReturnURL(getViewContext().getActionURL()))
            )));
        }
        else
        {
            Button changeStatusBtn = null;
            if (user.hasSiteAdminPermission())
            {
                changeStatusBtn = changeStatusButtonBuilder(entry.getApproved(), expAnnotations.getId(), entry.getId(), container)
                        .style("margin-left: 10px")
                        .build();
            }

            URLHelper ctxReturnUrl = getViewContext().getActionURL().getReturnURL();
            ActionURL deleteUrl = new ActionURL(PanoramaPublicController.DeleteCatalogEntryAction.class, container)
                    .addParameter("id", expAnnotations.getId());
            if (ctxReturnUrl != null)
            {
                deleteUrl.addReturnURL(ctxReturnUrl);
            }

            ActionURL editUrl = new ActionURL(PanoramaPublicController.EditCatalogEntryAction.class, container)
                    .addParameter("id", expAnnotations.getId())
                    .addReturnURL(ctxReturnUrl != null ? ctxReturnUrl : getContextURLHelper());

            CatalogEntrySettings settings = CatalogEntryManager.getCatalogEntrySettings();
            addView(new HtmlView(
                    DIV(
                    TABLE(
                        TR(
                            TD(at(style, "padding:0 10px 15px 0; vertical-align:top;"), U("Status:")),
                            TD(at(style, "vertical-align:top;"),
                                    CatalogEntry.getStatusText(entry.getApproved()),
                                    changeStatusBtn == null ? "" : changeStatusBtn)
                        ),
                        TR(
                            TD(at(style, "padding:0 10px 15px 0; vertical-align:top;"), U("Title:")),
                            TD(at(style, "vertical-align:top;"), expAnnotations.getTitle())
                        ),
                        TR(
                            TD(at(style, "padding:0 10px 15px 0; vertical-align:top;"), U("Description:")),
                            TD(at(style, "vertical-align:top;"), entry.getDescription())
                        )
                    ),
                    DIV(IMG(at(src, PanoramaPublicController.getCatalogImageDownloadUrl(expAnnotations, entry.getImageFileName()))
                            .at(width, settings.getImgWidth()).at(height, settings.getImgHeight())
                            .at(style, "margin:10px 0 15px 0;border: 1px solid lightgrey;"))),
                    new Button.ButtonBuilder("Edit").href(editUrl),
                    HtmlString.NBSP,
                    new Button.ButtonBuilder("Delete").href(deleteUrl)
                            .usePost("Are you sure you want to delete the Panorama Public catalog entry for this experiment?"),
                    standalone ? DIV(at(style, "margin-top:25px;"),
                                     new Button.ButtonBuilder("Back to Folder").href(PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(container)))
                               : HtmlString.EMPTY_STRING
            )));
        }
    }

    public static boolean canBeDisplayed(ExperimentAnnotations expAnnotations, User user)
    {
        return CatalogEntryManager.getCatalogEntrySettings().isEnabled()
                && expAnnotations.isJournalCopy() // This is an experiment in the Panorama Public project
                && expAnnotations.isPublic() // The folder is public
                && expAnnotations.getContainer().hasOneOf(user, Set.of(AdminPermission.class, PanoramaPublicSubmitterPermission.class))
                && ExperimentAnnotationsManager.isCurrentVersion(expAnnotations);
    }

    public static Button.ButtonBuilder changeStatusButtonBuilder(Boolean status, int expAnnotationsId, int catalogEntryId, Container container)
    {
        boolean approve = status == null || !status;
        String btnTxt = approve ? "Approve" : "Reject";
        return new Button.ButtonBuilder(btnTxt).href(
                        new ActionURL(PanoramaPublicController.ChangeCatalogEntryStateAction.class, container)
                                .addParameter("id", expAnnotationsId)
                                .addParameter("catalogEntryId", catalogEntryId)
                                .addParameter("approve", approve)
                                .addReturnURL(getContextURLHelper()))
                .usePost("Are you sure you want to " + btnTxt.toLowerCase() + " this catalog entry?");
    }
}

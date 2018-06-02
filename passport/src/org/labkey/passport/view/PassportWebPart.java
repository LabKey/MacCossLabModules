package org.labkey.passport.view;

import org.labkey.api.data.Container;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartConfigurationException;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;

public class PassportWebPart extends BaseWebPartFactory
{
    public PassportWebPart()
    {
        super("Passport", true, false, WebPartFactory.LOCATION_BODY);
    }

    @Override
    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws WebPartConfigurationException
    {

        JspView view = new JspView<>("/org/labkey/passport/view/PassportWebPart.jsp");
        view.setTitle("Passport");
        view.setFrame(WebPartView.FrameType.PORTAL);
        return view;
    }
}
package org.labkey.nextflow;

import org.apache.logging.log4j.Logger;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.util.logging.LogHelper;

public class NextFlowController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(NextFlowController.class);
    public static final String NAME = "nextflow";

    private static final Logger LOG = LogHelper.getLogger(NextFlowController.class, NAME);

    public NextFlowController()
    {
        setActionResolver(_actionResolver);
    }
}

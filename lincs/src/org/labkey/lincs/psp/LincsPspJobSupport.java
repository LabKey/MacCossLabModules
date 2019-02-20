package org.labkey.lincs.psp;

import org.labkey.api.targetedms.ITargetedMSRun;

public interface LincsPspJobSupport
{
    ITargetedMSRun getRun();

    LincsPspJob getPspJob();

    LincsPspJob getOldPspJob();

    PspEndpoint getPspEndpoint();
}

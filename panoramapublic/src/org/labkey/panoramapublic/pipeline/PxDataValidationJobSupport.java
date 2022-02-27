package org.labkey.panoramapublic.pipeline;

import org.labkey.panoramapublic.model.ExperimentAnnotations;

public interface PxDataValidationJobSupport
{
    ExperimentAnnotations getExpAnnotations();
    int getValidationId();
}

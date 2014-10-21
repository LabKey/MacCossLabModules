package org.labkey.targetedms;

import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExperimentListener;
import org.labkey.api.security.User;
import org.labkey.targetedms.query.ExperimentAnnotationsManager;

/**
 * User: vsharma
 * Date: 8/22/2014
 * Time: 3:22 PM
 */
public class TargetedMSListener implements ExperimentListener
{
    @Override
    public void beforeExperimentDeleted(ExpExperiment experiment, User user)
    {
        ExperimentAnnotationsManager.beforeDeleteExpExperiment(experiment, user);
    }
}

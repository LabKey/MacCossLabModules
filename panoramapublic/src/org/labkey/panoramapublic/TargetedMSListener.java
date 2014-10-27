/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

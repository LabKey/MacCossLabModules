/*
 * Copyright (c) 2013 LabKey Corporation
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

package org.labkey.signup;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager.ContainerListener;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;

import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;

public class SignUpListener implements ContainerListener, UserManager.UserListener
{
    private static final Logger _log = Logger.getLogger(SignUpListener.class);

    @Override
    public void userDeletedFromSite(User user)
    {
        try
        {
            SignUpManager.get().deleteUser(user.getEmail());
        }
        catch(SQLException e)
        {
            _log.error(e);
        }
    }

    @Override
    public void userAddedToSite(User user)
    {
        // Do nothing
    }

    @Override
    public void userAccountDisabled(User user)
    {
        // Do nothing.
    }

    @Override
    public void userAccountEnabled(User user)
    {
        // Do nothing
    }

    @Override
    public void containerCreated(Container c, User user)
    {
    }

    @Override
    public void containerDeleted(Container c, User user)
    {
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
    }

    @Override
    public void containerMoved(Container c, Container oldParent, User user)
    {
    }

    @NotNull
    @Override
    public Collection<String> canMove(Container c, Container newParent, User user)
    {
        return Collections.emptyList();
    }
}
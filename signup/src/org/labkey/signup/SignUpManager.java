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

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.ValidEmail;

import java.sql.SQLException;

public class SignUpManager
{
    // Returned messages
    public static final String  CONFIRMATION_DID_NOT_MATCH = "The email address and confirmation key could not be verified.  " +
            "Please make sure that you copied the entire link into the browser's address bar.";
    // TODO: Add a link to reset password in this message.
    public static final String USER_ALREADY_EXISTS = "A user with the email %s already exists.  " +
            "If you have forgotten your password, you can click the \"Forgot your password?\" " +
            "link on the sign in page to reset your password.";
    public static final String CONFIRMATION_SENT = "A confirmation email has been sent to %s. " +
            "Please check your email to confirm your address and complete the sign-up process.";

    private static final SignUpManager _instance = new SignUpManager();

    private SignUpManager()
    {
        // prevent external construction with a private default constructor
    }

    public static SignUpManager get()
    {
        return _instance;
    }

    public static DbSchema getSchema()
    {
        return DbSchema.get(SignUpSchema.SCHEMA_NAME);
    }

    public static TableInfo getTableInfoTempUsers()
    {
        return getSchema().getTable(SignUpSchema.TABLE_TEMP_USERS);
    }

    public TempUser getTempUserWithEmail(String email)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("email"), email);
        return new TableSelector(getTableInfoTempUsers(),filter, null).getObject(TempUser.class);

    }

    public TempUser verifyUser(ValidEmail email, String key)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("email"), email.getEmailAddress());
        filter.addCondition(FieldKey.fromParts("key"), key);
        return new TableSelector(getTableInfoTempUsers(),filter, null).getObject(TempUser.class);
    }

    public void deleteUser(String email) throws SQLException
    {
        Table.delete(getTableInfoTempUsers(), new SimpleFilter(FieldKey.fromParts("email"), email));
    }
}
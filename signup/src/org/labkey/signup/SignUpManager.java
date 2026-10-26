/*
 * Copyright (c) 2017-2026 LabKey Corporation
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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.Crypt;
import org.labkey.api.security.LoginManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.time.Instant;
import java.util.Date;

public class SignUpManager
{
    // Returned messages
    public static final String  CONFIRMATION_DID_NOT_MATCH = "The email address and confirmation key could not be verified.  " +
            "The link may have expired or already been used. Please make sure that you copied the entire link " +
            "into the browser's address bar, or sign up again to receive a new link.";
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

    // Returns the pending user whose unexpired, unused key matches
    public TempUser verifyUser(ValidEmail email, String key)
    {
        if (key == null)
            return null;
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("email"), email.getEmailAddress());
        filter.addCondition(FieldKey.fromParts("key"), hashKey(key));
        filter.addCondition(FieldKey.fromParts("keyTimeout"), new Date(), CompareType.GT);
        return new TableSelector(getTableInfoTempUsers(),filter, null).getObject(TempUser.class);
    }

    // Saves a new key for the pending user, and returns the key. A repeat signup for the same email replaces
    // the key, so the link already sent stops working. The name, organization and folder from the latest
    // submitted form replace the stored ones.
    public String saveWithNewKey(ValidEmail email, String firstName, String lastName, String organization, Container container)
    {
        TempUser tempUser = getTempUserWithEmail(email.getEmailAddress());
        boolean exists = tempUser != null;
        if (!exists)
        {
            tempUser = new TempUser();
            tempUser.setEmail(email.getEmailAddress());
        }
        tempUser.setFirstName(firstName);
        tempUser.setLastName(lastName);
        tempUser.setOrganization(organization);
        tempUser.setContainer(container);

        String key = assignKey(tempUser);
        if (exists)
            Table.update(null, getTableInfoTempUsers(), tempUser, tempUser.getUserId());
        else
            Table.insert(null, getTableInfoTempUsers(), tempUser);
        return key;
    }

    // Store the labkey UserId created for the user, and clear the key so the link cannot be used again.
    public void markKeyUsed(TempUser tempUser, int labkeyUserId)
    {
        tempUser.setLabkeyUserId(labkeyUserId);
        tempUser.setKey(null);
        tempUser.setKeyTimeout(null);
        Table.update(null, getTableInfoTempUsers(), tempUser, tempUser.getUserId());
    }

    // Sets a new key on tempUser, storing only its hash, and returns the key. The caller saves tempUser.
    private String assignKey(TempUser tempUser)
    {
        String key = LoginManager.createTempPassword();
        tempUser.setKey(hashKey(key));
        tempUser.setKeyTimeout(Date.from(Instant.now().plus(LoginManager.SETUP_LINK_TIMEOUT)));
        return key;
    }

    // Keys are long and random, so a fast unsalted hash suffices. signup-26.000-26.001.sql hashes existing rows identically.
    private static String hashKey(String key)
    {
        return Crypt.SHA256.digestWithPrefix(key);
    }

    public void deleteUser(String email)
    {
        if (null != getTableInfoTempUsers())
            Table.delete(getTableInfoTempUsers(), new SimpleFilter(FieldKey.fromParts("email"), email));
    }

    public static class TestCase extends Assert
    {
        private static final String EMAIL = "signup-key-test@signup.test";

        private ValidEmail _email;
        private TempUser _tempUser;
        private String _key;

        @Before
        public void setUp() throws ValidEmail.InvalidEmailException
        {
            _email = new ValidEmail(EMAIL);
            get().deleteUser(EMAIL);

            _key = get().saveWithNewKey(_email, "Signup", "Test", "Signup Test", JunitUtil.getTestContainer());
            _tempUser = get().getTempUserWithEmail(EMAIL);
        }

        @After
        public void tearDown()
        {
            get().deleteUser(EMAIL);
        }

        private void save()
        {
            Table.update(null, getTableInfoTempUsers(), _tempUser, _tempUser.getUserId());
        }

        @Test
        public void testKeyStoredAsHash()
        {
            TempUser stored = get().getTempUserWithEmail(EMAIL);
            assertNotEquals("Key stored in plain text", _key, stored.getKey());
            assertTrue("Stored key is not a SHA-256 hash", stored.getKey().startsWith("sha256:"));
            assertNotNull("Key rejected", get().verifyUser(_email, _key));

            long expected = Instant.now().plus(LoginManager.SETUP_LINK_TIMEOUT).toEpochMilli();
            assertTrue("Key does not expire SETUP_LINK_TIMEOUT after signup",
                    Math.abs(stored.getKeyTimeout().getTime() - expected) < 60_000);
        }

        @Test
        public void testWrongKeyRejected()
        {
            assertNull("Wrong key accepted", get().verifyUser(_email, _key + "x"));
            assertNull("Missing key accepted", get().verifyUser(_email, null));
            assertNull("Stored hash accepted as a key", get().verifyUser(_email, _tempUser.getKey()));
        }

        @Test
        public void testExpiredKeyRejected()
        {
            _tempUser.setKeyTimeout(Date.from(Instant.now().minusSeconds(1)));
            save();
            assertNull("Expired key accepted", get().verifyUser(_email, _key));
        }

        @Test
        public void testRepeatSignupReplacesKeyAndDetails()
        {
            String newKey = get().saveWithNewKey(_email, "Corrected", "Name", "New Org", JunitUtil.getTestContainer());
            assertNull("Replaced key accepted", get().verifyUser(_email, _key));

            TempUser stored = get().verifyUser(_email, newKey);
            assertNotNull("New key rejected", stored);
            assertEquals("Repeat signup created a second row", _tempUser.getUserId(), stored.getUserId());
            assertEquals("First name not updated", "Corrected", stored.getFirstName());
            assertEquals("Last name not updated", "Name", stored.getLastName());
            assertEquals("Organization not updated", "New Org", stored.getOrganization());
        }

        @Test
        public void testUsedKeyRejected()
        {
            int userId = TestContext.get().getUser().getUserId();
            get().markKeyUsed(_tempUser, userId);
            assertNull("Used key accepted", get().verifyUser(_email, _key));

            TempUser stored = get().getTempUserWithEmail(EMAIL);
            assertNull("Key not cleared after use", stored.getKey());
            assertNull("Key timeout not cleared after use", stored.getKeyTimeout());
            assertEquals("Account not recorded on the signup row", Integer.valueOf(userId), stored.getLabkeyUserId());
        }
    }
}
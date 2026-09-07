/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.test.tests.panoramapublic;

import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.SimplePostCommand;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestProperties;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;

import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

/**
 * Covers the NCBI API key on the Private Data Reminder Settings page. The key is a credential, so
 * the page stores it in the encrypted property store and never displays it again.
 *
 * The key is site wide and cannot be read back, so this test cannot save one without destroying
 * whatever is already there. It fails rather than skips when a key is saved, so the missing
 * coverage is visible instead of silent.
 */
@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class NcbiApiKeyTest extends PanoramaPublicBaseTest
{
    private static final String TEST_API_KEY = "test-ncbi-api-key";

    private boolean _savedTestApiKey = false;
    private boolean _useMockNcbi = false;
    private Map<String, String> _originalReminderSettings;

    @Test
    public void testNcbiApiKeySettings()
    {
        setupMockNcbiService();

        // Capture the existing reminder settings up front so removeTestApiKey can put them back. A dev
        // machine may have non-default values set.
        _originalReminderSettings = getPrivateDataReminderSettings();

        assertEquals("An NCBI API key is saved on this server. This test saves its own key and cannot"
                        + " restore yours, because a saved key is never readable. Remove the key on the"
                        + " Private Data Reminder Settings page, run this test, then enter the key again.",
                "false", _originalReminderSettings.get("ncbiApiKeySaved"));

        _savedTestApiKey = true;
        savePrivateDataReminderSettings("2", "0", "0", true, TEST_API_KEY);

        assertEquals("The saved key must never be rendered into the form", "",
                getFormElement(Locator.input("ncbiApiKey")));

        // Saving with the field left blank must keep the stored key. Every other field on this page
        // is edited routinely, so a blank field cannot mean "remove the key".
        savePrivateDataReminderSettings("3", "0", "0", true, "");
        assertEquals("A blank key field must leave the saved key alone", "true",
                getPrivateDataReminderSettings().get("ncbiApiKeySaved"));

        verifyValidateButton();

        // Removing a key takes the explicit checkbox.
        checkCheckbox(Locator.checkboxByName("clearNcbiApiKey"));
        clickButton("Save");
        _savedTestApiKey = false;
        assertEquals("Remove the saved key should remove it", "false",
                getPrivateDataReminderSettings().get("ncbiApiKeySaved"));
    }

    /**
     * Covers the button, the request it sends and the message it displays. The mock responds to every
     * request, so it reports the key as accepted.
     */
    private void verifyValidateButton()
    {
        setFormElement(Locator.input("ncbiApiKey"), "not-a-real-key");
        click(Locator.lkButton("Validate"));

        Locator result = Locator.id("ncbiApiKeyValidationResult");
        if (_useMockNcbi)
        {
            waitForElement(result.containing("NCBI accepted this key"));
        }
        else
        {
            // NCBI rejects this key with a 400, or returns a 5xx and the check is unconfirmed.
            // Neither reports the key as accepted. The retries mean a live check can take half a minute.
            waitFor(() -> {
                        String text = result.findElement(getDriver()).getText();
                        return !text.isEmpty() && !text.startsWith("Checking");
                    },
                    "NCBI validation result was not displayed", WAIT_FOR_PAGE);

            String message = result.findElement(getDriver()).getText();
            assertFalse("A key NCBI does not recognise must not be reported as accepted. Message: " + message,
                    message.contains("accepted"));
        }

        setFormElement(Locator.input("ncbiApiKey"), "");
    }

    /*
     * On TeamCity, route NCBI requests through the mock so this test does not depend on NCBI being
     * reachable. On a development machine, use the real service so a key can actually be rejected.
     */
    private void setupMockNcbiService()
    {
        if (!TestProperties.isTestRunningOnTeamCity())
        {
            return;
        }

        try
        {
            SimplePostCommand command = new SimplePostCommand("panoramapublic", "setupMockNcbiService");
            command.execute(createDefaultConnection(), "/");
            _useMockNcbi = true;
            log("Using mock NCBI service");
        }
        catch (IOException | CommandException e)
        {
            fail("Failed to set up the mock NCBI service: " + e.getMessage());
        }
    }

    private void restoreNcbiService()
    {
        try
        {
            SimplePostCommand command = new SimplePostCommand("panoramapublic", "restoreNcbiService");
            command.execute(createDefaultConnection(), "/");
            log("Restored real NCBI service");
        }
        catch (IOException | CommandException e)
        {
            log("Warning: Failed to restore NCBI service: " + e.getMessage());
        }
    }

    @After
    public void removeTestApiKey()
    {
        if (_useMockNcbi)
        {
            restoreNcbiService();
        }

        if (_savedTestApiKey)
        {
            // Remove the key even when the test failed before its own removal step. A key NCBI
            // rejects makes every publication search on this server fail, including the next run's.
            getPrivateDataReminderSettings();
            checkCheckbox(Locator.checkboxByName("clearNcbiApiKey"));
            clickButton("Save");
        }

        if (_originalReminderSettings != null)
        {
            // The reminder settings are site wide. Restore the values this test overwrote.
            savePrivateDataReminderSettings(
                    _originalReminderSettings.get("extensionLength"),
                    _originalReminderSettings.get("delayUntilFirstReminder"),
                    _originalReminderSettings.get("reminderFrequency"),
                    Boolean.parseBoolean(_originalReminderSettings.get("enablePublicationSearch")),
                    // A key saved before the run cannot be read back, so leave the key field alone.
                    null);
        }
    }
}

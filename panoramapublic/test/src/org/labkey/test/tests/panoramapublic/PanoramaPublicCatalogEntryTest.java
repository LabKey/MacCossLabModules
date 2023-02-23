package org.labkey.test.tests.panoramapublic;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.components.panoramapublic.TargetedMsExperimentWebPart;
import org.labkey.test.pages.admin.PermissionsPage;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.PermissionsHelper;
import org.openqa.selenium.NoSuchElementException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class PanoramaPublicCatalogEntryTest extends PanoramaPublicMakePublicTest
{
    @Override
    @Test
    public void testExperimentCopy()
    {
        // Set up our source folder. We will create an experiment and submit it to our "Panorama Public" project.
        String projectName = getProjectName();
        String folderName = "Folder 1";
        String targetFolder = "Test Copy 1";
        String experimentTitle = "This is an experiment to test adding a catalog entry";
        setupSubmitAndCopy(projectName, folderName, targetFolder, experimentTitle);

        // 1. Make the data public and add a catalog entry
        // - Verify can view catalog entry only in the Panorama Public folder
        verifyMakePublic(PANORAMA_PUBLIC, targetFolder, SUBMITTER, true, false);

        // 2. Resubmit data and make a new copy
        // - Verify catalog entry moved to the new copy
        // - Verify no catalog entry in the older copy


        // 3. Submit and copy another experiment
        // - Make data public, no publication details added, no catalog entry added
        // - Add publication details, verify can add catalog entry


        // 4. Submit and copy another experiment
        // - Make data public with publication details, no catalog entry added
        // - Verify can add catalog entry from the Panorama Public folder's experiment details page

        // Verify permissions - only PanoramaPublicSubmitterRole can view / edit / delete the catalog entry in the
        // Panorama Public folder
    }

    private void verifyMakePublic(String projectName, String folderName, String user, boolean isSubmitter, boolean addPublication)
    {
        if (isImpersonating())
        {
            stopImpersonating(true);
        }
        goToProjectFolder(projectName, folderName);
        impersonate(user);
        goToDashboard();
        TargetedMsExperimentWebPart expWebPart = new TargetedMsExperimentWebPart(this);

        makeDataPublic();

        stopImpersonating();
    }

    private void makeDataPublic()
    {
        makePublic(true);
    }

    private void addPublication()
    {
        makePublic(false);
    }

    private void makePublic(boolean unpublishedData)
    {
        TargetedMsExperimentWebPart expWebPart = new TargetedMsExperimentWebPart(this);

        if (unpublishedData)
        {
            expWebPart.clickMakePublic();
            assertTextPresent("Publication Details");
            _ext4Helper.checkCheckbox(Ext4Helper.Locators.checkbox(this, "Unpublished:"));
        }
        else
        {
            expWebPart.clickAddPublication();
            assertTextPresent("Publication Details");
            setFormElement(Locator.input("link"), "http://panorama-publication-test.org");
            setFormElement(Locator.tagWithName("textarea", "citation"), "Paper citation goes here");
        }

        clickButton("Continue");
        assertTextPresent("Confirm Publication Details");
        clickButton("OK");
        if (unpublishedData)
        {
            assertTextPresent("Data on Panorama Public", "was made public.");
        }
        else
        {
            assertTextPresent("Publication details were updated for data on Panorama Public");
        }
        clickButton("Back to Folder");
    }
}

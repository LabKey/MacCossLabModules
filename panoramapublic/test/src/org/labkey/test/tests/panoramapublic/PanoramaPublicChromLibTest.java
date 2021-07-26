package org.labkey.test.tests.panoramapublic;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.components.panoramapublic.TargetedMsExperimentWebPart;

import static org.junit.Assert.assertEquals;

@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class PanoramaPublicChromLibTest extends PanoramaPublicBaseTest
{

    private static final String SKY_FILE1 = "Stergachis-SupplementaryData_2_a.sky.zip";
    private static final String SKY_FILE2 = "Stergachis-SupplementaryData_2_b.sky.zip";

    @Test
    public void testLibraryCopy()
    {
        // Set up our source folder. We will create an experiment here and submit it to our "Panorama Public" project.
        String projectName = getProjectName();
        String folderName = "Protein Library";
        String experimentTitle = "This is a test experiment";
        setupProteinLibraryFolder(projectName, folderName, SUBMITTER);

        impersonate(SUBMITTER);
        updateSubmitterAccountInfo("One");

        importData(SKY_FILE1);
        importData(SKY_FILE2, 2);
        // TODO: try to submit a library folder in conflicted state
        resolveConflicts();

        // Read the state of the library in the source folder
        ChromLibTestHelper libHelper = new ChromLibTestHelper(this);
        ChromLibTestHelper.ChromLibState libStateSource = libHelper.getLibState(projectName + "/" + folderName);

        // Add the "Targeted MS Experiment" webpart
        TargetedMsExperimentWebPart expWebPart = createTargetedMsExperimentWebPart(experimentTitle);
        submitExperiment(expWebPart);

        // Copy the experiment to the Panorama Public project
        var targetFolder = "Copy of " + folderName;
        copyExperimentAndVerify(projectName, folderName, null, experimentTitle, false, targetFolder);

        // Read the state of the library in the Panorama Public folder and compare the states
        ChromLibTestHelper.ChromLibState libStateTarget = libHelper.getLibState(PANORAMA_PUBLIC + "/" + targetFolder);

        assertEquals(libStateSource, libStateTarget);
    }

    private void resolveConflicts()
    {
        goToDashboard();
        var resolveConflictsLink = Locator.tagWithClass("div", "labkey-download").descendant(Locator.linkWithText("RESOLVE CONFLICTS"));
        assertElementPresent(resolveConflictsLink);
        clickAndWait(resolveConflictsLink);
        clickButton("Apply Changes");
    }

    private void submitExperiment(TargetedMsExperimentWebPart expWebPart)
    {
        goToDashboard();
        expWebPart.clickSubmit();
        submitWithoutPXId();
        goToDashboard();
        assertTextPresent("Copy Pending!");
    }
}

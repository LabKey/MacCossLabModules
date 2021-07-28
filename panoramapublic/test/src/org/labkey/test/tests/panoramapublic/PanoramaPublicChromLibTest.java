package org.labkey.test.tests.panoramapublic;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.components.panoramapublic.TargetedMsExperimentWebPart;

@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class PanoramaPublicChromLibTest extends PanoramaPublicBaseTest
{

    private static final String SKY_FILE1 = "Stergachis-SupplementaryData_2_a.sky.zip";
    private static final String SKY_FILE2 = "Stergachis-SupplementaryData_2_b.sky.zip";

    @Test
    public void testProteinLibraryCopy()
    {
        testLibraryCopy("Protein Library", // Name of the source folder
                FolderType.LibraryProtein, // Library folder type
                "Experiment to test Protein library copy", // TargetedMSExperiment title
                10, // library protein count
                91, // library peptide count
                597 // library transition count
                );
    }

    @Test
    public void testPeptideLibraryCopy()
    {
        testLibraryCopy("Peptide Library", // Name of the source folder
                FolderType.Library, // Library folder type
                "Experiment to test Peptide library copy", // TargetedMSExperiment title
                -1, // library protein count
                93, // library peptide count
                607 // library transition count
                );
    }

    private void testLibraryCopy(String folderName, FolderType folderType, String experimentTitle, int proteinCount, int peptideCount,
                                        int transitionCount)
    {
        // Set up our source folder. We will create an experiment here and submit it to our "Panorama Public" project.
        String projectName = getProjectName();
        setupLibraryFolder(projectName, folderName, folderType, SUBMITTER);

        impersonate(SUBMITTER);
        updateSubmitterAccountInfo("One");

        importData(SKY_FILE1);
        importData(SKY_FILE2, 2);
        // TODO: try to submit a library folder in conflicted state
        resolveConflicts();
        // Download link, library statistics and revision in the ChromatogramLibraryDownloadWebpart
        verifyChromLibDownloadWebPart(folderType, proteinCount, peptideCount, transitionCount, 3);

        // Read the state of the library in the source folder
        ChromLibTestHelper libHelper = new ChromLibTestHelper(this);
        ChromLibTestHelper.ChromLibState libStateSource = libHelper.getLibState(projectName + "/" + folderName);

        // Add the "Targeted MS Experiment" webpart
        TargetedMsExperimentWebPart expWebPart = createTargetedMsExperimentWebPart(experimentTitle);
        submitExperiment(expWebPart);

        // Copy the experiment to the Panorama Public project
        var targetFolder = "Copy of " + folderName;
        copyExperimentAndVerify(projectName, folderName, null, experimentTitle, false, targetFolder);
        // Download link, library statistics and revision in the ChromatogramLibraryDownloadWebpart
        goToProjectFolder(PANORAMA_PUBLIC, targetFolder);
        verifyChromLibDownloadWebPart(folderType, proteinCount, peptideCount, transitionCount, 3);

        // Read the state of the library in the Panorama Public folder and compare the states
        ChromLibTestHelper.ChromLibState libStateTarget = libHelper.getLibState(PANORAMA_PUBLIC + "/" + targetFolder);

        ChromLibTestHelper.compareLibState(libStateSource, libStateTarget);
    }

    private void verifyChromLibDownloadWebPart(FolderType folderType, int proteinCount, int peptideCount, int transitionCount, int libRevision)
    {
        if(folderType == FolderType.LibraryProtein)
        {
            verifyProteinChromLibDownloadWebPart(proteinCount, peptideCount, transitionCount, libRevision);
        }

        else
        {
            verifyPeptideChromLibDownloadWebPart(peptideCount, transitionCount, libRevision);
        }
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

    private void verifyProteinChromLibDownloadWebPart(int proteinCount, int peptideCount, int transitionCount, int revision)
    {
        goToDashboard();
        // clickAndWait(Locator.linkContainingText("Panorama Dashboard"));
        assertElementPresent(Locator.xpath("//img[contains(@src, 'graphLibraryStatistics.view')]"));
        assertTextPresent(
                proteinCount + " proteins", peptideCount + " ranked peptides",
                transitionCount + " ranked transitions");
        assertElementPresent(Locator.lkButton("Download"));
        assertTextPresent("Revision " + revision);
    }

    private void verifyPeptideChromLibDownloadWebPart(int peptideCount, int transitionCount, int revision)
    {
        goToDashboard();
        // clickAndWait(Locator.linkContainingText("Panorama Dashboard"));
        assertElementPresent(Locator.xpath("//img[contains(@src, 'graphLibraryStatistics.view')]"));
        assertTextPresent(
                peptideCount + " peptides",
                transitionCount + " ranked transitions");
        assertElementPresent(Locator.lkButton("Download"));
        assertTextPresent("Revision " + revision);
    }
}

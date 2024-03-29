package org.labkey.test.tests.panoramapublic;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.components.panoramapublic.TargetedMsExperimentWebPart;

import java.util.ArrayList;
import java.util.List;

@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 7)
public class PanoramaPublicChromLibTest extends PanoramaPublicBaseTest
{

    private static final String SKY_FILE1 = "Stergachis-SupplementaryData_2_a.sky.zip";
    private static final String SKY_FILE2 = "Stergachis-SupplementaryData_2_b.sky.zip";
    private static final String SMALL_MOL_FILE1 = "SmMolLibA.sky.zip";
    private static final String SMALL_MOL_FILE2 = "SmMolLibB.sky.zip";
    private static final String DUP_PROTEINS_FILE = "duplicate_protein.sky.zip";

    @Test
    public void testProteinLibraryCopy()
    {
        testLibraryCopy("Protein Library", // Name of the source folder
                FolderType.LibraryProtein, // Library folder type
                List.of(SKY_FILE1, SKY_FILE2),
                "Experiment to test Protein library copy", // TargetedMSExperiment title
                10, // library protein count
                91, // library peptide count
                -1, // molecule count
                597 // library transition count
                );
    }

    @Test
    public void testPeptideLibraryCopy()
    {
        testLibraryCopy("Peptide Library", // Name of the source folder
                FolderType.Library, // Library folder type
                List.of(SKY_FILE1, SKY_FILE2, SMALL_MOL_FILE1, SMALL_MOL_FILE2), // Include small molecule data as well
                "Experiment to test Peptide library copy", // TargetedMSExperiment title
                -1, // library protein count
                93, // library peptide count
                3, // molecule count
                619 // library transition count
                );
    }

    @Test
    public void testDuplicateProteinsCopy()
    {
        var projectName = getProjectName();
        var folderName = "Duplicate Protein Peptide Library";
        var experimentTitle = "Test Duplicate Protein in a Peptide Library Folder";
        setupLibraryFolder(projectName, folderName, FolderType.Library, SUBMITTER);
        importData(DUP_PROTEINS_FILE, 1);
        // Download link, library statistics and revision in the ChromatogramLibraryDownloadWebpart
        verifyChromLibDownloadWebPart(FolderType.Library, -1, 10, -1, 87, 1);
        // Read the state of the library in the source folder
        ChromLibTestHelper libHelper = new ChromLibTestHelper(this);
        ChromLibTestHelper.ChromLibState libStateSource = libHelper.getLibState(projectName + "/" + folderName);

        impersonate(SUBMITTER);
        updateSubmitterAccountInfo("One");
        // Add the "Targeted MS Experiment" webpart
        TargetedMsExperimentWebPart expWebPart = createExperimentCompleteMetadata(experimentTitle);
        // Submit the experiment
        String shortAccessUrl = submitExperiment(expWebPart);

        // Copy the experiment to the Panorama Public project
        var targetFolder = "Copy of " + folderName;
        copyExperimentAndVerify(projectName, folderName, null, experimentTitle, targetFolder, shortAccessUrl);
        // Download link, library statistics and revision in the ChromatogramLibraryDownloadWebpart
        goToProjectFolder(PANORAMA_PUBLIC, targetFolder);
        verifyChromLibDownloadWebPart(FolderType.Library, -1, 10, -1, 87, 1);

        // Read the state of the library in the Panorama Public folder and compare the states
        ChromLibTestHelper.ChromLibState libStateTarget = libHelper.getLibState(PANORAMA_PUBLIC + "/" + targetFolder);

        ChromLibTestHelper.compareLibState(libStateSource, libStateTarget);
    }

    private void testLibraryCopy(String folderName, FolderType folderType, List<String> skyFiles,
                                 String experimentTitle, int proteinCount, int peptideCount, int moleculeCount,
                                        int transitionCount)
    {
        // Set up our source folder. We will create an experiment here and submit it to our "Panorama Public" project.
        String projectName = getProjectName();
        setupLibraryFolder(projectName, folderName, folderType, SUBMITTER);

        impersonate(SUBMITTER);
        updateSubmitterAccountInfo("One");

        // Add the "Targeted MS Experiment" webpart
        TargetedMsExperimentWebPart expWebPart = createExperimentCompleteMetadata(experimentTitle);

        int count = 0;
        int revision = 0;
        for(var skyFile: skyFiles)
        {
            importData(skyFile, ++count);
            revision++;
            if(count == 2)
            {
                // After uploading the second Skyline document there should be a library conflict.  User should not be
                // able to submit a library folder in a conflicted state.
                trySubmitWithConflicts(expWebPart);
            }
            if(count > 1 && resolveConflicts())
            {
                // Library revision is incremented after conflicts are resolved.
                revision++;
            }
        }
        // Download link, library statistics and revision in the ChromatogramLibraryDownloadWebpart
        verifyChromLibDownloadWebPart(folderType, proteinCount, peptideCount, moleculeCount, transitionCount, revision);

        // Read the state of the library in the source folder
        ChromLibTestHelper libHelper = new ChromLibTestHelper(this);
        ChromLibTestHelper.ChromLibState libStateSource = libHelper.getLibState(projectName + "/" + folderName);

        // Submit the experiment
        String shortAccessUrl = submitExperiment(expWebPart);

        // Copy the experiment to the Panorama Public project
        var targetFolder = "Copy of " + folderName;
        copyExperimentAndVerify(projectName, folderName, null, experimentTitle, targetFolder, shortAccessUrl);
        // Download link, library statistics and revision in the ChromatogramLibraryDownloadWebpart
        goToProjectFolder(PANORAMA_PUBLIC, targetFolder);
        verifyChromLibDownloadWebPart(folderType, proteinCount, peptideCount, moleculeCount, transitionCount, revision);

        // Read the state of the library in the Panorama Public folder and compare the states
        ChromLibTestHelper.ChromLibState libStateTarget = libHelper.getLibState(PANORAMA_PUBLIC + "/" + targetFolder);

        ChromLibTestHelper.compareLibState(libStateSource, libStateTarget);
    }

    private boolean resolveConflicts()
    {
        goToDashboard();
        var resolveConflictsLink = Locator.tagWithClass("div", "labkey-download").descendant(Locator.linkWithText("RESOLVE CONFLICTS"));
        if(resolveConflictsLink.findElementOrNull(getDriver()) != null)
        {
            clickAndWait(resolveConflictsLink);
            clickButton("Apply Changes");
            return true;
        }
        return false;
    }

    private void trySubmitWithConflicts(TargetedMsExperimentWebPart expWebPart)
    {
        goToDashboard();
        expWebPart.clickSubmit();
        assertTextPresent("Please resolve the conflicts before submitting");
    }

    private String submitExperiment(TargetedMsExperimentWebPart expWebPart)
    {
        goToDashboard();
        expWebPart.clickSubmit();
        return submitWithoutPXId();
    }

    private void verifyChromLibDownloadWebPart(FolderType folderType, int proteinCount, int peptideCount, int moleculeCount, int transitionCount, int libRevision)
    {
        if(folderType == FolderType.LibraryProtein)
        {
            verifyProteinChromLibDownloadWebPart(proteinCount, peptideCount, transitionCount, libRevision);
        }

        else
        {
            verifyPeptideChromLibDownloadWebPart(peptideCount, moleculeCount, transitionCount, libRevision);
        }
    }

    private void verifyProteinChromLibDownloadWebPart(int proteinCount, int peptideCount, int transitionCount, int revision)
    {
        goToDashboard();
        assertElementPresent(Locator.xpath("//img[contains(@src, 'graphLibraryStatistics.view')]"));
        assertTextPresent(
                proteinCount + " proteins", peptideCount + " ranked peptides",
                transitionCount + " ranked transitions");
        assertElementPresent(Locator.lkButton("Download"));
        assertTextPresent("Revision " + revision);
    }

    private void verifyPeptideChromLibDownloadWebPart(int peptideCount, int moleculeCount, int transitionCount, int revision)
    {
        goToDashboard();
        assertElementPresent(Locator.xpath("//img[contains(@src, 'graphLibraryStatistics.view')]"));
        List<String> texts = new ArrayList<>();
        if(peptideCount > 0) texts.add(peptideCount + " peptides");
        if(moleculeCount > 0) texts.add(moleculeCount + " molecules");
        texts.add(transitionCount + " ranked transitions");
        assertTextPresent(texts);
        assertElementPresent(Locator.lkButton("Download"));
        assertTextPresent("Revision " + revision);
    }
}

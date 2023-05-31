package org.labkey.test.tests.panoramapublic;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.api.util.FileUtil;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.components.panoramapublic.TargetedMsExperimentWebPart;
import org.labkey.test.components.targetedms.TargetedMSRunsTable;
import org.labkey.test.util.APIContainerHelper;
import org.openqa.selenium.WebElement;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class PanoramaPublicSymlinkTest extends PanoramaPublicBaseTest
{

    @Override
    public String getSampleDataFolder()
    {
        return SAMPLEDATA_FOLDER;
    }

    @Test
    public void testSymLinks()
    {
        String projectName = getProjectName();
        String sourceFolder = "Folder 3";

        String targetFolder = "Test Copy 3";
        String experimentTitle = "This is a test for Panorama Public symlinks";

        setupSourceFolder(projectName, sourceFolder, SUBMITTER);
        impersonate(SUBMITTER);
        updateSubmitterAccountInfo("One");

        // Import Skyline documents to the folder
        importData(SKY_FILE_1, 1);
        importData(QC_1_FILE, 2, false);
        importDataInSubfolder(getSampleDataFolder() + SKY_FILE_SMALLMOL_PEP, "SmallMoleculeFiles", 3);

        // Upload some raw files
        portalHelper.click(Locator.folderTab("Raw Data"));
        _fileBrowserHelper.uploadFile(getSampleDataPath(RAW_FILE_WIFF));
        _fileBrowserHelper.uploadFile(getSampleDataPath(RAW_FILE_WIFF_SCAN));

        // Add the "Targeted MS Experiment" webpart
        TargetedMsExperimentWebPart expWebPart = createExperimentCompleteMetadata(experimentTitle);
        expWebPart.clickSubmit();
        String shortAccessUrl = submitWithoutPXId();

        // Copy the experiment to the Panorama Public project
        copyExperimentAndVerify(projectName, sourceFolder, null, experimentTitle, targetFolder, shortAccessUrl);
        // TODO: verify symlinks after the first copy.  All files in the submitted folder should be symlinked to the files in the Panorama Public copy.

        // Prepare to resubmit
        goToProjectFolder(projectName, sourceFolder);
        impersonate(SUBMITTER);

        // Reorganize data in subfolders.
        String subfolder1 = "SystemSuitability";
        String subfolder2 = "ExperimentalData";
        setupSubfolder(projectName, sourceFolder, subfolder1, FolderType.QC);
        setupSubfolder(projectName, sourceFolder, subfolder2, FolderType.Experiment);

        // Add QC document to the "SystemSuitability" subfolder
        goToProjectFolder(projectName, sourceFolder + "/" + subfolder1);
        importData(QC_1_FILE, 1, false);

        // Add the small molecule document to "ExperimentalData" subfolder, and remove it from the parent folder
        goToProjectFolder(projectName, sourceFolder + "/" + subfolder2);
        importData(SKY_FILE_SMALLMOL_PEP, 1);
        goToProjectFolder(projectName, sourceFolder);
        TargetedMSRunsTable runsTable = new TargetedMSRunsTable(this);
        runsTable.deleteRun(SKY_FILE_SMALLMOL_PEP); // delete the run
        goToModule("FileContent");
        _fileBrowserHelper.deleteFile("SmallMoleculeFiles/" + SKY_FILE_SMALLMOL_PEP); // delete the .sky.zip
        _fileBrowserHelper.deleteFile("SmallMoleculeFiles/" + FileUtil.getBaseName(SKY_FILE_SMALLMOL_PEP, 2)); // delete the exploded folder

        // Rename one of the raw files
        portalHelper.click(Locator.folderTab("Raw Data"));
        _fileBrowserHelper.renameFile(RAW_FILE_WIFF, RAW_FILE_WIFF + ".RENAMED");


        // Include subfolders in the experiment
        goToDashboard();
        expWebPart = new TargetedMsExperimentWebPart(this);
        expWebPart.clickMoreDetails();
        clickButton("Include Subfolders");

        // Resubmit
        goToDashboard();
        expWebPart.clickResubmit();
        resubmitWithoutPxd(false, true);
        goToDashboard();
        assertTextPresent("Copy Pending!");

        // Copy, and keep the previous copy
        copyExperimentAndVerify(projectName, sourceFolder, List.of(subfolder1, subfolder2), experimentTitle,
                2, // We are not deleting the first copy so this is version 2
                true,
                false, // Do not delete old copy
                targetFolder,
                shortAccessUrl);

        // TODO: verify expected symlinks here.
        //  - Files that were deleted from the submitted folder should not be moved from the old copy to the new one
        //  - Renamed file in the submitted folder (Site52_041009_Study9S_Phase-I.wiff -> Site52_041009_Study9S_Phase-I.wiff.RENAMED):
        //    should still be named Site52_041009_Study9S_Phase-I.wiff in the previous copy (Test Copy 3 V.1),
        //    and link to Site52_041009_Study9S_Phase-I.wiff.RENAMED in the current copy (Test Copy 3)
    }

    @Test
    public void testDeletePanoramaPublicFolder()
    {
        String projectName = getProjectName();
        String sourceFolder = "Folder 4";

        String targetFolder = "Test Copy 4";
        String experimentTitle = "This is a test for moving symlinked files back after the Panorama Public copy is deleted";

        setupSourceFolder(projectName, sourceFolder, SUBMITTER);
        impersonate(SUBMITTER);
        updateSubmitterAccountInfo("One");

        // Import Skyline documents to the folder
        importData(SKY_FILE_1, 1);
        // Upload some raw files
        portalHelper.click(Locator.folderTab("Raw Data"));
        _fileBrowserHelper.uploadFile(getSampleDataPath(RAW_FILE_WIFF));

        // Add the "Targeted MS Experiment" webpart
        TargetedMsExperimentWebPart expWebPart = createExperimentCompleteMetadata(experimentTitle);
        expWebPart.clickSubmit();
        String shortAccessUrl = submitWithoutPXId();

        // Copy the experiment to the Panorama Public project
        copyExperimentAndVerify(projectName, sourceFolder, null, experimentTitle, targetFolder, shortAccessUrl);

        // Prepare to resubmit
        goToProjectFolder(projectName, sourceFolder);
        impersonate(SUBMITTER);

        // Upload another raw file
        portalHelper.click(Locator.folderTab("Raw Data"));
        _fileBrowserHelper.uploadFile(getSampleDataPath(RAW_FILE_WIFF_SCAN));
        // Rename the raw file uploaded earlier
        portalHelper.click(Locator.folderTab("Raw Data"));
        _fileBrowserHelper.renameFile(RAW_FILE_WIFF, RAW_FILE_WIFF + ".RENAMED");

        // Resubmit
        goToDashboard();
        expWebPart.clickResubmit();
        resubmitWithoutPxd(false, true);
        goToDashboard();
        assertTextPresent("Copy Pending!");

        // Copy, and keep the previous copy
        copyExperimentAndVerify(projectName, sourceFolder, null, experimentTitle,
                2, // We are not deleting the first copy so this is version 2
                true,
                false, // Do not delete old copy
                targetFolder,
                shortAccessUrl);

        // The folder containing the older copy should have a "V.1" suffix added to the name.
        String v1Folder = targetFolder + " V.1";
        assertTrue("Expected the container for the previous copy to have been renamed with a suffix 'V.1'",
                _containerHelper.doesContainerExist(PANORAMA_PUBLIC + "/" + v1Folder));

        // Source container path (sourceFolder): getProjectName() + "/" + sourceFolder
        // Container path for current copy on Panorama Public (current_copy): PANORAMA_PUBLIC + "/" + targetFolder
        // Container path for the previous copy (V.1) on Panorama Public (v1_folder): PANORAMA_PUBLIC + "/" + v1Folder
        // State of symlinks should be (TODO: @Sweta)
        // In the source folder:
        // sourceFolder/@files/Study9S_Site52_v1.sky.zip -> current_copy/@files/Study9S_Site52_v1.sky.zip
        // sourceFolder/@files/Study9S_Site52_v1/Study9S_Site52_v1.sky -> current_copy/@files/Study9S_Site52_v1/Study9S_Site52_v1.sky
        // sourceFolder/@files/Study9S_Site52_v1/Study9S_Site52_v1.skyd -> current_copy/@files/Study9S_Site52_v1/Study9S_Site52_v1.skyd
        // sourceFolder/@files/RawFiles/Site52_041009_Study9S_Phase-I.wiff.RENAMED -> current_copy/@files/RawFiles/Site52_041009_Study9S_Phase-I.wiff.RENAMED
        // sourceFolder/@files/RawFiles/Site52_041009_Study9S_Phase-I.wiff.scan -> current_copy/@files/RawFiles/Site52_041009_Study9S_Phase-I.wiff.scan
        // In V.1 folder:
        // v1_folder/@files/Study9S_Site52_v1.sky.zip -> current_copy/@files/Study9S_Site52_v1.sky.zip
        // v1_folder/@files/Study9S_Site52_v1/Study9S_Site52_v1.sky -> current_copy/@files/Study9S_Site52_v1/Study9S_Site52_v1.sky
        // v1_folder/@files/Study9S_Site52_v1/Study9S_Site52_v1.skyd -> current_copy/@files/Study9S_Site52_v1/Study9S_Site52_v1.skyd
        // v1_folder/@files/RawFiles/Site52_041009_Study9S_Phase-I.wiff -> current_copy/@files/RawFiles/Site52_041009_Study9S_Phase-I.wiff.RENAMED
        // v1_folder/@files/RawFiles/Site52_041009_Study9S_Phase-I.wiff.scan -- FILE SHOULD NOT EXISTS IN V.1 copy


        APIContainerHelper apiContainerHelper = new APIContainerHelper(this);

        // Delete the current copy. Symlinked files should be moved to the previous copy
        apiContainerHelper.deleteFolder(PANORAMA_PUBLIC, targetFolder);
        assertFalse("Expected the container for the current copy to have been deleted",
                _containerHelper.doesContainerExist(PANORAMA_PUBLIC + "/" + targetFolder));

        // State of symlinks should be (TODO: @Sweta)
        // In the source folder:
        // sourceFolder/@files/Study9S_Site52_v1.sky.zip -> v1_folder/@files/Study9S_Site52_v1.sky.zip
        // sourceFolder/@files/Study9S_Site52_v1/Study9S_Site52_v1.sky -> v1_folder/@files/Study9S_Site52_v1/Study9S_Site52_v1.sky
        // sourceFolder/@files/Study9S_Site52_v1/Study9S_Site52_v1.skyd -> v1_folder/@files/Study9S_Site52_v1/Study9S_Site52_v1.skyd
        // sourceFolder/@files/RawFiles/Site52_041009_Study9S_Phase-I.wiff.RENAMED -> v1_folder/@files/RawFiles/Site52_041009_Study9S_Phase-I.wiff
        // sourceFolder/@files/RawFiles/Site52_041009_Study9S_Phase-I.wiff.scan --  Not a symlink. Did not exist in V.1 copy so should have been moved back to the sourceFolder.
        // In V.1 version folder:
        // NONE OF THE FILES SHOULD BE SYMLINKS


        // Delete the older copy as well. Symlinked files should be moved back to the source folder
        apiContainerHelper.deleteFolder(PANORAMA_PUBLIC, v1Folder);
        assertFalse("Expected the container for the current copy to have been deleted",
                _containerHelper.doesContainerExist(PANORAMA_PUBLIC + "/" + v1Folder));

        // State of symlinks should be (TODO: @Sweta)
        // In the source folder:
        // ALL FILES SHOULD HAVE BEEN MOVED BACK TO sourceFolder. NONE OF THE FILES SHOULD BE SYMLINKS
        // sourceFolder/@files/Study9S_Site52_v1.sky.zip
        // sourceFolder/@files/Study9S_Site52_v1/Study9S_Site52_v1.sky
        // sourceFolder/@files/Study9S_Site52_v1/Study9S_Site52_v1.skyd
        // sourceFolder/@files/RawFiles/Site52_041009_Study9S_Phase-I.wiff.RENAMED
        // sourceFolder/@files/RawFiles/Site52_041009_Study9S_Phase-I.wiff.scan
    }

    private void importDataInSubfolder(String file, String subfolder, int jobCount)
    {
        Locator.XPathLocator importButtonLoc = Locator.lkButton("Process and Import Data");
        WebElement importButton = importButtonLoc.findElementOrNull(getDriver());
        if (null == importButton)
        {
            goToModule("Pipeline");
            importButton = importButtonLoc.findElement(getDriver());
        }
        clickAndWait(importButton);
        String fileName = Paths.get(file).getFileName().toString();
        if (!_fileBrowserHelper.fileIsPresent(subfolder))
        {
            _fileBrowserHelper.createFolder(subfolder);
        }
        _fileBrowserHelper.selectFileBrowserItem("/" + subfolder + "/");
        if (!_fileBrowserHelper.fileIsPresent(fileName))
        {
            _fileBrowserHelper.uploadFile(TestFileUtils.getSampleData("TargetedMS/" + file));
        }
        _fileBrowserHelper.importFile(fileName, "Import Skyline Results");
        waitForText("Skyline document import");
        waitForPipelineJobsToComplete(jobCount, file, false);
    }
}

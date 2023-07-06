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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 10)
public class PanoramaPublicSymlinkTest extends PanoramaPublicBaseTest
{

    @Override
    public String getSampleDataFolder()
    {
        return SAMPLEDATA_FOLDER;
    }

    private void fileCheck(String parent, String file, String target, boolean symlink) throws IOException
    {
        File f = new File(parent, file);
        assertTrue(file + " in " + parent + " is missing", f.exists());
        if (symlink)
        {
            assertTrue(file + " in " + parent + " is not sym link",
                    Files.isSymbolicLink(f.toPath()));
            assertTrue(file + " in " + parent + " is not pointing to correct target. Expected: " + target
                    + " Actual: " + Files.readSymbolicLink(f.toPath()).toString(),
                    Files.readSymbolicLink(f.toPath()).toString().startsWith(target));
        }
        else
        {
            assertFalse(file + " in " + parent + " is sym link",
                    Files.isSymbolicLink(f.toPath()));
        }
    }

    private void verifySmallMolFiles(String filesLoc, String filesTarget, boolean symlinks) throws IOException
    {
        fileCheck(filesLoc, SMALLMOL_PLUS_PEPTIDES_SKY_ZIP, filesTarget, symlinks);

        // directory should never be symlink
        fileCheck(filesLoc, SMALLMOL_PLUS_PEPTIDES, filesTarget, false);

        fileCheck(filesLoc + SMALLMOL_PLUS_PEPTIDES, SMALLMOL_PLUS_PEPTIDES_SKY, filesTarget, symlinks);
        fileCheck(filesLoc + SMALLMOL_PLUS_PEPTIDES, SMALLMOL_PLUS_PEPTIDES_SKYD, filesTarget, symlinks);
        fileCheck(filesLoc + SMALLMOL_PLUS_PEPTIDES, SMALLMOL_PLUS_PEPTIDES_SKY_VIEW, filesTarget, symlinks);
    }

    private void verifyQC1Files(String filesLoc, String filesTarget, boolean symlinks) throws IOException
    {
        fileCheck(filesLoc, QC_1_SKY_ZIP, filesTarget, symlinks);

        // directory should never be symlink
        fileCheck(filesLoc, QC_1, filesTarget, false);

        fileCheck(filesLoc + QC_1, QC_1_SKY, filesTarget, symlinks);
        fileCheck(filesLoc + QC_1, QC_1_SKY_VIEW, filesTarget, symlinks);
        fileCheck(filesLoc + QC_1, QC_1_SKYD, filesTarget, symlinks);
    }

    private void verifyCopyFiles(String sourcePath, String targetPath, boolean symlinks) throws IOException
    {
        String filesLoc = TestFileUtils.getDefaultFileRoot(sourcePath).getPath() + File.separator;
        String filesTarget = TestFileUtils.getDefaultFileRoot(targetPath).getPath() + File.separator;

        fileCheck(filesLoc, SKY_FILE_1, filesTarget, symlinks);

        // directory should never be symlink
        fileCheck(filesLoc, SKY_FOLDER_NAME, filesTarget, false);

        fileCheck(filesLoc + SKY_FOLDER_NAME, SKY_FOLDER_NAME + ".skyd", filesTarget, symlinks);
        fileCheck(filesLoc + SKY_FOLDER_NAME, SKY_FOLDER_NAME + ".sky", filesTarget, symlinks);
        fileCheck(filesLoc + SKY_FOLDER_NAME, SKY_FOLDER_NAME + ".sky.view", filesTarget, symlinks);

        // directory should never be symlink
        fileCheck(filesLoc, "RawFiles", filesTarget, false);

        fileCheck(filesLoc + "RawFiles", RAW_FILE_WIFF_SCAN, filesTarget, symlinks);
        fileCheck(filesLoc + "RawFiles", RAW_FILE_WIFF, filesTarget, symlinks);

        verifyQC1Files(filesLoc, filesTarget, symlinks);

        // directory should never be symlink
        fileCheck(filesLoc, SMALL_MOL_FILES, filesTarget, false);
        filesLoc = TestFileUtils.getDefaultFileRoot(sourcePath).getPath() + File.separator + SMALL_MOL_FILES + File.separator;

        verifySmallMolFiles(filesLoc, filesTarget, symlinks);
    }

    private void verifyCopySubfolderFiles(String sourcePath, String targetPath, boolean symlinks) throws IOException
    {
        String filesLoc = TestFileUtils.getDefaultFileRoot(sourcePath).getPath() + File.separator;
        String filesTarget = TestFileUtils.getDefaultFileRoot(targetPath).getParent() + File.separator;

        fileCheck(filesLoc, SKY_FILE_1, filesTarget, symlinks);

        // directory should never be symlink
        fileCheck(filesLoc, SKY_FOLDER_NAME, filesTarget, false);

        fileCheck(filesLoc + SKY_FOLDER_NAME, SKY_FOLDER_NAME + ".skyd", filesTarget, symlinks);
        fileCheck(filesLoc + SKY_FOLDER_NAME, SKY_FOLDER_NAME + ".sky", filesTarget, symlinks);
        fileCheck(filesLoc + SKY_FOLDER_NAME, SKY_FOLDER_NAME + ".sky.view", filesTarget, symlinks);

        // directory should never be symlink
        fileCheck(filesLoc, "RawFiles", filesTarget, false);

        fileCheck(filesLoc + "RawFiles", RAW_FILE_WIFF_SCAN, filesTarget, symlinks);
        fileCheck(filesLoc + "RawFiles", RAW_FILE_WIFF_RENAMED, filesTarget, symlinks);

        verifyQC1Files(filesLoc, filesTarget, symlinks);

        filesLoc = TestFileUtils.getDefaultFileRoot(sourcePath + File.separator + "ExperimentalData").getPath() + File.separator;
        verifySmallMolFiles(filesLoc, filesTarget, symlinks);

        filesLoc = TestFileUtils.getDefaultFileRoot(sourcePath + File.separator + "SystemSuitability").getPath() + File.separator;
        verifyQC1Files(filesLoc, filesTarget, symlinks);
    }

    @Test
    public void testSymLinks() throws IOException
    {
        testCopyOrSymlink(true);
    }

    @Test
    public void testFileCopy() throws IOException
    {
        testCopyOrSymlink(false);
    }

    private void testCopyOrSymlink(boolean useSymlinks) throws IOException
    {
        String projectName = getProjectName();
        String sourceFolder = "Folder 3" + (useSymlinks ? " Symlinks" : " Copy");

        String targetFolder = "Test Copy 3" + (useSymlinks ? " Symlinks" : " Copy");
        String experimentTitle = "This is a test for Panorama Public file " + (useSymlinks ? "symlinks" : "copy") + " functionality";

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

        // Verify files symlinked or copied correctly
        String targetPath = PANORAMA_PUBLIC + File.separator + targetFolder + File.separator;
        verifyCopyFiles(projectName + File.separator + sourceFolder + File.separator, targetPath, true);
        verifyCopyFiles(targetPath, targetPath, false);


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
                shortAccessUrl,
                useSymlinks);

        verifyCopySubfolderFiles(projectName + File.separator + sourceFolder + File.separator, targetPath, useSymlinks);
        verifyCopySubfolderFiles(targetPath, targetPath, false);

        // Verify files that were deleted from the submitted folder should not be moved from the old copy to the new one
        String filesTarget = TestFileUtils.getDefaultFileRoot(targetPath).getPath() + File.separator;
        String filesTargetParent = filesTarget + "SmallMoleculeFiles" + File.separator;
        filesTarget = filesTargetParent + SKY_FILE_SMALLMOL_PEP;
        File f = new File(filesTarget);
        assertFalse(filesTarget + " should not exist in " + filesTargetParent + ".", f.exists());

        filesTarget = filesTargetParent + FileUtil.getBaseName(SKY_FILE_SMALLMOL_PEP, 2);
        f = new File(filesTarget);
        assertFalse(filesTarget + " should not exist in " + filesTargetParent + ".", f.exists());
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
        updateSubmitterAccountInfo("Rollback");

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

        log("Verifying files in source folder");
        File filesLoc = TestFileUtils.getDefaultFileRoot(projectName + "/" + sourceFolder);
        assertTrue("sky.zip should be sym link", Files.isSymbolicLink(new File(filesLoc, "/" + SKY_FILE_1).toPath()));
        assertTrue("file(.sky) in the " + SKY_FOLDER_NAME + " is not sym link",
                Files.isSymbolicLink(new File(filesLoc, "/" + SKY_FOLDER_NAME + "/" + SKY_FOLDER_NAME + ".sky").toPath()));
        assertTrue("file(.skyd) in the " + SKY_FOLDER_NAME + " is not sym link",
                Files.isSymbolicLink(new File(filesLoc, "/" + SKY_FOLDER_NAME + "/" + SKY_FOLDER_NAME + ".skyd").toPath()));
        assertTrue("Rename raw file missing", new File(filesLoc + "/RawFiles/" + RAW_FILE_WIFF + ".RENAMED").exists());
        assertTrue("Additional raw file missing", new File(filesLoc + "/RawFiles/" + RAW_FILE_WIFF_SCAN).exists());

        log("Verifying files in copied folder");
        filesLoc = TestFileUtils.getDefaultFileRoot(PANORAMA_PUBLIC + "/" + v1Folder);
        assertTrue(v1Folder + " sky.zip should be sym link", Files.isSymbolicLink(new File(filesLoc, "/" + SKY_FILE_1).toPath()));
        assertTrue(v1Folder + " file(.sky) in the " + SKY_FOLDER_NAME + " should be a sym link",
                Files.isSymbolicLink(new File(filesLoc, "/" + SKY_FOLDER_NAME + "/" + SKY_FOLDER_NAME + ".sky").toPath()));
        assertTrue(v1Folder + " file(.skyd) in the " + SKY_FOLDER_NAME + " should be a sym link",
                Files.isSymbolicLink(new File(filesLoc, "/" + SKY_FOLDER_NAME + "/" + SKY_FOLDER_NAME + ".skyd").toPath()));
        assertFalse("Rename raw file should not be present", new File(filesLoc + "/RawFiles/" + RAW_FILE_WIFF + ".RENAMED").exists());
        assertFalse("Additional new raw file should not be present", new File(filesLoc + "/RawFiles/" + RAW_FILE_WIFF_SCAN).exists());

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

        log("Verifying files in target folder after delete");
        filesLoc = TestFileUtils.getDefaultFileRoot(PANORAMA_PUBLIC + "/" + v1Folder);
        assertFalse("sky.zip should not be sym link", Files.isSymbolicLink(new File(filesLoc, "/" + SKY_FILE_1).toPath()));
        assertFalse(v1Folder + " file(.sky) in the " + SKY_FOLDER_NAME + " should be a sym link",
                Files.isSymbolicLink(new File(filesLoc, "/" + SKY_FOLDER_NAME + "/" + SKY_FOLDER_NAME + ".sky").toPath()));
        assertFalse(v1Folder + " file(.skyd) in the " + SKY_FOLDER_NAME + " should be a sym link",
                Files.isSymbolicLink(new File(filesLoc, "/" + SKY_FOLDER_NAME + "/" + SKY_FOLDER_NAME + ".skyd").toPath()));
        assertTrue("Original raw file should be present", new File(filesLoc + "/RawFiles/" + RAW_FILE_WIFF).exists());
        assertFalse("Rename raw file should not be present", new File(filesLoc + "/RawFiles/" + RAW_FILE_WIFF + ".RENAMED").exists());
        assertFalse("Additional new raw file should not be present", new File(filesLoc + "/RawFiles/" + RAW_FILE_WIFF_SCAN).exists());

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

        log("Verifying files in source folder after deleting target folder");
        filesLoc = TestFileUtils.getDefaultFileRoot(projectName + "/" + sourceFolder);
        assertFalse("sky.zip should be sym link", Files.isSymbolicLink(new File(filesLoc, "/" + SKY_FILE_1).toPath()));
        assertFalse("file(.sky) in the " + SKY_FOLDER_NAME + " is not sym link",
                Files.isSymbolicLink(new File(filesLoc, "/" + SKY_FOLDER_NAME + "/" + SKY_FOLDER_NAME + ".sky").toPath()));
        assertFalse("file(.skyd) in the " + SKY_FOLDER_NAME + " is not sym link",
                Files.isSymbolicLink(new File(filesLoc, "/" + SKY_FOLDER_NAME + "/" + SKY_FOLDER_NAME + ".skyd").toPath()));
        assertTrue("Rename raw file missing", new File(filesLoc + "/RawFiles/" + RAW_FILE_WIFF + ".RENAMED").exists());
        assertTrue("Additional raw file missing", new File(filesLoc + "/RawFiles/" + RAW_FILE_WIFF_SCAN).exists());
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

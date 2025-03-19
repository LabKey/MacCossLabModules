package org.labkey.test.tests.panoramapublic;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.components.CustomizeView;
import org.labkey.test.util.DataRegionTable;

import static org.junit.Assert.fail;


@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class PanoramaPublicMoveSkyDocTest extends PanoramaPublicBaseTest
{
    private static final String SKY_FILE_1 = "MRMer.zip";
    private static final String SKY_FILE_2 = "MRMer_renamed_protein.zip";
    private static final String SKY_FILE_3 = "SmMolLibA.sky.zip";

    @Test
    public void testExperimentCopy()
    {
        String projectName = getProjectName();
        String sourceFolder = "SourceFolder";
        String sourceSubfolder = "SourceSubFolder";
        String targetFolder = "TargetFolder";

        log("Creating source folder " + sourceFolder);
        setupSourceFolder(projectName, sourceFolder, SUBMITTER);
        log("Creating subfolder, " + sourceSubfolder + " in folder " + sourceFolder);
        setupSubfolder(projectName, sourceFolder, sourceSubfolder, FolderType.Experiment, SUBMITTER);

        goToProjectHome();
        log("Creating target folder " + targetFolder);
        setupSourceFolder(projectName, targetFolder, SUBMITTER);

        impersonate(SUBMITTER);
        updateSubmitterAccountInfo("One");

        goToProjectFolder(projectName, sourceFolder);
        log("Importing " + SKY_FILE_1 + " in folder " + sourceFolder);
        importData(SKY_FILE_1, 1);
        goToDashboard();
        log("Moving " + SKY_FILE_1 + " FROM " + sourceFolder + " TO " + targetFolder);
        moveDocument(SKY_FILE_1, targetFolder, 1);

        var skyDocSourceFolder = sourceFolder + "/" + sourceSubfolder;
        goToProjectFolder(projectName, skyDocSourceFolder);
        log("Importing " + SKY_FILE_2 + " in folder " + skyDocSourceFolder);
        importData(SKY_FILE_2, 1);
        goToDashboard();
        log("Moving " + SKY_FILE_2 + " FROM " + skyDocSourceFolder + "TO " + targetFolder);
        moveDocument(SKY_FILE_2, targetFolder, 2);

        goToProjectFolder(projectName, targetFolder);
        log("Importing " + SKY_FILE_3 + " in folder " + targetFolder);
        importData(SKY_FILE_3, 3);

        // Test moving the sky.zip to a subdirectory in the file root, while the .skyd remains in the original location.
        //
        // If the sky.zip file and the .skyd file are not in their typical relative locations, the skydDataId in
        // TargetedMSRun has to be updated after the folder is copied to Panorama Public. Without the update the
        // copy pipeline job fails.
        // Example:
        // -------------------------
        // BEFORE MOVE:
        // SmallMolLibA.sky.zip
        // SmallMolLibA
        //   - SmallMolLibA.sky
        //   - SmallMolLibA.skyd
        // -------------------------
        // AFTER MOVE:
        // - SkylineFiles
        //   - SmallMolLibA.sky.zip (new location after move)
        // SmallMolLibA
        //   - SmallMolLibA.sky
        //   - SmallMolLibA.skyd
        // This results in:
        // Two ExpData rows created for the .skyd file in folder copy on Panorama Public.
        // 1. @files/export/.../Run<id>/SkylineFiles/SmallMolLibA/SmallMolLibA.skyd
        // 2. @files/SmallMolLibA/SmallMolLibA.skyd
        // #1 is set as the skydDataId in TargetedMSRuns, but it is not linked to the ExpRun (runId is null)
        // #2 is linked to the ExpRun.  This is the ExpData that skydDataId in TargetedMSRun *should* refer to.
        // This situation causes two problems
        // 1. Since the "export" directory gets deleted after folder import, chromatogram data in no longer available
        //    to view since the .skyd file that skydDataId points to no longer exists.
        // 2. ExpData cleanup in CopyExperimentFinalTask fails due to FK violation - cannot delete ExpData #1 since
        //    skydDataId in TargetedMSRun points to it.
        // PanoramaPublicFileImporter.updateSkydDataId() fixes the skydDataId, if required.
        log("Moving " + SKY_FILE_3 + " TO TargetSubDir in the Files browser");
        // Move the .sky.zip file to a subdirectory
        moveSkyZipToSubDir(SKY_FILE_3, "SkylineFiles");

        log("Creating and submitting an experiment");
        String experimentTitle = "Experiment to test moving Skyline documents from other folders";
        var expWebPart = createExperimentCompleteMetadata(experimentTitle);
        expWebPart.clickSubmit();
        String shortAccessLink = submitWithoutPXId();

        // Copy the experiment to the Panorama Public project
        var panoramaCopyFolder = "Copy of " + targetFolder;
        log("Copying experiment to folder " + panoramaCopyFolder +" in the Panorama Public project");
        copyExperimentAndVerify(projectName, targetFolder, experimentTitle, panoramaCopyFolder, shortAccessLink);
        goToProjectFolder(PANORAMA_PUBLIC, panoramaCopyFolder);
        verifyRunFilePathRoot(SKY_FILE_1, PANORAMA_PUBLIC, panoramaCopyFolder);
        verifyRunFilePathRoot(SKY_FILE_2, PANORAMA_PUBLIC, panoramaCopyFolder);
        verifyRunFilePathRoot(SKY_FILE_3, PANORAMA_PUBLIC, panoramaCopyFolder);

        // Verify that we can view chromatograms for the Skyline document that was moved to a subdirectory.
        goToDashboard();
        clickAndWait(Locator.linkContainingText(SKY_FILE_3));
        clickAndWait(Locator.linkContainingText("2 replicates"));
        clickAndWait(Locator.linkContainingText("FU2_2017_0915_RJ_05_1ab_30").index(0));
        assertTextPresent("Sample File Summary");
        assertTextPresent("Total Ion Chromatogram");
        assertTextNotPresent("Unable to load chromatogram");
    }

    private void moveDocument(String skylineDocName, String targetFolder, int jobCount)
    {
        DataRegionTable table = new DataRegionTable.DataRegionFinder(getDriver()).withName("TargetedMSRuns").waitFor();
        table.checkCheckbox(0);
        table.clickHeaderButton("Move");
        waitAndClickAndWait(Locator.linkWithText(targetFolder));
        waitForPipelineJobsToComplete(jobCount, false);

        goToDashboard();

        // Verify that the document moved
        table = new DataRegionTable.DataRegionFinder(getDriver()).withName("TargetedMSRuns").waitFor();
        int rowIndex = table.getRowIndex("File", skylineDocName);
        if (rowIndex < 0)
            fail("Unable to find row for moved Skyline document: " + skylineDocName);

        verifyRunFilePathRoot(skylineDocName, getProjectName(), targetFolder);
    }

    private void moveSkyZipToSubDir(String documentName, String subDir)
    {
        portalHelper.goToModule("FileContent");
        waitForText(documentName);
        if (!_fileBrowserHelper.fileIsPresent(subDir))
        {
            _fileBrowserHelper.createFolder(subDir);
        }
        _fileBrowserHelper.moveFile(documentName, subDir);
    }


    private void verifyRunFilePathRoot(String skylineDocName, String projectName, String targetFolder)
    {
        // Verify that exp.run filePathRoot is set to the target folder
        portalHelper.navigateToQuery("exp", "Runs");
        DataRegionTable queryGrid = new DataRegionTable("query", this);
        CustomizeView view = queryGrid.openCustomizeGrid();
        view.showHiddenItems();
        view.addColumn("FilePathRoot");
        view.applyCustomView();
        int rowIndex = queryGrid.getRowIndex("Name", skylineDocName);
        if (rowIndex < 0)
            fail("Unable to find row for Skyline document in exp.Runs query grid: " + skylineDocName);

        var expectedFileRoot =  TestFileUtils.getDefaultFileRoot(projectName + "/" + targetFolder).getPath();
        var filePathRoot = queryGrid.getDataAsText(rowIndex, "FilePathRoot");

        Assert.assertEquals("Unexpected FilePathRoot ",expectedFileRoot, filePathRoot);
    }
}

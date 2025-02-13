package org.labkey.test.tests.panoramapublic;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.components.CustomizeView;
import org.labkey.test.util.APIContainerHelper;
import org.labkey.test.util.DataRegionTable;

import static org.junit.Assert.fail;


@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class PanoramaPublicMoveSkyDocTest extends PanoramaPublicBaseTest
{
    private static final String SKY_FILE_1 = "MRMer.zip";
    private static final String SKY_FILE_2 = "MRMer_renamed_protein.zip";
    private static final String SKY_FILE_3 = "SmMolLibA.sky.zip";

    private static final String SOURCE_FOLDER = "SourceFolder";
    private static final String TARGET_FOLDER = "TargetFolder";
    @Test
    public void testExperimentCopy()
    {
        String projectName = getProjectName();
        String sourceSubfolder = "SourceSubFolder";

        log("Creating source folder " + SOURCE_FOLDER);
        setupSourceFolder(projectName, SOURCE_FOLDER, SUBMITTER);
        log("Creating subfolder, " + sourceSubfolder + " in folder " + SOURCE_FOLDER);
        setupSubfolder(projectName, SOURCE_FOLDER, sourceSubfolder, FolderType.Experiment, SUBMITTER);

        goToProjectHome();
        log("Creating target folder " + TARGET_FOLDER);
        setupSourceFolder(projectName, TARGET_FOLDER, SUBMITTER);

        impersonate(SUBMITTER);
        updateSubmitterAccountInfo("One");

        goToProjectFolder(projectName, SOURCE_FOLDER);
        log("Importing " + SKY_FILE_1 + " in folder " + SOURCE_FOLDER);
        importData(SKY_FILE_1, 1);
        goToDashboard();
        log("Moving " + SKY_FILE_1 + " FROM " + SOURCE_FOLDER + " TO " + TARGET_FOLDER);
        moveDocument(SKY_FILE_1, TARGET_FOLDER, 1);

        var skyDocSourceFolder = SOURCE_FOLDER + "/" + sourceSubfolder;
        goToProjectFolder(projectName, skyDocSourceFolder);
        log("Importing " + SKY_FILE_2 + " in folder " + skyDocSourceFolder);
        importData(SKY_FILE_2, 1);
        goToDashboard();
        log("Moving " + SKY_FILE_2 + " FROM " + skyDocSourceFolder + "TO " + TARGET_FOLDER);
        moveDocument(SKY_FILE_2, TARGET_FOLDER, 2);

        goToProjectFolder(projectName, TARGET_FOLDER);
        log("Importing " + SKY_FILE_3 + " in folder " + skyDocSourceFolder);
        importData(SKY_FILE_3, 3);

        log("Creating and submitting an experiment");
        String experimentTitle = "Experiment to test moving Skyline documents from other folders";
        var expWebPart = createExperimentCompleteMetadata(experimentTitle);
        expWebPart.clickSubmit();
        String shortAccessLink = submitWithoutPXId();

        // Copy the experiment to the Panorama Public project
        var panoramaCopyFolder = "Copy of " + TARGET_FOLDER;
        log("Copying experiment to folder " + panoramaCopyFolder +" in the Panorama Public project");
        copyExperimentAndVerify(projectName, TARGET_FOLDER, experimentTitle, panoramaCopyFolder, shortAccessLink);
        goToProjectFolder(PANORAMA_PUBLIC, panoramaCopyFolder);
        verifyRunFilePathRoot(SKY_FILE_1, PANORAMA_PUBLIC, panoramaCopyFolder);
        verifyRunFilePathRoot(SKY_FILE_2, PANORAMA_PUBLIC, panoramaCopyFolder);
        verifyRunFilePathRoot(SKY_FILE_3, PANORAMA_PUBLIC, panoramaCopyFolder);
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

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        String projectName = getProjectName();
        APIContainerHelper apiContainerHelper = new APIContainerHelper(this);
        apiContainerHelper.deleteFolder(projectName, SOURCE_FOLDER);
        apiContainerHelper.deleteFolder(projectName, TARGET_FOLDER);

        super.doCleanup(afterTest);
    }
}

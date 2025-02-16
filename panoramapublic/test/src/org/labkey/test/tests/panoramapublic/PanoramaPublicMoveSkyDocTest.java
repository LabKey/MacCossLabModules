package org.labkey.test.tests.panoramapublic;

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.BeforeClass;
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
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.PermissionsHelper;

import java.util.List;

import static org.junit.Assert.fail;


@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class PanoramaPublicMoveSkyDocTest extends PanoramaPublicBaseTest
{
    private static final String SKY_FILE_1 = "MRMer.zip";
    private static final String SKY_FILE_2 = "MRMer_renamed_protein.zip";
    private static final String SKY_FILE_3 = "SmMolLibA.sky.zip";

    private static final String SOURCE_PROJECT = "MoveSkyDoc_SourceFolder";
    private static final String TARGET_PROJECT = "MoveSkyDoc_TargetFolder";

    @BeforeClass
    public static void initSourceAndTargetProjects()
    {
        PanoramaPublicMoveSkyDocTest test = (PanoramaPublicMoveSkyDocTest) getCurrentTest();
        test.doInit();
    }

    private void doInit()
    {
        log("Creating source project");
        setUpFolder(SOURCE_PROJECT, FolderType.Experiment);

        log("Creating target project");
        setUpFolder(TARGET_PROJECT, FolderType.Experiment);
    }

    @Test
    public void testExperimentCopy()
    {
        String projectName = getProjectName();
        String sourceSubfolder = "SourceSubFolder";

        ApiPermissionsHelper permissionsHelper = new ApiPermissionsHelper(this);
        _userHelper.ensureUsersExist(List.of(SUBMITTER));

        goToProjectHome(SOURCE_PROJECT);
        permissionsHelper.addMemberToRole(SUBMITTER, "Folder Administrator",
                PermissionsHelper.MemberType.user, SOURCE_PROJECT);

        goToProjectHome(TARGET_PROJECT);
        permissionsHelper.addMemberToRole(SUBMITTER, "Folder Administrator",
                PermissionsHelper.MemberType.user, TARGET_PROJECT);


        log("Creating subfolder, " + sourceSubfolder + " in project " + SOURCE_PROJECT);
        setupSubfolder(SOURCE_PROJECT, sourceSubfolder, FolderType.Experiment);
        goToProjectFolder(SOURCE_PROJECT, sourceSubfolder);
        permissionsHelper.addMemberToRole(SUBMITTER, "Folder Administrator",
                PermissionsHelper.MemberType.user, SOURCE_PROJECT + "/" + sourceSubfolder);


        impersonate(SUBMITTER);
        updateSubmitterAccountInfo("One");

        goToProjectHome(SOURCE_PROJECT);
        log("Importing " + SKY_FILE_1 + " in project " + SOURCE_PROJECT);
        importData(SKY_FILE_1, 1);
        goToDashboard();
        log("Moving " + SKY_FILE_1 + " FROM " + SOURCE_PROJECT + " TO " + TARGET_PROJECT);
        moveDocument(SKY_FILE_1, TARGET_PROJECT, 1);

        var skyDocSourceFolder = SOURCE_PROJECT + "/" + sourceSubfolder;
        goToProjectFolder(SOURCE_PROJECT, sourceSubfolder);
        log("Importing " + SKY_FILE_2 + " in folder " + skyDocSourceFolder);
        importData(SKY_FILE_2, 1);
        goToDashboard();
        log("Moving " + SKY_FILE_2 + " FROM " + skyDocSourceFolder + "TO " + TARGET_PROJECT);
        moveDocument(SKY_FILE_2, TARGET_PROJECT, 2);

        goToProjectHome(TARGET_PROJECT);
        log("Importing " + SKY_FILE_3 + " in project " + TARGET_PROJECT);
        importData(SKY_FILE_3, 3);

        log("Creating and submitting an experiment");
        String experimentTitle = "Experiment to test moving Skyline documents from other folders";
        var expWebPart = createExperimentCompleteMetadata(experimentTitle);
        expWebPart.clickSubmit();
        String shortAccessLink = submitWithoutPXId();

        // Copy the experiment to the Panorama Public project
        var panoramaCopyFolder = "Copy of " + TARGET_PROJECT;
        log("Copying experiment to folder " + panoramaCopyFolder +" in the Panorama Public project");
        copyExperimentAndVerify(TARGET_PROJECT, null, experimentTitle, panoramaCopyFolder, shortAccessLink);
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

        verifyRunFilePathRoot(skylineDocName, targetFolder, null);
    }

    private void verifyRunFilePathRoot(String skylineDocName, String projectName, @Nullable String targetFolder)
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

        var containerPath = projectName + (targetFolder != null ? "/" + targetFolder : "");
        var expectedFileRoot =  TestFileUtils.getDefaultFileRoot(containerPath).getPath();
        var filePathRoot = queryGrid.getDataAsText(rowIndex, "FilePathRoot");

        Assert.assertEquals("Unexpected FilePathRoot ",expectedFileRoot, filePathRoot);
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        APIContainerHelper apiContainerHelper = new APIContainerHelper(this);
        apiContainerHelper.deleteProject(SOURCE_PROJECT, afterTest);
        apiContainerHelper.deleteProject(TARGET_PROJECT, afterTest);

        super.doCleanup(afterTest);
    }
}

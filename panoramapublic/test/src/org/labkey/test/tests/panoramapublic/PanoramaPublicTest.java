package org.labkey.test.tests.panoramapublic;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.CustomModules;
import org.labkey.test.categories.Panorama;
import org.labkey.test.components.BodyWebPart;
import org.labkey.test.components.CustomizeView;
import org.labkey.test.pages.InsertPage;
import org.labkey.test.tests.targetedms.TargetedMSTest;
import org.labkey.test.util.APIContainerHelper;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.PostgresOnlyTest;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;

import static org.junit.Assert.assertEquals;

@Category({CustomModules.class, Panorama.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class PanoramaPublicTest extends TargetedMSTest implements PostgresOnlyTest
{
    private static final String SKY_FILE_1 = "MRMer.zip";
    private static final String SKY_FILE_2 = "smallmol_plus_peptides.sky.zip";

    private static String PANORAMA_PUBLIC = "Panorama Public " + TRICKY_CHARACTERS_FOR_PROJECT_NAMES;
    private static final String PANORAMA_PUBLIC_GROUP = "panoramapublictest";
    private static final String DESTINATION_FOLDER = "Test Copy";

    @Override
    protected String getProjectName()
    {
        return getClass().getSimpleName() + " Project " + TRICKY_CHARACTERS_FOR_PROJECT_NAMES;
    }

    @BeforeClass
    public static void initProject()
    {
        PanoramaPublicTest init = (PanoramaPublicTest)getCurrentTest();
        init.createPanoramaPublicJournalProject();
    }

    private void createPanoramaPublicJournalProject()
    {
        // Create a "Panorama Public" project where we will copy data.
        goToAdminConsole().goToSettingsSection();
        clickAndWait(Locator.linkWithText("Panorama Public"));
        clickAndWait(Locator.linkWithText("Create a new journal group"));
        setFormElement(Locator.id("journalNameTextField"), PANORAMA_PUBLIC);
        setFormElement(Locator.id("groupNameTextField"), PANORAMA_PUBLIC_GROUP);
        setFormElement(Locator.id("projectNameTextField"), PANORAMA_PUBLIC);
        clickButton("Submit", "Journal group details");
    }

    @Test
    public void testExperimentCopy()
    {
        goToProjectHome(PANORAMA_PUBLIC);
        DataRegionTable expListTable = DataRegionTable.findDataRegionWithinWebpart(this, "Targeted MS Experiment List");
        assertEquals(0, expListTable.getDataRowCount()); // Table should be empty since we have not yet copied any experiments.

        // 1. Set up our source folder. We will create an experiment here and submit it to our "Panorama Public" project.
        setupFolder(FolderType.Experiment);

        // 2. Import a Skyline document to the folder
        importData(SKY_FILE_1);
        importData(SKY_FILE_2, 2);

        // 3. Add the "Targeted MS Experiment" webpart
        PortalHelper portal = new PortalHelper(this);
        portal.click(Locator.folderTab("Panorama Dashboard"));
        portal.enterAdminMode();
        portal.addBodyWebPart("Targeted MS Experiment");

        // 4. Create a new experiment
        TargetedMsExperimentWebPart expWebPart = new TargetedMsExperimentWebPart(this);
        TargetedMsExperimentInsertPage insertPage = expWebPart.startInsert();
        insertPage.insert();

        // 5. Submit the experiment
        portal.click(Locator.folderTab("Panorama Dashboard"));
        expWebPart.submitExperiment();
        assertTextPresent("Copy Pending!");

        // 6. Copy the experiment to the Panorama Public project
        // Customize the "Submission" grid to display the short copy URL
        DataRegionTable table = DataRegionTable.findDataRegionWithinWebpart(this, "Submission");
        CustomizeView customize = table.openCustomizeGrid();
        customize.addColumn("ShortCopyURL");
        customize.applyCustomView();
        table.closeCustomizeGrid();
        table.ensureColumnPresent("Copy Link");
        List<String> copyLink = table.getColumnDataAsText("Copy Link");
        click(Locator.linkContainingText(copyLink.get(0))); // Click the copy link to start copying this folder to the Panorama Public project

        // In the copy form's folder tree view select the Panorama Public project as the destination.
        Locator.tagWithClass("span", "x4-tree-node-text").withText(PANORAMA_PUBLIC).waitForElement(new WebDriverWait(getDriver(), 5)).click();
        // Enter the name of the destination folder in the Panorama Public project
        setFormElement(Locator.tagWithName("input", "destContainerName"), DESTINATION_FOLDER);
        // Locator.extButton("Begin Copy"); // Locator.extButton() does not work.
        click(Locator.xpath(".//a[contains(@class, 'x4-btn')]//span[contains(text(), 'Begin Copy')]/ancestor::a"));

        // Wait for the pipeline job to finish
        waitForText("Copying experiment");
        waitForPipelineJobsToComplete(1, "Copying experiment: " + TargetedMsExperimentInsertPage.EXP_TITLE, false);

        // Verify the copy
        goToProjectHome(PANORAMA_PUBLIC);
        expListTable = DataRegionTable.findDataRegionWithinWebpart(this, "Targeted MS Experiment List");
        assertEquals(1, expListTable.getDataRowCount()); // The table should have a row for the copied experiment.
        expListTable.ensureColumnPresent("Title");
        Assert.assertTrue(expListTable.getDataAsText(0,"Title").contains(TargetedMsExperimentInsertPage.EXP_TITLE));
        expListTable.ensureColumnPresent("Runs");
        expListTable.ensureColumnPresent("Public"); // Column to indicate if the data is public or not
        expListTable.ensureColumnPresent("Data License");
        assertEquals("2", expListTable.getDataAsText(0, "Runs"));
        assertEquals("No", expListTable.getDataAsText(0, "Public"));
        assertEquals("CC BY 4.0", expListTable.getDataAsText(0, "Data License"));
        clickAndWait(expListTable.link(0, "Title"));
        assertTextPresentInThisOrder("Targeted MS Experiment", // Webpart title
                TargetedMsExperimentInsertPage.EXP_TITLE, // Title of the experiment
                "Data License", "CC BY 4.0" // This is the default data license
        );
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        // these tests use the UIContainerHelper for project creation, but we can use the APIContainerHelper for deletion
        APIContainerHelper apiContainerHelper = new APIContainerHelper(this);
        apiContainerHelper.deleteProject(PANORAMA_PUBLIC, afterTest);

        super.doCleanup(afterTest);
    }

    private class TargetedMsExperimentWebPart extends BodyWebPart
    {
        public static final String DEFAULT_TITLE = "Targeted MS Experiment";
        private DataRegionTable _dataRegionTable;
        private BaseWebDriverTest _test;

        public TargetedMsExperimentWebPart(BaseWebDriverTest test)
        {
            this(test, 0);
        }

        public TargetedMsExperimentWebPart(BaseWebDriverTest test, int index)
        {
            super(test.getDriver(), DEFAULT_TITLE, 0);
            _test = test;
        }

        public DataRegionTable getDataRegion()
        {
            if (_dataRegionTable == null)
                _dataRegionTable = DataRegionTable.DataRegion(_test.getDriver()).find(getComponentElement());
            return _dataRegionTable;
        }

        public PanoramaPublicTest.TargetedMsExperimentInsertPage startInsert()
        {
            findElement(Locator.linkContainingText("Create New Experiment")).click();
            return new PanoramaPublicTest.TargetedMsExperimentInsertPage(_test.getDriver());
        }

        public void submitExperiment()
        {
            findElement(Locator.linkContainingText("Submit")).click();
            waitAndClick(Locator.linkContainingText("Continue Without ProteomeXchange ID"));
            waitAndClick(Locator.linkContainingText("Submit"));
            waitAndClick(Locator.lkButton("OK")); // Confirm to proceed with the submission.
            waitAndClick(Locator.linkWithSpan("Back to Experiment Details")); // Navigate to the experiment details page.
        }

        public void resubmit()
        {
            clickAndWait(Locator.linkContainingText("Resubmit"));
            waitAndClick(Locator.lkButton("OK")); // Confirm to proceed with the submission.
        }
    }

    private class TargetedMsExperimentInsertPage extends InsertPage
    {
        private static final String DEFAULT_TITLE = "Targeted MS Experiment";
        public static final String EXP_TITLE = "This is a test experiment";

        public TargetedMsExperimentInsertPage(WebDriver driver)
        {
            super(driver, DEFAULT_TITLE);
        }

        @Override
        protected void waitForReady()
        {
            waitForElement(elements().expTitle);
        }

        public void insert()
        {
            Elements elements = elements();
            setFormElement(elements.expTitle, EXP_TITLE);
            setFormElement(elements.expAbstract, "This is a really short, 50 character long abstract");
            clickAndWait(elements.submit);
        }

        @Override
        protected Elements elements()
        {
            return new Elements();
        }

        private class Elements extends InsertPage.Elements
        {
            public Locator.XPathLocator expTitle = body.append(Locator.tagWithName("textarea", "title"));
            public Locator.XPathLocator expAbstract = body.append(Locator.tagWithName("textarea", "abstract"));
        }
    }
}

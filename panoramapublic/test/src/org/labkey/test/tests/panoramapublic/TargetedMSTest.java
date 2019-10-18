/*
 * Copyright (c) 2016-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.test.tests.panoramapublic;

import org.junit.BeforeClass;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestProperties;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.pages.targetedms.PanoramaDashboard;
import org.labkey.test.util.APIContainerHelper;
import org.labkey.test.util.ConfiguresSite;
import org.labkey.test.util.DefaultSiteConfigurer;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.LoggedParam;
import org.labkey.test.util.ReflectionUtils;
import org.labkey.test.util.UIContainerHelper;
import org.openqa.selenium.WebElement;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public abstract class TargetedMSTest extends BaseWebDriverTest
{
    private static ConfiguresSite siteConfigurer;

    public enum FolderType {
        Experiment, Library, LibraryProtein, QC, Undefined
    }

    public TargetedMSTest()
    {
        // We want to use the UI when creating the project/folder so that we can verify that we get the wizard
        // that has the extra steps
        setContainerHelper(new UIContainerHelper(this));
    }

    @Override
    protected String getProjectName()
    {
        return "TargetedMSProject" + TRICKY_CHARACTERS_FOR_PROJECT_NAMES;
    }

    @BeforeClass
    public static void initPipeline()
    {
        TargetedMSTest init = (TargetedMSTest)getCurrentTest();

        init.doInitPipeline();
    }

    protected ConfiguresSite getSiteConfigurer()
    {
        if (siteConfigurer == null)
        {
            if (TestProperties.isCloudPipelineEnabled())
                siteConfigurer = ReflectionUtils.getSiteConfigurerOrDefault("org.labkey.test.util.cloud.S3Configurer", this);
            else
                siteConfigurer = new DefaultSiteConfigurer();
        }
        else
        {
            siteConfigurer.setWrapper(this);
        }

        return siteConfigurer;
    }

    private void doInitPipeline()
    {
        getSiteConfigurer().configureSite();
    }

    protected void setupFolder(FolderType folderType)
    {
        _containerHelper.createProject(getProjectName(), "Panorama");
        waitForElement(Locator.linkContainingText("Save"));
        clickAndWait(Locator.linkContainingText("Next"));
        selectFolderType(folderType);
        getSiteConfigurer().configureProject(getProjectName());
    }

    @LogMethod
    protected void setupSubfolder(String projectName, String parentFolderName, String folderName, FolderType folderType)
    {
        _containerHelper.createSubfolder(projectName, parentFolderName, folderName, "Panorama", null, false);
        selectFolderType(folderType);
    }

    protected void importData(String file)
    {
        importData(file, 1);
    }

    @LogMethod
    protected void importData(@LoggedParam String file, int jobCount)
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
        if (!_fileBrowserHelper.fileIsPresent(fileName))
            _fileBrowserHelper.uploadFile(TestFileUtils.getSampleData("TargetedMS/" + file));
        _fileBrowserHelper.importFile(fileName, "Import Skyline Results");
        waitForText("Skyline document import");
        waitForPipelineJobsToComplete(jobCount, file, false);
    }

    @LogMethod
    protected void selectFolderType(FolderType folderType) {
        log("Select Folder Type: " + folderType);
        switch(folderType)
        {
            case Experiment:
                click(Locator.radioButtonById("experimentalData")); // click the first radio button - Experimental Data
                break;
            case Library:
                click(Locator.radioButtonById("chromatogramLibrary")); // click the 2nd radio button - Library
                break;
            case LibraryProtein:
                click(Locator.radioButtonById("chromatogramLibrary")); // click the 2nd radio button - Library
                click(Locator.checkboxByName("precursorNormalized")); // check the normalization checkbox.
                break;
            case QC:
                click(Locator.radioButtonById("QC")); // click the 3rd radio button - QC
                break;
        }
        clickButton("Finish");
    }

    public PanoramaDashboard goToDashboard()
    {
        clickTab("Panorama Dashboard");
        return new PanoramaDashboard(this);
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        // these tests use the UIContainerHelper for project creation, but we can use the APIContainerHelper for deletion
        APIContainerHelper apiContainerHelper = new APIContainerHelper(this);
        apiContainerHelper.deleteProject(getProjectName(), afterTest);
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("targetedms", "panoramapublic");
    }

    @Override
    public BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }
}

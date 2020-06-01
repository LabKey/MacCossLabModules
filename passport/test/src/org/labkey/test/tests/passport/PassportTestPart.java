/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.test.tests.passport;

import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.util.FileBrowserHelper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.LoggedParam;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.UIContainerHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;

import java.nio.file.Paths;


public abstract class PassportTestPart extends BaseWebDriverTest
{
    //The 2 users that are created and used in this test
    public String[] users = {"normaluser@gmail.com"};
    public String[] passwords = {"123123"};
    //Zip files must be in the LabKey\trunk\sampledata\ToolStore directory
    public String[] filesToUpload = {"user1-v1.zip","user2-v1.zip"};
    //File urls



    @Override
    protected String getProjectName()
    {
        return "PassportTest";
    }

    //loginAs method, reference to log into any of the accounts
    protected void loginAs(String type)
    {
        if (type.equals("siteAdmin"))
        {
            signIn();
        }
        if (type.equals(users[0]))
        {
            attemptSignIn(users[0], passwords[0]);
        }
        if (type.equals("guest"))
        {
            signOut();
        }
        log("Logging in as " + type);
    }

    @LogMethod(category = LogMethod.MethodType.SETUP)
    protected void setupProject()
    {
        setContainerHelper(new UIContainerHelper(this));
        _containerHelper.createProject(getProjectName(), "Collaboration");
        goToFolderPermissions();
        _permissionsHelper.setUserPermissions(users[0], "Reader");

        goToFolderManagement();
        click(Locator.xpath("//a[text()='Folder Type']"));

        checkCheckbox(Locator.checkboxByNameAndValue("activeModules", "Passport"));
        assertChecked(Locator.checkboxByNameAndValue("activeModules", "Passport"));
        assertChecked(Locator.checkboxByNameAndValue("activeModules", "Pipeline"));
        assertChecked(Locator.checkboxByNameAndValue("activeModules", "Query"));
        assertChecked(Locator.checkboxByNameAndValue("activeModules", "TargetedMS"));

        clickButton("Update Folder");
        goToProjectHome();
        PortalHelper ph = new PortalHelper(this);
        ph.removeWebPart("Messages");
        ph.removeWebPart("Wiki");
        ph.removeWebPart("Pages");
        ph.addWebPart("Passport");
        ph.enterAdminMode();
        ph.addTab("Pipeline");
        ph.addWebPart("Pipeline Files");
        ph.renameTab("Start Page", "Passport");
        importData("data1.sky.zip", 1, true);
        importData("data2.sky.zip", 2, true);
        ph.hideTab("Pipeline");
        ph.exitAdminMode();
    }

    @LogMethod(category = LogMethod.MethodType.SETUP)
    protected void setupUserAccounts(String user1, String user1Pass)
    {
        log("Setting up test user accounts");
        goToSiteUsers();
        clickButton("Add Users");
        WebElement userInput= getDriver().findElement(By.xpath("//textarea[contains(@id, 'newUsers')]"));
        userInput.clear();
        userInput.sendKeys(user1);
        userInput.sendKeys(Keys.RETURN);
        clickButton("Add Users");
        goToSiteUsers();
        setUserPassword(user1, user1Pass);
        assertTextPresent(user1);
        log("Test user accounts successfully set up");
        //creates user accounts and checks to make sure they appear in Site Users
    }

    protected void setUserPassword(String user, String password)
    {
        String base = getBaseURL();
        getDriver().get(base+"/security/showResetEmail.view?email=" + user);
        click(Locator.xpath("//a[position()=1]"));
        WebElement passwordbox= getDriver().findElement(By.xpath("//input[contains(@id, 'password')]"));
        passwordbox.clear();
        passwordbox.sendKeys(password);
        WebElement passwordbox2= getDriver().findElement(By.xpath("//input[contains(@id, 'password2')]"));
        passwordbox2.clear();
        passwordbox2.sendKeys(password);
        clickButton("Set Password");
        goToSiteUsers();
    }

    @LogMethod
    protected void importData(@LoggedParam String file, int jobCount, boolean failOnError)
    {
        Locator.XPathLocator importButtonLoc = Locator.lkButton("Process and Import Data");
        WebElement importButton = importButtonLoc.findElementOrNull(getDriver());
        if (null == importButton)
        {
            goToModule("Pipeline");
            importButton = importButtonLoc.findElement(getDriver());
        }
        clickAndWait(importButton);
        _fileBrowserHelper.waitForFileGridReady();
        String fileName = Paths.get(file).getFileName().toString();
        if (!isElementPresent(FileBrowserHelper.Locators.gridRow(fileName)))
            _fileBrowserHelper.uploadFile(TestFileUtils.getSampleData("Passport/" + file));
        _fileBrowserHelper.importFile(fileName, "Import Skyline Results");
        waitForText("Skyline document import");
        if (failOnError)
            waitForPipelineJobsToComplete(jobCount, file, false);
        else
            waitForPipelineJobsToFinish(jobCount);
    }

    public void deleteProject(String project, boolean failIfFail) throws TestTimeoutException
    {
        _containerHelper.deleteProject(project, failIfFail, 120000); // Wait 2 minutes for project deletion
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        deleteProject(getProjectName(), afterTest);
        try
        {
            _userHelper.deleteUser(users[0]);
        } catch(Throwable t) { showMessage(t);}
        try
        {
            _userHelper.deleteUser(users[1]);
        } catch(Throwable t) { showMessage(t);}
    }



    private void showMessage(Throwable t){
        log("User does not exist");
    }


    @Override
    public BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }
}

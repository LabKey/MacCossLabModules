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
package org.labkey.test.tests;

import org.jetbrains.annotations.Nullable;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.util.LogMethod;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.labkey.test.util.PortalHelper;
import org.openqa.selenium.remote.server.handler.ExecuteScript;

import java.io.File;
import java.util.concurrent.TimeUnit;


public abstract class ToolStoreTestPart extends BaseWebDriverTest
{
    //The 2 users that are created and used in this test
    public String[] users = {"toolowner1@gmail.com", "toolowner2@gmail.com"};
    public String[] passwords = {"123123", "456456"};
    //Zip files must be in the LabKey\trunk\sampledata\ToolStore directory
    public String[] filesToUpload = {"user1-v1.zip","user2-v1.zip"};
    //File urls



    @Nullable
    @Override
    protected String getProjectName()
    {
        return "ToolStoreTest";
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
        if (type.equals(users[1]))
        {
            attemptSignIn(users[1], passwords[1]);
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
       _containerHelper.createProject(getProjectName(), "Collaboration");
        goToFolderManagement();
        click(Locator.xpath("//a[text()='Folder Type']"));
        checkCheckbox(Locator.xpath("//input[contains(@value, 'SkylineToolsStore')]"));
        clickButton("Update Folder");
        goToProjectHome();
        PortalHelper ph = new PortalHelper(this);
        ph.addWebPart("Skyline Tool Store");
    }
    @LogMethod(category = LogMethod.MethodType.SETUP)
    protected void setupUserAccounts(String user1, String user2, String user1Pass, String user2Pass)
    {
        log("Setting up test user accounts");
        goToSiteUsers();
        clickButton("Add Users");
        WebElement userInput= getDriver().findElement(By.xpath("//textarea[contains(@id, 'newUsers')]"));
        userInput.clear();
        userInput.sendKeys(user1);
        userInput.sendKeys(Keys.RETURN);
        userInput.sendKeys(user2);
        clickButton("Add Users");
        goToSiteUsers();
        setUserPassword(user1, user1Pass);
        setUserPassword(user2, user2Pass);
        assertTextPresent(user1);
        assertTextPresent(user2);
        log("Test user accounts successfully set up");
        //creates user accounts and checks to make sure they appear in Site Users
    }

    @LogMethod(category = LogMethod.MethodType.SETUP)
    protected void uploadTools(String fileName)
    {
       log("Installing " + fileName);
       //clickAndWait(Locator.linkContainingText("LabKey Server"));
        String base = getBaseURL();
        getDriver().get(base+"/skylinetoolsstore/ToolStoreTest/insert.view?");
        getDriver().findElement(By.name("toolZip")).sendKeys(getToolPath(fileName));
        click(Locator.xpath("//input[contains(@value, 'Upload Tool')]"));
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

    protected void setupUserPermissions(String user, String toolName, String permissionString, String locationToSetPermissions)
    {
        if(locationToSetPermissions.equals("panoramaPermissions"))
        {
        goToFolderManagement();
        clickFolder(toolName);
        setUserPermissions(user, permissionString);
        }
//        if(locationToSetPermissions.equals("toolStorePermissions"))
//        {
//            goToHome();
//            clickProject(getProjectName());
//            if(user.equals(users[0]))
//            {
//                click(Locator.xpath("//div[contains(@class, 'menuMouseArea sprocket Population Variation Updated')]"));
//                click(Locator.linkContainingText("Manage tool owners"));
//               // click(Locator.xpath("//div[contains(@class, 'menuMouseArea sprocket Population Variation Updated')]/ul/li/a[text()='Manage tool owners']"));
//                WebElement we= getDriver().findElement(By.xpath("//input[contains(@id, 'toolOwnersManage')]"));
//                we.clear();
//                we.sendKeys(users[0]);
//                click(Locator.xpath("//input[contains(@value, 'Update Tool Owners')]"));
//            }
//            if(user.equals(users[1]))
//            {
//                click(Locator.xpath("//div[contains(@class, 'menuMouseArea sprocket MSstats')]"));
//                click(Locator.linkContainingText("Manage tool owners"));
//                //click(Locator.xpath("//div[contains(@class, 'menuMouseArea sprocket Population Variation Updated')]/ul/li/a[text()='Manage tool owners']"));
//                WebElement we= getDriver().findElement(By.xpath("//input[contains(@id, 'toolOwnersManage')]"));
//                we.clear();
//                we.sendKeys(users[1]);
//                click(Locator.xpath("//input[contains(@value, 'Update Tool Owners')]"));
//            }
//
//        }
        //Unable to get this to work with new JQuery UI
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
            deleteGroup("SkylineToolStoreGroup");
        } catch(Throwable t) { showMessage(t);}

        //Deletes users created by this test if they exist
        try
        {
            deleteUser(users[0]);
        } catch(Throwable t) { showMessage(t);}
        try
        {
            deleteUser(users[1]);
        } catch(Throwable t) { showMessage(t);}
        //Deletes tools if at beginning and end of test
//        clickAndWait(Locator.linkContainingText("LabKey Server"));
//        goToModule("SkylineToolStore");
//        while(true)
//        {
//            try
//            {
//               click(Locator.xpath("//div[contains(@class, 'menuMouseArea')]"));
//                click(Locator.xpath("//a[text()='Delete']"));
//                click(Locator.xpath("//span[text()='Ok']"));
//                Thread.sleep(10000) ;
//                refresh(200);
//            }  catch(Throwable a){break;}
//        }

    }

    protected void initialVerification(String user)
    {
        String noPermissionsTool = null, ownsTool = null;


            if(user.equals(users[0]))
            {
            noPermissionsTool = "MSstats";
            ownsTool = "Population Variation Updated";
            } else if(user.equals(users[1]))
            {
            noPermissionsTool = "Population Variation Updated";
            ownsTool = "MSstats";
            }

        assertElementNotPresent(Locator.xpath("//div[contains(@class, 'menuMouseArea sprocket') and contains(@alt, '"+noPermissionsTool+"')]"));
        assertElementPresent(Locator.xpath("//div[contains(@class, 'menuMouseArea sprocket') and contains(@alt, '"+ownsTool+"')]"));
        assertElementPresent(Locator.xpath("//a[contains(@id, 'ui-id-3') and text()='Upload supplementary file']"));
        assertElementPresent(Locator.xpath("//a[contains(@id, 'ui-id-2') and text()='Upload new version']"));
        //Clicking elements in dropdown not supported with text()= which is why we need to use ui-id-x
        assertElementPresent(Locator.xpath("//button[text()='Download']"));
    }

    protected static String getPathToTools()
    {
        File path = new File(getSampledataPath(), File.separator + "ToolStore");
        return path.toString();
    }

    protected String getToolPath(String toolFile)
    {
        return getPathToTools() + File.separator + toolFile;
    }

    private void showMessage(Throwable t){
       log("User does not exist");
    }

    @Override
    public String getAssociatedModuleDirectory()
    {
        return "server/modules/skylinetoolsstore";
    }

    @Override
    public BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }


}

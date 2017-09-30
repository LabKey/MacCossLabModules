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


import org.junit.experimental.categories.Category;
import org.labkey.test.Locator;
import org.labkey.test.categories.CustomModules;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.UIContainerHelper;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

@Category({CustomModules.class})
public class ToolStoreRatingsTest  extends ToolStoreTestPart
{
    public ToolStoreRatingsTest()
    {
        // We want to use the UI when creating the project/folder so that we can verify that we get the wizard
        // that has the extra steps
        setContainerHelper(new UIContainerHelper(this));
    }

    @Override
    protected void doTestSteps() throws Exception
    {
        setupUserAccounts(users[0], users[1], passwords[0], passwords[1]);
        setupProject();
        //createPermissionsGroup("SkylineToolStoreGroup", users[0], users[1]);

        createPermissionsGroup("SkylineToolStoreGroup");
        clickManageGroup("SkylineToolStoreGroup");
        waitForElement(Locator.name("names"));
        setFormElement(Locator.name("names"), users[0] +"\n"+ users[1]);
        uncheckCheckbox(Locator.name("sendEmail"));
        clickButton("Update Group Membership");
        enterPermissionsUI();
        setUserPermissions("SkylineToolStoreGroup", "Editor");
        _securityHelper.setSiteGroupPermissions("All Site Users", "Reader");
        _securityHelper.setSiteGroupPermissions("Guests", "Reader");
        int fileNumber = 0;
        while(fileNumber < filesToUpload.length)
        {
            uploadTools(filesToUpload[fileNumber]);
            fileNumber++;
        }
        //toolName is the directory name in LabKey
        setupUserPermissions(users[0], "_tool_Population Variation Updated_1.2.16269", "Editor", "panoramaPermissions");
        setupUserPermissions(users[1], "_tool_MSstats_1.0", "Editor", "panoramaPermissions");
        testAsGuest();
        testAsUser1();
        testAsUser2();
        testAsAdmin();
    }

    @LogMethod(category = LogMethod.MethodType.VERIFICATION)
    protected void testAsGuest()
    {
        signOut();
        log("Testing as guest");
        clickProject(getProjectName());
        assertElementNotPresent(Locator.xpath("//button[text()='Leave the first Review!']"));
        click(Locator.linkContainingText("MSstats"));
        assertElementNotPresent(Locator.linkContainingText("Leave a review"));
        log("Guest test finished");
    }

    @LogMethod(category = LogMethod.MethodType.VERIFICATION)
    protected void testAsUser1()
    {
    log("Testing as User 1");
    loginAs(users[0]);
    clickProject(getProjectName());
    click(Locator.xpath("//a[text()='MSstats']"));
    waitForElement(Locator.xpath("//input[contains(@name, 'title')]"));
    waitForElement(Locator.xpath("//textarea[contains(@name, 'review')]"));
    WebElement titleBox = getDriver().findElement(By.xpath("//input[contains(@name, 'title')]"));
    titleBox.clear();
    titleBox.sendKeys("Great Tool!");
    WebElement reviewBox = getDriver().findElement(By.xpath("//textarea[contains(@name, 'review')]"));
    reviewBox.clear();
    reviewBox.sendKeys("Really really liked this.");
    click(Locator.xpath("//input[contains(@value, 'Submit Review')]"));
    assertTextPresent("Great Tool!");
    assertTextPresent("Really really liked this.");
    assertElementNotPresent(Locator.linkContainingText("Leave a review"));
    click(Locator.xpath("//button[normalize-space()='Edit Review']"));
    waitForElement(Locator.xpath("//textarea[contains(@name, 'review')]"));
    WebElement reviewBoxEdit = getDriver().findElement(By.xpath("//textarea[contains(@name, 'review')]"));
    reviewBoxEdit.clear();
    reviewBoxEdit.sendKeys("Did not really like this...");
    click(Locator.xpath("//input[contains(@value, 'Submit Review')]"));
    assertTextPresent("Did not really like this...");
    assertTextPresent("Great Tool!");
}

    @LogMethod(category = LogMethod.MethodType.VERIFICATION)
    protected void testAsUser2()
    {
        signOut();
        log("Testing as User 2");
        loginAs(users[1]);
        clickProject(getProjectName());
        click(Locator.linkContainingText("MSstats"));
        waitForElement(Locator.xpath("//input[contains(@name, 'title')]"));
        waitForElement(Locator.xpath("//textarea[contains(@name, 'review')]"));
        WebElement titleBox = getDriver().findElement(By.xpath("//input[contains(@name, 'title')]"));
        titleBox.clear();
        titleBox.sendKeys("Didn't like it.");
        WebElement reviewBox = getDriver().findElement(By.xpath("//textarea[contains(@name, 'review')]"));
        reviewBox.clear();
        reviewBox.sendKeys("I've seen better.");
        click(Locator.xpath("//input[contains(@value, 'Submit Review')]"));
        assertTextPresent("Didn't like it.");
        assertTextPresent("I've seen better.");

        clickProject(getProjectName());
        click(Locator.linkContainingText("Population Variation Updated"));
        waitForElement(Locator.xpath("//input[contains(@name, 'title')]"));
        waitForElement(Locator.xpath("//textarea[contains(@name, 'review')]"));
        WebElement titleBox2 = getDriver().findElement(By.xpath("//input[contains(@name, 'title')]"));
        titleBox2.clear();
        titleBox2.sendKeys("Didn't like it.");
        WebElement reviewBox2 = getDriver().findElement(By.xpath("//textarea[contains(@name, 'review')]"));
        reviewBox2.clear();
        reviewBox2.sendKeys("I've seen better.");
        click(Locator.xpath("//input[contains(@value, 'Submit Review')]"));
        assertTextPresent("Didn't like it.");
        assertTextPresent("I've seen better.");

    }

    @LogMethod(category = LogMethod.MethodType.VERIFICATION)
    protected void testAsAdmin()
    {
        signOut();
        log("Testing as an Admin");
        loginAs("siteAdmin");
        clickProject(getProjectName());
        click(Locator.linkContainingText("MSstats"));
        click(Locator.xpath("//button[normalize-space()='Delete Review']"));
        click(Locator.xpath("//span[text()='Ok']"));
        assertTextNotPresent("Didn't like it.");
        assertTextNotPresent("I've seen better.");
        clickProject(getProjectName());
        click(Locator.linkContainingText("MSstats"));
        click(Locator.xpath("//div[contains(@class, 'menuMouseArea sprocket')]"));
//        waitForElement(Locator.xpath("//a[contains(@id, 'ui-id-4')]"));
//        click(Locator.xpath("//a[contains(@id, 'ui-id-4')]"));
        waitForElement(Locator.xpath("//a[text()='Delete']"));
        click(Locator.xpath("//a[text()='Delete']"));
        waitForElement(Locator.xpath("//span[text()='Ok']"));
        clickButton("Ok");
        waitForText("Population Variation Updated");
        click(Locator.linkContainingText("Population Variation Updated"));
        click(Locator.xpath("//div[contains(@class, 'menuMouseArea sprocket')]"));
        waitForElement(Locator.xpath("//a[contains(@id, 'ui-id-2')]"));
        click(Locator.xpath("//a[contains(@id, 'ui-id-2')]"));
        click(Locator.xpath("//input[contains(@value, 'Upload Tool')]"));
        getDriver().findElement(By.name("toolZip")).sendKeys(getToolPath("user1-v2.zip"));
        click(Locator.xpath("//input[contains(@value, 'Upload Tool')]"));

        waitForElement(Locator.xpath("//input[contains(@name, 'title')]"));
        waitForElement(Locator.xpath("//textarea[contains(@name, 'review')]"));
        WebElement titleBox2 = getDriver().findElement(By.xpath("//input[contains(@name, 'title')]"));
        titleBox2.clear();
        titleBox2.sendKeys("Didn't like it.");
        WebElement reviewBox2 = getDriver().findElement(By.xpath("//textarea[contains(@name, 'review')]"));
        reviewBox2.clear();
        reviewBox2.sendKeys("I've seen better.");
        click(Locator.xpath("//input[contains(@value, 'Submit Review')]"));

        clickProject(getProjectName());
        click(Locator.linkContainingText("Population Variation Updated"));
        click(Locator.xpath("//div[contains(@class, 'menuMouseArea sprocket')]"));
        waitForElement(Locator.xpath("//a[text()='Delete']"));
        click(Locator.xpath("//a[text()='Delete']"));
        waitForElement(Locator.xpath("//span[text()='Ok']"));
        clickButton("Ok");
        log("Finished");
    }
}
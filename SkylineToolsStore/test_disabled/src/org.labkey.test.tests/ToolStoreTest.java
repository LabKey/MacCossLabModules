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

@Category({CustomModules.class})
public class ToolStoreTest  extends ToolStoreTestPart
{
    public ToolStoreTest()
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
        testAsSuperAdmin();
        testAsGuest();
        testAsUser1(0);
        testAsUser2();
    }

    //The following tests are specific to the files uploaded.
    @LogMethod(category = LogMethod.MethodType.VERIFICATION)
    protected void testAsSuperAdmin()
    {

    }

    @LogMethod(category = LogMethod.MethodType.VERIFICATION)
    protected void testAsGuest()
    {
        loginAs("guest");
        clickProject(getProjectName());
        log("Verifying guest can only download");
        assertTextPresent("MSstats");
        assertTextPresent("Population Variation Updated");
        assertElementPresent(Locator.xpath("//p[contains(@class, 'toolSubtitle')]"));
        assertElementPresent(Locator.xpath("//img[contains(@class, 'icon')]"));
        assertTextPresent("Vitek Lab, Purdue University");
        assertTextPresent("Version 1.0");
        assertElementPresent(Locator.xpath("//button[text()='Download']"));
        assertElementNotPresent(Locator.xpath("//button[text()='Upload New Version']"));
        assertElementNotPresent(Locator.xpath("//button[text()='Upload Supplementary File']"));
        assertElementNotPresent(Locator.xpath("//button[text()='Delete']"));
        assertElementNotPresent(Locator.xpath("//button[text()='Add New Tool']"));
        click(Locator.xpath("//img[contains(@alt, 'MSstats')]"));
        assertElementPresent(Locator.xpath("//span[contains(@id, 'downloadcounter') and text()='0']"));
        click(Locator.linkContainingText("Download MSstats"));
        refresh(200);
        assertElementPresent(Locator.xpath("//span[contains(@id, 'downloadcounter') and text()='1']"));
        assertElementPresent(Locator.xpath("//img[contains(@alt, 'MSstats')]"));
        assertTextPresent("Organization:");
        assertTextPresent("Authors:");
        assertTextPresent("More Information:");
        assertTextPresent("Meena Choi, Ching-Yun Chang, Dr. Timothy Clough, Dr. Olga Vitek");
        assertTextPresent("MSstats is an R package that provides tools for protein quantification in label-free and label-based LC-MS experiments and also SRM");
        assertElementNotPresent(Locator.xpath("//a[text()='Manage tool owners']"));
        assertElementNotPresent(Locator.xpath("//a[text()='Upload new version']"));
        assertElementNotPresent(Locator.xpath("//a[text()='Upload supplementary file']"));
        assertElementNotPresent(Locator.xpath("//a[text()='Delete tool']"));
        assertElementNotPresent(Locator.xpath("//div[contains(@class, 'menuMouseArea')]"));

    }

    @LogMethod(category = LogMethod.MethodType.VERIFICATION)
    protected void testAsUser1(int run)
    {
        if(run == 0)
        {
        loginAs(users[0]);
        }
        clickProject(getProjectName());
        log("Verifying " + users[0] + " details");
        initialVerification(users[0]);

        click(Locator.xpath("//img[contains(@alt, 'MSstats')]"));
        assertElementNotPresent(Locator.xpath("//div[contains(@class, 'menuMouseArea sprocket') and contains(@alt, 'MSstats')]"));
        clickProject(getProjectName());

        assertElementPresent(Locator.xpath("//div[contains(@class, 'menuMouseArea sprocket') and contains(@alt, 'Population Variation Updated')]"));
        click(Locator.xpath("//img[contains(@alt, 'Population Variation Updated')]"));
        assertElementPresent(Locator.xpath("//div[contains(@class, 'menuMouseArea sprocket')]"));

        assertElementPresent(Locator.xpath("//a[contains(@id, 'ui-id-2') and text()='Upload new version']"));
        assertElementPresent(Locator.xpath("//a[contains(@id, 'ui-id-3') and text()='Upload supplementary file']"));
        //Clicking elements in dropdown not supported with text()= which is why we need to use ui-id-x

        if(run==0)
        {
            assertElementNotPresent(Locator.xpath("//a[contains(@id, 'ui-id-4')]"));
            click(Locator.xpath("//div[contains(@class, 'menuMouseArea sprocket')]"));
            click(Locator.xpath("//a[contains(@id, 'ui-id-2')]"));
            //Clicking elements in dropdown not supported with text()= which is why we need to use ui-id-x
            click(Locator.xpath("//input[contains(@value, 'Upload Tool')]"));
            getDriver().findElement(By.name("toolZip")).sendKeys(getToolPath("user1-v2.zip"));
            click(Locator.xpath("//input[contains(@value, 'Upload Tool')]"));
        testAsUser1(1);
        }
        if(run==1)
        {
            assertTextPresent("View All");
            click(Locator.linkContainingText("View All"));
            assertTextPresent("Population Variation Updated (version 1.2.16269)");
            goBack();
            click(Locator.xpath("//img[contains(@alt, 'Population Variation Updated')]"));
            click(Locator.xpath("//div[contains(@class, 'menuMouseArea sprocket')]"));
            waitForElement(Locator.xpath("//a[contains(@id, 'ui-id-4')]"));
            click(Locator.xpath("//a[contains(@id, 'ui-id-4')]"));
            //Clicking elements in dropdown not supported with text()= which is why we need to use ui-id-x
            waitForElement(Locator.xpath("//span[text()='Ok']"));
            clickButton("Ok");
            assertElementNotPresent(Locator.xpath("//a[contains(@id, 'ui-id-4')]"));
            //Clicking elements in dropdown not supported with text()= which is why we need to use ui-id-x
            assertTextPresent("1.2.16269");

            click(Locator.xpath("//div[contains(@class, 'menuMouseArea sprocket')]"));
            waitForElement(Locator.xpath("//a[contains(@id, 'ui-id-2')]"));
            click(Locator.xpath("//a[contains(@id, 'ui-id-2')]"));
            //Clicking elements in dropdown not supported with text()= which is why we need to use ui-id-x
            click(Locator.xpath("//input[contains(@value, 'Upload Tool')]"));
            getDriver().findElement(By.name("toolZip")).sendKeys(getToolPath("user1-v2.zip"));
            click(Locator.xpath("//input[contains(@value, 'Upload Tool')]"));

            click(Locator.xpath("//div[contains(@class, 'menuMouseArea sprocket')]"));
            waitForElement(Locator.xpath("//a[contains(@id, 'ui-id-3')]"));
            click(Locator.xpath("//a[contains(@id, 'ui-id-3')]"));
            waitForElement(Locator.xpath("//input[contains(@name, 'suppFile')]"));
            waitForElement(Locator.xpath("//input[contains(@value, 'Upload Supplementary File')]"));
            //Clicking elements in dropdown not supported with text()= which is why we need to use ui-id-x
            getDriver().findElement(By.name("suppFile")).sendKeys(getToolPath("test.pdf"));
            click(Locator.xpath("//input[contains(@value, 'Upload Supplementary File')]"));
            assertTextPresent("test.pdf");
        }
    }

    @LogMethod(category = LogMethod.MethodType.VERIFICATION)
    protected void testAsUser2()
    {
        signOut();
        loginAs(users[1]);
        clickProject(getProjectName());
        log("Verifying " + users[1] + " details");
        initialVerification(users[1]);
    }
}

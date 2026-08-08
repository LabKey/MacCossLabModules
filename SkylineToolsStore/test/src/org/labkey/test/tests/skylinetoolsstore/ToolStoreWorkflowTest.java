/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.test.tests.skylinetoolsstore;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.util.APITestHelper;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.PermissionsHelper;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.PostgresOnlyTest;
import org.labkey.test.util.WikiHelper;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The real Skyline Tool Store workflow, end to end.
 *
 * Outside authors cannot upload to the store. They attach a zip to a message board post via a wiki
 * page, a site admin adds the tool naming the author as an owner, and the author can then maintain
 * their own tool without further admin help.
 */
@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 10)
public class ToolStoreWorkflowTest extends BaseWebDriverTest implements PostgresOnlyTest
{
    private static final String PROJECT_NAME = "ToolStoreWorkflowTest";
    private static final String OTHER_STORE = "ToolStoreWorkflowTestOtherStore";
    // Its own store so the tools this test adds cannot disturb the single-tool assertions elsewhere.
    private static final String FORMS_STORE = "ToolStoreWorkflowTestForms";

    private static final String FORMS_TOOL_NAME = "FormBindingProbe";
    private static final String FORMS_TOOL_IDENTIFIER = "URN:LSID:toolstore.test:formbinding";
    private static File _formsToolV1;
    private static File _formsToolV2;

    // Its own store, because this test deliberately fails an upload and then retries the same
    // version, which would disturb the single-tool assertions in the other stores.
    private static final String RETRY_STORE = "ToolStoreWorkflowTestFailedUpload";
    private static final String RETRY_TOOL_NAME = "PartialUploadProbe";
    private static final String RETRY_TOOL_IDENTIFIER = "URN:LSID:toolstore.test:partialupload";
    private static final String RETRY_TOOL_VERSION = "1.0";

    // Its own store, because it needs a tool with two versions and the other stores assert on one.
    private static final String OLDER_STORE = "ToolStoreWorkflowTestOlderVersion";
    private static final String OLDER_TOOL_NAME = "OlderVersionProbe";
    private static final String OLDER_TOOL_IDENTIFIER = "URN:LSID:toolstore.test:olderversion";

    // An ordinary site user. Gets Editor on their tool's folder only after the admin names them.
    private static final String TOOL_AUTHOR = "toolstore_author@toolstore.test";

    private static final String WIKI_NAME = "submit-a-tool";
    private static final String WIKI_TITLE = "Submit a Skyline Tool";
    private static final String SUBMISSION_TITLE = "Skyline Tool submission";

    private static final String TOOL_V1 = "skylinetoolsstore/user1-v1.zip";
    private static final String TOOL_V2 = "skylinetoolsstore/user1-v2.zip";
    private static final String TOOL_OTHER = "skylinetoolsstore/user2-v1.zip";
    private static final String SUPP_FILE = "skylinetoolsstore/test.pdf";

    private final ApiPermissionsHelper _permissionsHelper = new ApiPermissionsHelper(this);

    @BeforeClass
    public static void setupProject()
    {
        ToolStoreWorkflowTest init = getCurrentTest();
        init.doSetup();
    }

    @LogMethod
    private void doSetup()
    {
        _containerHelper.createProject(PROJECT_NAME, "Collaboration");
        _containerHelper.enableModule("SkylineToolsStore");

        PortalHelper portalHelper = new PortalHelper(this);
        portalHelper.addWebPart("Skyline Tool Store");
        portalHelper.addWebPart("Messages");
        portalHelper.addWebPart("Wiki");

        // Public store, and site users may post tool submissions to the message board. This mirrors
        // skyline.ms, where Message Board Contributor is granted to All Site Users.
        _permissionsHelper.setSiteGroupPermissions("Guests", "Reader");
        _permissionsHelper.setSiteGroupPermissions("All Site Users", "Reader");
        _permissionsHelper.setSiteGroupPermissions("All Site Users", "Message Board Contributor");

        _userHelper.createUser(TOOL_AUTHOR);

        _formsToolV1 = writeMinimalToolZip("1.0");
        _formsToolV2 = writeMinimalToolZip("2.0");

        ToolStoreTestHelper.removeToolsFromCatalog(PROJECT_NAME,
                TestFileUtils.getSampleData(TOOL_V1), TestFileUtils.getSampleData(TOOL_OTHER),
                _formsToolV1);

        // wikiVisualBody=false so the HTML goes in through the source tab. The visual editor is not
        // interactable for raw markup.
        new WikiHelper(this).createWikiPage(WIKI_NAME, "HTML", WIKI_TITLE,
                submissionFormHtml(), false, null, false);
    }

    /**
     * The submission form as it appears on skyline.ms - it posts to the announcements controller in
     * this folder, not to the tool store.
     */
    private String submissionFormHtml()
    {
        String action = WebTestHelper.buildRelativeUrl("announcements", PROJECT_NAME, "insert");
        return "<div id=\"requestUser\">\n" +
               "<form id=\"submit-tool-form\" action=\"" + action + "\" enctype=\"multipart/form-data\" method=\"post\">\n" +
               "<input type=\"hidden\" name=\"X-LABKEY-CSRF\" value=\"\" id=\"submit-tool-form-labkey-csrf\" />\n" +
               "<input value=\"" + SUBMISSION_TITLE + "\" name=\"title\" type=\"hidden\">\n" +
               "<input value=\"Hi! Here is my Skyline Tool.\" name=\"body\" type=\"hidden\">\n" +
               "<input value=\"RADEOX\" name=\"rendererType\" type=\"hidden\">\n" +
               "<input name=\"formFiles[00]\" type=\"file\"><br />\n" +
               "<input type=\"submit\" value=\"Submit\" name=\"Submit\">\n" +
               "</form>\n</div>\n" +
               "<script type=\"text/javascript\">\n" +
               "document.getElementById(\"submit-tool-form-labkey-csrf\").value = LABKEY.CSRF;\n" +
               "</script>";
    }

    /**
     * Submission through to self-service maintenance, in the order it happens in production.
     *
     * One method rather than several because each stage depends on the last, and JUnit does not
     * order test methods.
     */
    @Test
    public void testSubmitPublishAndMaintainATool()
    {
        log("The wiki page offers the submission form to a logged-in user");
        goToProjectHome(PROJECT_NAME);
        clickAndWait(Locator.linkWithText(WIKI_TITLE));
        assertElementPresent(Locator.id("submit-tool-form"));

        log("An ordinary site user submits a tool zip to the message board");
        impersonate(TOOL_AUTHOR);
        try
        {
            int status = submitToolToMessageBoard(TOOL_V1);
            assertTrue("Tool submission should be accepted, got HTTP " + status, status < 400);
        }
        finally
        {
            stopImpersonating();
        }

        log("The submission is visible on the message board with its attachment");
        goToProjectHome(PROJECT_NAME);
        assertTextPresent(SUBMISSION_TITLE);
        clickAndWait(Locator.linkWithText(SUBMISSION_TITLE));
        assertTextPresent("user1-v1.zip");

        log("The author cannot add the tool to the store themselves");
        Set<String> beforeAdminAdd = catalogIdentifiers();
        impersonate(TOOL_AUTHOR);
        try
        {
            uploadTool(TOOL_V1, null);
        }
        finally
        {
            stopImpersonating();
        }
        assertEquals("A submitter must not be able to add a tool to the store",
                beforeAdminAdd, catalogIdentifiers());

        log("A site admin adds the tool and names the author as an owner");
        uploadTool(TOOL_V1, TOOL_AUTHOR);
        JSONObject tool = onlyToolInThisStore();
        String identifier = tool.getString("Identifier");
        String v1Folder = toolFolderPath(tool);
        assertTrue("The author should own their tool's folder", hasEditorRole(v1Folder, TOOL_AUTHOR));

        log("The store lists the tool");
        goToProjectHome(PROJECT_NAME);
        assertTextPresent(tool.getString("Name"));

        log("The author can attach a supplementary file to their own tool");
        int v1RowId = rowId(tool);
        String v1Version = tool.getString("Version");
        impersonate(TOOL_AUTHOR);
        try
        {
            int status = uploadSupplementaryFile(v1Folder, v1RowId);
            assertTrue("Supplementary upload should be accepted, got HTTP " + status, status < 400);
        }
        finally
        {
            stopImpersonating();
        }
        goToProjectHome(PROJECT_NAME);
        assertTextPresent("test.pdf");

        // The supplementary file is attached BEFORE the new version is published on purpose. A new
        // version copies the previous version's supplementary files into its own folder, and that
        // copy loop only runs when the previous version has some.
        log("The author publishes a new version without admin help");
        impersonate(TOOL_AUTHOR);
        try
        {
            uploadNewVersion(v1Folder, TOOL_V2, v1RowId);
        }
        finally
        {
            stopImpersonating();
        }

        JSONObject latest = onlyToolInThisStore();
        assertEquals("Still the same tool", identifier, latest.getString("Identifier"));
        assertNotEquals("The author's upload should have become the latest version",
                v1Version, latest.getString("Version"));

        log("Ownership carries forward to the new version's folder");
        String v2Folder = toolFolderPath(latest);
        int v2RowId = rowId(latest);
        assertNotEquals(v1Folder, v2Folder);
        assertTrue("The author should own the new version too", hasEditorRole(v2Folder, TOOL_AUTHOR));

        log("The supplementary file carries forward to the new version");
        goToProjectHome(PROJECT_NAME);
        assertTextPresent("test.pdf");

        log("Owning one tool does not let the author add another, or reassign ownership");
        Set<String> beforeAuthorAttempts = catalogIdentifiers();
        impersonate(TOOL_AUTHOR);
        try
        {
            uploadTool(TOOL_OTHER, null);
            setOwners(v2RowId, PasswordUtilUsername());
        }
        finally
        {
            stopImpersonating();
        }
        assertEquals("A tool owner must not be able to add a different tool",
                beforeAuthorAttempts, catalogIdentifiers());
        assertTrue("Ownership must be unchanged", hasEditorRole(v2Folder, TOOL_AUTHOR));
    }

    /**
     * One store folder must not list another's tools. The JSON API stays global on purpose, because
     * Skyline asks a container that is not the store folder and only finds tools because of it.
     */
    @Test
    public void testListingIsScopedToThisStoreButApiIsNot()
    {
        _containerHelper.createProject(OTHER_STORE, "Collaboration");
        _containerHelper.enableModule(OTHER_STORE, "SkylineToolsStore");
        new PortalHelper(this).addWebPart("Skyline Tool Store");

        uploadToolTo(OTHER_STORE, TOOL_OTHER);

        JSONObject otherTool = onlyToolInStore(OTHER_STORE);
        String otherName = otherTool.getString("Name");

        goToProjectHome(OTHER_STORE);
        assertTextPresent(otherName);

        goToProjectHome(PROJECT_NAME);
        assertTextNotPresent(otherName);

        // The catalog is global, so the other store's tool is still there for Skyline.
        assertTrue("getToolsApi must keep returning tools from every container",
                catalogIdentifiers().contains(otherTool.getString("Identifier")));
    }

    /**
     * Drives the store's own dialogs instead of building the POST by hand.
     *
     * The other tests name every parameter themselves, so a field renamed in a JSP but not in its
     * form bean - or the reverse - cannot fail them. Submitting the real forms is the only way that
     * drift shows up, since a name the bean does not bind either silently stays at its default or
     * fails to convert.
     */
    @Test
    public void testStoreDialogsPostWhatTheActionsBind()
    {
        _containerHelper.createProject(FORMS_STORE, "Collaboration");
        _containerHelper.enableModule(FORMS_STORE, "SkylineToolsStore");
        new PortalHelper(this).addWebPart("Skyline Tool Store");

        log("Add a tool through the web part's Add New Tool dialog");
        goToProjectHome(FORMS_STORE);
        click(Locator.id("add-new-tool-btn"));
        setFormElement(Locator.css("#uploadPop input[name='toolZip']"), _formsToolV1);
        clickAndWait(Locator.css("#uploadPop input[type='submit']"));

        goToProjectHome(FORMS_STORE);
        assertTextPresent(FORMS_TOOL_NAME);
        assertEquals("The dialog should have added exactly one tool", 1, toolsInStore(FORMS_STORE));

        log("Publish a new version through the details page dialog");
        clickAndWait(Locator.linkWithText(FORMS_TOOL_NAME));
        clickSprocketMenuItem("Upload new version");
        setFormElement(Locator.css("#uploadPop input[name='toolZip']"), _formsToolV2);
        clickAndWait(Locator.css("#uploadPop input[type='submit']"));

        assertEquals("The dialog should have published 2.0",
                "2.0", onlyToolInStore(FORMS_STORE).getString("Version"));
        assertEquals("Publishing a version must not add a second tool", 1, toolsInStore(FORMS_STORE));

        log("Delete the newest version through the details page dialog");
        clickSprocketMenuItem("Delete latest version");
        // Scoped to this dialog's own wrapper. The page holds several jQuery UI dialogs and the
        // hidden ones have an Ok button too.
        Locator.XPathLocator ok = Locator.xpath(
                "//div[contains(@class,'ui-dialog')][.//div[@id='delToolLatestDlg']]" +
                "//div[contains(@class,'ui-dialog-buttonpane')]//button[normalize-space()='Ok']");
        waitForElement(ok.notHidden());
        clickAndWait(ok.notHidden());

        // Read the version from the catalog rather than the page - the details page carries script
        // constants that a bare text search for a version number picks up.
        assertEquals("Deleting the newest version should leave 1.0 as the latest",
                "1.0", onlyToolInStore(FORMS_STORE).getString("Version"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Opens the gear menu on the tool details page and clicks one of its items. */
    private void clickSprocketMenuItem(String item)
    {
        click(Locator.css(".menuMouseArea.sprocket"));

        // The menu slides open, so the item is in the DOM before it is visible, and once a tool has
        // more than one version the menu is long enough to run past the bottom of the window.
        Locator.XPathLocator link = Locator.linkWithText(item);
        waitForElement(link.notHidden());
        scrollIntoView(link.notHidden());
        click(link.notHidden());
    }

    /**
     * An older version's page must not offer to publish a new version. UpdateToolAction refuses
     * anything but the latest, so offering it there spent the owner's whole upload before saying so.
     *
     * Supplementary files are per version and the action accepts them on any version, so that item
     * has to stay. Checked here as well, to keep a later change from hiding the whole menu.
     */
    @Test
    public void testAnOlderVersionPageOffersOnlyWhatItCanDo()
    {
        _containerHelper.createProject(OLDER_STORE, "Collaboration");
        _containerHelper.enableModule(OLDER_STORE, "SkylineToolsStore");
        new PortalHelper(this).addWebPart("Skyline Tool Store");

        uploadToolFileTo(OLDER_STORE, ToolStoreTestHelper.writeMinimalToolZip(
                OLDER_TOOL_NAME, OLDER_TOOL_IDENTIFIER, "1.0"));
        int v1RowId = rowId(onlyToolInStore(OLDER_STORE));
        String v1Folder = "/" + OLDER_STORE + "/" +
                ToolStoreTestHelper.toolFolderName(OLDER_TOOL_NAME, "1.0");
        uploadToolFileTo(v1Folder, ToolStoreTestHelper.writeMinimalToolZip(
                OLDER_TOOL_NAME, OLDER_TOOL_IDENTIFIER, "2.0"), v1RowId);

        log("The latest version's page offers both items");
        beginAt(WebTestHelper.buildURL("skyts", OLDER_STORE, "details",
                Map.of("name", OLDER_TOOL_NAME)));
        assertTrue("The latest version should still offer Upload new version",
                sprocketHasItem("Upload new version"));

        log("The older version's page offers only the supplementary file item");
        beginAt(WebTestHelper.buildURL("skyts", OLDER_STORE, "details",
                Map.of("name", OLDER_TOOL_NAME, "version", "1.0")));
        assertFalse("Upload new version must not be offered where the action would refuse it",
                sprocketHasItem("Upload new version"));
        assertTrue("Upload supplementary file works on any version and must stay",
                sprocketHasItem("Upload supplementary file"));
    }

    /** Whether the details page gear menu carries an item, without clicking it. */
    private boolean sprocketHasItem(String item)
    {
        // Gated in the JSP, so an item that does not apply is absent from the page rather than
        // hidden by CSS. A DOM check needs no hover and cannot race the menu animation.
        return Locator.tagWithClass("ul", "dropMenu").append(Locator.linkWithText(item))
                .existsIn(getDriver());
    }

    /** Number of tools the given store folder lists. */
    private int toolsInStore(String storeContainerPath)
    {
        return toolsInStoreJson(storeContainerPath).length();
    }

    /**
     * An upload that fails after the version folder has been created must not leave the folder
     * behind. makeContainer refuses a name that is already taken, so an orphan blocks that same
     * version from then on - the owner retries with a good zip and is told the tool already exists,
     * with nothing to say the folder is the reason.
     *
     * Asserts the effect rather than the status. A refusal here renders as an error view with
     * status 200, so a status alone proves nothing.
     */
    @Test
    public void testAnUploadThatFailsPartWayLeavesNoFolderBehind()
    {
        _containerHelper.createProject(RETRY_STORE, "Collaboration");
        _containerHelper.enableModule(RETRY_STORE, "SkylineToolsStore");
        new PortalHelper(this).addWebPart("Skyline Tool Store");

        // resetErrorMark is server wide, so consuming this upload's errors below consumes everything
        // logged since the last mark. checkErrors() runs after every test method, so nothing should
        // be pending here - assert that rather than let the reset hide an error silently.
        assertEquals("Server errors were already pending before this test",
                0, getServerErrorCount());

        // Its icon is not a decodable image, so storing the version throws in writeIconToFile, which
        // runs after the folder has been made.
        uploadToolFileTo(RETRY_STORE, ToolStoreTestHelper.writeToolZipWithUnreadableIcon(
                RETRY_TOOL_NAME, RETRY_TOOL_IDENTIFIER, RETRY_TOOL_VERSION));

        // The failure has to reach the log as a server error, because an exception escaping the
        // action is what skips the cleanup and strands the folder. Counted rather than named,
        // because one failure writes more than one ERROR line. If this zip ever starts being
        // refused gracefully, this fails rather than passing for the wrong reason - the test would
        // no longer be exercising a throw part way through storing the version.
        assertTrue("The upload was supposed to fail with a server error", getServerErrorCount() > 0);
        resetErrors();

        assertFalse("The upload was supposed to fail while storing the version",
                ToolStoreTestHelper.catalogIdentifiers(RETRY_STORE).contains(RETRY_TOOL_IDENTIFIER));

        // Same name and version, so it needs the folder name the failed upload already used.
        uploadToolFileTo(RETRY_STORE, ToolStoreTestHelper.writeMinimalToolZip(
                RETRY_TOOL_NAME, RETRY_TOOL_IDENTIFIER, RETRY_TOOL_VERSION));
        assertTrue("A failed upload left its folder behind, blocking this version for good",
                ToolStoreTestHelper.catalogIdentifiers(RETRY_STORE).contains(RETRY_TOOL_IDENTIFIER));
    }

    /** Like uploadToolTo, for a zip this test built rather than one from sample data. */
    private int uploadToolFileTo(String containerPath, File zip)
    {
        return uploadToolFileTo(containerPath, zip, -1);
    }

    /** @param updateTarget row id of the tool getting a new version, or -1 for a brand-new tool */
    private int uploadToolFileTo(String containerPath, File zip, int updateTarget)
    {
        boolean newVersion = updateTarget >= 0;
        HttpPost request = new HttpPost(
                WebTestHelper.buildURL("skyts", containerPath, newVersion ? "updateTool" : "insertTool"));
        MultipartEntityBuilder entity = MultipartEntityBuilder.create()
                .addBinaryBody("toolZip", zip, ContentType.create("application/zip"), zip.getName());
        if (newVersion)
            entity.addTextBody("toolId", String.valueOf(updateTarget));
        request.setEntity(entity.build());
        return execute(request);
    }

    /**
     * A tool zip holding nothing but tool-inf/info.properties. Name, Version and Identifier are the
     * only required properties, and the sample zips in this module are tens of megabytes, so this
     * test builds its own rather than adding more of those to the repository.
     */
    private static File writeMinimalToolZip(String version)
    {
        return ToolStoreTestHelper.writeMinimalToolZip(FORMS_TOOL_NAME, FORMS_TOOL_IDENTIFIER, version);
    }

    /** Posts a tool zip to the folder's message board, the way the wiki form does. */
    private int submitToolToMessageBoard(String sampleDataRelativePath)
    {
        File zip = TestFileUtils.getSampleData(sampleDataRelativePath);
        HttpPost request = new HttpPost(WebTestHelper.buildURL("announcements", PROJECT_NAME, "insert"));
        request.setEntity(MultipartEntityBuilder.create()
                .addTextBody("title", SUBMISSION_TITLE)
                .addTextBody("body", "Hi! Here is my Skyline Tool.")
                .addTextBody("rendererType", "RADEOX")
                .addBinaryBody("formFiles[00]", zip, ContentType.create("application/zip"), zip.getName())
                .build());
        return execute(request);
    }

    /** Adds a brand-new tool to this store folder. */
    private int uploadTool(String sampleDataRelativePath, String toolOwners)
    {
        return uploadToolTo(PROJECT_NAME, sampleDataRelativePath, -1, toolOwners);
    }

    /** Publishes a new version, which is addressed to the tool's own folder. */
    private int uploadNewVersion(String toolContainerPath, String sampleDataRelativePath, int toolId)
    {
        return uploadToolTo(toolContainerPath, sampleDataRelativePath, toolId, null);
    }

    private void uploadToolTo(String containerPath, String sampleDataRelativePath)
    {
        uploadToolTo(containerPath, sampleDataRelativePath, -1, null);
    }

    /**
     * @param containerPath  the store folder for a new tool, or the TOOL's own folder for a new
     *                       version - the two actions are addressed to different containers
     * @param updateTarget   row id of the tool being updated, or -1 for a brand-new tool
     */
    @LogMethod
    private int uploadToolTo(String containerPath, String sampleDataRelativePath, int updateTarget,
                             String toolOwners)
    {
        File zip = TestFileUtils.getSampleData(sampleDataRelativePath);
        boolean newVersion = updateTarget >= 0;
        HttpPost request = new HttpPost(
                WebTestHelper.buildURL("skyts", containerPath, newVersion ? "updateTool" : "insertTool"));
        MultipartEntityBuilder entity = MultipartEntityBuilder.create()
                .addBinaryBody("toolZip", zip, ContentType.create("application/zip"), zip.getName());
        if (newVersion)
            entity.addTextBody("toolId", String.valueOf(updateTarget));
        if (toolOwners != null)
            entity.addTextBody("toolOwners", toolOwners);
        request.setEntity(entity.build());
        return execute(request);
    }

    /**
     * insertSupplement is addressed to the tool's own container, so its permission annotation checks
     * the folder that holds the tool. The author holds Editor there and nothing on the store folder.
     */
    private int uploadSupplementaryFile(String toolContainerPath, int toolRowId)
    {
        File pdf = TestFileUtils.getSampleData(SUPP_FILE);
        HttpPost request = new HttpPost(WebTestHelper.buildURL("skyts", toolContainerPath, "insertSupplement"));
        request.setEntity(MultipartEntityBuilder.create()
                .addTextBody("toolId", String.valueOf(toolRowId))
                .addBinaryBody("suppFile", pdf, ContentType.create("application/pdf"), pdf.getName())
                .build());
        return execute(request);
    }

    private int setOwners(int toolRowId, String owner)
    {
        HttpPost request = new HttpPost(WebTestHelper.buildURL("skyts", PROJECT_NAME, "setOwners"));
        request.setEntity(MultipartEntityBuilder.create()
                .addTextBody("toolId", String.valueOf(toolRowId))
                .addTextBody("toolOwners", owner)
                .build());
        return execute(request);
    }

    /** Sends as the current (possibly impersonated) user, with a valid CSRF token. */
    private int execute(HttpPost request)
    {
        APITestHelper.injectCookies(request);
        try (CloseableHttpClient client = WebTestHelper.getHttpClient())
        {
            return client.execute(request, response -> {
                EntityUtils.consumeQuietly(response.getEntity());
                return response.getCode();
            });
        }
        catch (Exception e)
        {
            throw new RuntimeException("Request failed: " + request.getRequestUri(), e);
        }
    }

    private JSONArray toolsFromApi()
    {
        return ToolStoreTestHelper.toolsFromApi(PROJECT_NAME);
    }

    private Set<String> catalogIdentifiers()
    {
        return ToolStoreTestHelper.catalogIdentifiers(PROJECT_NAME);
    }

    private JSONObject onlyToolInThisStore()
    {
        return onlyToolInStore(PROJECT_NAME);
    }

    /** Every tool whose folder sits under the given store, read from the global catalog. */
    private JSONArray toolsInStoreJson(String containerPath)
    {
        JSONArray tools = toolsFromApi();
        JSONArray inStore = new JSONArray();
        for (int i = 0; i < tools.length(); i++)
        {
            JSONObject tool = tools.getJSONObject(i);
            if (tool.optString("IconUrl").contains("/" + containerPath + "/") ||
                tool.optString("DownloadUrl").contains("/" + containerPath + "/"))
                inStore.put(tool);
        }
        return inStore;
    }

    /** The single tool whose folder sits under the given store, read from the global catalog. */
    private JSONObject onlyToolInStore(String containerPath)
    {
        JSONArray tools = toolsFromApi();
        JSONObject found = null;
        for (int i = 0; i < tools.length(); i++)
        {
            JSONObject tool = tools.getJSONObject(i);
            if (tool.optString("IconUrl").contains("/" + containerPath + "/") ||
                tool.optString("DownloadUrl").contains("/" + containerPath + "/"))
            {
                assertTrue("Expected one tool in " + containerPath, found == null);
                found = tool;
            }
        }
        assertTrue("No tool found in " + containerPath, found != null);
        return found;
    }

    private int rowId(JSONObject tool)
    {
        return ToolStoreTestHelper.rowId(tool);
    }

    private String toolFolderPath(JSONObject tool)
    {
        return "/" + PROJECT_NAME + "/" +
                ToolStoreTestHelper.toolFolderName(tool.getString("Name"), tool.getString("Version"));
    }

    private boolean hasEditorRole(String containerPath, String user)
    {
        return _permissionsHelper.getUserRoles(containerPath, user).stream()
                .anyMatch(role -> role.endsWith("EditorRole"));
    }

    private String PasswordUtilUsername()
    {
        return org.labkey.test.util.PasswordUtil.getUsername();
    }

    @Override
    protected void doCleanup(boolean afterTest)
    {
        _containerHelper.deleteProject(PROJECT_NAME, afterTest);
        _containerHelper.deleteProject(OTHER_STORE, false);
        _containerHelper.deleteProject(FORMS_STORE, false);
        _containerHelper.deleteProject(RETRY_STORE, false);
        _containerHelper.deleteProject(OLDER_STORE, false);
        _userHelper.deleteUsers(false, TOOL_AUTHOR);
    }

    @Override
    protected String getProjectName()
    {
        return PROJECT_NAME;
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return List.of("SkylineToolsStore");
    }
}

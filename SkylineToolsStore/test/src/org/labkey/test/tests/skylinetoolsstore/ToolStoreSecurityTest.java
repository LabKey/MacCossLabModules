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
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
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

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Authorization tests for the Skyline Tool Store.
 * <p>
 * These use raw HTTP rather than the browser because the UI hides the controls these actions expose.
 * A browser-driven test would pass while the endpoint stayed open.
 */
@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 3)
public class ToolStoreSecurityTest extends BaseWebDriverTest implements PostgresOnlyTest
{
    private static final String PROJECT_NAME = "ToolStoreSecurityTest";

    // Hold no role anywhere, so any role they end up with was granted by an exploit. One per test
    // because a successful exploit changes the folder policy permanently, and these methods do not
    // run in source order.
    private static final String ATTACKER_ANON = "toolstore_attacker_anon@toolstore.test";
    private static final String ATTACKER_USER = "toolstore_attacker_user@toolstore.test";

    // Never signs in, so a client built for this user sends no session and no CSRF cookie.
    private static final String NO_SESSION_USER = "toolstore_nosession@toolstore.test";

    // Holds Editor on the store folder, so it has InsertPermission there but is not a site admin.
    // This is the account that distinguishes the site-admin rule from the old InsertPermission one.
    private static final String CONTRIBUTOR = "toolstore_contributor@toolstore.test";

    // Two versions of one tool: same identifier, different version.
    private static final String TOOL_V1 = "skylinetoolsstore/user1-v1.zip";
    private static final String TOOL_V2 = "skylinetoolsstore/user1-v2.zip";
    // A different tool, so uploading it would add a new identifier to the catalog.
    private static final String TOOL_OTHER = "skylinetoolsstore/user2-v1.zip";

    private static int _toolV1RowId = -1;
    private static String _toolV1FolderPath;

    // getToolsApi returns tools from every container, so the catalog also carries unrelated tools
    // that exist on the server. Everything here is keyed off our own tool's LSID.
    private static String _toolIdentifier;

    private final ApiPermissionsHelper _permissionsHelper = new ApiPermissionsHelper(this);

    @BeforeClass
    public static void setupProject()
    {
        ToolStoreSecurityTest init = getCurrentTest();
        init.doSetup();
    }

    @LogMethod
    private void doSetup()
    {
        _containerHelper.createProject(PROJECT_NAME, null);
        _containerHelper.enableModule("SkylineToolsStore");
        new PortalHelper(this).addWebPart("Skyline Tool Store");

        _userHelper.createUser(ATTACKER_ANON);
        _userHelper.createUser(ATTACKER_USER);
        _userHelper.createUser(CONTRIBUTOR);
        _permissionsHelper.addMemberToRole(CONTRIBUTOR, "Editor", PermissionsHelper.MemberType.user,
                "/" + PROJECT_NAME);

        // Tool identifiers are server-global and the test classes here share sample data, so clear
        // any tool left behind by another class or an earlier run.
        ToolStoreTestHelper.removeToolsFromCatalog(PROJECT_NAME,
                TestFileUtils.getSampleData(TOOL_V1), TestFileUtils.getSampleData(TOOL_OTHER));

        // InsertAction renders its failures into the JSP and still returns 200, so a new identifier
        // appearing in the catalog is the only reliable proof that the upload worked.
        Set<String> before = catalogIdentifiers();
        uploadTool(TOOL_V1);
        Set<String> added = catalogIdentifiers();
        added.removeAll(before);
        assertEquals("Upload should have added exactly one tool identifier, got " + added,
                1, added.size());
        _toolIdentifier = added.iterator().next();

        // The module registers no UserSchema, so the query API cannot read SkylineTool. The row id
        // comes from the DownloadUrl in the public JSON API instead.
        JSONObject tool = getOurTool();
        _toolV1RowId = extractRowIdFromDownloadUrl(tool.getString("DownloadUrl"));
        _toolV1FolderPath = toolFolderPath(tool.getString("Name"), tool.getString("Version"));

        // A wrong folder path would make the security assertions pass vacuously.
        assertTrue("Expected the upload to create tool folder " + _toolV1FolderPath,
                _containerHelper.doesContainerExist(_toolV1FolderPath));
        for (String attacker : List.of(ATTACKER_ANON, ATTACKER_USER))
            assertFalse(attacker + " must start with no role on the tool folder",
                    hasEditorRole(_toolV1FolderPath, attacker));
    }

    // -------------------------------------------------------------------------
    // STS-8 - SetOwnersAction has no permission check at all
    // -------------------------------------------------------------------------

    /**
     * setOwners must reject a request carrying no session and no CSRF token.
     */
    @Test
    public void testSetOwnersRejectsAnonymousRequest()
    {
        String folder = currentFolderPath();
        int status = post("setOwners", ownerParams(ATTACKER_ANON), false, false);

        // Assert the effect rather than the status. A rejection may surface as 401, a login redirect
        // or a basic-auth challenge; what matters is that the policy did not change.
        assertFalse("SECURITY: an unauthenticated setOwners request granted " + ATTACKER_ANON +
                        " the Editor role on " + folder + " (HTTP " + status + ")",
                hasEditorRole(folder, ATTACKER_ANON));
    }

    /**
     * setOwners must also reject a signed-in non-admin. Sent with a valid CSRF token so only the
     * authorization check can reject it, since a fix that merely rejects guests would leave this open.
     * <p>
     * Impersonation reuses the admin's session with the target user's permissions, so the CSRF cookie
     * and header still match while the server sees ATTACKER_USER.
     */
    @Test
    public void testSetOwnersRejectsNonAdminUser()
    {
        String folder = currentFolderPath();
        List<NameValuePair> params = ownerParams(ATTACKER_USER);
        int status;
        impersonate(ATTACKER_USER);
        try
        {
            status = post("setOwners", params, true, true);
        }
        finally
        {
            stopImpersonating();
        }

        assertFalse("SECURITY: a non-admin setOwners request granted " + ATTACKER_USER +
                        " the Editor role on " + folder + " (HTTP " + status + ")",
                hasEditorRole(folder, ATTACKER_USER));
    }

    private List<NameValuePair> ownerParams(String newOwner)
    {
        return List.of(new BasicNameValuePair("toolId", String.valueOf(currentRowId())),
                new BasicNameValuePair("toolOwners", newOwner));
    }

    // Resolved per call, not cached: testZDeleteLatest uploads a second version, which changes both
    // the row id and the folder, and these methods do not run in source order.
    private int currentRowId()
    {
        return extractRowIdFromDownloadUrl(getOurTool().getString("DownloadUrl"));
    }

    private String currentFolderPath()
    {
        JSONObject tool = getOurTool();
        return toolFolderPath(tool.getString("Name"), tool.getString("Version"));
    }

    // -------------------------------------------------------------------------
    // InsertAction - new tools are site admin only, matching the Add New Tool button
    // -------------------------------------------------------------------------

    /**
     * Adding a new tool is offered only to site admins in the web part, so the action must enforce
     * that too rather than settling for InsertPermission on the folder.
     * <p>
     * CONTRIBUTOR holds Editor here, so it passes the old InsertPermission check and is refused only
     * by the site-admin rule. TOOL_OTHER is a different tool, so a successful upload would show up as
     * a new identifier in the catalog.
     */
    @Test
    public void testInsertRejectsNonAdminNewTool()
    {
        Set<String> before = catalogIdentifiers();
        String reply;
        impersonate(CONTRIBUTOR);
        try
        {
            reply = uploadToolExpectingRefusal(TOOL_OTHER);
        }
        finally
        {
            stopImpersonating();
        }

        assertEquals("SECURITY: a folder Editor who is not a site admin added a tool to the store",
                before, catalogIdentifiers());
        assertTrue("Expected the site-admin refusal, got: " + StringUtils.abbreviate(reply, 300),
                reply.contains(NO_INSERT_PERMISSIONS_MESSAGE));
    }

    /**
     * The refusal must come before the owner list is resolved. Otherwise the "unknown users" reply
     * tells any logged-in caller whether an email is a registered account.
     * <p>
     * The form echoes the submitted owners value back into its input, so the test looks for the
     * enumeration message rather than for the address itself.
     */
    @Test
    public void testInsertDoesNotRevealWhetherAccountsExist()
    {
        String unknown = "definitely_not_a_user_" + System.nanoTime() + "@toolstore.test";

        String reply;
        impersonate(ATTACKER_USER);
        try
        {
            reply = postOwnersOnly(unknown);
        }
        finally
        {
            stopImpersonating();
        }

        assertFalse("SECURITY: the reply reported which accounts are unknown, which lets any " +
                        "logged-in user test whether an address is registered",
                reply.contains(UNKNOWN_USERS_MESSAGE));
        assertTrue("A non-admin should be refused before the owner list is read",
                reply.contains(NO_INSERT_PERMISSIONS_MESSAGE));
    }

    // Copies of the InsertAction messages. The action keeps them private.
    private static final String UNKNOWN_USERS_MESSAGE = "The following users are unknown";
    private static final String NO_INSERT_PERMISSIONS_MESSAGE =
            "You do not have permission to add a new Skyline Tool.";

    // -------------------------------------------------------------------------
    // STS-9 - CSRF validation is skipped on the mutating actions
    // -------------------------------------------------------------------------

    /**
     * A session cookie without the CSRF header is what a cross-site form post looks like. CSRF is a
     * double submit, so the token must arrive as a parameter or header and match the cookie.
     */
    @Test
    public void testUpdatePropertyRequiresCsrfToken()
    {
        String originalDescription = getOurTool().getString("Description");
        String forged = "forged-by-csrf-" + System.nanoTime();

        int status = postTo(currentFolderPath(), "updateProperty", descriptionParams(forged), true, false);

        assertEquals("SECURITY: updateProperty accepted a POST with no CSRF token (HTTP " + status + ")",
                originalDescription, getOurTool().getString("Description"));
    }

    /**
     * Positive control: with a valid token the same edit must still work, so CSRF enforcement cannot
     * be satisfied by breaking the feature. Also guards SkylineToolDetails.jsp, which posts here
     * through raw jQuery and must attach the token itself.
     */
    @Test
    public void testUpdatePropertySucceedsWithCsrfToken()
    {
        String newDescription = "edited-with-token-" + System.nanoTime();

        int status = postTo(currentFolderPath(), "updateProperty", descriptionParams(newDescription), true, true);

        assertTrue("An authenticated updateProperty with a CSRF token should succeed, got HTTP " + status,
                status < 400);
        assertEquals("Description should have been updated",
                newDescription, getOurTool().getString("Description"));
    }

    private List<NameValuePair> descriptionParams(String description)
    {
        return List.of(new BasicNameValuePair("toolId", String.valueOf(currentRowId())),
                new BasicNameValuePair("propName", "Description"),
                new BasicNameValuePair("propValue", description));
    }

    // -------------------------------------------------------------------------
    // Error hygiene - a bad request must not surface as a server fault
    // -------------------------------------------------------------------------

    /**
     * These actions used to answer a missing tool with a bare Exception or an IllegalStateException,
     * so an ordinary bad request was reported as a 500 and alerted to mothership.
     */
    @Test
    public void testMissingToolReturnsNotFoundNotServerError()
    {
        int absentId = 99999999;

        assertEquals("insertSupplement with an unknown tool id should be a 404",
                404, post("insertSupplement",
                        List.of(new BasicNameValuePair("toolId", String.valueOf(absentId))), true, true));

        assertEquals("deleteSupplement with an unknown tool id should be a 404",
                404, postTo(currentFolderPath(), "deleteSupplement",
                        List.of(new BasicNameValuePair("toolId", String.valueOf(absentId)),
                                new BasicNameValuePair("suppFile", "whatever.pdf")), true, true));

        assertEquals("updateProperty with an unknown tool id should be a 404",
                404, postTo(currentFolderPath(), "updateProperty",
                        List.of(new BasicNameValuePair("toolId", String.valueOf(absentId)),
                                new BasicNameValuePair("propName", "Description"),
                                new BasicNameValuePair("propValue", "x")), true, true));

        assertEquals("deleteLatest with an unknown tool id should be a 404",
                404, post("deleteLatest",
                        List.of(new BasicNameValuePair("toolId", String.valueOf(absentId))), true, true));
    }

    /**
     * Asking for a supplementary file that is not one must not be a server fault either.
     */
    @Test
    public void testDeletingSomethingThatIsNotASupplementaryFileIsNotFound()
    {
        assertEquals("Deleting a non-existent supplementary file should be a 404",
                404, postTo(currentFolderPath(), "deleteSupplement",
                        List.of(new BasicNameValuePair("toolId", String.valueOf(currentRowId())),
                                new BasicNameValuePair("suppFile", "no-such-file.pdf")), true, true));

        assertEquals("The tool's own icon is not a supplementary file",
                404, postTo(currentFolderPath(), "deleteSupplement",
                        List.of(new BasicNameValuePair("toolId", String.valueOf(currentRowId())),
                                new BasicNameValuePair("suppFile", "icon.png")), true, true));
    }

    // -------------------------------------------------------------------------
    // STS-10 - DeleteLatestAction deletes a container in response to a GET
    // -------------------------------------------------------------------------

    /**
     * deleteLatest deletes a container, so it must not be reachable by GET. A GET is forgeable with an
     * img tag and no CSRF token can protect it.
     * <p>
     * A GET here also trips SpringActionController.checkForMutatingSql on a dev server, which aborts
     * the request. Do not silence that with ignoreSqlUpdates() - correct for the download counter in
     * PR #608, but here it would hide the warning and leave the delete reachable by GET.
     */
    @Test
    public void testZDeleteLatestRejectsGetRequest()
    {
        String v1Version = getOurTool().getString("Version");
        uploadTool(TOOL_V2, _toolV1RowId);

        JSONObject latest = getOurTool();
        // The upload returns 200 even on failure, so confirm the version actually moved.
        assertNotEquals("The second upload should have become the latest version",
                v1Version, latest.getString("Version"));
        int latestRowId = extractRowIdFromDownloadUrl(latest.getString("DownloadUrl"));
        String latestFolderPath = toolFolderPath(latest.getString("Name"), latest.getString("Version"));

        assertTrue("The second version folder should exist before the GET",
                _containerHelper.doesContainerExist(latestFolderPath));

        String url = WebTestHelper.buildURL("skyts", PROJECT_NAME, "deleteLatest") + "?id=" + latestRowId;
        int status = execute(new HttpGet(url), true, true);

        assertTrue("SECURITY: a GET to deleteLatest deleted tool folder " + latestFolderPath +
                        " (HTTP " + status + ")",
                _containerHelper.doesContainerExist(latestFolderPath));
        assertTrue("The original version must also survive",
                _containerHelper.doesContainerExist(_toolV1FolderPath));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Uploads a brand-new tool through InsertAction as the site admin.
     */
    private void uploadTool(String sampleDataRelativePath)
    {
        uploadTool(sampleDataRelativePath, -1);
    }

    /**
     * Uploads a tool zip through InsertAction as the site admin, using a multipart POST. The status
     * code proves little, since InsertAction returns 200 on failure, so callers verify the effect.
     *
     * @param updateTarget row id of the tool being updated, or -1 for a brand-new tool. Without it
     *                     the new-tool path rejects a zip whose identifier already exists.
     */
    @LogMethod
    private void uploadTool(String sampleDataRelativePath, int updateTarget)
    {
        File zip = TestFileUtils.getSampleData(sampleDataRelativePath);
        HttpPost request = new HttpPost(WebTestHelper.buildURL("skyts", PROJECT_NAME, "insert"));
        MultipartEntityBuilder entity = MultipartEntityBuilder.create()
                .addBinaryBody("toolZip", zip, ContentType.create("application/zip"), zip.getName());
        if (updateTarget >= 0)
            entity.addTextBody("toolId", String.valueOf(updateTarget));
        request.setEntity(entity.build());

        int status = execute(request, true, true);
        assertTrue("Tool upload failed for " + zip.getName() + ", HTTP " + status, status < 400);
    }

    /**
     * Uploads as the current (possibly impersonated) user and returns the response body.
     */
    private String uploadToolExpectingRefusal(String sampleDataRelativePath)
    {
        File zip = TestFileUtils.getSampleData(sampleDataRelativePath);
        HttpPost request = new HttpPost(WebTestHelper.buildURL("skyts", PROJECT_NAME, "insert"));
        request.setEntity(MultipartEntityBuilder.create()
                .addBinaryBody("toolZip", zip, ContentType.create("application/zip"), zip.getName())
                .build());
        APITestHelper.injectCookies(request);
        try (CloseableHttpClient client = WebTestHelper.getHttpClient())
        {
            return client.execute(request, response -> EntityUtils.toString(response.getEntity()));
        }
        catch (Exception e)
        {
            throw new RuntimeException("Upload request failed", e);
        }
    }

    /**
     * Posts only a toolOwners value and returns the response body.
     */
    private String postOwnersOnly(String owner)
    {
        HttpPost request = new HttpPost(WebTestHelper.buildURL("skyts", PROJECT_NAME, "insert"));
        request.setEntity(MultipartEntityBuilder.create()
                .addTextBody("toolOwners", owner)
                .build());
        APITestHelper.injectCookies(request);
        try (CloseableHttpClient client = WebTestHelper.getHttpClient())
        {
            return client.execute(request, response -> EntityUtils.toString(response.getEntity()));
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to post toolOwners", e);
        }
    }

    private int post(String action, List<NameValuePair> params, boolean withSession, boolean withCsrfToken)
    {
        return postTo(PROJECT_NAME, action, params, withSession, withCsrfToken);
    }

    /**
     * Actions that operate on a single tool are addressed to the tool's OWN container, so their
     * permission annotation checks the folder that holds the tool. Use currentFolderPath() for those.
     */
    private int postTo(String containerPath, String action, List<NameValuePair> params,
                       boolean withSession, boolean withCsrfToken)
    {
        HttpPost request = new HttpPost(WebTestHelper.buildURL("skyts", containerPath, action));
        request.setEntity(new UrlEncodedFormEntity(new ArrayList<>(params)));
        return execute(request, withSession, withCsrfToken);
    }

    /**
     * @param withSession   send the site-admin session. False produces a fully anonymous request.
     * @param withCsrfToken send the X-LABKEY-CSRF header. The cookie alone is not enough, so false
     *                      simulates a cross-site post.
     */
    private int execute(HttpUriRequest request, boolean withSession, boolean withCsrfToken)
    {
        if (withSession && withCsrfToken)
            APITestHelper.injectCookies(request);
        else if (withSession)
            injectSessionOnly(request);

        try (CloseableHttpClient client = withSession
                ? WebTestHelper.getHttpClient()
                : WebTestHelper.getHttpClientBuilder(NO_SESSION_USER, "").build())
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

    /**
     * Sends the session but not the CSRF header. injectCookies sets both, this sets only the one.
     */
    private void injectSessionOnly(HttpUriRequest request)
    {
        org.openqa.selenium.Cookie session =
                WebTestHelper.getCookies(org.labkey.test.util.PasswordUtil.getUsername())
                        .get(org.labkey.remoteapi.Connection.JSESSIONID);
        assertTrue("No saved session for the primary test user", session != null);
        request.setHeader(session.getName(), session.getValue());
    }

    /**
     * Reads the public JSON API. Returns the latest version of every tool in the store.
     */
    private JSONArray getToolsFromApi()
    {
        String url = WebTestHelper.buildURL("skyts", PROJECT_NAME, "getToolsApi");
        try (CloseableHttpClient client = WebTestHelper.getHttpClient())
        {
            HttpGet request = new HttpGet(url);
            APITestHelper.injectCookies(request);
            String body = client.execute(request, response -> EntityUtils.toString(response.getEntity()))
                    .trim();
            // The response is empty for an empty store, a bare object for one tool, an array otherwise.
            if (body.isEmpty())
                return new JSONArray();
            return body.startsWith("[") ? new JSONArray(body)
                    : new JSONArray().put(new JSONObject(body));
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to read getToolsApi", e);
        }
    }

    /**
     * The latest version of the tool this test uploaded, located by its LSID.
     */
    private JSONObject getOurTool()
    {
        JSONArray tools = getToolsFromApi();
        for (int i = 0; i < tools.length(); i++)
        {
            JSONObject tool = tools.getJSONObject(i);
            if (_toolIdentifier.equals(tool.optString("Identifier")))
                return tool;
        }
        throw new AssertionError("Tool " + _toolIdentifier + " is not in the catalog");
    }

    private Set<String> catalogIdentifiers()
    {
        JSONArray tools = getToolsFromApi();
        Set<String> identifiers = new HashSet<>();
        for (int i = 0; i < tools.length(); i++)
            identifiers.add(tools.getJSONObject(i).optString("Identifier"));
        return identifiers;
    }

    /**
     * DownloadUrl looks like .../skyts-downloadTool.view?id=123
     */
    private int extractRowIdFromDownloadUrl(String downloadUrl)
    {
        int idx = downloadUrl.indexOf("id=");
        assertTrue("DownloadUrl should carry an id parameter: " + downloadUrl, idx >= 0);
        String tail = downloadUrl.substring(idx + 3);
        int amp = tail.indexOf('&');
        return Integer.parseInt(amp >= 0 ? tail.substring(0, amp) : tail);
    }

    // Mirrors the folder name InsertAction builds. The sample tool names contain no characters that
    // makeLegalName rewrites, so plain concatenation matches, and doSetup asserts the folder exists.
    private String toolFolderPath(String toolName, String version)
    {
        return "/" + PROJECT_NAME + "/_tool_" + toolName + "_" + version;
    }

    private boolean hasEditorRole(String containerPath, String user)
    {
        return _permissionsHelper.getUserRoles(containerPath, user).stream()
                .anyMatch(role -> role.endsWith("EditorRole"));
    }

    @Override
    protected void doCleanup(boolean afterTest)
    {
        _containerHelper.deleteProject(PROJECT_NAME, afterTest);
        _userHelper.deleteUsers(false, ATTACKER_ANON, ATTACKER_USER, CONTRIBUTOR);
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

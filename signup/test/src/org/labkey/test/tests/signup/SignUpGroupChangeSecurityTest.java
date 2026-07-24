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
package org.labkey.test.tests.signup;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandResponse;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.SimplePostCommand;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.util.APITestHelper;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.PermissionsHelper.MemberType;
import org.labkey.test.util.PermissionsHelper.PrincipalType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.labkey.test.util.PermissionsHelper.FOLDER_ADMIN_ROLE;

/**
 * The self-service group change (ChangeGroupsApiAction) must not let a logged-in user escalate their own privileges.
 *
 * The transition rule map (the site-global "group A may move to group B" list) is admin-configured
 * and could be pointed at a privileged target by mistake. ChangeGroupsApiAction therefore re-validates
 * the resolved groups with validateGroupChangeTarget before it changes any membership, and rejects the
 * move with status TARGET_NOT_ALLOWED when the target:
 *  - is a site group rather than a project group,
 *  - is in a different project than the source group, or
 *  - carries administrative permission anywhere in its project or its subfolders.
 *
 * This test plants each of those dangerous rules on purpose, then confirms that a non-admin member of
 * the source group is refused every escalating move (and is neither added to the target nor removed from
 * the source), while a legitimate move to a plain non-admin project group in the same project still works.
 *
 * Each escalating case needs its transition rule planted so the attempt gets past the "rule exists" gate and
 * actually reaches validateGroupChangeTarget. A target with no rule is refused before that check, with
 * NO_PERMISSIONS. The test drives a no-rule move on purpose and asserts NO_PERMISSIONS, so that path stays
 * distinguishable from the TARGET_NOT_ALLOWED path.
 *
 * Design notes:
 *  - The live client (a skyline.ms wiki page) is not in the module, so this drives the server actions
 *    directly: the admin AddGroupChangeProperty action to plant rules, and the ChangeGroupsApi action as
 *    the user to attempt the moves.
 *  - The transition rule map is site-global (no container), so the planted rules are removed in @After
 *    (not doCleanup) so they are cleaned up even on a local clean=false run.
 */
@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 2)
public class SignUpGroupChangeSecurityTest extends BaseWebDriverTest
{
    private static final String PROJECT_1 = "SignUpSecurityTest P1";
    private static final String PROJECT_2 = "SignUpSecurityTest P2";
    private static final String SUBFOLDER = "Sub";

    private static final String GROUP_SOURCE = "SignupSource";
    private static final String GROUP_TARGET_OK = "SignupTargetOk";
    private static final String GROUP_TARGET_ADMIN = "SignupTargetAdmin";
    private static final String GROUP_TARGET_SUBADMIN = "SignupTargetSubAdmin";
    private static final String GROUP_TARGET_CROSS = "SignupTargetCross";
    private static final String SITE_GROUP = "SignupSiteGroup";

    // Group ids captured during setup, used both for the rule map and for the ChangeGroupsApi params.
    private static int idSource;
    private static int idTargetOk;
    private static int idTargetAdmin;
    private static int idTargetSubAdmin;
    private static int idTargetCross;
    private static int idSiteGroup;

    private static final String USER = "signup_user@signupsecurity.test";

    // Password for the user account, so we can POST to ChangeGroupsApi as that user.
    private static String userPassword;

    // Transition rules this test planted, so @After can remove them from the site-global rule map.
    private final List<int[]> _plantedRules = new ArrayList<>();

    @Override
    protected String getProjectName()
    {
        return PROJECT_1;
    }

    @BeforeClass
    public static void setupProject()
    {
        SignUpGroupChangeSecurityTest init = getCurrentTest();
        init.doSetup();
    }

    @LogMethod
    private void doSetup()
    {
        _containerHelper.createProject(PROJECT_1, null);
        _containerHelper.createSubfolder(PROJECT_1, SUBFOLDER);
        _containerHelper.createProject(PROJECT_2, null);

        ApiPermissionsHelper perms = new ApiPermissionsHelper(this);
        idSource = perms.createProjectGroup(GROUP_SOURCE, PROJECT_1);
        idTargetOk = perms.createProjectGroup(GROUP_TARGET_OK, PROJECT_1);
        idTargetAdmin = perms.createProjectGroup(GROUP_TARGET_ADMIN, PROJECT_1);
        idTargetSubAdmin = perms.createProjectGroup(GROUP_TARGET_SUBADMIN, PROJECT_1);
        idTargetCross = perms.createProjectGroup(GROUP_TARGET_CROSS, PROJECT_2);
        idSiteGroup = perms.createGlobalPermissionsGroup(SITE_GROUP);

        // TargetAdmin carries admin in the P1 project; TargetSubAdmin carries admin only in the P1/Sub
        // subfolder. Assigning a role in the subfolder gives it its own permission policy (breaks
        // inheritance), which is what the subfolder branch of validateGroupChangeTarget looks for.
        perms.addMemberToRole(idTargetAdmin, FOLDER_ADMIN_ROLE, "/" + PROJECT_1);
        perms.addMemberToRole(idTargetSubAdmin, FOLDER_ADMIN_ROLE, "/" + PROJECT_1 + "/" + SUBFOLDER);

        // A non-admin user who is a member of the source group. All rejection cases depend on the user
        // being in Source, because the action checks source-group membership before it validates the target.
        _userHelper.createUser(USER);
        userPassword = setInitialPassword(USER);
        perms.addUserToProjGroup(USER, PROJECT_1, GROUP_SOURCE);
    }

    @Test
    public void testSelfServiceGroupChangeRejectsPrivilegeEscalation() throws Exception
    {
        // Plant the four dangerous rules so each escalating attempt gets past the "rule exists" check and
        // actually reaches validateGroupChangeTarget. TargetOk's rule is planted later, just before the
        // happy path, so it can first stand in for the "no rule configured" case below.
        addTransitionRule(idSource, idTargetAdmin);
        addTransitionRule(idSource, idTargetSubAdmin);
        addTransitionRule(idSource, idTargetCross);
        addTransitionRule(idSource, idSiteGroup);

        Connection userConnection = new Connection(WebTestHelper.getBaseURL(), USER, userPassword);

        // Contrast case: a valid same-project non-admin target with no rule configured is rejected before
        // validateGroupChangeTarget, with NO_PERMISSIONS. Asserting this keeps the two rejection reasons distinguishable.
        assertMoveNotEligible(userConnection, idTargetOk, GROUP_TARGET_OK,
                "no transition rule is configured for the target");

        // Group transition validation rejections. Run these while the user is still in Source; each must
        // leave membership untouched and report TARGET_NOT_ALLOWED.
        assertMoveRejected(userConnection, idTargetAdmin, GROUP_TARGET_ADMIN, PROJECT_1,
                "target group carries admin permission in its project");
        assertMoveRejected(userConnection, idTargetSubAdmin, GROUP_TARGET_SUBADMIN, PROJECT_1,
                "target group carries admin permission in a subfolder");
        assertMoveRejected(userConnection, idTargetCross, GROUP_TARGET_CROSS, PROJECT_2,
                "target group is in a different project");
        assertMoveRejected(userConnection, idSiteGroup, SITE_GROUP, "/",
                "target is a site group, not a project group");

        // Legitimate move, run last because it actually moves the user out of the source group.
        addTransitionRule(idSource, idTargetOk);
        assertMoveSucceeded(userConnection, idTargetOk, GROUP_TARGET_OK);
    }

    private void assertMoveRejected(Connection userConnection, int targetId, String targetGroup,
                                    String targetContainer, String because) throws Exception
    {
        String status = postGroupChange(userConnection, idSource, targetId);
        assertEquals("Escalating move should be refused with TARGET_NOT_ALLOWED because " + because,
                "TARGET_NOT_ALLOWED", status);

        ApiPermissionsHelper perms = new ApiPermissionsHelper(this);
        assertFalse("User must not have been added to " + targetGroup + " (" + because + ")",
                perms.isUserInGroup(USER, targetGroup, targetContainer, PrincipalType.USER));
        assertTrue("User must remain in the source group after a refused move (" + because + ")",
                perms.isUserInGroup(USER, GROUP_SOURCE, PROJECT_1, PrincipalType.USER));
    }

    // A move rejected before the group transition validation runs (e.g. no rule configured, or caller not in
    // the source group) reports NO_PERMISSIONS, distinct from the validation's TARGET_NOT_ALLOWED.
    private void assertMoveNotEligible(Connection userConnection, int targetId, String targetGroup,
                                       String because) throws Exception
    {
        String status = postGroupChange(userConnection, idSource, targetId);
        assertEquals("Move should be refused with NO_PERMISSIONS because " + because, "NO_PERMISSIONS", status);

        ApiPermissionsHelper perms = new ApiPermissionsHelper(this);
        assertFalse("User must not have been added to " + targetGroup + " (" + because + ")",
                perms.isUserInGroup(USER, targetGroup, PROJECT_1, PrincipalType.USER));
        assertTrue("User must remain in the source group after a refused move (" + because + ")",
                perms.isUserInGroup(USER, GROUP_SOURCE, PROJECT_1, PrincipalType.USER));
    }

    private void assertMoveSucceeded(Connection userConnection, int targetId, String targetGroup) throws Exception
    {
        String status = postGroupChange(userConnection, idSource, targetId);
        assertEquals("A legitimate move to a non-admin project group should succeed", "USER_MOVED_SUCCESS", status);

        ApiPermissionsHelper perms = new ApiPermissionsHelper(this);
        assertTrue("User should now be a member of " + targetGroup,
                perms.isUserInGroup(USER, targetGroup, PROJECT_1, PrincipalType.USER));
        assertFalse("User should have been removed from the source group",
                perms.isUserInGroup(USER, GROUP_SOURCE, PROJECT_1, PrincipalType.USER));
    }

    // Posts to ChangeGroupsApi as the user and returns the response status string.
    private String postGroupChange(Connection userConnection, int oldGroup, int newGroup) throws Exception
    {
        SimplePostCommand command = new SimplePostCommand("signup", "changeGroupsApi");
        command.setParameters(Map.of("oldgroup", oldGroup, "newgroup", newGroup));
        CommandResponse response = command.execute(userConnection, "/");
        return (String) response.getProperty("status");
    }

    private void addTransitionRule(int oldGroup, int newGroup) throws IOException
    {
        postAdminGroupChangeForm("addGroupChangeProperty", oldGroup, newGroup);
        _plantedRules.add(new int[]{oldGroup, newGroup});
    }

    // Drives the site-admin AddGroupChangeProperty / RemoveGroupChangeProperty form actions. These are
    // FormHandlerActions (they redirect rather than return JSON), so this posts form-encoded fields as the
    // logged-in admin: injectCookies carries the admin session and CSRF token, and getBasicHttpContext adds
    // preemptive basic auth.
    private void postAdminGroupChangeForm(String action, int oldGroup, int newGroup) throws IOException
    {
        HttpPost post = new HttpPost(WebTestHelper.buildURL("signup", "/", action));
        List<NameValuePair> form = List.of(
                new BasicNameValuePair("oldgroup", String.valueOf(oldGroup)),
                new BasicNameValuePair("newgroup", String.valueOf(newGroup)));
        post.setEntity(new UrlEncodedFormEntity(form));
        APITestHelper.injectCookies(post);

        HttpContext context = WebTestHelper.getBasicHttpContext();
        try (CloseableHttpClient client = WebTestHelper.getHttpClient();
             CloseableHttpResponse response = client.execute(post, context))
        {
            int code = response.getCode();
            assertTrue(action + " returned unexpected HTTP status " + code,
                    code == HttpStatus.SC_OK || code == HttpStatus.SC_MOVED_TEMPORARILY);
            EntityUtils.consumeQuietly(response.getEntity());
        }
    }

    @After
    public void removePlantedRules()
    {
        // The transition rule map is site-global, so remove the rules this test planted even when project
        // cleanup is skipped (clean=false). Runs after the test method.
        if (_plantedRules.isEmpty())
            return;

        ensureSignedInAsPrimaryTestUser();
        for (int[] rule : _plantedRules)
        {
            try
            {
                postAdminGroupChangeForm("removeGroupChangeProperty", rule[0], rule[1]);
            }
            catch (IOException e)
            {
                log("Failed to remove planted transition rule " + rule[0] + " -> " + rule[1] + ": " + e.getMessage());
            }
        }
        _plantedRules.clear();
    }

    @Override
    protected void doCleanup(boolean afterTest)
    {
        // The site group is not scoped to a project, so it survives project deletion and must be removed
        // explicitly.
        new ApiPermissionsHelper(this).deleteGroup(SITE_GROUP, false);
        _userHelper.deleteUsers(false, USER);
        _containerHelper.deleteProject(PROJECT_1, afterTest);
        _containerHelper.deleteProject(PROJECT_2, afterTest);
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return List.of("signup");
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }
}

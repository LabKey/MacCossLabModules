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

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.test.WebTestHelper;
import org.labkey.test.util.APITestHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertTrue;

/**
 * Catalog helpers shared by the tool store tests.
 *
 * Tool identifiers are unique across the whole server rather than per folder, and the sample data
 * has only one tool with two versions, so every test class here competes for the same identifiers.
 * A tool left behind by another class or an earlier run makes an upload fail as TOOL_ALREADY_EXISTS,
 * silently, because InsertAction renders that error with a 200.
 */
public class ToolStoreTestHelper
{
    private ToolStoreTestHelper()
    {
    }

    /** The catalog. Global by design, so the container asked is immaterial. */
    public static JSONArray toolsFromApi(String containerPath)
    {
        try (CloseableHttpClient client = WebTestHelper.getHttpClient())
        {
            HttpGet request = new HttpGet(WebTestHelper.buildURL("skyts", containerPath, "getToolsApi"));
            APITestHelper.injectCookies(request);
            String body = client.execute(request, r -> EntityUtils.toString(r.getEntity())).trim();
            // Empty for an empty store, a bare object for one tool, an array otherwise.
            if (body.isEmpty())
                return new JSONArray();
            return body.startsWith("[") ? new JSONArray(body) : new JSONArray().put(new JSONObject(body));
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to read getToolsApi", e);
        }
    }

    public static Set<String> catalogIdentifiers(String containerPath)
    {
        JSONArray tools = toolsFromApi(containerPath);
        Set<String> identifiers = new HashSet<>();
        for (int i = 0; i < tools.length(); i++)
            identifiers.add(tools.getJSONObject(i).optString("Identifier"));
        return identifiers;
    }

    /** DownloadUrl looks like /&lt;store container&gt;/skyts-downloadTool.view?id=N */
    public static int rowId(JSONObject tool)
    {
        String url = tool.getString("DownloadUrl");
        int idx = url.indexOf("id=");
        // Without this the substring below silently starts two characters in and the parse fails with
        // a NumberFormatException naming whatever it found, which says nothing about the real problem.
        assertTrue("DownloadUrl should carry an id parameter: " + url, idx >= 0);
        String tail = url.substring(idx + 3);
        int amp = tail.indexOf('&');
        return Integer.parseInt(amp >= 0 ? tail.substring(0, amp) : tail);
    }

    public static String storeContainerOf(JSONObject tool)
    {
        String url = tool.getString("DownloadUrl");
        String path = url.substring(0, url.indexOf("/skyts-"));

        // DownloadUrl comes from ActionURL.getPath(), which includes the servlet context path, while
        // buildURL adds that back. Leaving it in produces /labkey/labkey/<container> on a deployment
        // that uses one.
        // Compared on a segment boundary, or a context path of /labkey would also strip the front of
        // a project actually named labkeyFoo.
        String contextPath = WebTestHelper.getContextPath();
        if (!contextPath.isEmpty() && (path.equals(contextPath) || path.startsWith(contextPath + "/")))
            path = path.substring(contextPath.length());

        return StringUtils.strip(path, "/");
    }

    /**
     * The folder name SkylineToolsStoreController.toolFolderName builds for a tool version.
     *
     * The controller passes the name through FileUtil.getBaseName, which drops everything from the
     * last dot on, so a tool called "MSstats 3.5" lives in _tool_MSstats 3_1.0. Rebuilding the path
     * by plain concatenation pointed at a folder that does not exist, and a role check against a
     * missing container just returns nothing, so the failure read as a permissions problem.
     *
     * The controller also applies makeLegalName, which is not repeated here. No sample tool name
     * contains a character it rewrites, and the setup asserts the folder exists, so a fixture that
     * broke that assumption would fail loudly rather than silently.
     */
    public static String toolFolderName(String toolName, String version)
    {
        int lastDot = toolName.lastIndexOf('.');
        return "_tool_" + (lastDot >= 0 ? toolName.substring(0, lastDot) : toolName) + "_" + version;
    }

    /**
     * A tool zip holding nothing but tool-inf/info.properties, since the sample zips carry a real
     * tool's payload. Identifiers are server wide, so each caller needs its own name and identifier,
     * and it must sit inside the namespace removeToolsFromCatalog will accept.
     */
    public static File writeMinimalToolZip(String name, String identifier, String version)
    {
        try
        {
            // Short prefix - ZipName is 50 characters and createTempFile appends up to 19 digits.
            File zip = File.createTempFile("ts-" + version + "-", ".zip");
            zip.deleteOnExit();
            try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip)))
            {
                out.putNextEntry(new ZipEntry("tool-inf/info.properties"));
                out.write(("Name = " + name + "\n" +
                           "Version = " + version + "\n" +
                           "Identifier = " + identifier + "\n").getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
            return zip;
        }
        catch (IOException e)
        {
            throw new RuntimeException("Could not build the test tool zip", e);
        }
    }

    /**
     * A tool zip whose tool-inf icon is not a decodable image. The controller picks the icon by file
     * extension and stores the bytes without decoding them, so this passes upload parsing and then
     * fails in writeIconToFile, which is after the version folder has been created. That is what
     * makes it a fixture for an upload failing part way through storing a version.
     */
    public static File writeToolZipWithUnreadableIcon(String name, String identifier, String version)
    {
        try
        {
            File zip = File.createTempFile("ts-badicon-" + version + "-", ".zip");
            zip.deleteOnExit();
            try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip)))
            {
                out.putNextEntry(new ZipEntry("tool-inf/info.properties"));
                out.write(("Name = " + name + "\n" +
                           "Version = " + version + "\n" +
                           "Identifier = " + identifier + "\n").getBytes(StandardCharsets.UTF_8));
                out.closeEntry();

                out.putNextEntry(new ZipEntry("tool-inf/icon.png"));
                out.write("This is not an image.".getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
            return zip;
        }
        catch (IOException e)
        {
            throw new RuntimeException("Could not build the test tool zip", e);
        }
    }

    /** Reads the Identifier out of tool-inf/info.properties inside a tool zip. */
    public static String identifierOf(File zip)
    {
        try (ZipFile zf = new ZipFile(zip))
        {
            ZipEntry entry = zf.getEntry("tool-inf/info.properties");
            assertTrue("No tool-inf/info.properties in " + zip.getName(), entry != null);
            Properties props = new Properties();
            try (InputStream in = zf.getInputStream(entry))
            {
                props.load(in);
            }
            String identifier = props.getProperty("Identifier");
            assertTrue("No Identifier in " + zip.getName(), identifier != null);
            return identifier.trim();
        }
        catch (Exception e)
        {
            throw new RuntimeException("Could not read the identifier from " + zip, e);
        }
    }

    /** The LSID namespace every sample tool zip in this module uses. */
    private static final String TEST_IDENTIFIER_PREFIX = "URN:LSID:toolstore.test:";

    /**
     * Deletes tools in the given project whose identifier matches one of the given zips, so a test
     * starts from a known state. Runs as the current user, who must be a site admin.
     */
    public static void removeToolsFromCatalog(String containerPath, File... zips)
    {
        // getToolsApi is deliberately not container scoped, so the loop below sees every tool on the
        // server, and DeleteAction removes the container of every version of whatever it matches.
        // The only thing separating a fixture from a real tool is the identifier, so refuse to run
        // at all against a zip from outside the test namespace rather than deleting someone's tool.
        // Do not try to tell them apart by folder name - a real store folder can be called anything.
        Set<String> wanted = new HashSet<>();
        for (File zip : zips)
        {
            String identifier = identifierOf(zip);
            assertTrue("Refusing to clean up " + zip.getName() + ". Its identifier " + identifier +
                            " is outside " + TEST_IDENTIFIER_PREFIX + ", and this deletes every " +
                            "version's folder wherever it lives, so it must only run on fixtures.",
                    identifier.startsWith(TEST_IDENTIFIER_PREFIX));
            wanted.add(identifier);
        }

        JSONArray tools = toolsFromApi(containerPath);
        for (int i = 0; i < tools.length(); i++)
        {
            JSONObject tool = tools.getJSONObject(i);
            if (!wanted.contains(tool.optString("Identifier")))
                continue;

            // DeleteAction redirects unless it is called in the tool's own store folder.
            HttpPost request = new HttpPost(
                    WebTestHelper.buildURL("skyts", storeContainerOf(tool), "delete"));
            request.setEntity(MultipartEntityBuilder.create()
                    .addTextBody("toolId", String.valueOf(rowId(tool)))
                    .build());
            APITestHelper.injectCookies(request);
            int status;
            try (CloseableHttpClient client = WebTestHelper.getHttpClient())
            {
                status = client.execute(request, response -> {
                    EntityUtils.consumeQuietly(response.getEntity());
                    return response.getCode();
                });
            }
            catch (Exception e)
            {
                throw new RuntimeException("Failed to remove leftover tool " + tool.optString("Name"), e);
            }
            assertTrue("Deleting leftover tool " + tool.optString("Name") + " returned HTTP " + status,
                    status < 400);
        }

        // DeleteAction renders a refusal as an error view with status 200, so the status above does
        // not prove anything went away. Read the catalog back and fail here rather than leaving the
        // next upload to fail as TOOL_ALREADY_EXISTS somewhere unrelated.
        Set<String> stillPresent = new HashSet<>(catalogIdentifiers(containerPath));
        stillPresent.retainAll(wanted);
        assertTrue("Tools still in the catalog after cleanup: " + stillPresent, stillPresent.isEmpty());
    }
}

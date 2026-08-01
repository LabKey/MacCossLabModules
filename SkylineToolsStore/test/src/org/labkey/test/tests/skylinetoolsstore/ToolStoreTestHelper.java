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
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.test.WebTestHelper;
import org.labkey.test.util.APITestHelper;

import java.io.File;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
        String tail = url.substring(url.indexOf("id=") + 3);
        int amp = tail.indexOf('&');
        return Integer.parseInt(amp >= 0 ? tail.substring(0, amp) : tail);
    }

    public static String storeContainerOf(JSONObject tool)
    {
        String url = tool.getString("DownloadUrl");
        return url.substring(1, url.indexOf("/skyts-"));
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

    /**
     * Deletes every tool in the catalog whose identifier matches one of the given zips, wherever it
     * lives, so a test starts from a known state. Runs as the current user, who must be a site admin.
     */
    public static void removeToolsFromCatalog(String containerPath, File... zips)
    {
        Set<String> wanted = new HashSet<>();
        for (File zip : zips)
            wanted.add(identifierOf(zip));

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

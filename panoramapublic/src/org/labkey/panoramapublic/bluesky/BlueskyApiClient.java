package org.labkey.panoramapublic.bluesky;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.panoramapublic.catalog.CatalogImageAttachmentParent;
import org.labkey.panoramapublic.model.CatalogEntry;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.query.CatalogEntryManager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BlueskyApiClient
{
    protected static final Logger logger = LogHelper.getLogger(BlueskyApiClient.class, "BlueskyApiClient logger");

    private static final BlueskyApiClient INSTANCE = new BlueskyApiClient();

    public static BlueskyApiClient getInstance()
    {
        return INSTANCE;
    }

    private BlueskyApiClient() {}

    /**
     * Login to Bluesky and obtain auth tokens
     */
    public LoginInfo login(@NotNull BlueskySettings settings, boolean testAccount) throws BlueskyException
    {
        ClientConfig config = new ClientConfig(settings, testAccount);

        validateNotBlank(config.getAccount(), String.format("Cannot find Bluesky %saccount.", testAccount ? "test " : ""));
        validateNotBlank(config.getPassword(), String.format("Cannot find password for Bluesky %saccount.", testAccount ? "test " : ""));
        validateNotBlank(config.getAuthEndpoint(), "Bluesky auth endpoint not configured");

        JSONObject requestBody = new JSONObject();
        requestBody.put("identifier", config.getAccount());
        requestBody.put("password", config.getPassword());

        HttpPost httpPost = new HttpPost(config.getAuthEndpoint());
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setEntity(new StringEntity(requestBody.toString(), ContentType.APPLICATION_JSON));

        logger.debug(String.format("Logging into Bluesky as '%s' at endpoint '%s'", config.getAccount(), config.getAuthEndpoint()));

        BlueskyResponse response;
        try (CloseableHttpClient httpClient = HttpClients.createDefault())
        {
            response = getResponse(httpClient, httpPost, config, "Bluesky login failed");
            JSONObject responseJson = response.getJsonObject();
            if (!responseJson.has("accessJwt"))
            {
                throw new BlueskyException("'accessJwt' not found in the login response", response);
            }
            String accessJwt = responseJson.getString("accessJwt");
            if (!responseJson.has("did"))
            {
                throw new BlueskyException("'did' not found in the login response", response);
            }
            String did = responseJson.getString("did");
            return new LoginInfo(accessJwt, did);
        }
        catch (IOException | JSONException e)
        {
            throw new BlueskyException("Bluesky login failed", config.getAccount(), config.getAuthEndpoint(), e);
        }
    }

    private static void validateNotBlank(String value, String error) throws BlueskyException
    {
        if(StringUtils.isBlank(value))
        {
            throw new BlueskyException(error);
        }
    }

    public LoginInfo login(@NotNull BlueskySettings settings) throws BlueskyException
    {
        return login(settings, false); // Login to the primary account
    }

    public LoginInfo loginTestAccount(@NotNull BlueskySettings settings) throws BlueskyException
    {
        return login(settings, true); // Login to the test account
    }

    public static class LoginInfo
    {
        private final String _accessJwt;
        private final String _did; // DID (Decentralized Identifier) of the logged-in user

        public LoginInfo(String accessJwt, String did)
        {
            _accessJwt = accessJwt;
            _did = did;
        }

        public String getAccessJwt()
        {
            return _accessJwt;
        }

        public String getDid()
        {
            return _did;
        }
    }

    /**
     * Create a post on Bluesky announcing the data
     */
    @NotNull
    public String createPost(@NotNull ExperimentAnnotations exptAnnotations, BlueskySettings settings, boolean useTestAccount) throws BlueskyException
    {
        return createPost(exptAnnotations, settings, login(settings, useTestAccount), useTestAccount);
    }

    /**
     * Create a post on Bluesky announcing the data.  The post is created only if a post wasn't already created.
     */
    @Nullable
    public String createPostIfNotExists(@NotNull ExperimentAnnotations exptAnnotations, BlueskySettings settings, boolean useTestAccount) throws BlueskyException
    {
        if (BlueskyLinksManager.getBlueskyUriForExperiment(exptAnnotations) != null)
        {
            // There is already a post on Bluesky related to this data
            return null;
        }

        return createPost(exptAnnotations, settings, useTestAccount);
    }

    @NotNull
    public String createPost(@NotNull ExperimentAnnotations exptAnnotations, @NotNull BlueskySettings settings,
                              @NotNull LoginInfo loginInfo, boolean useTestAccount) throws BlueskyException
    {
        ClientConfig config = new ClientConfig(settings, useTestAccount);
        validateNotBlank(config.getAuthEndpoint(), "Bluesky auth endpoint not configured");
        validateNotBlank(config.getPostEndpoint(), "Bluesky post endpoint not configured");
        validateNotBlank(config.getBlobUploadEndpoint(), "Bluesky image upload endpoint not configured");

        if (exptAnnotations.getShortUrl() == null)
        {
            throw new BlueskyException(String.format("Experiment with Id %d does not have a short access URL", exptAnnotations.getId()));
        }

        JSONObject requestBody = createRequestBody(exptAnnotations, config, loginInfo);

        HttpPost httpPost = new HttpPost(config.getPostEndpoint());
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setHeader("Authorization", "Bearer " + loginInfo.getAccessJwt());
        httpPost.setEntity(new StringEntity(requestBody.toString(), ContentType.APPLICATION_JSON));

        logger.debug(String.format("Posting to Bluesky account %s at endpoint '%s' for Panorama Public data at '%s'",
                config.getAccount(), config.getPostEndpoint(), exptAnnotations.getShortUrl().renderShortURL()));

        BlueskyResponse response;
        String blueskyAtUri;
        try (CloseableHttpClient httpClient = HttpClients.createDefault())
        {
            response = getResponse(httpClient, httpPost, config, "Post creation failed");
            JSONObject responseJson = response.getJsonObject();
            if (responseJson.has("uri"))
            {
                blueskyAtUri = responseJson.getString("uri");
            }
            else
            {
                throw new BlueskyException("Post creation failed - Missing URI in response.", response);
            }
            if (StringUtils.isBlank(blueskyAtUri))
            {
                throw new BlueskyException("Post URI in response is blank.", response);
            }
        }
        catch (IOException | JSONException e)
        {
            throw new BlueskyException("Post creation failed.", config.getAccount(), config.getPostEndpoint(), e);
        }

        if (!useTestAccount)
        {
            BlueskyLinksManager.saveBlueskyUriForExperiment(exptAnnotations, blueskyAtUri);
        }

        return blueskyAtUri;
    }

    @NotNull
    private JSONObject createRequestBody(@NotNull ExperimentAnnotations exptAnnotations,
                                         @NotNull ClientConfig config, @NotNull LoginInfo loginInfo) throws BlueskyException
    {
        JSONObject record = buildRecord(exptAnnotations, config, loginInfo);

        return new JSONObject()
                .put("repo", loginInfo.getDid())
                .put("collection", "app.bsky.feed.post")
                .put("record", record);
    }

    @NotNull
    private JSONObject buildRecord(@NotNull ExperimentAnnotations exptAnnotations,
                                   @NotNull ClientConfig config, @NotNull LoginInfo loginInfo) throws BlueskyException
    {
        String text = getAnnouncement(config);

        JSONObject record = new JSONObject()
                .put("$type", "app.bsky.feed.post")
                .put("text", text)
                .put("createdAt", Instant.now().toString());

        // Add hashtag facets if any
        JSONArray facets = buildHashtagFacets(text, config.getHashtags());
        if (!facets.isEmpty())
        {
            record.put("facets", facets);
        }

        // Build and attach the embed object
        record.put("embed", buildEmbed(exptAnnotations, config, loginInfo));
        return record;
    }

    /**
     * Generate post text with hashtags
     */
    private static String getAnnouncement(@NotNull ClientConfig config)
    {
        String[] hashtags = config.getHashtags();
        return String.format("%s%s%s", config.getAnnouncementText(), hashtags.length > 0 ? " " : "", formatHashtags(hashtags));
    }

    public static String getAnnouncement(@NotNull BlueskySettings settings, boolean isTestAccount)
    {
        return getAnnouncement(new ClientConfig(settings, isTestAccount));
    }

    /**
     * Convert an array of hashtag strings to a comma-separated string with '#' prefix
     */
    private static String formatHashtags(@NotNull String[] hashtags)
    {
        return Arrays.stream(hashtags)
                .map(tag -> "#" + tag)
                .collect(Collectors.joining(" "));
    }

    /**
     * Create hashtag facets for Bluesky's rich text formatting
     */
    private static JSONArray buildHashtagFacets(String postText, String[] hashtags)
    {
        JSONArray facets = new JSONArray();

        for (String tag : hashtags) {
            JSONObject tagFacet = createSingleHashtagFacet(postText, tag);
            if (tagFacet != null) {
                facets.put(tagFacet);
            }
        }

        return facets;
    }

    /**
     * Create a single hashtag facet for Bluesky's rich text formatting
     */
    @Nullable
    private static JSONObject createSingleHashtagFacet(@NotNull String postText, @NotNull String tag)
    {
        // Find the tag in the text (including the # character)
        String hashtagInText = "#" + tag;
        int tagStart = postText.indexOf(hashtagInText);

        if (tagStart == -1)
        {
            return null;
        }

        // Convert to byte position for UTF-8
        int byteStart = postText.substring(0, tagStart).getBytes(StandardCharsets.UTF_8).length;
        int byteEnd = byteStart + hashtagInText.getBytes(StandardCharsets.UTF_8).length;

        JSONObject indices = new JSONObject()
                .put("byteStart", byteStart)
                .put("byteEnd", byteEnd);

        JSONObject feature = new JSONObject()
                .put("$type", "app.bsky.richtext.facet#tag")
                .put("tag", tag); // Tag without the # character

        return new JSONObject()
                .put("index", indices)
                .put("features", new JSONArray().put(feature));
    }

    @NotNull
    private JSONObject buildEmbed(@NotNull ExperimentAnnotations exptAnnotations, @NotNull ClientConfig config, @NotNull LoginInfo loginInfo) throws BlueskyException
    {
        // Create the embed object for a web card
        JSONObject embed = new JSONObject()
                .put("$type", "app.bsky.embed.external");

        String panoramaLink = exptAnnotations.getShortUrl().renderShortURL();

        JSONObject external = new JSONObject()
                .put("uri", panoramaLink)
                .put("title", exptAnnotations.getTitle())
                .put("description", panoramaLink);

        // Upload and grab the blob reference for the logo image
        external.put("thumb", getImageBlobReference(exptAnnotations, config, loginInfo));

        embed.put("external", external);
        return embed;
    }

    private JSONObject getImageBlobReference(ExperimentAnnotations exptAnnotations, ClientConfig config, LoginInfo loginInfo) throws BlueskyException
    {
        AttachmentParent attachmentParent = null;
        // First check if the data submitter provided a catalog entry for the data.
        Attachment attachment = getCatalogEntryAttachment(exptAnnotations);
        if (attachment != null)
        {
            attachmentParent = new CatalogImageAttachmentParent(exptAnnotations.getShortUrl(), exptAnnotations.getContainer());
        }
        else if (!StringUtils.isBlank(config.getImageFileName()))
        {
            // If the data does not have a catalog entry, or we were unable to get it, use the Panorama Public logo
            attachmentParent = PanoramaPublicLogoAttachmentParent.get();
            if (attachmentParent == null)
            {
                throw new BlueskyException("Unable to initialize PanoramaPublicLogoAttachmentParent. Perhaps a Panorama Public project does not exist on the server.");
            }
            attachment = PanoramaPublicLogoManager.getNewDataLogo(config.getImageFileName());
        }

        if (attachment == null)
        {
            throw new BlueskyException("Unable to find an image file to include in the post.");
        }

        JSONObject blobResponse = uploadImage(attachment, attachmentParent, config, loginInfo);
        // Get the blob reference from the response
        if (blobResponse.has("blob"))
        {
            return blobResponse.getJSONObject("blob");
        }
        else
        {
            throw new BlueskyException("Blob reference not found in Bluesky response after uploading the image file.");
        }
    }

    private static Attachment getCatalogEntryAttachment(ExperimentAnnotations exptAnnotations)
    {
        CatalogEntry catalogEntry = CatalogEntryManager.getApprovedEntryForExperiment(exptAnnotations);
        return (catalogEntry != null) ? catalogEntry.getAttachment() : null;
    }

    /**
     * Upload image bytes to Bluesky
     */
    @NotNull
    private JSONObject uploadToBluesky(byte[] imageBytes, String mimeType, ClientConfig config, LoginInfo loginInfo) throws BlueskyException
    {
        String endpoint = config.getBlobUploadEndpoint();
        HttpPost httpPost = new HttpPost(endpoint);
        httpPost.setHeader("Content-Type", mimeType);
        httpPost.setHeader("Authorization", "Bearer " + loginInfo.getAccessJwt());
        httpPost.setEntity(new ByteArrayEntity(imageBytes, ContentType.create(mimeType)));

        try (CloseableHttpClient httpClient = HttpClients.createDefault())
        {
            BlueskyResponse response = getResponse(httpClient, httpPost, config, "Failed to upload image to Bluesky");
            return response.getJsonObject();
        }
        catch (IOException | JSONException e)
        {
            throw new BlueskyException("Failed to upload image to Bluesky", config.getAccount(), endpoint, e);
        }
    }

    @NotNull
    private BlueskyResponse getResponse(CloseableHttpClient httpClient, HttpPost httpPost, ClientConfig config, String failureMessage) throws IOException, BlueskyException
    {
        BlueskyResponse response = httpClient.execute(httpPost, new BlueskyResponseHandler(config, httpPost.getRequestUri()));
        String responseContent = response.getResponseBody() != null ? response.getResponseBody() : "";

        if (!response.success())
        {
            throw new BlueskyException(failureMessage, response);
        }

        if (responseContent.isEmpty())
        {
            throw new BlueskyException("Received empty response from Bluesky", response);
        }

        return response;
    }

    /**
     * Upload an image attachment to Bluesky
     */
    @NotNull
    private JSONObject uploadImage(@NotNull Attachment attachment, @NotNull AttachmentParent parent,
                                   ClientConfig config, LoginInfo loginInfo) throws BlueskyException
    {
        try (InputStream is = AttachmentService.get().getInputStream(parent, attachment.getName()))
        {
            byte[] imageBytes = is.readAllBytes();
            String mimeType = PageFlowUtil.getContentTypeFor(attachment.getName());
            return uploadToBluesky(imageBytes, mimeType, config, loginInfo);
        }
        catch (FileNotFoundException e)
        {
            logger.error("Image attachment file not found: " + attachment.getName(), e);
            throw new BlueskyException("Image attachment file not found", e);
        }
        catch (IOException e)
        {
            logger.error("Error reading image file: " + attachment.getName(), e);
            throw new BlueskyException("Error reading image file", e);
        }
    }

    /**
     * Custom response handler to handle HTTP responses from Bluesky
     */
    private static class BlueskyResponseHandler implements HttpClientResponseHandler<BlueskyResponse>
    {
        private final String _account;
        private final String _apiEndpoint;

        public BlueskyResponseHandler(ClientConfig config, String apiEndpoint)
        {
            _account = config.getAccount();
            _apiEndpoint = apiEndpoint;
        }

        @Override
        public BlueskyResponse handleResponse(ClassicHttpResponse response) throws IOException
        {
            try
            {
                HttpEntity entity = response.getEntity();
                String content = entity != null ? EntityUtils.toString(entity) : null;
                return new BlueskyResponse(response.getCode(), response.getReasonPhrase(), content, _account, _apiEndpoint);
            }
            catch (ParseException e)
            {
                throw new IOException("Failed to parse response content", e);
            }
        }
    }

    private static final Pattern AT_PROTOCOL_URI = Pattern.compile(
            "^at://([^/]+)/app\\.bsky\\.feed\\.post/([^/]+)$"
    );


    /**
     * Converts an AT Protocol URI to a Bluesky web URL.
     * Returns null if the input cannot be converted to a valid Bluesky URL.
     * Example: at://</did>/app.bsky.feed.post/<post_id>
     * Convert to: https://bsky.app/profile/<did>/post/<post_id>
     */
    public static String tryConvertToWebUrl(String atProtocolUri)
    {
        if (StringUtils.isBlank(atProtocolUri))
        {
            return null;
        }

        Matcher m = AT_PROTOCOL_URI.matcher(atProtocolUri.trim());
        return m.matches()
                ? "https://bsky.app/profile/" + m.group(1) + "/post/" + m.group(2)
                : null;
    }

    private static class ClientConfig
    {
        private final String _account;
        private final String _password;
        private final String _imageFileName;
        private final String[] _hashtags;

        private final String _announcementText;
        private final String _authEndpoint;
        private final String _postEndpoint;
        private final String _blobUploadEndpoint;

        public ClientConfig(BlueskySettings settings, boolean test)
        {
            _account = settings.getAccount(test); // Get either test or primary account
            _password = settings.getPassword(test); // Get either test or primary password
            _imageFileName = settings.getImageFileName();
            _hashtags = test ? settings.getTestHashtagArray() : settings.getHashtagArray(); // Get the appropriate hashtags
            _announcementText = settings.getAnnouncementText();
            _authEndpoint = settings.getAuthEndpoint();
            _postEndpoint = settings.getPostEndpoint();
            _blobUploadEndpoint = settings.getBlobUploadEndpoint();
        }

        public String getAccount()
        {
            return _account;
        }

        public String getPassword()
        {
            return _password;
        }

        public String getImageFileName()
        {
            return _imageFileName;
        }

        public String[] getHashtags()
        {
            return _hashtags;
        }

        public String getAnnouncementText()
        {
            return _announcementText;
        }

        public String getAuthEndpoint()
        {
            return _authEndpoint;
        }

        public String getPostEndpoint()
        {
            return _postEndpoint;
        }

        public String getBlobUploadEndpoint()
        {
            return _blobUploadEndpoint;
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testConvertToArray()
        {
            String input = "proteomics, proteomicssky, massspec, massspecsky";
            String[] expected = {"proteomics", "proteomicssky", "massspec", "massspecsky"};
            Arrays.sort(expected);
            compareSorted(expected, input);

            // Test for null string
            assertArrayEquals(new String[0], BlueskySettings.convertToArray(null));

            // Test for empty string
            assertArrayEquals(new String[0], BlueskySettings.convertToArray(""));

            // Test for input with leading, trailing and internal spaces
            input = " proteomics, proteomics sky, massspec , massspecsky ";
            compareSorted(expected, input);

            // Test for input with duplicates
            input = " proteomics, massspec, proteomics sky, massspec , massspecsky, proteomics ";
            compareSorted(expected, input);

            // Test for input with '#' characters
            input = " #proteomics, proteomics sky,  # massspec , massspecsky, #massspec ";
            compareSorted(expected, input);

            // Test for single tag TODO: remove
            assertArrayEquals(new String[]{"proteomics"}, BlueskySettings.convertToArray("#proteomics"));
            input = "proteomics,proteomics sky,#massspec, massspecsky,proteomics";
            compareSorted(expected, input);
        }

        private void compareSorted(String[] expected, String input)
        {
            String[] actual = BlueskySettings.convertToArray(input);
            Arrays.sort(actual);
            assertArrayEquals(expected, actual);
        }

        @Test
        public void testHashtagGetters()
        {
            BlueskySettings settings = new BlueskySettings();

            // Test primary account hashtags
            settings.setHashtags(" #proteomics, proteomics sky,  # massspec , massspecsky, #massspec ");
            String[] expectedMainTags = {"proteomics", "proteomicssky", "massspec", "massspecsky"};
            // Set test account hashtags
            settings.setTestHashtags(" #panoramapublictest, panoramaweb test ");
            String[] expectedTestTags = {"panoramapublictest", "panoramawebtest"};

            assertArrayEquals(expectedMainTags, settings.getHashtagArray());
            assertArrayEquals(expectedTestTags, settings.getTestHashtagArray());

            ClientConfig config = new ClientConfig(settings, false);
            assertArrayEquals(expectedMainTags, config.getHashtags());
            config = new ClientConfig(settings, true);
            assertArrayEquals(expectedTestTags, config.getHashtags());
        }

        @Test
        public void testCredentialsGetters()
        {
            BlueskySettings settings = new BlueskySettings();

            settings.setAccount("main-account");
            settings.setPassword("main-account-password");
            settings.setTestAccount("test-account");
            settings.setTestAccountPassword("test-account-password");

            // Test account getter with parameter
            Assert.assertEquals("main-account", settings.getAccount(false));
            Assert.assertEquals("test-account", settings.getAccount(true));
            Assert.assertEquals("main-account-password", settings.getPassword(false));
            Assert.assertEquals("test-account-password", settings.getPassword(true));

            ClientConfig config = new ClientConfig(settings, false);
            Assert.assertEquals("main-account", config.getAccount());
            Assert.assertEquals("main-account-password", config.getPassword());
            config = new ClientConfig(settings, true);
            Assert.assertEquals("test-account", config.getAccount());
            Assert.assertEquals("test-account-password", config.getPassword());
        }

        @Test
        public void testGetAnnouncementText()
        {
            String announcementText = "New data available on Panorama Public!";
            // Test with null hashtags
            BlueskySettings settings = new BlueskySettings();
            settings.setAnnouncementText(announcementText);
            settings.setHashtags(null);
            settings.setTestHashtags(null);

            // Primary account, no hashtags
            String text = BlueskyApiClient.getAnnouncement(new ClientConfig(settings, false));
            Assert.assertEquals(announcementText, text);
            // Test account, no hashtags
            String testText = BlueskyApiClient.getAnnouncement(new ClientConfig(settings, true));
            Assert.assertEquals(announcementText, testText);

            // Test with empty hashtags
            settings = new BlueskySettings();
            settings.setAnnouncementText(announcementText);
            settings.setHashtags("");
            settings.setTestHashtags("");

            // Primary account, no hashtags
            text = BlueskyApiClient.getAnnouncement(new ClientConfig(settings, false));
            Assert.assertEquals(announcementText, text);
            // Test account, no hashtags
            testText = BlueskyApiClient.getAnnouncement(new ClientConfig(settings, true));
            Assert.assertEquals(announcementText, testText);

            // Test with single hashtag
            settings = new BlueskySettings();
            announcementText = "Check out this new data on Panorama Public!";
            settings.setAnnouncementText(announcementText);
            settings.setHashtags("skyline");
            settings.setTestHashtags("panoramapublic");

            // Primary account, single hashtag
            text = BlueskyApiClient.getAnnouncement(new ClientConfig(settings, false));
            Assert.assertEquals(announcementText + " #skyline", text);
            // Test account, single hashtag
            testText = BlueskyApiClient.getAnnouncement(new ClientConfig(settings, true));
            Assert.assertEquals(announcementText + " #panoramapublic", testText);

            // Test with multiple hashtags
            settings = new BlueskySettings();
            announcementText = "New Panorama Public data available.";
            settings.setAnnouncementText(announcementText);
            settings.setHashtags("proteomics, proteomicssky, massspec, massspecsky");
            settings.setTestHashtags("panoramapublictest, panoramawebtest");

            // Primary account, multiple hashtags
            text = BlueskyApiClient.getAnnouncement(new ClientConfig(settings, false));
            Assert.assertEquals(announcementText + " #proteomics #proteomicssky #massspec #massspecsky", text);
            // Test account, multiple hashtags
            testText = BlueskyApiClient.getAnnouncement(new ClientConfig(settings, true));
            Assert.assertEquals(announcementText + " #panoramapublictest #panoramawebtest", testText);
        }
    }
}

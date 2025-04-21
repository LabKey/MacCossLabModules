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
    public static String ANNOUNCEMENT_TEXT = "New data available on Panorama Public!";

    protected static final Logger logger = LogHelper.getLogger(BlueskyApiClient.class, "BlueskyService logger");

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
        String account = testAccount ? settings.getTestAccount() : settings.getAccount();
        String password = testAccount ? settings.getTestAccountPassword() : settings.getPassword();

        if(StringUtils.isBlank(account))
        {
            throw new BlueskyException(String.format("Cannot find Bluesky %saccount.", testAccount ? "test " : ""));
        }
        if(StringUtils.isBlank(password))
        {
            throw new BlueskyException(String.format("Cannot find password for Bluesky %saccount.", testAccount ? "test " : ""));
        }
        if(StringUtils.isBlank(settings.getAuthEndpoint()))
        {
            throw new BlueskyException("Bluesky auth endpoint not configured");
        }

        JSONObject requestBody = new JSONObject();
        requestBody.put("identifier", account);
        requestBody.put("password", password);

        HttpPost httpPost = new HttpPost(settings.getAuthEndpoint());
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setEntity(new StringEntity(requestBody.toString(), ContentType.APPLICATION_JSON));

        logger.debug(String.format("Logging into Bluesky as '%s' at endpoint '%s'", account, settings.getAuthEndpoint()));

        BlueskyResponse response;
        try (CloseableHttpClient httpClient = HttpClients.createDefault())
        {
            response = getResponse(httpClient, httpPost, "Bluesky login failed");
            JSONObject responseJson = response.getJsonObject();
            String accessJwt = responseJson.getString("accessJwt");
            String did = responseJson.getString("did");
            return new LoginInfo(accessJwt, did);
        }
        catch (IOException | JSONException e)
        {
            throw new BlueskyException("Bluesky login failed", e);
        }
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
    public String createPost(@NotNull ExperimentAnnotations exptAnnotations, BlueskySettings settings, boolean testPost) throws BlueskyException
    {
        return createPost(exptAnnotations, settings, login(settings, testPost), testPost);
    }

    /**
     * Create a post on Bluesky announcing the data.  The post is created only if a post wasn't already created.
     */
    @Nullable
    public String createPostIfNotExists(@NotNull ExperimentAnnotations exptAnnotations, BlueskySettings settings, boolean testPost) throws BlueskyException
    {
        if (BlueskySettingsManager.getPostUrlForExperiment(exptAnnotations) != null)
        {
            // There is already a post on Bluesky related to this data
            return null;
        }

        return createPost(exptAnnotations, settings, testPost);
    }

    @NotNull
    public String createPost(@NotNull ExperimentAnnotations exptAnnotations, @NotNull BlueskySettings settings,
                              @NotNull LoginInfo loginInfo, boolean testPost) throws BlueskyException
    {
        if(StringUtils.isBlank(settings.getAuthEndpoint()))
        {
            throw new BlueskyException("Bluesky auth endpoint not configured");
        }
        if(StringUtils.isBlank(settings.getPostEndpoint()))
        {
            throw new BlueskyException("Bluesky post endpoint not configured");
        }
        if(StringUtils.isBlank(settings.getBlobUploadEndpoint()))
        {
            throw new BlueskyException("Bluesky image upload endpoint not configured");
        }
        if (exptAnnotations.getShortUrl() == null)
        {
            throw new BlueskyException(String.format("Experiment with Id %d does not have a short access URL", exptAnnotations.getId()));
        }

        JSONObject requestBody = createRequestBody(exptAnnotations, testPost, settings, loginInfo);

        HttpPost httpPost = new HttpPost(settings.getPostEndpoint());
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setHeader("Authorization", "Bearer " + loginInfo.getAccessJwt());
        httpPost.setEntity(new StringEntity(requestBody.toString(), ContentType.APPLICATION_JSON));

        logger.debug(String.format("Posting into Bluesky at endpoint '%s' for Panorama Public data at '%s'",
                settings.getPostEndpoint(), exptAnnotations.getShortUrl().renderShortURL()));

        BlueskyResponse response;
        String blueskyPostUrl;
        try (CloseableHttpClient httpClient = HttpClients.createDefault())
        {
            response = getResponse(httpClient, httpPost, "Post creation failed");
            JSONObject responseJson = response.getJsonObject();
            if (responseJson.has("uri"))
            {
                blueskyPostUrl = responseJson.getString("uri");
            }
            else
            {
                throw new BlueskyException("Post creation failed - Missing URI in response.", response);
            }
            if (StringUtils.isBlank(blueskyPostUrl))
            {
                throw new BlueskyException("Post URI in response is blank.", response);
            }
        }
        catch (IOException | JSONException e)
        {
            throw new BlueskyException("Post creation failed.", e);
        }

        BlueskySettingsManager.savePostUrlForExperiment(exptAnnotations, blueskyPostUrl);

        return blueskyPostUrl;
    }

    @NotNull
    private JSONObject createRequestBody(@NotNull ExperimentAnnotations exptAnnotations, boolean testPost,
                                         @NotNull BlueskySettings settings, @NotNull LoginInfo loginInfo) throws BlueskyException
    {
        JSONObject record = buildRecord(exptAnnotations, testPost, settings, loginInfo);

        return new JSONObject()
                .put("repo", loginInfo.getDid())
                .put("collection", "app.bsky.feed.post")
                .put("record", record);
    }

    @NotNull
    private JSONObject buildRecord(@NotNull ExperimentAnnotations exptAnnotations, boolean testPost,
                                   @NotNull BlueskySettings settings, @NotNull LoginInfo loginInfo) throws BlueskyException
    {
        String[] hashtags = testPost ? settings.getTestHashtagArray() : settings.getHashtagArray();
        String text = getPostText(hashtags);

        JSONObject record = new JSONObject()
                .put("$type", "app.bsky.feed.post")
                .put("text", text)
                .put("createdAt", Instant.now().toString());

        // Add hashtag facets if any
        JSONArray facets = buildHashtagFacets(text, hashtags);
        if (!facets.isEmpty())
        {
            record.put("facets", facets);
        }

        // Build and attach the embed object
        record.put("embed", buildEmbed(exptAnnotations, settings, loginInfo));
        return record;
    }

    /**
     * Generate post text with hashtags
     */
    private static String getPostText(@NotNull String[] hashtags)
    {
        return ANNOUNCEMENT_TEXT + " " + formatHashtags(hashtags);
    }

    /**
     * Convert an array of hashtag strings to a comma-separated string with '#' prefix
     */
    private static String formatHashtags(@NotNull String[] hashtags)
    {
        return Arrays.stream(hashtags)
                .map(tag -> "#" + tag)
                .collect(Collectors.joining(", "));
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
    private JSONObject buildEmbed(@NotNull ExperimentAnnotations exptAnnotations, @NotNull BlueskySettings settings, @NotNull LoginInfo loginInfo) throws BlueskyException
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
        external.put("thumb", getImageBlobReference(exptAnnotations, settings, loginInfo));

        embed.put("external", external);
        return embed;
    }

    private JSONObject getImageBlobReference(ExperimentAnnotations exptAnnotations, BlueskySettings settings, LoginInfo loginInfo) throws BlueskyException
    {
        AttachmentParent attachmentParent = null;
        // First check if the data submitter provided a catalog entry for the data.
        Attachment attachment = getCatalogEntryAttachment(exptAnnotations);
        if (attachment != null)
        {
            attachmentParent = new CatalogImageAttachmentParent(exptAnnotations.getShortUrl(), exptAnnotations.getContainer());
        }
        else if (!StringUtils.isBlank(settings.getImageFileName()))
        {
            // If the data does not have a catalog entry, or we were unable to get it, use the Panorama Public logo
            attachmentParent = PanoramaPublicLogoAttachmentParent.get();
            if (attachmentParent == null)
            {
                throw new BlueskyException("Unable to initialize PanoramaPublicLogoAttachmentParent. Perhaps a Panorama Public project does not exist on the server.");
            }
            attachment = PanoramaPublicLogoManager.getNewDataLogo(settings.getImageFileName());
        }

        if (attachment == null)
        {
            throw new BlueskyException("Unable to find an image file to include in the post.");
        }

        JSONObject blobResponse = uploadImage(attachment, attachmentParent, settings, loginInfo);
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
    private JSONObject uploadToBluesky(byte[] imageBytes, String mimeType, BlueskySettings settings, LoginInfo loginInfo) throws BlueskyException
    {
        HttpPost httpPost = new HttpPost(settings.getBlobUploadEndpoint());
        httpPost.setHeader("Content-Type", mimeType);
        httpPost.setHeader("Authorization", "Bearer " + loginInfo.getAccessJwt());
        httpPost.setEntity(new ByteArrayEntity(imageBytes, ContentType.create(mimeType)));

        try (CloseableHttpClient httpClient = HttpClients.createDefault())
        {
            BlueskyResponse response = getResponse(httpClient, httpPost, "Failed to upload image to Bluesky");
            return response.getJsonObject();
        }
        catch (IOException | JSONException e)
        {
            throw new BlueskyException("Failed to upload image to Bluesky", e);
        }
    }

    @NotNull
    private BlueskyResponse getResponse(CloseableHttpClient httpClient, HttpPost httpPost, String failureMessage) throws IOException, BlueskyException
    {
        BlueskyResponse response = httpClient.execute(httpPost, new BlueskyResponseHandler());
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
                                   BlueskySettings settings, LoginInfo loginInfo) throws BlueskyException
    {
        try (InputStream is = AttachmentService.get().getInputStream(parent, attachment.getName()))
        {
            byte[] imageBytes = is.readAllBytes();
            String mimeType = PageFlowUtil.getContentTypeFor(attachment.getName());
            return uploadToBluesky(imageBytes, mimeType, settings, loginInfo);
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
        @Override
        public BlueskyResponse handleResponse(ClassicHttpResponse response) throws IOException
        {
            try
            {
                HttpEntity entity = response.getEntity();
                String content = entity != null ? EntityUtils.toString(entity) : null;
                return new BlueskyResponse(response.getCode(), response.getReasonPhrase(), content);
            }
            catch (ParseException e)
            {
                throw new IOException("Failed to parse response content", e);
            }
        }
    }

    private static final Pattern AT_URI = Pattern.compile(
            "^at://([^/]+)/app\\.bsky\\.feed\\.post/([^/]+)$"
    );


    /**
     * Converts an AT Protocol URI to a Bluesky web URL.
     * Returns null if the input cannot be converted to a valid Bluesky URL.
     * Example: at://</did>/app.bsky.feed.post/<post_id>
     * Convert to: https://bsky.app/profile/<did>/post/<post_id>
     */
    public static String tryFormatBlueskyUrl(String postUri)
    {
        if (StringUtils.isBlank(postUri))
        {
            return null;
        }

        Matcher m = AT_URI.matcher(postUri.trim());
        return m.matches()
                ? "https://bsky.app/profile/" + m.group(1) + "/post/" + m.group(2)
                : null;
    }
}

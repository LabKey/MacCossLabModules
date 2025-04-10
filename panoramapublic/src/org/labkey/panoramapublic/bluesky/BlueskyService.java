package org.labkey.panoramapublic.bluesky;

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
import java.util.stream.Collectors;

public class BlueskyService
{
    public static final String CREDENTIALS = "Bluesky Credentials";
    public static final String USER = "Bluesky User";
    public static final String PASSWORD = "Bluesky Password";

    public static final String TEST_USER = "Bluesky Test User";
    public static final String TEST_PASSWORD = "Bluesky Test Password";

    public static final String BLUESKY_LINK = "Bluesky link";

    public static String ANNOUNCEMENT_TEXT = "New data available on Panorama Public!";
    public static String[] HASHTAGS = {"proteomics", "proteomicssky", "massspec", "massspecsky"};
    public static String[] TEST_HASHTAGS = {"panoramapublictest", "panoramawebtest"};

    // API endpoints
    //  Endpoint for authentication and creating a session with Bluesky
    private static final String AUTH_URL = "https://bsky.social/xrpc/com.atproto.server.createSession";
    // Endpoint for creating any type of record in Bluesky, including posts
    private static final String POST_URL = "https://bsky.social/xrpc/com.atproto.repo.createRecord";
    private static final String BLOB_UPLOAD_URL = "https://bsky.social/xrpc/com.atproto.repo.uploadBlob";

    protected static final Logger logger = LogHelper.getLogger(BlueskyService.class, "BlueskyService logger");

    /**
     * Login to Bluesky and obtain auth tokens
     */
    @NotNull
    public LoginInfo login(@NotNull String identifier, @NotNull String password) throws BlueskyException
    {
        JSONObject requestBody = new JSONObject();
        requestBody.put("identifier", identifier);
        requestBody.put("password", password);

        HttpPost httpPost = new HttpPost(AUTH_URL);
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setEntity(new StringEntity(requestBody.toString(), ContentType.APPLICATION_JSON));

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
    public String createBlueskyPost(@NotNull ExperimentAnnotations exptAnnotations, boolean testPost, String identifier, String password) throws BlueskyException
    {
        LoginInfo loginInfo = login(identifier, password);
        return createBlueskyPost(exptAnnotations, testPost, loginInfo);
    }

    @NotNull
    public String createBlueskyPost(@NotNull ExperimentAnnotations exptAnnotations, boolean testPost, @NotNull LoginInfo loginInfo) throws BlueskyException
    {
        JSONObject requestBody = createRequestBody(exptAnnotations, testPost, loginInfo);

        HttpPost httpPost = new HttpPost(POST_URL);
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setHeader("Authorization", "Bearer " + loginInfo.getAccessJwt());
        httpPost.setEntity(new StringEntity(requestBody.toString(), ContentType.APPLICATION_JSON));

        BlueskyResponse response;
        try (CloseableHttpClient httpClient = HttpClients.createDefault())
        {
            response = getResponse(httpClient, httpPost, "Post creation failed");
            JSONObject responseJson = response.getJsonObject();
            if (responseJson.has("uri"))
            {
                return responseJson.getString("uri");
            }
            else
            {
                throw new BlueskyException("Post creation failed - Missing URI in response.", response);
            }


        }
        catch (IOException | JSONException e)
        {
            throw new BlueskyException("Post creation failed.", e);
        }
    }

    @NotNull
    private JSONObject createRequestBody(ExperimentAnnotations exptAnnotations, boolean testPost, LoginInfo loginInfo) throws BlueskyException
    {
        String title = exptAnnotations.getTitle();
        String panoramaPublicLink = exptAnnotations.getShortUrl().renderShortURL();

        String[] hashtags = testPost ? TEST_HASHTAGS : HASHTAGS;
        String postText = getPostText(hashtags);

        // Create the post record
        JSONObject record = new JSONObject();
        record.put("$type", "app.bsky.feed.post");
        record.put("text", postText);
        record.put("createdAt", Instant.now().toString());

        addHashTags(record, postText, hashtags);

        // Create the embed object for a web card
        JSONObject embed = new JSONObject();
        embed.put("$type", "app.bsky.embed.external");

        JSONObject external = new JSONObject();
        external.put("uri", panoramaPublicLink);
        external.put("title", title);
        external.put("description", panoramaPublicLink);


        AttachmentParent attachmentParent;
        // First check if the user has provided a catalog entry for the data.
        Attachment attachment = getCatalogEntryAttachment(exptAnnotations);
        if (attachment != null)
        {
            attachmentParent = new CatalogImageAttachmentParent(exptAnnotations.getShortUrl(), exptAnnotations.getContainer());
        }
        else
        {
            // If the data does not have a catalog entry, or we were unable to get it, use the Panorama Public logo
            attachmentParent = PanoramaPublicLogoAttachmentParent.get();
            if (attachmentParent == null)
            {
                throw new BlueskyException("Unable to initialize PanoramaPublicLogoAttachmentParent. Perhaps a Panorama Public project does not exist on the server.");
            }
            attachment = PanoramaPublicLogoManager.getNewDataLogo();
        }

        if (attachment == null)
        {
            throw new BlueskyException("Unable to find an image file to include in the post.");
        }

        JSONObject blobResponse = uploadImage(attachment, attachmentParent, loginInfo);
        // Get the blob reference from the response
        if (blobResponse.has("blob"))
        {
            JSONObject blob = blobResponse.getJSONObject("blob");
            external.put("thumb", blob);
        }
        else
        {
            throw new BlueskyException("Blob reference not found in Bluesky response after uploading the image file.");
        }

        embed.put("external", external);
        record.put("embed", embed);

        // Create the request body
        JSONObject requestBody = new JSONObject();
        requestBody.put("repo", loginInfo.getDid());
        requestBody.put("collection", "app.bsky.feed.post");
        requestBody.put("record", record);
        return requestBody;
    }

    private Attachment getCatalogEntryAttachment(ExperimentAnnotations exptAnnotations)
    {
        CatalogEntry catalogEntry = CatalogEntryManager.getEntryForExperiment(exptAnnotations);
        return (catalogEntry != null && catalogEntry.getApproved()) ? catalogEntry.getAttachment() : null;
//        {
//            // Use the catalog entry image provided by the submitter
//            attachment = catalogEntry.getAttachment();
//            attachmentParent = new CatalogImageAttachmentParent(exptAnnotations.getShortUrl(), exptAnnotations.getContainer());
//        }
    }

    /**
     * Generate post text with hashtags
     */
    private String getPostText(String[] hashtags)
    {
        return ANNOUNCEMENT_TEXT + " " + formatHashtags(hashtags);
    }

    /**
     * Convert an array of hashtag strings to a comma-separated string with '#' prefix
     */
    private String formatHashtags(String[] hashtags)
    {
        return Arrays.stream(hashtags)
                .map(tag -> "#" + tag)
                .collect(Collectors.joining(", "));
    }

    /**
     * Add hashtag facets to the record for Bluesky's rich text formatting
     */
    private void addHashTags(JSONObject record, String postText, String[] hashtags)
    {
        JSONArray facets = createHashtagFacets(postText, hashtags);

        // Add facets to the record if we have any
        if (!facets.isEmpty())
        {
            record.put("facets", facets);
        }
    }

    /**
     * Create hashtag facets for Bluesky's rich text formatting
     */
    private JSONArray createHashtagFacets(String postText, String[] hashtags)
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
    private JSONObject createSingleHashtagFacet(String postText, String tag)
    {
        // Find the tag in the text (including the # character)
        String hashtagInText = "#" + tag;
        int tagStart = postText.indexOf(hashtagInText);

        if (tagStart == -1) {
            return null;
        }

        // Convert to byte position for UTF-8
        byte[] beforeTagBytes = postText.substring(0, tagStart).getBytes(StandardCharsets.UTF_8);
        int byteStart = beforeTagBytes.length;

        byte[] tagBytes = hashtagInText.getBytes(StandardCharsets.UTF_8);
        int byteEnd = byteStart + tagBytes.length;

        // Create the tag facet
        JSONObject tagFacet = new JSONObject();

        // Create index object
        JSONObject indices = new JSONObject();
        indices.put("byteStart", byteStart);
        indices.put("byteEnd", byteEnd);
        tagFacet.put("index", indices);

        // Create features array
        JSONArray features = new JSONArray();
        JSONObject tagFeature = new JSONObject();
        tagFeature.put("$type", "app.bsky.richtext.facet#tag");
        tagFeature.put("tag", tag); // Tag without the # character
        features.put(tagFeature);
        tagFacet.put("features", features);

        return tagFacet;
    }

    /**
     * Upload image bytes to Bluesky
     */
    @NotNull
    private JSONObject uploadToBluesky(byte[] imageBytes, String mimeType, LoginInfo loginInfo) throws BlueskyException
    {
        HttpPost httpPost = new HttpPost(BLOB_UPLOAD_URL);
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
    private static BlueskyResponse getResponse(CloseableHttpClient httpClient, HttpPost httpPost, String failureMessage) throws IOException, BlueskyException
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
    private JSONObject uploadImage(@NotNull Attachment attachment, @NotNull AttachmentParent parent, LoginInfo loginInfo) throws BlueskyException
    {
        try (InputStream is = AttachmentService.get().getInputStream(parent, attachment.getName()))
        {
            byte[] imageBytes = is.readAllBytes();
            String mimeType = PageFlowUtil.getContentTypeFor(attachment.getName());
            return uploadToBluesky(imageBytes, mimeType, loginInfo);
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

    /**
     * Convert a Bluesky post URI to a clickable URL
     * Example: at://<did></did>/app.bsky.feed.post/<post_id>
     * Convert to: https://bsky.app/profile/<did>/post/<post_id>
     */
    public static String convertToClickableUrl(String postUri)
    {
        // Handle URIs with or without leading slashes
        String cleanUri = postUri.replaceFirst("^//", "");

        // Extract the DID and post ID
        String did = "";
        String postId = "";

        if (cleanUri.startsWith("at://"))
        {
            cleanUri = cleanUri.substring(5); // Remove "at://"
        }

        String[] parts = cleanUri.split("/");
        if (parts.length >= 1)
        {
            did = parts[0];
        }

        if (parts.length >= 3)
        {
            postId = parts[parts.length - 1];
        }

        return "https://bsky.app/profile/" + did + "/post/" + postId;
    }
}

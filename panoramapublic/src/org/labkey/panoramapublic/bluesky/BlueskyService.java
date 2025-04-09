package org.labkey.panoramapublic.bluesky;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.panoramapublic.catalog.CatalogImageAttachmentParent;
import org.labkey.panoramapublic.model.CatalogEntry;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.query.CatalogEntryManager;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    // public static String[] HASHTAGS = {"proteomics", "proteomicssky", "massspec", "massspecsky"};
    public static String[] TEST_HASHTAGS = {"panoramapublictest", "panoramawebtest"};

    //  Endpoint for authentication and creating a session with Bluesky
    private static final String AUTH_URL = "https://bsky.social/xrpc/com.atproto.server.createSession";
    // Endpoint for creating any type of record in Bluesky, including posts
    private static final String POST_URL = "https://bsky.social/xrpc/com.atproto.repo.createRecord";


    private String _accessJwt;
    private String _did; // decentralized Identifier

    protected static final Logger logger = LogHelper.getLogger(BlueskyService.class, "BlueskyService logger");

    /**
     * Get the DID (Decentralized Identifier) of the logged-in user
     * @return DID string
     */
    public String getDid()
    {
        return _did;
    }

    /**
     * Login to Bluesky and obtain auth tokens
     */
    public void login(String identifier, String password) throws BlueskyException
    {
        JSONObject requestBody = new JSONObject();
        requestBody.put("identifier", identifier);
        requestBody.put("password", password);

        HttpURLConnection connection = null;
        try
        {
            URL url = new URL(AUTH_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000); // 10 seconds
            connection.setReadTimeout(10000); // 10 seconds

            // Write request body
            try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream()))
            {
                wr.write(requestBody.toString().getBytes(StandardCharsets.UTF_8));
                wr.flush();
            }

            int responseCode = connection.getResponseCode();
            String response;
            try (InputStream in = connection.getInputStream())
            {
                response = IOUtils.toString(in, StandardCharsets.UTF_8);
            }
            catch (IOException e)
            {
                try (InputStream in = connection.getErrorStream())
                {
                    response = IOUtils.toString(in, StandardCharsets.UTF_8);
                }
            }
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED)
            {
                JSONObject responseJson = new JSONObject(response);
                _accessJwt = responseJson.getString("accessJwt");
                _did = responseJson.getString("did");
            }
            else
            {
                throw new BlueskyException("Bluesky login failed", new BlueskyResponse(responseCode, connection.getResponseMessage(), response));
            }
        }
        catch (IOException e)
        {
            throw new BlueskyException("Bluesky login failed", e);
        }
        finally
        {
            if (connection != null)
            {
                connection.disconnect();
            }
        }
    }

    public String createBlueskyPost(ExperimentAnnotations exptAnnotations, boolean testPost) throws BlueskyException
    {
        if (_accessJwt == null || _did == null)
        {
            throw new BlueskyException("Not logged in. Call login() first.");
        }

        String title = exptAnnotations.getTitle();
        String panoramaPublicLink = exptAnnotations.getShortUrl().renderShortURL();
        // "https://panoramaweb.org/KsL1do.url" // https://panoramaweb.org/zobellia_sulfatases.url";

        String[] hashtags = TEST_HASHTAGS; // testPost ? TEST_HASHTAGS : HASHTAGS;
        String postText = getPostText(hashtags);
        // Create the post record
        JSONObject record = new JSONObject();
        record.put("$type", "app.bsky.feed.post");
        record.put("text", postText);
        record.put("createdAt", java.time.Instant.now().toString());

        addHashTags(record, postText, hashtags);

        // Create the embed object for a web card
        JSONObject embed = new JSONObject();
        embed.put("$type", "app.bsky.embed.external");

        JSONObject external = new JSONObject();
        external.put("uri", panoramaPublicLink);
        external.put("title", title);
        external.put("description", panoramaPublicLink);

        JSONObject blobResponse = null;
        try
        {
            CatalogEntry catalogEntry = CatalogEntryManager.getEntryForExperiment(exptAnnotations);
            if (catalogEntry != null && catalogEntry.getApproved())
            {
                // Add the catalog entry image provided by the submitter
                Attachment attachment = catalogEntry.getAttachment();
//                ActionURL downloadLink = PanoramaPublicController.getCatalogImageDownloadUrl(exptAnnotations, catalogEntry.getImageFileName());
//                blobResponse= uploadCatalogImage(downloadLink);
                blobResponse = attachment != null ? uploadImage(catalogEntry.getAttachment(),
                        new CatalogImageAttachmentParent(exptAnnotations.getShortUrl(), exptAnnotations.getContainer())) : null;
            }
            else
            {
                // If a catalog entry was not provided, use the Panorama Public logo
                Attachment logoAttachment = PanoramaPublicLogoManager.getNewDataLogo();
                blobResponse = uploadImage(logoAttachment, PanoramaPublicLogoAttachmentParent.get());
//                if (logoAttachment != null)
//                {
//                    File file = logoAttachment.getFile();
//                    Path imageFilePath = (file != null && file.exists()) ? file.toPath() : null;
//                   if (imageFilePath != null && Files.exists(imageFilePath))
//                    {
//                        blobResponse = uploadImage(imageFilePath);
//                    }
//                    else
//                    {
//                        logger.warn("Unable to find image file. " + imageFilePath != null ? imageFilePath.toString() : "");
//                    }
//                }
            }
            // Get the blob reference from the response
            JSONObject blob = blobResponse != null ? blobResponse.getJSONObject("blob") : null;
            if (blob != null)
            {
                external.put("thumb", blob);
            }
            else
            {
                logger.warn("Blob reference not found in Bluesky response");
            }
        }
        catch (BlueskyException | JSONException e)
        {
            // We will log the error, but submit the post without an image.
            logger.error(e.getMessage());
        }

        embed.put("external", external);
        record.put("embed", embed);

        // Create the request body
        JSONObject requestBody = new JSONObject();
        requestBody.put("repo", _did);
        requestBody.put("collection", "app.bsky.feed.post");
        requestBody.put("record", record);

        HttpURLConnection connection = null;
        try
        {
            URL url = new URL(POST_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + _accessJwt);
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000); // 10 seconds
            connection.setReadTimeout(10000); // 10 seconds

            // Write request body
            try (OutputStream os = connection.getOutputStream())
            {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            String response;
            try (InputStream in = connection.getInputStream())
            {
                response = IOUtils.toString(in, StandardCharsets.UTF_8);
            }
            catch (IOException e)
            {
                try (InputStream in = connection.getErrorStream())
                {
                    response = IOUtils.toString(in, StandardCharsets.UTF_8);
                }
            }

            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED)
            {
                JSONObject responseJson = new JSONObject(response);
                if (responseJson.has("uri"))
                {
                    return responseJson.getString("uri");
                }
                else
                {
                    throw new BlueskyException("Post creation failed - Missing URI in response",
                            new BlueskyResponse(responseCode, connection.getResponseMessage(), response));
                }
            }
            else
            {
                throw new BlueskyException("Post creation failed.", new BlueskyResponse(responseCode, connection.getResponseMessage(), response));
            }
        }
        catch (IOException e)
        {
            throw new BlueskyException("Post creation failed.", e);
        }
        finally
        {
            if (connection != null)
            {
                connection.disconnect();
            }
        }
    }

    private String getPostText(String[] hashtags)
    {
        return ANNOUNCEMENT_TEXT + " " + Arrays.stream(hashtags).map(tag -> "#" + tag).collect(Collectors.joining(", "));
    }

    private void addHashTags(JSONObject record, String postText, String[] hashtags)
    {
        // Create facets for the hashtags
        JSONArray facets = new JSONArray();

        for (String tag : hashtags) {
            // Find the tag in the text (including the # character)
            String hashtagInText = "#" + tag;
            int tagStart = postText.indexOf(hashtagInText);

            if (tagStart != -1) {
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

                facets.put(tagFacet);
            }
        }

        // Add facets to the record if we have any
        if (facets.length() > 0)
        {
            record.put("facets", facets);
        }
    }

//    @Nullable
//    private JSONObject uploadImage(Path imageFilePath) throws BlueskyException
//    {
//        if (imageFilePath == null || !Files.exists(imageFilePath))
//        {
//            return null;
//        }
//
//        try
//        {
//            // Get image bytes
//            byte[] imageBytes = Files.readAllBytes(imageFilePath);
//            String mimeType = PageFlowUtil.getContentTypeFor(imageFilePath.getFileName().toString());
//            return uploadToBluesky(imageBytes, mimeType);
//        }
//        catch (IOException e)
//        {
//            throw new BlueskyException("Failed to upload image to Bluesky", e);
//        }
//    }

    @NotNull
    private JSONObject uploadToBluesky(byte[] imageBytes, String mimeType) throws IOException, BlueskyException
    {
        // Upload to Bluesky
        String BLOB_UPLOAD_URL = "https://bsky.social/xrpc/com.atproto.repo.uploadBlob";
        URL uploadUrl = new URL(BLOB_UPLOAD_URL);
        HttpURLConnection uploadConnection = (HttpURLConnection) uploadUrl.openConnection();
        uploadConnection.setRequestMethod("POST");
        uploadConnection.setRequestProperty("Content-Type", mimeType);
        uploadConnection.setRequestProperty("Authorization", "Bearer " + _accessJwt);
        uploadConnection.setDoOutput(true);

        // Write image bytes
        try (OutputStream os = uploadConnection.getOutputStream())
        {
            os.write(imageBytes);
        }

        // Process response
        int responseCode = uploadConnection.getResponseCode();
        String response;
        try (InputStream in = uploadConnection.getInputStream())
        {
            response = IOUtils.toString(in, StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            try (InputStream in = uploadConnection.getErrorStream())
            {
                response = IOUtils.toString(in, StandardCharsets.UTF_8);
            }
            throw new BlueskyException("Failed to upload image to Bluesky",
                    new BlueskyResponse(responseCode, uploadConnection.getResponseMessage(), response));
        }
        return new JSONObject(response);
    }

    private JSONObject uploadImage(@NotNull Attachment attachment, @NotNull AttachmentParent parent) throws BlueskyException
    {
        try (InputStream is = AttachmentService.get().getInputStream(parent, attachment.getName()))
        {
            byte[] imageBytes = is.readAllBytes();
            String mimeType = PageFlowUtil.getContentTypeFor(attachment.getName());
            return uploadToBluesky(imageBytes, mimeType);
        }
        catch (FileNotFoundException e)
        {
            logger.error("Image attachment file not found " + attachment.getName(), e);
        }
        catch (IOException e)
        {
            logger.error("Error reading image file " + attachment.getName(), e);
        }
        return null;
    }
    public JSONObject uploadCatalogImage(ActionURL catalogEntryUrl) throws BlueskyException
    {
        try {
            // Download the image first
            URL url = new URL(catalogEntryUrl.getURIString());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // Get image bytes
            byte[] imageBytes;
            try (InputStream in = connection.getInputStream())
            {
                imageBytes = IOUtils.toByteArray(in);
            }

            // Determine MIME type
            String mimeType = connection.getContentType();
            if (mimeType == null || mimeType.isEmpty())
            {
                mimeType = "image/png"; // Default to PNG if unknown
            }

            return uploadToBluesky(imageBytes, mimeType);
        }
        catch (IOException e)
        {
            throw new BlueskyException("Failed to upload image", e);
        }
    }



    public static String convertToClickableUrl(String postUri)
    {
        // Handle URIs with or without leading slashes
        String cleanUri = postUri.replaceFirst("^//", "");

        // Extract the DID and post ID
        String did = "";
        String postId = "";

        if (cleanUri.startsWith("at://")) {
            cleanUri = cleanUri.substring(5); // Remove "at://"
        }

        String[] parts = cleanUri.split("/");
        if (parts.length >= 1) {
            did = parts[0];
        }

        if (parts.length >= 3) {
            postId = parts[parts.length - 1];
        }

        return "https://bsky.app/profile/" + did + "/post/" + postId;
    }
}

package org.labkey.panoramapublic.datacite;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.User;
import org.labkey.api.util.DOM;
import org.labkey.api.util.DateUtil;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.JournalExperiment;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.labkey.api.util.DOM.BR;
import static org.labkey.api.util.DOM.DIV;

/**
 * Class for creating, deleting or publishing DOIs with the DataCite API
 * https://support.datacite.org/reference/dois-2
 */
public class DataCiteService
{
    public static final String CREDENTIALS = "DataCite Credentials";
    public static final String USER = "DataCite User";
    public static final String PASSWORD = "DataCite Password";
    public static final String PREFIX = "DOI Prefix";

    public static final String TEST_USER = "Test DataCite User";
    public static final String TEST_PASSWORD = "Test DataCite Password";
    public static final String TEST_PREFIX = "Test DOI Prefix";

    /**
     * This will create a 'draft' DOI using either the live or the test DataCite API
     * @param test if true, the DataCite test API is used for creating the DOI
     */
    @NotNull
    public static Doi create(boolean test) throws DataCiteException
    {
        // curl -X POST -H "Content-Type: application/vnd.api+json" --user YOUR_REPOSITORY_ID:YOUR_PASSWORD -d @my_draft_doi.json https://api.test.datacite.org/dois
        DataCiteConfig config = getDataCiteConfig(test);
        METHOD method = METHOD.POST;
        DataCiteResponse response = doRequest(config, getCreateDoiJson(config), method);
        if(response.success(method))
        {
            Doi doi = response.getDoi();
            if(doi != null && doi.getDoi() != null) return doi;
            throw new DataCiteException("Could not parse DOI form response: ", response);
        }
        throw new DataCiteException("DOI could not be created", response);
    }

    /**
     * This will delete a DOI. The DataCite API (live or test) that will be used is determined by looking
     * at the DOI prefix of the given DOI.
     */
    public static void delete(@NotNull String doi) throws DataCiteException
    {
        // curl --request DELETE --url https://api.test.datacite.org/dois/id
        METHOD method = METHOD.DELETE;
        DataCiteResponse response = doRequest(getDataCiteConfig(doi), null, method);
        if(!response.success(method))
        {
            throw new DataCiteException("DOI could not be deleted", response);
        }
    }

    /**
     * This will publish a DOI, i.e make it 'findable'. The DataCite API (live or test) that will be used is determined by looking
     * at the DOI prefix in the DOI assigned to the given ExperimentAnnotations
     */
    public static void publish(@NotNull ExperimentAnnotations expAnnot, @NotNull JournalExperiment je) throws DataCiteException
    {
        // # PUT /dois/:id
        //$ curl -X PUT -H "Content-Type: application/vnd.api+json" --user YOUR_REPOSITORY_ID:YOUR_PASSWORD -d @my_doi_update.json https://api.test.datacite.org/dois/:id
        METHOD method = METHOD.PUT;
        DataCiteResponse response = doRequest(getDataCiteConfig(expAnnot.getDoi()), DoiMetadata.from(expAnnot, je).getJson(), method);
        if(!response.success(method))
        {
            throw new DataCiteException("DOI could not be published", response);
        }
    }

    private static DataCiteResponse doRequest(DataCiteConfig config, @Nullable JSONObject json, METHOD method) throws DataCiteException
    {
        String auth = Base64.getEncoder().encodeToString((config.getName() + ":" + config.getPassword()).getBytes(StandardCharsets.UTF_8));
        HttpURLConnection conn = null;
        try
        {
            URL url = new URL(config.getUrl());
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Authorization", "Basic " + auth);
            conn.setRequestMethod(method.name());
            if(json != null)
            {
                byte[] postData = json.toString().getBytes(StandardCharsets.UTF_8);
                int postDataLength = postData.length;

                conn.setRequestProperty("Content-Type", "application/vnd.api+json");
                conn.setRequestProperty("charset", "utf-8");
                conn.setRequestProperty("Content-Length", Integer.toString(postDataLength));
                conn.setUseCaches(false);
                try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream()))
                {
                    wr.write(postData);
                    wr.flush();
                }
            }

            int responseCode = conn.getResponseCode();
            String message = conn.getResponseMessage();
            String response;
            try (InputStream in = conn.getInputStream())
            {
                response = IOUtils.toString(in, StandardCharsets.UTF_8);
            }
            catch (IOException e)
            {
                try (InputStream in = conn.getErrorStream())
                {
                    response = IOUtils.toString(in, StandardCharsets.UTF_8);
                }
            }

            return new DataCiteResponse(responseCode, message, response);
        }
        catch (IOException e)
        {
            throw new DataCiteException("Error submitting " + method + " request to " + config.getUrl(), e);
        }
        finally
        {
            if (conn != null)
            {
                conn.disconnect();
            }
        }
    }

    private static JSONObject getCreateDoiJson(DataCiteConfig config)
    {
        /* Example:
         {
           "data": {
           "type": "dois",
           "attributes": {
              "prefix": "10.70027"
             }
           }
         }
         */
        return new JSONObject().put("data", Objects.requireNonNull(new JSONObject().put("type", "dois"))
                                                            .put("attributes", new JSONObject(Collections.singletonMap("prefix", config.getPrefix()))));
    }

    /**
     * This will return the DataCiteConfig object containing the account name, password, DOI prefix and API URL for the DataCite API
     * @param test if true, the returned DataCiteConfig will be for the DataCite test API
     */
    private static DataCiteConfig getDataCiteConfig(boolean test) throws DataCiteException
    {
        PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getWritableProperties(DataCiteService.CREDENTIALS, false);
        return getConfig(map, null, test);
    }

    /**
     * This will return the DataCiteConfig object containing the account name, password, DOI prefix and API URL for the DataCite API
     * that should be used for managing the given DOI. The DOI prefix is used to determine if the live or test API should be used.
     * @param doi
     */
    private static DataCiteConfig getDataCiteConfig(@NotNull String doi) throws DataCiteException
    {
        PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getWritableProperties(DataCiteService.CREDENTIALS, false);
        DataCiteConfig testConfig = getConfig(map, doi, true);
        DataCiteConfig config = getConfig(map, doi, false);
        if(testConfig.hasPrefix(doi))
        {
            return testConfig;
        }
        if(config.hasPrefix(doi))
        {
            return config;
        }
        throw new DataCiteException("Unrecognized prefix in DOI: " + doi);
    }

    private static DataCiteConfig getConfig(PropertyManager.PropertyMap map, @Nullable String doi, boolean test) throws DataCiteException
    {
        if(map == null)
        {
            throw new DataCiteException("DataCite configuration is not saved");
        }
        String testUrl = test ? "https://api.test.datacite.org/dois" : "https://api.datacite.org/dois";
        if(doi != null)
        {
            testUrl += "/" + doi;
        }
        String user = map.get(test ? DataCiteService.TEST_USER : DataCiteService.USER);
        String passwd = map.get(test ? DataCiteService.TEST_PASSWORD : DataCiteService.PASSWORD);
        String doiPrefix = map.get(test ? DataCiteService.TEST_PREFIX : DataCiteService.PREFIX);

        DataCiteConfig config = new DataCiteConfig(user, passwd, doiPrefix, testUrl);
        if(!config.valid())
        {
            List<String> missing = new ArrayList<>();
            if(StringUtils.isBlank(user)) missing.add("user name");
            if(StringUtils.isBlank(passwd)) missing.add("password");
            if(StringUtils.isBlank(doiPrefix)) missing.add("DOI prefix");
            throw new DataCiteException("DataCite " + (test ? "test " : "") + "configuration is missing the following properties: " + StringUtils.join(missing, ","));
        }
        return config;
    }

    /**
     * This class encapsulates the metadata that will be submitted to DataCite when a DOI is made 'findable'
     */
    public static class DoiMetadata
    {
        private String _url; // The Panorama Public access URL for the data
        private String _labHead;
        private String _submitter;
        private String _title;
        private String _abstract;
        private int _year;
        private String _doi;

        private DoiMetadata() {}

        public static DoiMetadata from(ExperimentAnnotations expAnnot, JournalExperiment je) throws DataCiteException
        {
            DoiMetadata metadata = new DoiMetadata();
            metadata._url = expAnnot.getShortUrl().renderShortURL();
            User labHead = expAnnot.getLabHeadUser();
            if(labHead != null)
            {
                metadata._labHead = getUserName(labHead, "Lab Head");
            }
            User submitter = expAnnot.getSubmitterUser();
            if(submitter == null)
            {
                throw new DataCiteException("Submitter not found for experiment");
            }
            metadata._submitter = getUserName(submitter, "Submitter");
            metadata._title = expAnnot.getTitle();
            metadata._abstract = expAnnot.getAbstract();
            metadata._year = DateUtil.now().get(Calendar.YEAR);
            metadata._doi = expAnnot.getDoi();

            return metadata;
        }

        private static String getUserName(@NotNull User user, @NotNull String userType) throws DataCiteException
        {
            if(StringUtils.isBlank(user.getFirstName()))
            {
                throw new DataCiteException("First name missing for " + userType + " " + user.getEmail());
            }
            if(StringUtils.isBlank(user.getLastName()))
            {
                throw new DataCiteException("Last name missing for " + userType + " " + user.getEmail());
            }
            return user.getLastName() + ", " + user.getFirstName();
        }

        public JSONObject getJson()
        {
            /* Example
                {
                  "data": {
                    "id": "10.5438/0012",
                    "type": "dois",
                    "attributes": {
                      "event": "publish",
                      "doi": "10.5438/0012",
                      "creators": [{
                        "name": "DataCite Metadata Working Group"
                      }],
                      "titles": [{
                        "title": "DataCite Metadata Schema Documentation for the Publication and Citation of Research Data v4.0"
                      }],
                      "publisher": "DataCite e.V.",
                      "publicationYear": 2016,
                      "types": {
                        "resourceTypeGeneral": "Text"
                      },
                      "url": "https://schema.datacite.org/meta/kernel-4.0/index.html",
                      "schemaVersion": "http://datacite.org/schema/kernel-4"
                    }
                  }
                }
             */
            JSONObject data = new JSONObject();
            data.put("id", _doi);
            data.put("type", "dois");
            data.put("attributes", getAttributes());

            return new JSONObject().put("data", data);
        }

        private JSONObject getAttributes()
        {
            JSONObject attribs = new JSONObject();
            attribs.put("event", "publish");
            attribs.put("doi", _doi);
            attribs.put("publisher", "Panorama Public");
            attribs.put("publicationYear", _year);
            attribs.put("url", _url);
            attribs.put("types", new JSONObject().put("resourceTypeGeneral", "Dataset"));
            JSONArray creators = new JSONArray();
            creators.put(new JSONObject().put("name", _submitter).put("nameType", "Personal"));
            if(_labHead != null)
            {
                creators.put(new JSONObject().put("name", _labHead).put("nameType", "Personal"));
            }
            attribs.put("creators", creators);
            attribs.put("titles", new JSONArray().put(new JSONObject().put("title", _title)));

            JSONArray descriptions = new JSONArray();
            descriptions.put(new JSONObject().put("description", _abstract).put("descriptionType", "Abstract"));
            attribs.put("descriptions", descriptions);

            return attribs;
        }

        public @NotNull DOM.Renderable getHtmlString()
        {
            String creators = _submitter;
            if(_labHead != null)
            {
                creators += ", " + _labHead;
            }
            return DIV("DOI: " + _doi,
                    BR(), "Publisher: Panorama Public",
                    BR(), "Publication Year: " + _year,
                    BR(), "URL " + _url,
                    BR(), "Type: Dataset",
                    BR(), "Title: " + _title,
                    BR(), "Creators: " + creators,
                    BR(), "JSON: " + getJson().toString());
        }
    }

    private static class DataCiteConfig
    {
        private final String _name;
        private final String _password;
        private final String _prefix; // The DOI prefix
        private final String _url; // The API URL

        public DataCiteConfig(String name, String password, String prefix, String url)
        {
            _name = name;
            _password = password;
            _prefix = prefix;
            _url = url;
        }

        public String getName()
        {
            return _name;
        }

        public String getPassword()
        {
            return _password;
        }

        public String getPrefix()
        {
            return _prefix;
        }

        public String getUrl()
        {
            return _url;
        }

        public boolean valid()
        {
            return !StringUtils.isBlank(_name) && !StringUtils.isBlank(_password) && !StringUtils.isBlank(_prefix) && !StringUtils.isBlank(_url);
        }

        public boolean hasPrefix(String doi)
        {
            return doi != null && doi.startsWith(getPrefix());
        }
    }

    private enum METHOD {GET, POST, PUT, DELETE}

    static class DataCiteResponse
    {
        private final int _responseCode;
        private final String _message;
        private final String _responseBody;

        public DataCiteResponse(int responseCode, String message, String responseBody)
        {
            _responseCode = responseCode;
            _message = message;
            _responseBody = responseBody;
        }

        public int getResponseCode()
        {
            return _responseCode;
        }

        public String getMessage()
        {
            return _message;
        }

        public String getResponseBody()
        {
            return _responseBody;
        }

        public boolean success(METHOD method)
        {
            switch (method)
            {
                case GET: case PUT: return _responseCode == 200;
                case POST: return _responseCode == 201;
                case DELETE: return _responseCode == 204;
                default:
                    throw new IllegalStateException("Unexpected method: " + method);
            }
        }

        public Doi getDoi() throws DataCiteException
        {
            return _responseBody == null ? null : Doi.fromJson(getJsonObject(_responseBody));
        }

        private org.json.simple.JSONObject getJsonObject(String response) throws DataCiteException
        {
            JSONParser parser = new JSONParser();
            try
            {
                return (org.json.simple.JSONObject) parser.parse(new StringReader(response));
            }
            catch (IOException | ParseException e)
            {
                throw new DataCiteException("Error parsing JSON from response: " + response);
            }
        }
    }

    public static class Doi
    {
        private final String _doi;
        private final String _state; // e.g. 'draft', 'findable' etc.

        private Doi(String doi, String state)
        {
            _doi = doi;
            _state = state;
        }

        public String getDoi()
        {
            return _doi;
        }

        public String getState()
        {
            return _state;
        }

        public boolean isFindable()
        {
            return "findable".equals(_state);
        }

        static Doi fromJson(@NotNull org.json.simple.JSONObject json)
        {
            if(json.containsKey("data"))
            {
                org.json.simple.JSONObject data = (org.json.simple.JSONObject) json.get("data");
                org.json.simple.JSONObject attribs = (org.json.simple.JSONObject) data.get("attributes");
                if(attribs != null)
                {
                    String doi = (String) attribs.get("doi");
                    String state = (String) attribs.get("state");
                    return new Doi(doi, state);
                }
            }
            return null;
        }
    }
}

package org.labkey.panoramapublic.datacite;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.old.JSONObject;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.util.Link;
import org.labkey.panoramapublic.model.ExperimentAnnotations;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Class for creating, deleting or publishing Digital Object Identifiers (DOIs) with the DataCite API.
 * DataCite is a DOI registration organization, primarily for research data. https://datacite.org/
 * DataCite REST API Guide: https://support.datacite.org/docs/api
 * DataCite API Reference: https://support.datacite.org/reference/dois-2
 * DataCite Metadata schema: https://schema.datacite.org/meta/kernel-4.3/
 * What is a DOI: https://en.wikipedia.org/wiki/Digital_object_identifier
 *
 * DOIs have the form: prefix/suffix
 * The prefix/suffix combination uniquely identify a resource (e.g. a Panorama Public dataset).
 * A 'prefix' is assigned to each member (e.g. UW Libraries) of DataCite.  There is a prefix for the live account, and
 * another prefix for the test account.
 * The DataCite account details are entered through the Panorama Public Admin Console link.
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

    enum METHOD {GET, POST, PUT, DELETE}

    /**
     * This will create a 'draft' DOI using either the live or the test DataCite API
     * @param test if true, the DataCite test API is used for creating the DOI
     */
    @NotNull
    public static Doi create(boolean test, ExperimentAnnotations expAnnot) throws DataCiteException
    {
        // curl -X POST -H "Content-Type: application/vnd.api+json" --user YOUR_REPOSITORY_ID:YOUR_PASSWORD -d @my_draft_doi.json https://api.test.datacite.org/dois
        DataCiteConfig config = getDataCiteConfig(test);
        METHOD method = METHOD.POST;
        DataCiteResponse response = doRequest(config, DoiMetadata.from(expAnnot, config.getPrefix()).getJson(), method);
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
    public static void publish(@NotNull ExperimentAnnotations expAnnot) throws DataCiteException
    {
        // # PUT /dois/:id
        //$ curl -X PUT -H "Content-Type: application/vnd.api+json" --user YOUR_REPOSITORY_ID:YOUR_PASSWORD -d @my_doi_update.json https://api.test.datacite.org/dois/:id
        METHOD method = METHOD.PUT;
        DataCiteResponse response = doRequest(getDataCiteConfig(expAnnot.getDoi()), DoiMetadata.from(expAnnot).getJson(), method);
        if(!response.success(method))
        {
            throw new DataCiteException("DOI could not be published", response);
        }
    }

    public static void publishIfDraftDoi(@NotNull ExperimentAnnotations expAnnot) throws DataCiteException
    {
        if (!StringUtils.isBlank(expAnnot.getDoi()) && !isPublished(expAnnot.getDoi()))
        {
            publish(expAnnot);
        }
    }

    private static boolean isPublished (@NotNull String experimentDoi) throws DataCiteException
    {
        // curl --request GET --url https://api.test.datacite.org/dois/id --header 'Accept: application/vnd.api+json'
        METHOD method = METHOD.GET;
        DataCiteResponse response = doRequest(getDataCiteConfig(experimentDoi), null, method);
        if(!response.success(method))
        {
            throw new DataCiteException("Request to get DOI status failed", response);
        }
        Doi doi = response.getDoi();
        return (doi != null && doi.getDoi() != null && doi.isFindable());
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
     * This will return the DataCiteConfig object containing the account name, password, DOI prefix and URL for the DataCite API
     * that should be used for managing the given DOI. The prefix part of the given DOI is used to determine if the live or test API should be used.
     * @param doi the doi for which the appropriate (test or live) DataCite configuration is returned.
     * @throws DataCiteException if a configuration could not be found for the given DOI.
     */
    @NotNull
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
        String url = test ? "https://api.test.datacite.org/dois" : "https://api.datacite.org/dois";
        if(doi != null)
        {
            url += "/" + doi;
        }
        String user = map.get(test ? DataCiteService.TEST_USER : DataCiteService.USER);
        String passwd = map.get(test ? DataCiteService.TEST_PASSWORD : DataCiteService.PASSWORD);
        String doiPrefix = map.get(test ? DataCiteService.TEST_PREFIX : DataCiteService.PREFIX);

        DataCiteConfig config = new DataCiteConfig(user, passwd, doiPrefix, url);
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

    public static String toUrl(@NotNull String doi)
    {
        // Regular expression for modern DOIs is /^10.\d{4,9}/[-._;()/:A-Z0-9]+$/i  (https://www.crossref.org/blog/dois-and-matching-regular-expressions/)
        // DOI example from Panorama Public: 10.6069/9cd7-b485; Prefix: 10.6069, Suffix: 9cd7-b485
        // The suffix part of the DOI can be anything (https://support.datacite.org/docs/doi-basics#suffix)
        // BUT, auto-generated DOI suffixes contain only a-z, 0-9 and -.  (https://support.datacite.org/docs/what-characters-should-i-use-in-the-suffix-of-my-doi)
        // So we don't need to URI-encode the DOI.
        return "https://doi.org/" + doi;
    }

    public static Link.LinkBuilder toLink(@NotNull String doi)
    {
        // Display the complete link: https://support.datacite.org/docs/datacite-doi-display-guidelines
        String url = toUrl(doi);
        return new Link.LinkBuilder(url).href(url).rel("noopener noreferrer");
    }

    /**
     * Class encapsulating the DataCite account name, password, url, and prefix assigned to the DataCite account (test or live)
     */
    private static class DataCiteConfig
    {
        private final String _name;
        private final String _password;
        private final String _prefix; // The DOI prefix; different prefixes are assigned to the test and live DataCite account.
        private final String _url; // The API URL; different for the test and live DataCite accounts.

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
}

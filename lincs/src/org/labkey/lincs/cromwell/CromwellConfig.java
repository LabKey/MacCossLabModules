package org.labkey.lincs.cromwell;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.ApiKeyManager;
import org.labkey.api.security.User;

import java.net.URI;
import java.net.URISyntaxException;

public class CromwellConfig
{
    public static final String PROPS_CROMWELL = "cromwell_properties";
    public static final String PROP_CROMWELL_SERVER_URL = "cromwell_server_url";
    public static final String PROP_CROMWELL_SERVER_PORT = "cromwell_server_port";
    public static final String PROP_API_KEY = "cromwell_panorama_api_key";

    private static String CROMWELL_API_PATH = "/api/workflows/v1";

    private String _panoramaApiKey;
    private String _cromwellServerUrl;
    private Integer _cromwellServerPort;

    private URI _cromwellServerUri;

    private CromwellConfig() {}

    public static CromwellConfig create(String cromwellServerUrl, Integer cromwellServerPort, String panoramaApiKey)
    {
        CromwellConfig config = new CromwellConfig();
        config._cromwellServerUrl = cromwellServerUrl;
        config._cromwellServerPort = cromwellServerPort;
        config._panoramaApiKey = panoramaApiKey;
        return config;
    }

    public String getCromwellServerUrl()
    {
        return _cromwellServerUrl;
    }

    public Integer getCromwellServerPort()
    {
        return _cromwellServerPort;
    }

    public String getPanoramaApiKey()
    {
        return _panoramaApiKey;
    }

    public URI getCromwellServerUri()
    {
        return _cromwellServerUri;
    }

    private URI buildCromwellServerUri(String path)
    {
        return getCromwellServerUri().resolve(path);
    }

    public URI getJobSubmitUri()
    {
        return buildCromwellServerUri(CROMWELL_API_PATH);
    }

    public URI getJobStatusUri(String cromwellJobId)
    {
        String path = CROMWELL_API_PATH + '/' + cromwellJobId + "/status";
        return buildCromwellServerUri(path);
    }

    public URI getAbortUri(String cromwellJobId)
    {
        String path = CROMWELL_API_PATH + '/' + cromwellJobId + "/abort";
        return buildCromwellServerUri(path);
    }

    public void save(@NotNull Container container)
    {
        PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getWritableProperties(container, PROPS_CROMWELL, true);
        map.put(PROP_CROMWELL_SERVER_URL, getCromwellServerUrl());
        map.put(PROP_CROMWELL_SERVER_PORT, String.valueOf(getCromwellServerPort()));
        map.put(PROP_API_KEY, String.valueOf(getPanoramaApiKey()));
        map.save();
    }

    public void validate() throws CromwellException
    {
        if(StringUtils.isBlank(getCromwellServerUrl()))
        {
            throw new CromwellException("Cromwell server URL not found in config");
        }
        if(getCromwellServerPort() == null)
        {
            throw new CromwellException("Cromwell server port not found in config");
        }
        String apiKey = getPanoramaApiKey();
        if(StringUtils.isBlank(apiKey))
        {
            throw new CromwellException("Panorama API key not found in config");
        }
        if(!apiKey.startsWith("apikey|"))
        {
            throw new CromwellException("Invalid API key. API keys must start with \"apikey|\": " + apiKey);
        }
        User user = ApiKeyManager.get().authenticateFromApiKey(apiKey);
        if(user == null)
        {
            throw new CromwellException("Cannot authenticate user with API Key.  The key may have expired");
        }

        if(_cromwellServerUri == null)
        {
            try
            {
                URIBuilder builder = new URIBuilder(_cromwellServerUrl);
                if(_cromwellServerPort != null && _cromwellServerPort != -1)
                {
                    builder = builder.setPort(_cromwellServerPort);
                }
                _cromwellServerUri = builder.build();
            }
            catch (URISyntaxException e)
            {
                throw new CromwellException("Error parsing Cromwell server URL. Error was: " + e.getMessage(), e);
            }
        }
    }

    @Nullable
    public static CromwellConfig get(Container container)
    {
        PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getProperties(container, PROPS_CROMWELL);

        // Don't check map != null since getProperties(Container container, String category) does not return null
        // even if properties for the given categories were not found
        if(map.get(PROP_CROMWELL_SERVER_URL) != null)
        {
            CromwellConfig config = new CromwellConfig();
            config._cromwellServerUrl = map.get(PROP_CROMWELL_SERVER_URL);
            String serverPortStr = map.get(PROP_CROMWELL_SERVER_PORT);
            if(serverPortStr != null)
            {
                config._cromwellServerPort = Integer.valueOf(serverPortStr);
            }
            config._panoramaApiKey = map.get(PROP_API_KEY);
            return config;
        }
        return null;
    }

    public static CromwellConfig getValidConfig(Container container) throws CromwellException
    {
        CromwellConfig config = get(container);
        if(config == null)
        {
            throw new CromwellException("Cromwell configuration is not saved in the container " + container.getPath());
        }
        config.validate();
        return config;
    }
}

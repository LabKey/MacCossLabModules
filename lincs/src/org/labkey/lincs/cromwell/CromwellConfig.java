package org.labkey.lincs.cromwell;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;

import java.net.URI;
import java.net.URISyntaxException;

public class CromwellConfig
{
    public static final String PROPS_CROMWELL = "cromwell_properties";
    public static final String PROP_CROMWELL_SERVER_URL = "cromwell_server_url";
    public static final String PROP_CROMWELL_SERVER_PORT = "cromwell_server_port";

    private static String CROMWELL_API_PATH = "/api/workflows/v1";

    private final String _cromwellServerUrl;
    private final Integer _cromwellServerPort;

    private URI _cromwellServerUri;

    public CromwellConfig(String cromwellServerUrl, Integer cromwellServerPort)
    {
        _cromwellServerUrl = cromwellServerUrl;
        _cromwellServerPort = cromwellServerPort;
    }

    public String getCromwellServerUrl()
    {
        return _cromwellServerUrl;
    }

    public Integer getCromwellServerPort()
    {
        return _cromwellServerPort;
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

        String cromwellServerUrl = map.get(PROP_CROMWELL_SERVER_URL);
        if(cromwellServerUrl != null)
        {
            String serverPortStr = map.get(PROP_CROMWELL_SERVER_PORT);
            Integer cromwellServerPort = null;
            if(serverPortStr != null)
            {
                cromwellServerPort = Integer.valueOf(serverPortStr);
            }
            return new CromwellConfig(cromwellServerUrl, cromwellServerPort);
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

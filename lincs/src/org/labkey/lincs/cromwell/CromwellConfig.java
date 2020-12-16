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
    public static final String PROP_SCP_USER = "cromwell_scp_user";
    public static final String PROP_SCP_KEY_FILE = "cromwell_scp_key_filepath";
    public static final String PROP_SCP_PORT = "cromwell_scp_port";
    public static final String PROP_API_KEY = "cromwell_panorama_api_key";
    public static final String PROP_ASSAY_TYPE = "lincs_assay_type";

    private static String CROMWELL_API_PATH = "/api/workflows/v1";

    private String _panoramaApiKey;
    private String _cromwellServerUrl;
    private Integer _cromwellServerPort;
    private String _cromwellScpUser;
    private String _cromwellScpKeyFilePath;
    private Integer _cromwellScpPort;

    private URI _cromwellServerUri;
    private CromwellJobSubmitter.JobType _jobType;

    private CromwellConfig() {}

    public static CromwellConfig create(String cromwellServerUrl, Integer cromwellServerPort, String panoramaApiKey, String assayType) throws CromwellException
    {
        CromwellConfig config = new CromwellConfig();
        config._cromwellServerUrl = cromwellServerUrl;
        config._cromwellServerPort = cromwellServerPort;
        config._panoramaApiKey = panoramaApiKey;
        config._jobType = CromwellJobSubmitter.JobType.getFor(assayType);
        config.initCromwellServerUri();
        config.validate();
        return config;
    }

    void validate() throws CromwellException
    {
        if(StringUtils.isBlank(getCromwellServerUrl()))
        {
            throw new CromwellException("Cromwell server URL not found in config");
        }
        if(getCromwellServerPort() == null)
        {
            throw new CromwellException("Cromwell server port not found in config");
        }
        if(StringUtils.isBlank(getPanoramaApiKey()))
        {
            throw new CromwellException("Panorama API key not found in config");
        }
        if(_jobType == null)
        {
            throw new CromwellException("Job type not found in config");
        }
        if(_cromwellServerUri == null)
        {
            initCromwellServerUri();
        }
    }

    private void initCromwellServerUri() throws CromwellException
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

    @Nullable
    public static CromwellConfig get(Container container)
    {
        PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getProperties(container, PROPS_CROMWELL);
        if(map != null)
        {
            CromwellConfig config = new CromwellConfig();
            config._cromwellServerUrl = map.get(PROP_CROMWELL_SERVER_URL);
            String serverPortStr = map.get(PROP_CROMWELL_SERVER_PORT);
            if(serverPortStr != null)
            {
                config._cromwellServerPort = Integer.valueOf(serverPortStr);
            }
            config._panoramaApiKey = map.get(PROP_API_KEY);
            config._jobType = CromwellJobSubmitter.JobType.getFor(map.get(PROP_ASSAY_TYPE));

            String scpKeyFilePath = map.get(PROP_SCP_KEY_FILE);
            String scpPort = map.get(PROP_SCP_PORT);
            String scpUser = map.get(PROP_SCP_USER);

            return config;
        }
        return null;
    }

    public void save(@NotNull Container container)
    {
        PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getWritableProperties(container, PROPS_CROMWELL, true);
        map.put(PROP_CROMWELL_SERVER_URL, getCromwellServerUrl());
        map.put(PROP_CROMWELL_SERVER_PORT, String.valueOf(getCromwellServerPort()));
        map.put(PROP_API_KEY, String.valueOf(getPanoramaApiKey()));
        map.put(PROP_ASSAY_TYPE, getJobType().name());

        map.put(PROP_SCP_USER, getCromwellScpUser());
        map.put(PROP_SCP_KEY_FILE, getCromwellScpKeyFilePath());
        map.put(PROP_SCP_PORT, getCromwellScpPort() != null ? String.valueOf(getCromwellScpPort()) : null);
        map.save();
    }

    public String getCromwellServerUrl()
    {
        return _cromwellServerUrl;
    }

    public Integer getCromwellServerPort()
    {
        return _cromwellServerPort;
    }

    public String getCromwellScpUser()
    {
        return _cromwellScpUser;
    }

    public String getCromwellScpKeyFilePath()
    {
        return _cromwellScpKeyFilePath;
    }

    public Integer getCromwellScpPort()
    {
        return _cromwellScpPort;
    }

    public String getPanoramaApiKey()
    {
        return _panoramaApiKey;
    }

    public CromwellJobSubmitter.JobType getJobType()
    {
        return _jobType;
    }

    public URI getCromwellServerUri()
    {
        return _cromwellServerUri;
    }

    private URI buildCromwellServerUri(String path)
    {
       return _cromwellServerUri.resolve(path);
    }

    public URI buildJobSubmitUri()
    {
        return buildCromwellServerUri(CROMWELL_API_PATH);
    }

    public URI buildJobStatusUri(String cromwellJobId)
    {
        String path = CROMWELL_API_PATH + '/' + cromwellJobId + "/status";
        return buildCromwellServerUri(path);
    }

    public URI buildjobLogsUrl(String cromwellJobId) throws URISyntaxException
    {
        String path = CROMWELL_API_PATH + '/' + cromwellJobId + "/metadata";
        // Query params to limit returned JSON to the keys we are interesed in
        // includeKey=calls&includeKey=stdout&includeKey=stderr&includeKey=callCaching"
        URI uri = new URIBuilder(buildCromwellServerUri(path))
                .addParameter("includeKey", "calls")
                .addParameter("includeKey", "stdout")
                .addParameter("includeKey", "stderr")
                .addParameter("includeKey", "callCaching")
                .addParameter("includeKey", "callRoot")
                .build();

        return uri;

    }

    public URI buildMetadataUri(String cromwellJobId)
    {
        String path = CROMWELL_API_PATH + '/' + cromwellJobId + "/metadata";
        return buildCromwellServerUri(path);
    }
}

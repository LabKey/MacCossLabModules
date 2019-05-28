package org.labkey.lincs.psp;

public class PspEndpoint
{
    private String _url;
    private String _apiKey;

    public PspEndpoint(){}

    public PspEndpoint(String url, String apiKey)
    {
        _url = url;
        _apiKey = apiKey;
    }

    public String getUrl()
    {
        return _url;
    }

    public String getApiKey()
    {
        return _apiKey;
    }
}

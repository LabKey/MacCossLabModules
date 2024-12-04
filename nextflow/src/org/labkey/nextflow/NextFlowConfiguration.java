package org.labkey.nextflow;

public class NextFlowConfiguration
{
    private String nextFlowConfigFilePath;
    private String accountName;
    private String identity;
    private String s3BucketPath;
    private String credential;
    private String apiKey;

    public String getNextFlowConfigFilePath()
    {
        return nextFlowConfigFilePath;
    }

    public void setNextFlowConfigFilePath(String nextFlowConfigFilePath)
    {
        this.nextFlowConfigFilePath = nextFlowConfigFilePath;
    }

    public String getAccountName()
    {
        return accountName;
    }

    public void setAccountName(String accountName)
    {
        this.accountName = accountName;
    }

    public String getIdentity()
    {
        return identity;
    }

    public void setIdentity(String identity)
    {
        this.identity = identity;
    }

    public String getS3BucketPath()
    {
        return s3BucketPath;
    }

    public void setS3BucketPath(String s3BucketPath)
    {
        this.s3BucketPath = s3BucketPath;
    }

    public String getCredential()
    {
        return credential;
    }

    public void setCredential(String credential)
    {
        this.credential = credential;
    }

    public String getApiKey()
    {
        return apiKey;
    }

    public void setApiKey(String apiKey)
    {
        this.apiKey = apiKey;
    }
}

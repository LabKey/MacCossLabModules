/*
 * Copyright (c) 2024-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

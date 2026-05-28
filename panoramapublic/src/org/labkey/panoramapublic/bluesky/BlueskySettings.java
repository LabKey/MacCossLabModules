/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.panoramapublic.bluesky;

import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;

public class BlueskySettings
{
    private String _account;
    private String _password;

    private String _testAccount;
    private String _testAccountPassword;

    private String _imageFileName;

    private boolean _autopost;

    private String _hashtags;
    private String _testHashtags;

    private String _authEndpoint;
    private String _postEndpoint;
    private String _blobUploadEndpoint;

    private String _announcementText;

    public String getAccount()
    {
        return _account;
    }

    public void setAccount(String account)
    {
        _account = account;
    }

    public String getPassword()
    {
        return _password;
    }

    public void setPassword(String password)
    {
        _password = password;
    }

    public String getTestAccount()
    {
        return _testAccount;
    }

    public void setTestAccount(String testAccount)
    {
        _testAccount = testAccount;
    }

    public String getTestAccountPassword()
    {
        return _testAccountPassword;
    }

    public void setTestAccountPassword(String testAccountPassword)
    {
        _testAccountPassword = testAccountPassword;
    }

    public String getImageFileName()
    {
        return _imageFileName;
    }

    public void setImageFileName(String imageFileName)
    {
        _imageFileName = imageFileName;
    }

    public boolean isAutopost()
    {
        return _autopost;
    }

    public void setAutopost(boolean autopost)
    {
        _autopost = autopost;
    }

    public String getHashtags()
    {
        return _hashtags;
    }

    public String[] getHashtagArray()
    {
        return convertToArray(_hashtags);
    }

    public void setHashtags(String hashtags)
    {
        _hashtags = hashtags;
    }

    public String getTestHashtags()
    {
        return _testHashtags;
    }

    public String[] getTestHashtagArray()
    {
        return convertToArray(_testHashtags);
    }

    public void setTestHashtags(String testHashtags)
    {
        _testHashtags = testHashtags;
    }

    public static String[] convertToArray(String hashtags)
    {
        if (StringUtils.isBlank(hashtags))
        {
            return new String[0];
        }

        return Arrays.stream(hashtags.split(","))
                .map(String::trim)                       // remove surrounding whitespace
                .filter(tag -> !tag.isEmpty())           // filter out empty tokens
                .map(tag -> tag.replaceAll("\\s+", ""))  // remove all internal spaces
                .map(tag -> tag.startsWith("#")          // strip leading ‘#’ if present
                        ? tag.substring(1)
                        : tag)
                .distinct()                              // remove duplicates
                .toArray(String[]::new);
    }

    public String getAuthEndpoint()
    {
        return _authEndpoint;
    }

    public void setAuthEndpoint(String authEndpoint)
    {
        _authEndpoint = authEndpoint;
    }

    public String getPostEndpoint()
    {
        return _postEndpoint;
    }

    public void setPostEndpoint(String postEndpoint)
    {
        _postEndpoint = postEndpoint;
    }

    public String getBlobUploadEndpoint()
    {
        return _blobUploadEndpoint;
    }

    public void setBlobUploadEndpoint(String blobUploadEndpoint)
    {
        _blobUploadEndpoint = blobUploadEndpoint;
    }

    public String getAnnouncementText()
    {
        return _announcementText;
    }

    public void setAnnouncementText(String announcementText)
    {
        _announcementText = announcementText;
    }

    public String getAccount (boolean test)
    {
        return test ? getTestAccount() : getAccount();
    }

    public String getPassword (boolean test)
    {
        return test ? getTestAccountPassword() : getPassword();
    }
}

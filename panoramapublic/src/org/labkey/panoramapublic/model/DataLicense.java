/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
package org.labkey.panoramapublic.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Parameter;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.LinkBuilder;

public enum DataLicense implements Parameter.JdbcParameterValue
{
    CC_BY_4_0 ("CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/"),
    CC0_1_0 ("CC0 1.0", "https://creativecommons.org/publicdomain/zero/1.0/");

    private final String _displayName;
    private final String _url;

    DataLicense(String displayName, String url)
    {
        _displayName = displayName;
        _url = url;
    }

    public String getDisplayName()
    {
        return _displayName;
    }

    public String getUrl()
    {
        return _url;
    }

    public LinkBuilder.Link getDisplayLink()
    {
        return LinkBuilder.simpleLink(getDisplayName(), getUrl()).target("_blank").build();
    }

    public HtmlString getDisplayLinkHtml()
    {
        return getDisplayLink().getHtmlString();
    }

    public static DataLicense defaultLicense()
    {
        return DataLicense.CC_BY_4_0;
    }

    public static DataLicense resolveLicense(String license)
    {
        DataLicense resolved = DataLicense.valueOf(license);
        return resolved == null ? defaultLicense() : resolved;
    }

    @Override
    public @Nullable Object getJdbcParameterValue()
    {
        return name();
    }

    @Override
    public @NotNull JdbcType getJdbcParameterType()
    {
        return JdbcType.VARCHAR;
    }
}

package org.labkey.panoramapublic.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Parameter;
import org.labkey.api.util.Link;

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

    public Link getDisplayLink()
    {
        return new Link.LinkBuilder(getDisplayName()).href(getUrl()).target("_blank").clearClasses().build();
    }

    public String getDisplayLinkHtml()
    {
        return getDisplayLink().getHtmlString().toString();
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

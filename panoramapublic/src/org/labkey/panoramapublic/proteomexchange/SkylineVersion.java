package org.labkey.panoramapublic.proteomexchange;

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SkylineVersion
{
    // (Skyline|Skyline-daily)\s(\(.*\)\s)?(\d+)\.(\d+)(\.\d+)?(\.\d+)?.*
    private static final Pattern VERSION_PATTERN = Pattern.compile("(Skyline|Skyline-daily)\\s(\\(.*\\)\\s)?(\\d+)\\.(\\d+)(\\.(\\d+))?(\\.(\\d+))?.*");

    private final String _skylineType;
    private final int _majorVersion;
    private final int _minorVersion;

    private SkylineVersion(String skylineType, int majorVersion, int minorVersion)
    {
        _skylineType = skylineType;
        _majorVersion = majorVersion;
        _minorVersion = minorVersion;
    }

    @Nullable
    public static SkylineVersion parse(String versionString)
    {
        if(versionString != null)
        {
            Matcher matcher = VERSION_PATTERN.matcher(versionString);
            if (matcher.matches())
            {
                String skyType = matcher.group(1);
                int majorVer = Integer.parseInt(matcher.group(3));
                int minorVer = Integer.parseInt(matcher.group(4));
                return new SkylineVersion(skyType, majorVer, minorVer);
            }
        }
        return null;
    }

    public String getSkylineType()
    {
        return _skylineType;
    }

    public int getMajorVersion()
    {
        return _majorVersion;
    }

    public int getMinorVersion()
    {
        return _minorVersion;
    }

    @Override
    public String toString()
    {
        return String.format("%s %d.%d", _skylineType, _majorVersion, _minorVersion);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SkylineVersion that = (SkylineVersion) o;
        return getMajorVersion() == that.getMajorVersion() &&
                getMinorVersion() == that.getMinorVersion() &&
                getSkylineType().equals(that.getSkylineType());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getSkylineType(), getMajorVersion(), getMinorVersion());
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testSkylineVersionParsing()
        {
            /*
            Examples from PanoramaWeb
            Skyline
            Skyline (64-bit)
            Skyline 1.0.0.45
            Skyline 3.1.0.7382
            Skyline 3.6
            Skyline 20.2
            Skyline (64-bit) 19.1.0.193
            Skyline (64-bit) 20.1.0.155 (a0e7323e3)
            Skyline (64-bit : developer build) 20.1.1.88 (e22b286bc)
            Skyline-daily 2.4.9.5570
            Skyline-daily (64-bit) 19.0.9.149
            Skyline-daily (64-bit : automated build) 20.1.1.200 (b27502862)
         */
            assertNull(SkylineVersion.parse(null));
            SkylineVersion version = SkylineVersion.parse("Skyline");
            assertNull(version);
            version = SkylineVersion.parse("Skyline (64-bit)");
            assertNull(version);
            version = SkylineVersion.parse("Skyline 3.1.0.7382");
            assertEquals(new SkylineVersion("Skyline", 3, 1), version);
            version = SkylineVersion.parse("Skyline 20.2");
            assertEquals(new SkylineVersion("Skyline", 20, 2), version);
            version = SkylineVersion.parse("Skyline (64-bit) 19.1.0.193");
            assertEquals(new SkylineVersion("Skyline", 19, 1), version);
            version = SkylineVersion.parse("Skyline (64-bit : developer build) 20.1.1.88 (e22b286bc)");
            assertEquals(new SkylineVersion("Skyline", 20, 1), version);
            version = SkylineVersion.parse("Skyline-daily (64-bit : automated build) 20.1.1.200 (b27502862)");
            assertEquals(new SkylineVersion("Skyline-daily", 20, 1), version);
        }
    }
}

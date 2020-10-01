package org.labkey.panoramapublic.model;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

public class SpecLibInfo
{
    private int _id;
    private int _createdBy;
    private Date _created;
    private int _modifiedBy;
    private Date _modified;
    private String _filename;
    private int _sourceType;
    private String _sourceUrl;
    private String _sourcePxid;
    private String _sourceAccession;
    private String _sourceUsername;
    private String _sourcePassword;
    private int _dependencyType;

    public static class SourceType
    {
        public static final int SKYLINE = 0;
        public static final int PUBLIC_LIBRARY = 1;
        public static final int PX_REPOSITORY = 2;

        public static int[] All()
        {
            return new int[]{SKYLINE, PUBLIC_LIBRARY, PX_REPOSITORY};
        }

        public static String getDescription(int sourceType)
        {
            switch (sourceType)
            {
                case SKYLINE:
                    return "Built with Skyline";
                case PUBLIC_LIBRARY:
                    return "Public library";
                case PX_REPOSITORY:
                    return "Source files in another PX repository";
            }
            return null;
        }

        public static boolean isValid(int sourceType)
        {
            return IntStream.of(All()).anyMatch(x -> x == sourceType);
        }
    }

    public static class DependencyType
    {
        public static final int STATISTICALLY_DEPENDENT = 0;
        public static final int TARGETS_AND_FRAGMENTS = 1;
        public static final int TARGETS_ONLY = 2;
        public static final int SUPPORTING_INFO = 3;
        public static final int IRRELEVANT = 4;

        public static int[] All()
        {
            return new int[]{STATISTICALLY_DEPENDENT, TARGETS_AND_FRAGMENTS, TARGETS_ONLY, SUPPORTING_INFO, IRRELEVANT};
        }

        public static String getDescription(int dependencyType)
        {
            switch (dependencyType)
            {
                case STATISTICALLY_DEPENDENT:
                    return "Statistically dependent results";
                case TARGETS_AND_FRAGMENTS:
                    return "Used in choosing targets and fragments";
                case TARGETS_ONLY:
                    return "Used in choosing targets only";
                case SUPPORTING_INFO:
                    return "Used only as supporting information";
                case IRRELEVANT:
                    return "Irrelevant to results";
            }
            return null;
        }

        public static boolean isValid(int dependencyType)
        {
            return IntStream.of(All()).anyMatch(x -> x == dependencyType);
        }
    }

    public SpecLibInfo() {}

    public SpecLibInfo(String filename)
    {
        _filename = filename;
    }

    public static Map<String, SpecLibInfo> toMap(SpecLibInfo[] specLibInfos)
    {
        Map<String, SpecLibInfo> m = new HashMap<>();
        for (SpecLibInfo specLibInfo : specLibInfos)
        {
            m.put(specLibInfo._filename, specLibInfo);
        }
        return m;
    }

    public int getId()
    {
        return _id;
    }

    public void setId(int id)
    {
        _id = id;
    }

    public int getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(int createdBy)
    {
        _createdBy = createdBy;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public int getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(int modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }

    public Date getModified()
    {
        return _modified;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }

    public String getFilename()
    {
        return _filename;
    }

    public void setFilename(String filename)
    {
        _filename = filename;
    }

    public int getSourceType()
    {
        return _sourceType;
    }

    public void setSourceType(int sourceType)
    {
        _sourceType = sourceType;
    }

    public String getSourceUrl()
    {
        return _sourceUrl;
    }

    public void setSourceUrl(String sourceUrl)
    {
        _sourceUrl = sourceUrl;
    }

    public String getSourcePxid()
    {
        return _sourcePxid;
    }

    public void setSourcePxid(String sourcePxid)
    {
        _sourcePxid = sourcePxid;
    }

    public String getSourceAccession()
    {
        return _sourceAccession;
    }

    public void setSourceAccession(String sourceAccession)
    {
        _sourceAccession = sourceAccession;
    }

    public String getSourceUsername()
    {
        return _sourceUsername;
    }

    public void setSourceUsername(String sourceUsername)
    {
        _sourceUsername = sourceUsername;
    }

    public String getSourcePassword()
    {
        return _sourcePassword;
    }

    public void setSourcePassword(String sourcePassword)
    {
        _sourcePassword = sourcePassword;
    }

    public int getDependencyType()
    {
        return _dependencyType;
    }

    public void setDependencyType(int dependencyType)
    {
        _dependencyType = dependencyType;
    }
}

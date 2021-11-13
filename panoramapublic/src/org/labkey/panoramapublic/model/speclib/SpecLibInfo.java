package org.labkey.panoramapublic.model.speclib;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class SpecLibInfo
{
    private int _id;
    private int _createdBy;
    private Date _created;
    private int _modifiedBy;
    private Date _modified;

    private int _experimentAnnotationsId;

    private String _name;
    private String _fileNameHint;
    private String _skylineLibraryId;
    private String _libraryType;
    private String _revision;

    private SpecLibSourceType _sourceType;
    private String _sourceUrl;
    private String _sourceAccession;
    private String _sourceUsername;
    private String _sourcePassword;

    private SpecLibDependencyType _dependencyType;


    public SpecLibInfo() {}

    public SpecLibKey getLibraryKey()
    {
        return new SpecLibKey(_name, _fileNameHint, _skylineLibraryId, _libraryType, _revision);
    }

    public String getStringKey()
    {
        return getLibraryKey().getStringKey();
    }

    public static Map<SpecLibKey, SpecLibInfo> toMap(SpecLibInfo[] specLibInfos)
    {
        Map<SpecLibKey, SpecLibInfo> m = new HashMap<>();
        for (SpecLibInfo specLibInfo : specLibInfos)
        {
            m.put(specLibInfo.getLibraryKey(), specLibInfo);
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

    public int getExperimentAnnotationsId()
    {
        return _experimentAnnotationsId;
    }

    public void setExperimentAnnotationsId(int experimentAnnotationsId)
    {
        _experimentAnnotationsId = experimentAnnotationsId;
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

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getFileNameHint()
    {
        return _fileNameHint;
    }

    public void setFileNameHint(String fileNameHint)
    {
        _fileNameHint = fileNameHint;
    }

    public String getSkylineLibraryId()
    {
        return _skylineLibraryId;
    }

    public void setSkylineLibraryId(String skylineLibraryId)
    {
        _skylineLibraryId = skylineLibraryId;
    }

    public String getRevision()
    {
        return _revision;
    }

    public void setRevision(String revision)
    {
        _revision = revision;
    }

    public String getLibraryType()
    {
        return _libraryType;
    }

    public void setLibraryType(String libraryType)
    {
        _libraryType = libraryType;
    }

    public String getSourceUrl()
    {
        return _sourceUrl;
    }

    public void setSourceUrl(String sourceUrl)
    {
        _sourceUrl = sourceUrl;
    }

    public SpecLibDependencyType getDependencyType()
    {
        return _dependencyType;
    }

    public void setSourceType(SpecLibSourceType sourceType)
    {
        _sourceType = sourceType;
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

    public void setDependencyType(SpecLibDependencyType dependencyType)
    {
        _dependencyType = dependencyType;
    }

    public SpecLibSourceType getSourceType()
    {
        return _sourceType;
    }
}

package org.labkey.panoramapublic.model.speclib;

import org.labkey.panoramapublic.model.DbEntity;

public class SpecLibInfo extends DbEntity
{
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
        return new SpecLibKey(_name, _fileNameHint, _skylineLibraryId, _revision, _libraryType);
    }

    public int getExperimentAnnotationsId()
    {
        return _experimentAnnotationsId;
    }

    public void setExperimentAnnotationsId(int experimentAnnotationsId)
    {
        _experimentAnnotationsId = experimentAnnotationsId;
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

    public boolean isPublicLibrary()
    {
        return SpecLibSourceType.PUBLIC_LIBRARY == _sourceType;
    }

    public boolean isLibraryNotRelevant()
    {
        return SpecLibDependencyType.IRRELEVANT == _dependencyType || SpecLibDependencyType.SUPPORTING_INFO == _dependencyType;
    }

    public String getInfo()
    {
        return "Source: " + _sourceType.getLabel() +
                (_sourceUrl != null ? ", URL: " + _sourceUrl : "") +
                (_sourceAccession != null ? ", Accession: " + _sourceAccession : "") +
                ", Dependency: " + _dependencyType.getLabel();
    }
}

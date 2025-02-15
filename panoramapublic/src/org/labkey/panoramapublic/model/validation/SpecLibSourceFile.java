package org.labkey.panoramapublic.model.validation;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.panoramapublic.speclib.LibSourceFile;

import java.util.Objects;

// For table panoramapublic.speclibsourcefile
public class SpecLibSourceFile extends DataFile
{
    private int _id;
    private int _specLibValidationId;
    private LibrarySourceFileType _sourceType;

    public enum LibrarySourceFileType
    {
        SPECTRUM, PEPTIDE_ID
    }

    public SpecLibSourceFile() {}

    public SpecLibSourceFile(String name, LibrarySourceFileType sourceType)
    {
        setName(name);
        _sourceType = sourceType;
    }

    @Override
    public int getId()
    {
        return _id;
    }

    @Override
    public void setId(int id)
    {
        _id = id;
    }

    public int getSpecLibValidationId()
    {
        return _specLibValidationId;
    }

    public void setSpecLibValidationId(int specLibValidationId)
    {
        _specLibValidationId = specLibValidationId;
    }

    public LibrarySourceFileType getSourceType()
    {
        return _sourceType;
    }

    public void setSourceType(LibrarySourceFileType sourceType)
    {
        _sourceType = sourceType;
    }

    public boolean isSpectrumFile()
    {
        return LibrarySourceFileType.SPECTRUM.equals(_sourceType);
    }

    public boolean isIdFile()
    {
        return LibrarySourceFileType.PEPTIDE_ID.equals(_sourceType);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpecLibSourceFile that = (SpecLibSourceFile) o;
        return getSourceType().equals(that.getSourceType()) && getName().equals(that.getName());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getSourceType(), getName());
    }

    @NotNull
    public JSONObject toJSON(Container container)
    {
        JSONObject jsonObject = super.toJSON(container);
        if (isIdFile() && LibSourceFile.DIANN_REPORT_TSV_PLACEHOLDER.endsWith(getName()) && !found())
        {
            jsonObject.put("statusDetails", "The DIA-NN TSV report must be in the same directory as the " +
                    ".speclib, and share some leading characters in the file name");
        }
        return jsonObject;
    }
}

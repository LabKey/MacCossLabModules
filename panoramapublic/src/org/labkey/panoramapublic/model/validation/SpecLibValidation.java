package org.labkey.panoramapublic.model.validation;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public abstract class SpecLibValidation <D extends SkylineDocSpecLib>
{
    private int _id;
    private int _validationId;
    private String _libName;
    private String _fileName;
    private Long _size;
    private String _libType; // bibliospec, bibliospec_lite, elib, hunter, midas, nist, spectrast, chromatogram

    private List<SpecLibSourceFile> _spectrumFiles;
    private List<SpecLibSourceFile> _idFiles;

    public abstract @NotNull List<D> getDocsWithLibrary();

    public int getId()
    {
        return _id;
    }

    public void setId(int id)
    {
        _id = id;
    }

    public int getValidationId()
    {
        return _validationId;
    }

    public void setValidationId(int validationId)
    {
        _validationId = validationId;
    }

    public String getLibName()
    {
        return _libName;
    }

    public void setLibName(String libName)
    {
        _libName = libName;
    }

    public String getFileName()
    {
        return _fileName;
    }

    public void setFileName(String fileName)
    {
        _fileName = fileName;
    }

    public Long getSize()
    {
        return _size;
    }

    public void setSize(Long size)
    {
        _size = size;
    }

    public String getLibType()
    {
        return _libType;
    }

    public void setLibType(String libType)
    {
        _libType = libType;
    }

    public boolean isMissingInSkyZip()
    {
        return _size == null;
    }

    public boolean specLibrariesIncluded()
    {
        return getDocsWithLibrary().stream().allMatch(SkylineDocSpecLib::isIncluded);
    }

    public @NotNull List<SpecLibSourceFile> getSpectrumFiles()
    {
        return hasSpectrumFiles() ? Collections.unmodifiableList(_spectrumFiles) : Collections.emptyList();
    }

    public void setSpectrumFiles(@NotNull List<SpecLibSourceFile> spectrumFiles)
    {
        _spectrumFiles = spectrumFiles;
    }

    public @NotNull List<SpecLibSourceFile> getIdFiles()
    {
        return hasIdFiles() ? Collections.unmodifiableList(_idFiles) : Collections.emptyList();
    }

    public void setIdFiles(@NotNull List<SpecLibSourceFile> idFiles)
    {
        _idFiles = idFiles;
    }

    public boolean isValid()
    {
        if (isPending())
        {
            return false;
        }

        if (isMissingInSkyZip() || isAssayLibrary() || isUnsupportedLibrary() || isIncompleteBlib())
        {
            return false;
        }
        if (isPrositLibrary())
        {
            return true; // No source files for a library based on Prosit predictions
        }
        // No peptide search files needed for EncyclopeDIA libraries so we only check for spectrum source files
        if (isEncyclopeDiaLibrary() && foundSpectrumFiles())
        {
            return true;
        }

        return hasSpectrumFiles() && foundSpectrumFiles() && hasIdFiles() && foundIdFiles();
    }

    public boolean isPending()
    {
        return getSpectrumFiles().stream().anyMatch(DataFile::isPending) ||
                getIdFiles().stream().anyMatch(DataFile::isPending);
    }

    public String getStatusString()
    {
        if (isMissingInSkyZip())
        {
            return "Library file is missing from the Skyline document ZIP file";
        }
        if (isAssayLibrary())
        {
            return "BiblioSpec library not built with mass spec results";
        }
        if (isUnsupportedLibrary())
        {
            return "Unsupported library type: " + getLibType();
        }
        if (isPrositLibrary())
        {
            return "VALID";
        }

        if (hasSpectrumFiles() && foundSpectrumFiles() && hasIdFiles() && foundIdFiles())
        {
            return "VALID";
        }
        else
        {
            String status = null;
            boolean missingSpectrumFilesInBlib = !hasSpectrumFiles(); // .blib does not list any spectrum files
            boolean missingIdFilesInBlib = !hasIdFiles(); // .blib does not list any Id files
            boolean missingSpectrumFiles = !foundSpectrumFiles(); // spectrum files listed in the .blib were not found on the filesystem
            boolean missingIdFiles = !foundIdFiles(); // Id files listed in the .blib were not found on the filesystem

            if (missingSpectrumFiles || missingIdFiles)
            {
                status = String.format("Missing %s%s%s files",
                        missingSpectrumFiles ? "spectrum " : "",
                        missingSpectrumFiles && missingIdFiles ? "and " : "",
                        missingIdFiles ? "peptide Id " : "");
            }
            if (missingSpectrumFilesInBlib || missingIdFilesInBlib)
            {
                status = String.format("%sMissing %s%s%s file names in the .blib file. Library may have been built with an older version of Skyline.",
                        status == null ? "" : status + ". ",
                        missingSpectrumFilesInBlib ? "spectrum " : "",
                        (missingSpectrumFilesInBlib && missingIdFilesInBlib) ? "and " : "",
                        missingIdFilesInBlib ? "peptide ID" : "");
            }
            return status;
        }
    }

    public boolean hasMissingSpectrumFiles()
    {
        return !foundSpectrumFiles();
    }

    public boolean hasMissingIdFiles()
    {
        return !foundIdFiles();
    }

    public List<String> getMissingSpectrumFileNames()
    {
        return getSpectrumFiles().stream().filter(f -> !f.found()).map(DataFile::getName).collect(Collectors.toList());
    }

    public List<String> getMissingIdFileNames()
    {
        return getIdFiles().stream().filter(f -> !f.found()).map(DataFile::getName).collect(Collectors.toList());
    }

    private boolean isPrositLibrary()
    {
        // For a library based on Prosit we expect only one row in the SpectrumSourceFiles table,
        // We expect idFileName to be blank and the value in the fileName column to be "Prositintensity_prosit_publication_v1".
        // The value in the fileName column may be different in Skyline 21.1. This code will be have to be updated then.
        if(isBibliospecLibrary() && getSpectrumFiles().size() == 1 && getIdFiles().size() == 0)
        {
            return "Prositintensity_prosit_publication_v1".equals(getSpectrumFiles().get(0).getName());
        }
        return false;
    }

    private boolean isBibliospecLibrary()
    {
        return "bibliospec".equals(_libType) || "bibliospec_lite".equals(_libType);
    }

    private boolean isEncyclopeDiaLibrary()
    {
        return "elib".equals(_libType);
    }

    private boolean isUnsupportedLibrary()
    {
        return !(isBibliospecLibrary() || isEncyclopeDiaLibrary());
    }

    private boolean isAssayLibrary()
    {
        // https://skyline.ms/wiki/home/software/Skyline/page.view?name=building_spectral_libraries
        if (isBibliospecLibrary())
        {
            var spectrumFiles = getSpectrumFiles();
            return spectrumFiles.size() > 0 && spectrumFiles.stream().anyMatch(f -> getFileName().toLowerCase().endsWith(".csv"));
        }
        return false;
    }

    private boolean isIncompleteBlib()
    {
        return !isPrositLibrary() && !hasSpectrumFiles() || !hasIdFiles();
    }

    private boolean hasSpectrumFiles()
    {
        return _spectrumFiles != null && _spectrumFiles.size() > 0;
    }

    private boolean hasIdFiles()
    {
        return _idFiles != null && _idFiles.size() > 0;
    }

    private boolean foundSpectrumFiles()
    {
        return getSpectrumFiles().stream().allMatch(f -> !f.isPending() && f.found());
    }

    private boolean foundIdFiles()
    {
        return getIdFiles().stream().allMatch(f -> !f.isPending() && f.found());
    }
}

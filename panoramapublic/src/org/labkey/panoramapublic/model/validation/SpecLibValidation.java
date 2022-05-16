package org.labkey.panoramapublic.model.validation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.panoramapublic.model.speclib.SpecLibInfo;
import org.labkey.panoramapublic.model.speclib.SpecLibSourceType;

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
    private Integer _specLibInfoId;

    private List<SpecLibSourceFile> _spectrumFiles;
    private List<SpecLibSourceFile> _idFiles;

    public abstract @NotNull List<D> getDocsWithLibrary();
    public abstract SpecLibInfo getSpecLibInfo();

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

    public Integer getSpecLibInfoId()
    {
        return _specLibInfoId;
    }

    public void setSpecLibInfoId(Integer specLibInfoId)
    {
        _specLibInfoId = specLibInfoId;
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
        return isValidDueToSpecLibInfo() || isValidWithoutSpecLibInfo();
    }

    public boolean isValidWithoutSpecLibInfo()
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

    public boolean isValidDueToSpecLibInfo()
    {
        if (isPending())
        {
            return false;
        }

        SpecLibInfo specLibInfo = getSpecLibInfo();
        if (specLibInfo != null && (specLibInfo.isPublicLibrary() || specLibInfo.isLibraryNotRelevant()))
        {
            // We do not expect users to upload raw files and search results used to build a publicly available library.
            // We also do not need the source files if the user had told us that the library is not relevant to their
            // results. Sometimes when an older document is used as a template, the library information used with the
            // document is still in the Skyline XML even though the library is not used, and the library file is no longer
            // there. We also do not need the source files if the library was used just as supporting information, and not
            // used to pick targets in the Skyline document.
            return true;
        }
        return false;
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
            return "Library file is missing from the Skyline document ZIP file.";
        }
        if (isAssayLibrary())
        {
            return "BiblioSpec library was not built with mass spec results.";
        }
        if (isUnsupportedLibrary())
        {
            return getLibType() + " library type is not supported.";
        }
        if (isPrositLibrary())
        {
            return "Prosit Library";
        }
        if (hasSpectrumFiles() && foundSpectrumFiles() && hasIdFiles() && foundIdFiles())
        {
            return "VALID";
        }
        if (isEncyclopeDiaLibrary())
        {
            if (hasSpectrumFiles() && foundSpectrumFiles())
            {
                return "VALID";
            }
            else
            {
                return "Missing spectrum files.";
            }
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
                status = String.format("Missing %s%s%s files.",
                        missingSpectrumFiles ? "spectrum " : "",
                        missingSpectrumFiles && missingIdFiles ? "and " : "",
                        missingIdFiles ? "peptide Id " : "");
            }
            if (missingSpectrumFilesInBlib || missingIdFilesInBlib)
            {
                status = String.format("%sMissing %s%s%s file names in the .blib file.",
                        status == null ? "" : status + " ",
                        missingSpectrumFilesInBlib ? "spectrum " : "",
                        (missingSpectrumFilesInBlib && missingIdFilesInBlib) ? "and " : "",
                        missingIdFilesInBlib ? "peptide ID" : "");
            }
            return status;
        }
    }

    public @Nullable String getHelpString()
    {
        if (isBibliospecLibrary() && !(isMissingInSkyZip() || isAssayLibrary()) && !(hasSpectrumFiles() && hasIdFiles()))
        {
            return "Library may have been built with an older version of Skyline. For a complete ProteomeXchange submission, " +
                    "re-build the library with the latest Skyline and update the documents that use this library.";
        }
        return null;
    }

    public @Nullable String getLibInfoHelpString()
    {
        if (!isUnsupportedLibrary() && librarySourceExternal())
        {
            if ((isEncyclopeDiaLibrary() && hasSpectrumFiles()) ||
                    (isBibliospecLibrary() && hasSpectrumFiles() && hasIdFiles()))
            {
                return "Library source files in the external repository will be verified when the data is made public and announced on ProteomeXchange. " +
                        "If all the library source files are found in the external repository, this library will be considered complete.";
            }
            else {
                String missing = hasSpectrumFiles() ? "" : " spectrum file names";
                if (isBibliospecLibrary() && !hasIdFiles())
                {
                    missing += (missing.length() > 0 ? " and " : "") + " peptide ID file names";
                }
                return "Library source files in the external repository cannot be verified since the library is missing " + missing + ".";
            }
        }
        return null;
    }

    private boolean librarySourceExternal()
    {
        SpecLibInfo specLibInfo = getSpecLibInfo();
        return specLibInfo != null && specLibInfo.getSourceType() == SpecLibSourceType.OTHER_REPOSITORY;
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

    public boolean isPrositLibrary()
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
            return spectrumFiles.size() > 0 && spectrumFiles.stream().anyMatch(f -> f.getName().toLowerCase().endsWith(".csv"));
        }
        return false;
    }

    private boolean isIncompleteBlib()
    {
        // Return true if no spectrum or peptide id file names were found in the .blib
        return !isPrositLibrary() && (!hasSpectrumFiles() || !hasIdFiles());
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

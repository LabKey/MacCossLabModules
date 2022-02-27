package org.labkey.panoramapublic.speclib;

import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class LibSourceFile
{
    private final String spectrumSourceFile;
    private final String idFile;
    private final Set<String> scoreTypes;

    public LibSourceFile(String spectrumSourceFile, String idFile, Set<String> scoreTypes)
    {
        this.spectrumSourceFile = spectrumSourceFile;
        this.idFile = idFile;
        this.scoreTypes = scoreTypes != null && !scoreTypes.isEmpty() ? scoreTypes : null;
    }

    public boolean hasSpectrumSourceFile()
    {
        return spectrumSourceFile != null;
    }

    public @Nullable String getSpectrumSourceFile()
    {
        return hasSpectrumSourceFile() ? getFileName(spectrumSourceFile) : null;
    }

    public boolean hasIdFile()
    {
        return idFile != null;
    }

    public @Nullable String getIdFile()
    {
        return hasIdFile() ? getFileName(idFile) : null;
    }

    private String getFileName(String path)
    {
        // File paths in .blib will be in Windows format. Path.getFileName() does not work correctly with Windows paths
        // when running on Unix. For example: Paths.get("V:\\Allie\\RasPhos\\MY20170124_ARC_RasPhosHmix_500fmol_01.mzXML").getFileName()
        // returns the full path on Unix. FileNameUtils.getName() will handle a file in either Unix or Windows format
        // https://commons.apache.org/proper/commons-io/apidocs/org/apache/commons/io/FilenameUtils.html#getName-java.lang.String-
        return FilenameUtils.getName(path);
    }

    public boolean containsScoreType(String scoreType)
    {
        return scoreTypes != null && scoreTypes.contains(scoreType);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LibSourceFile that = (LibSourceFile) o;
        return Objects.equals(getSpectrumSourceFile(), that.getSpectrumSourceFile())
                && Objects.equals(getIdFile(), that.getIdFile())
                && Objects.equals(scoreTypes, that.scoreTypes);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getSpectrumSourceFile(), getIdFile(), scoreTypes);
    }

    // For a MaxQuant search we need all of these files.  Only msms.txt is listed in the SpectrumSourceFiles table of .blib
    // https://skyline.ms/wiki/home/software/Skyline/page.view?name=building_spectral_libraries
    public static List<String> MAX_QUANT_ID_FILES = List.of("msms.txt", "mqpar.xml", "evidence.txt", "modifications.xml");

    public boolean isMaxQuantSearch()
    {
        return (hasIdFile() && getIdFile().endsWith("msms.txt")) || containsScoreType("MAXQUANT SCORE");
    }
}
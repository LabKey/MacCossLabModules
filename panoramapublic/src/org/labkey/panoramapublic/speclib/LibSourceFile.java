package org.labkey.panoramapublic.speclib;

import org.apache.commons.lang3.StringUtils;

import java.nio.file.Paths;
import java.util.Set;

public class LibSourceFile
{
    private final String spectrumSourceFile;
    private final String idFile;
    private final Set<String> scoreTypes;

    public LibSourceFile(String spectrumSourceFile, String idFile, Set<String> scoreTypes)
    {
        this.spectrumSourceFile = !StringUtils.isBlank(spectrumSourceFile) ? Paths.get(spectrumSourceFile).getFileName().toString() : null;
        this.idFile = !StringUtils.isBlank(idFile) ? Paths.get(idFile).getFileName().toString() : null;
        this.scoreTypes = scoreTypes != null && !scoreTypes.isEmpty() ? scoreTypes : null;
    }

    public boolean hasSpectrumSourceFile()
    {
        return spectrumSourceFile != null;
    }

    public String getSpectrumSourceFile()
    {
        return spectrumSourceFile;
    }

    public boolean hasIdFile()
    {
        return idFile != null;
    }

    public String getIdFile()
    {
        return idFile;
    }

    public boolean containsScoreType(String scoreType)
    {
        return scoreTypes != null && scoreTypes.contains(scoreType);
    }
}
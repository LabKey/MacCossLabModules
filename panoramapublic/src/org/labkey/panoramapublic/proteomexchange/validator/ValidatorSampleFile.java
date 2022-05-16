package org.labkey.panoramapublic.proteomexchange.validator;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.targetedms.ISampleFile;
import org.labkey.api.util.FileUtil;
import org.labkey.panoramapublic.model.validation.SkylineDocSampleFile;

import java.util.Date;
import java.util.Objects;

public class ValidatorSampleFile extends SkylineDocSampleFile
{
    private ISampleFile _sampleFile;

    private static final String DOT_WIFF = ".wiff";
    private static final String DOT_WIFF2 = ".wiff2";
    static final String DOT_SCAN = ".scan";

    public ValidatorSampleFile() {}

    public ValidatorSampleFile(ISampleFile sampleFile)
    {
        setName(sampleFile.getFileName());
        setFilePathImported(sampleFile.getFilePath());
        setSampleFileId(sampleFile.getId());
        _sampleFile = sampleFile;
    }

    public ISampleFile getSampleFile()
    {
        return _sampleFile;
    }

    public String getFileName()
    {
        return _sampleFile.getFileName();
    }

    public String getFilePath()
    {
        return getSampleFilePath();
    }

    public SampleFileKey getKey()
    {
        return new SampleFileKey(getFileName(), getSampleFilePath(), getSampleFile().getAcquiredTime(), getSampleFile().getInstrumentSerialNumber());
    }

    private String getSampleFilePath()
    {
        return isSciexWiff() || isSciexWiffScan() ? getSciexSampleFilePath(_sampleFile) : _sampleFile.getFilePath();
    }

    public boolean isSciexWiff()
    {
        return isSciexWiff(_sampleFile.getFileName());
    }

    private static boolean isSciexWiff(String fileName)
    {
        return fileName.toLowerCase().endsWith(DOT_WIFF) || fileName.toLowerCase().endsWith(DOT_WIFF2);
    }

    public boolean isSciexWiffScan()
    {
        return _sampleFile.getFileName().toLowerCase().endsWith(DOT_WIFF + DOT_SCAN);
    }

    static String addDotScanToWiffPath(@NotNull String sampleFileName, @NotNull String filePath)
    {
        if (isSciexWiff(sampleFileName))
        {
            String ext = FileUtil.getExtension(sampleFileName).toLowerCase();
            int idx = filePath.toLowerCase().indexOf(ext);
            if (idx != -1)
            {
                // Path may be for a multi-injection wiff file.
                // Example: D:\Data\Site52_041009_Study9S_Phase-I.wiff|Site52_STUDY9S_PHASEI_6ProtMix_QC_03|2
                return filePath.substring(0, idx + DOT_WIFF.length() - 1) // .wiff2 files require .wiff.scan, not .wiff2.scan
                        + DOT_SCAN
                        + filePath.substring(idx + ext.length());
            }
        }
        return filePath;
    }

    static String addDotScanToWiffFileName(@NotNull String sampleFileName)
    {
        if (isSciexWiff(sampleFileName))
        {
            if (sampleFileName.toLowerCase().endsWith(DOT_WIFF2))
            {
                sampleFileName = sampleFileName.substring(0, sampleFileName.length() - 1);
            }
            return sampleFileName + DOT_SCAN;
        }
        return sampleFileName;
    }

    // D:\Data\Site52_041009_Study9S_Phase-I.wiff|Site52_STUDY9S_PHASEI_6ProtMix_QC_03|2 -> D:\Data\Site52_041009_Study9S_Phase-I.wiff
    private static String getSciexSampleFilePath(ISampleFile file)
    {
        String filePath = file.getFilePath();
        int idx = filePath.indexOf(file.getFileName());
        if (idx != -1)
        {
            // Multi-injection SCIEX wiff files will have the same file name but different file_path attribute in the .sky XML.
            // For <sample_file id="_6ProtMix_QC_03_f0" file_path="D:\Data\CPTAC_Study9s\Site52_041009_Study9S_Phase-I.wiff|Site52_STUDY9S_PHASEI_6ProtMix_QC_03|2"
            // ISampleFile.getFileName() -> Site52_041009_Study9S_Phase-I.wiff
            // ISampleFile.getFilePath() -> D:\Data\CPTAC_Study9s\Site52_041009_Study9S_Phase-I.wiff|Site52_STUDY9S_PHASEI_6ProtMix_QC_03|2
            // We want  D:\Data\Site52_041009_Study9S_Phase-I.wiff
            return filePath.substring(0, idx + file.getFileName().length());
        }
        return filePath;
    }

    public static class SampleFileKey
    {
        private final String _fileName;
        private final String _importedPath;
        private final Date _acquiredTime;
        private final String _instrumentSerialNumber;

        public SampleFileKey(String fileName, String importedPath, Date acquiredTime, String instrumentSerialNumber)
        {
            _fileName = fileName;
            _importedPath = importedPath;
            _acquiredTime = acquiredTime;
            _instrumentSerialNumber = instrumentSerialNumber;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SampleFileKey that = (SampleFileKey) o;
            if (!_fileName.equals(that._fileName))
            {
                return false;
            }
            if (!_importedPath.equals(that._importedPath))
            {
                // If the path is not the same return true if the acquired time is not null, and is the same for both files.
                return (_acquiredTime != null && _acquiredTime.equals(that._acquiredTime)) && (Objects.equals(_instrumentSerialNumber, that._instrumentSerialNumber));
            }
            // file name is the same and the imported file path is the same. Return true
            return true;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(_fileName);
        }
    }
}

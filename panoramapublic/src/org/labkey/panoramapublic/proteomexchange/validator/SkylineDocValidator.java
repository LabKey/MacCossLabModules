package org.labkey.panoramapublic.proteomexchange.validator;

import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.targetedms.ISampleFile;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.targetedms.model.SampleFilePath;
import org.labkey.panoramapublic.model.validation.DataFile;
import org.labkey.panoramapublic.model.validation.SkylineDocValidation;
import org.labkey.panoramapublic.model.validation.SkylineDocSampleFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SkylineDocValidator extends SkylineDocValidation<ValidatorSampleFile>
{
    private final List<ValidatorSampleFile> _sampleFiles;
    private ITargetedMSRun _run;

    private static final String DOT_WIFF = ".wiff";
    private static final String DOT_WIFF2 = ".wiff2";
    private static final String DOT_SCAN = ".scan";

    public SkylineDocValidator()
    {
        _sampleFiles = new ArrayList<>();
    }

    public SkylineDocValidator(@NotNull ITargetedMSRun run)
    {
        this();
        _run = run;
    }

    public void addSampleFile(ValidatorSampleFile sampleFile)
    {
        _sampleFiles.add(sampleFile);
    }

    public ITargetedMSRun getRun()
    {
        return _run;
    }

    public Container getRunContainer()
    {
        return _run != null ? _run.getContainer() : null;
    }

    @Override
    public List<ValidatorSampleFile> getSampleFiles()
    {
        return Collections.unmodifiableList(_sampleFiles);
    }

    void addSampleFiles(TargetedMSService svc)
    {
        List<ValidatorSampleFile> docSampleFile = getDocSampleFiles(svc.getSampleFiles(getRunId()));
        docSampleFile.forEach(this::addSampleFile);
    }

    private static List<ValidatorSampleFile> getDocSampleFiles(List<? extends ISampleFile> sampleFiles)
    {
        Set<String> pathsImported = new HashSet<>(); // File paths that were imported in the Skyline document
                                                     // The same sample file may be imported into more than one replicate
                                                     // in the same Skyline document.
        Set<String> sciexWiffFileNames = new HashSet<>();

        List<ValidatorSampleFile> docSampleFiles = new ArrayList<>();

        for (ISampleFile s: sampleFiles)
        {
            ValidatorSampleFile sampleFile = new ValidatorSampleFile(s);
            if (!isSciexWiff(s.getFileName()))
            {
                if (!pathsImported.contains(s.getFilePath()))
                {
                    docSampleFiles.add(sampleFile);
                    pathsImported.add(s.getFilePath());
                }
            }
            else
            {
                String sciexSampleFilePath = getSciexSampleFilePath(s);
                if (sciexWiffFileNames.contains(sampleFile.getName()) && pathsImported.contains(sciexSampleFilePath))
                {
                    // Multi-injection SCIEX wiff files will have the same file name but different file_path attribute in the .sky XML.
                    // For <sample_file id="_6ProtMix_QC_03_f0" file_path="D:\Data\CPTAC_Study9s\Site52_041009_Study9S_Phase-I.wiff|Site52_STUDY9S_PHASEI_6ProtMix_QC_03|2"
                    // ISampleFile.getFileName() -> Site52_041009_Study9S_Phase-I.wiff
                    // ISampleFile.getFilePath() -> D:\Data\CPTAC_Study9s\Site52_041009_Study9S_Phase-I.wiff|Site52_STUDY9S_PHASEI_6ProtMix_QC_03|2
                    // We don't want to add this again to the list of document sample files if this is from a multi-injection wiff
                    continue;
                }

                sciexWiffFileNames.add(sampleFile.getName());
                pathsImported.add(sciexSampleFilePath);
                docSampleFiles.add(sampleFile);

                // For a SCIEX wiff file we will also add a corresponding .wiff.scan file
                ValidatorSampleFile wiffScanFile = new ValidatorSampleFile(new AbstractSampleFile()
                {
                    @Override
                    public String getFileName()
                    {
                        return s.getFileName() + DOT_SCAN;
                    }

                    @Override
                    public String getFilePath()
                    {
                        return addDotScanToPath(s);
                    }

                    @Override
                    public Long getInstrumentId()
                    {
                        return s.getInstrumentId();
                    }
                });
                docSampleFiles.add(wiffScanFile);
            }
        }
        return docSampleFiles;
    }

    private static boolean isSciexWiff(String fileName)
    {
        return fileName.toLowerCase().endsWith(DOT_WIFF) || fileName.toLowerCase().endsWith(DOT_WIFF2);
    }

    // D:\Data\Site52_041009_Study9S_Phase-I.wiff|Site52_STUDY9S_PHASEI_6ProtMix_QC_03|2 -> D:\Data\Site52_041009_Study9S_Phase-I.wiff
    private static String getSciexSampleFilePath(ISampleFile file)
    {
        String filePath = file.getFilePath();
        int idx = filePath.indexOf(file.getFileName());
        if (idx != -1)
        {
            // Example: D:\Data\Site52_041009_Study9S_Phase-I.wiff|Site52_STUDY9S_PHASEI_6ProtMix_QC_03|2
            // We want D:\Data\Site52_041009_Study9S_Phase-I.wiff
            return filePath.substring(0, idx + file.getFileName().length());
        }
        return filePath;
    }

    private static String addDotScanToPath(ISampleFile sampleFile)
    {
        String ext = sampleFile.getFileName().toLowerCase().endsWith(DOT_WIFF) ? DOT_WIFF : DOT_WIFF2;
        String filePath = sampleFile.getFilePath();
        int idx = filePath.toLowerCase().indexOf(ext);
        if (idx != -1)
        {
            // Path may be for a multi-injection wiff file.
            // Example: D:\Data\Site52_041009_Study9S_Phase-I.wiff|Site52_STUDY9S_PHASEI_6ProtMix_QC_03|2
            return filePath.substring(0, idx + ext.length())
                    + DOT_SCAN
                    + filePath.substring(idx + ext.length());
        }
        return filePath;
    }

    /**
     * Set the path on the sample files for this document, if the file is found on the server
     */
    void validateSampleFiles(TargetedMSService svc)
    {
        List<ISampleFile> sampleFiles = getSampleFiles().stream().map(ValidatorSampleFile::getSampleFile).collect(Collectors.toList());
        List<SampleFilePath> paths = svc.getSampleFilePaths(sampleFiles, getRunContainer(), false);
        Map<String, Path> pathMap = new HashMap<>();
        paths.forEach(p -> pathMap.put(p.getSampleFile().getFileName(), p.getPath()));

        Set<String> duplicateSkylineSampleFileNames = getDuplicateSkylineSampleFileNames(getSampleFiles());
        for (SkylineDocSampleFile sampleFile : getSampleFiles())
        {
            if (duplicateSkylineSampleFileNames.contains(sampleFile.getName()))
            {
                // We do not allow sample files with the same name but different paths imported into separate
                // replicates. Skyline allows this but it can get confusing even for the user.
                sampleFile.setPath(DataFile.AMBIGUOUS);
            }
            else
            {
                Path path = pathMap.get(sampleFile.getName());
                sampleFile.setPath(path != null ? path.toString() : DataFile.NOT_FOUND);
            }
        }
    }

    private static Set<String> getDuplicateSkylineSampleFileNames(List<ValidatorSampleFile> sampleFiles)
    {
        Map<String, Integer> counts = sampleFiles.stream().collect(Collectors.toMap(ValidatorSampleFile::getName, value -> 1, Integer::sum));
        Set<String> duplicates = new HashSet<>();
        counts.entrySet().stream().filter(entry -> entry.getValue() > 1).forEach(entry -> duplicates.add(entry.getKey()));
        return duplicates;
    }

    public abstract static class AbstractSampleFile implements ISampleFile
    {
        @Override
        public String getSampleName()
        {
            return null;
        }

        @Override
        public Date getAcquiredTime()
        {
            return null;
        }

        @Override
        public Date getModifiedTime()
        {
            return null;
        }

        @Override
        public String getSkylineId()
        {
            return null;
        }

        @Override
        public Double getTicArea()
        {
            return null;
        }

        @Override
        public String getInstrumentSerialNumber()
        {
            return null;
        }

        @Override
        public String getSampleId()
        {
            return null;
        }

        @Override
        public Double getExplicitGlobalStandardArea()
        {
            return null;
        }

        @Override
        public String getIonMobilityType()
        {
            return null;
        }

        @Override
        public String getFileName()
        {
            return getFileName(getFilePath());
        }

        // Copied from SampleFile in the targetedms module.
        // TODO: Move this to the TargetedMS API
        static String getFileName(String path)
        {
            if(path != null)
            {
                // If the file path has a '?' part remove it
                // Example: 2017_July_10_bivalves_292.raw?centroid_ms2=true.
                int idx = path.indexOf('?');
                path = (idx == -1) ? path : path.substring(0, idx);

                // If the file path has a '|' part for sample name from multi-injection wiff files remove it.
                // Example: D:\Data\CPTAC_Study9s\Site52_041009_Study9S_Phase-I.wiff|Site52_STUDY9S_PHASEI_6ProtMix_QC_07|6
                idx = path.indexOf('|');
                path =  (idx == -1) ? path : path.substring(0, idx);

                return FilenameUtils.getName(path);
            }
            return null;
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testAddDotScanToPath()
        {
            testAddDotScan("Site52_041009_Study9S_Phase-I.wiff", "", true);
            testAddDotScan("Site52_041009_Study9S_Phase-I.wiff2", "", true);
            // Multi-injection wiff file
            testAddDotScan("Site52_041009_Study9S_Phase-I.wiff", "|Site52_STUDY9S_PHASEI_6ProtMix_QC_03|2", true);
            testAddDotScan("Site52_041009_Study9S_Phase-I.WIFF2", "|Site52_STUDY9S_PHASEI_6ProtMix_QC_03|2", true);

            testAddDotScan("Site52_041009_Study9S_Phase-I.RAW", "", false);
        }

        private void testAddDotScan(String fileName, String injection, boolean isSciex)
        {
            ISampleFile file = createFile("D:\\Data\\" + fileName + injection);
            assertEquals("D:\\Data\\" + fileName + (isSciex ? DOT_SCAN : "") + injection, addDotScanToPath(file));
        }

        @Test
        public void testGetDocSampleFiles()
        {
            // file1 and file1_dup are the same file. The file may have been imported into two different replicate in the document. Add it only once
            // Count 1
            ISampleFile file1 = createFile("D:\\Data\\Thermo1.raw");
            ISampleFile file1_dup = createFile("D:\\Data\\Thermo1.raw");
            // Sample file has the same file name but different path from file1 and file1_dup. This should get added
            // Count 2
            ISampleFile file1_dup_diff_path = createFile("D:\\Data\\Subfolder\\Thermo1.raw");
            // Count 3
            ISampleFile file2 = createFile("D:\\Data\\Thermo2.raw");
            // wiff1 and wiff1_dup are the same file. The file may have been imported into two different replicate in the document. Add it only once
            // A wiff.scan will also be added
            // Count 5
            ISampleFile wiff1 = createFile("D:\\Data\\Sciex1.wiff");
            ISampleFile wiff1_dup = createFile("D:\\Data\\Sciex1.wiff");
            // Sample file has the same file name but different path from wiff1 and wiff1_dup. This should get added + a wiff.scan
            // Count 7
            ISampleFile wiff1_dup_diff_path = createFile("D:\\Data\\Subfolder\\Sciex1.wiff");
            // Count 9 (wiff + wiff.scan)
            ISampleFile wiff2 = createFile("D:\\Data\\Sciex2.wiff");
            // Multi inject wiff files. Will get added only once + wiff.scan
            // Count 11
            ISampleFile multiInjectWiff1_0 = createFile("D:\\Data\\Sciex_multi_1.wiff|injection01|0");
            ISampleFile multiInjectWiff1_1 = createFile("D:\\Data\\Sciex_multi_1.wiff|injection02|1");
            ISampleFile multiInjectWiff1_2 = createFile("D:\\Data\\Sciex_multi_1.wiff|injection03|2");
            // Sample file has the same file name but different path from the multi injection files above. This should get dded (+ wiff.scan)
            // Count 13
            ISampleFile multiInjectWiff1_0_diff_path = createFile("D:\\Data\\Subfolder\\Sciex_multi_1.wiff|injection01|0");
            // file3 and file3_dup are the same file. The file may have been imported into two different replicate in the document. Add it only once
            // Count 14
            ISampleFile file3 = createFile("D:\\Data\\Thermo3.raw");
            ISampleFile file3_dup = createFile("D:\\Data\\Thermo3.raw");

            List<ValidatorSampleFile> docSampleFiles = getDocSampleFiles(List.of(file1, file1_dup, file1_dup_diff_path,
                    file2,
                    file3, file3_dup,
                    wiff1, wiff1_dup, wiff1_dup_diff_path,
                    wiff2,
                    multiInjectWiff1_0, multiInjectWiff1_1, multiInjectWiff1_2, multiInjectWiff1_0_diff_path));

            List<ISampleFile> expected = List.of(file1, file1_dup_diff_path,
                    file2,
                    file3,
                    wiff1, wiff1_dup_diff_path,
                    wiff2,
                    multiInjectWiff1_0, multiInjectWiff1_0_diff_path);

            assertEquals("Unexpected sample file count", expected.size() + 5, docSampleFiles.size());

            // Non-Sciex files
            for (int i = 0; i < 4; i++)
            {
                assertEquals("Unexpected sample file name", expected.get(i).getFileName(), docSampleFiles.get(i).getName());
                assertEquals("Unexpected sample file path", expected.get(i).getFilePath(), docSampleFiles.get(i).getFilePathImported());
            }
            // Sciex files
            int j = 4;
            for (int i = 4; i < docSampleFiles.size();)
            {
                assertEquals("Unexpected wiff file name", expected.get(j).getFileName(), docSampleFiles.get(i).getName());
                assertEquals("Unexpected wiff file path", expected.get(j).getFilePath(), docSampleFiles.get(i).getFilePathImported());
                String wiffScanName = expected.get(j).getFileName() + DOT_SCAN;
                String wiffScanPath = addDotScanToPath(expected.get(j));
                assertEquals("Unexpected wiff.scan file name", wiffScanName, docSampleFiles.get(++i).getName());
                assertEquals("Unexpected wiff.scan file path", wiffScanPath, docSampleFiles.get(i).getFilePathImported());
                i++; j++;
            }

            Set<String> duplicateNames = SkylineDocValidator.getDuplicateSkylineSampleFileNames(docSampleFiles);
            Set<String> expectedDuplicates = Set.of(file1.getFileName(),
                    wiff1.getFileName(), wiff1.getFileName() + DOT_SCAN,
                    multiInjectWiff1_0.getFileName(), multiInjectWiff1_0.getFileName() + DOT_SCAN);
            assertEquals(expectedDuplicates.size(), duplicateNames.size());
            assertTrue(duplicateNames.containsAll(expectedDuplicates));
        }

        private ISampleFile createFile(String path)
        {
            return new AbstractSampleFile()
            {
                @Override
                public String getFilePath()
                {
                    return path;
                }

                @Override
                public Long getInstrumentId()
                {
                    return null;
                }
            };
        }
    }
}

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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.labkey.panoramapublic.proteomexchange.validator.ValidatorSampleFile.*;

public class SkylineDocValidator extends SkylineDocValidation<ValidatorSampleFile>
{
    private final List<ValidatorSampleFile> _sampleFiles;
    private ITargetedMSRun _run;

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
        var sampleFileList = svc.getSampleFiles(getRunId());
        List<ValidatorSampleFile> docSampleFiles = new ArrayList<>();
        for (ISampleFile sf: sampleFileList)
        {
            ValidatorSampleFile vsf = new ValidatorSampleFile(sf);
            docSampleFiles.add(vsf);
            addWiffScanIfWiff(docSampleFiles, vsf);
        }
        docSampleFiles.forEach(this::addSampleFile);
    }

    private static void addWiffScanIfWiff(List<ValidatorSampleFile> docSampleFiles, ValidatorSampleFile sampleFile)
    {
        if (sampleFile.isSciexWiff())
        {
            // For a SCIEX wiff file we will also add a corresponding .wiff.scan file
            ValidatorSampleFile wiffScanFile = new ValidatorSampleFile(new AbstractSampleFile()
            {
                @Override
                public String getFileName()
                {
                    return ValidatorSampleFile.addDotScanToWiffFileName(sampleFile.getFileName());
                }

                @Override
                public long getId()
                {
                    return sampleFile.getSampleFile().getId();
                }

                @Override
                public long getReplicateId()
                {
                    return sampleFile.getSampleFile().getReplicateId();
                }

                @Override
                public String getFilePath()
                {
                    return addDotScanToPath(sampleFile);
                }

                @Override
                public Long getInstrumentId()
                {
                    return sampleFile.getSampleFile().getInstrumentId();
                }
            });
            docSampleFiles.add(wiffScanFile);
        }
    }

    private static String addDotScanToPath(ValidatorSampleFile sampleFile)
    {
        return ValidatorSampleFile.addDotScanToWiffPath(sampleFile.getFileName(), sampleFile.getFilePathImported());
    }

    /**
     * Set the path on the sample files for this document, if the file is found on the server.
     * The document's own container is searched first. For any files not found there, the other
     * folders of the experiment are searched (e.g. raw files shared in a parent folder while the
     * document lives in a subfolder). The first folder that contains a file wins.
     *
     * @param experimentFolders all the folders included in the experiment (see
     * {@code ExperimentAnnotationsManager.getExperimentFolders}). The document's own container is
     * always searched first; the remaining folders are searched only for files not yet found.
     */
    void validateSampleFiles(TargetedMSService svc, Collection<Container> experimentFolders)
    {
        List<ISampleFile> sampleFiles = getSampleFiles().stream().map(ValidatorSampleFile::getSampleFile).collect(Collectors.toList());

        List<Container> orderedFolders = orderedFolders(getRunContainer(), experimentFolders);
        Map<String, Path> pathMap = resolveSampleFilePaths(sampleFiles, orderedFolders,
                (files, container) -> svc.getSampleFilePaths(files, container, false));

        for (SkylineDocSampleFile sampleFile : getSampleFiles())
        {
            Path path = pathMap.get(sampleFile.getName());
            sampleFile.setPath(path != null ? path.toString() : DataFile.NOT_FOUND);
        }
    }

    /** The document's own container first, followed by the other experiment folders. */
    private static List<Container> orderedFolders(Container runContainer, Collection<Container> experimentFolders)
    {
        List<Container> ordered = new ArrayList<>();
        ordered.add(runContainer);
        for (Container c : experimentFolders)
        {
            if (!c.equals(runContainer))
            {
                ordered.add(c);
            }
        }
        return ordered;
    }

    /**
     * Resolve a server path for each sample file by searching the given folders in order. A file is
     * looked up only in folders up to and including the first one that contains it (first found wins);
     * once every file has a path no further folders are searched. Returns a map from file name to the
     * resolved path; files not found in any folder are absent from the map.
     *
     * <p>The folder lookup is injected as {@code resolver} (production binds it to
     * {@code TargetedMSService.getSampleFilePaths}) so the iteration logic can be unit tested without a
     * real filesystem, {@link Container}, or {@link TargetedMSService}.
     */
    static <C> Map<String, Path> resolveSampleFilePaths(List<ISampleFile> sampleFiles, List<C> orderedFolders,
                                                        BiFunction<List<ISampleFile>, C, List<SampleFilePath>> resolver)
    {
        Map<String, Path> pathMap = new HashMap<>();
        for (C folder : orderedFolders)
        {
            List<ISampleFile> missing = sampleFiles.stream()
                    .filter(sf -> !pathMap.containsKey(sf.getFileName()))
                    .collect(Collectors.toList());
            if (missing.isEmpty())
            {
                break;
            }
            // putIfAbsent so that the first folder with a match for a given file name wins.
            resolver.apply(missing, folder).stream()
                    .filter(p -> p.getPath() != null)
                    .forEach(p -> pathMap.putIfAbsent(p.getSampleFile().getFileName(), p.getPath()));
        }
        return pathMap;
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
            var file = createFile("D:\\Data\\" + fileName + injection);
            if (isSciex)
            {
                fileName = fileName.endsWith("2") ? fileName.substring(0, fileName.length() - 1): fileName;
            }
            assertEquals("D:\\Data\\" + fileName + (isSciex ? DOT_SCAN : "") + injection, addDotScanToPath(file));
        }

        @Test
        public void testGetDocSampleFiles() throws ParseException
        {
            Date date1 = new SimpleDateFormat("yyyy/MM/dd").parse("2021/03/28");
            Date date2 = new SimpleDateFormat("yyyy/MM/dd").parse("2021/04/18");

            // file1 and file1_dup are the same file. The file may have been imported into two different replicate in the document. Add it only once
            // Count 1
            var file1 = createFile("D:\\Data\\Thermo1.raw");
            var file1_dup = createFile("D:\\Data\\Thermo1.raw");
            assertEquals("Files have same name and path. They are equal", file1.getKey(), file1_dup.getKey());
            // Sample file has the same file name but different path from file1 and file1_dup. This should get added
            // Count 2
            var file1_dup_diff_path = createFile("D:\\Data\\Subfolder\\Thermo1.raw");
            assertNotEquals("Files have same name but different path. They are not equal", file1.getKey(), file1_dup_diff_path.getKey());

            // Count 3
            var file2 = createFile("D:\\Data\\Thermo2.raw", date1, "SERIAL1");
            // Sample file has the same file name and path but different date. If the name and file path are the same the files are considered equal
            var file2_dup_diff_time = createFile("D:\\Data\\Thermo2.raw", date2, "SERIAL2");
            assertEquals("Files have same name and path. Date is ignored in this case", file2.getKey(), file2_dup_diff_time.getKey());
            // Sample file has the same file name, different path but same acquisition date. This will be considered equal
            var file2_dup_diff_path_same_date = createFile("E:\\Data\\Thermo2.raw", date1, "SERIAL1");
            assertEquals("Files have different path but same date. They are equal", file2.getKey(), file2_dup_diff_path_same_date.getKey());
            // Sample file has the same file different path and acquisition date. This should get added
            // Count 4
            var file2_dup_diff_path_diff_date = createFile("E:\\Data\\Thermo2.raw", date2, "SERIAL2");
            assertNotEquals("Files have different path and acquisition date. They are not equal", file2.getKey(), file2_dup_diff_path_diff_date.getKey());

            // wiff1 and wiff1_dup are the same file. The file may have been imported into two different replicate in the document. Add it only once
            // A wiff.scan will also be added
            // Count 6
            var wiff1 = createFile("D:\\Data\\Sciex1.wiff");
            var wiff1_dup = createFile("D:\\Data\\Sciex1.wiff");
            assertEquals("Files have same name and path. They are equal", wiff1.getKey(), wiff1_dup.getKey());
            // Sample file has the same file name but different path from wiff1 and wiff1_dup. This should get added + a wiff.scan
            // Count 8
            var wiff1_dup_diff_path = createFile("D:\\Data\\Subfolder\\Sciex1.wiff");
            assertNotEquals("Files have same name but different path. They are not equal", wiff1.getKey(), wiff1_dup_diff_path.getKey());

            // Count 10 (wiff + wiff.scan)
            var wiff2 = createFile("D:\\Data\\Sciex2.wiff");

            // Multi inject wiff files. Will get added only once + wiff.scan
            // Count 12
            var multiInjectWiff1_0 = createFile("D:\\Data\\Sciex_multi_1.wiff|injection01|0");
            var multiInjectWiff1_1 = createFile("D:\\Data\\Sciex_multi_1.wiff|injection02|1");
            var multiInjectWiff1_2 = createFile("D:\\Data\\Sciex_multi_1.wiff|injection03|2");
            assertEquals("Files have same name and path. They are equal", multiInjectWiff1_0.getKey(), multiInjectWiff1_1.getKey());
            assertEquals("Files have same name and path. They are equal", multiInjectWiff1_0.getKey(), multiInjectWiff1_2.getKey());
            // Sample file has the same file name but different path from the multi injection files above. This should get dded (+ wiff.scan)
            // Count 14
            var multiInjectWiff1_0_diff_path = createFile("D:\\Data\\Subfolder\\Sciex_multi_1.wiff|injection01|0");
            assertNotEquals("Files have same name but different path. They are not equal", multiInjectWiff1_0.getKey(), multiInjectWiff1_0_diff_path.getKey());

            // file3 and file3_dup are the same file. The file may have been imported into two different replicate in the document. Add it only once
            // Count 15
            var file3 = createFile("D:\\Data\\Thermo3.raw");
            var file3_dup = createFile("D:\\Data\\Thermo3.raw");
            assertEquals("Files have same name and path. They are equal", file3.getKey(), file3_dup.getKey());

            // Count 17 (wiff2 + wiff.scan)
            var file4 = createFile("D:\\Data\\Sciex2.wiff2");
            // Count 19 (wiff2 + wiff.scan)
            var file5 = createFile("D:\\Data\\Sciex3.wiff2|injection01|0");

            List<ValidatorSampleFile> docSampleFiles = getUniqueDocSampleFiles(List.of(file1, file1_dup, file1_dup_diff_path,
                    file2, file2_dup_diff_time, file2_dup_diff_path_same_date, file2_dup_diff_path_diff_date,
                    file3, file3_dup,
                    wiff1, wiff1_dup, wiff1_dup_diff_path,
                    wiff2,
                    multiInjectWiff1_0, multiInjectWiff1_1, multiInjectWiff1_2, multiInjectWiff1_0_diff_path,
                    file4,
                    file5));

            List<ValidatorSampleFile> expected = List.of(file1, file1_dup_diff_path,
                    file2, file2_dup_diff_path_diff_date,
                    file3,
                    wiff1, wiff1_dup_diff_path,
                    wiff2,
                    multiInjectWiff1_0, multiInjectWiff1_0_diff_path,
                    file4,
                    file5);

            assertEquals("Unexpected sample file count", expected.size() + 7, docSampleFiles.size());

            // Non-Sciex files
            for (int i = 0; i < 5; i++)
            {
                assertEquals("Unexpected sample file name", expected.get(i).getFileName(), docSampleFiles.get(i).getName());
                assertEquals("Unexpected sample file path", expected.get(i).getFilePath(), docSampleFiles.get(i).getFilePathImported());
                assertEquals("Unexpected acquisition time", expected.get(i).getSampleFile().getAcquiredTime(),
                        docSampleFiles.get(i).getSampleFile().getAcquiredTime());
                assertEquals("Unexpected instrument serial number", expected.get(i).getSampleFile().getInstrumentSerialNumber(),
                        docSampleFiles.get(i).getSampleFile().getInstrumentSerialNumber());
            }
            // Sciex files
            int j = 5;
            for (int i = 5; i < docSampleFiles.size();)
            {
                assertEquals("Unexpected wiff file name", expected.get(j).getFileName(), docSampleFiles.get(i).getName());
                assertEquals("Unexpected wiff file path", expected.get(j).getFilePathImported(), docSampleFiles.get(i).getFilePathImported());
                String wiffName = expected.get(j).getFileName();
                if (wiffName.endsWith("wiff2"))
                {
                    wiffName = wiffName.substring(0, wiffName.length() - 1);
                }
                String wiffScanName = wiffName + DOT_SCAN;
                assertEquals("Unexpected wiff.scan file name", wiffScanName, docSampleFiles.get(++i).getName());

                String wiffScanPath = expected.get(j).getFilePathImported().replace( expected.get(j).getFileName(), wiffScanName); // .wiff -> .wiff.scan
                assertEquals("Unexpected wiff.scan imported file path", wiffScanPath, docSampleFiles.get(i).getFilePathImported());

                wiffScanPath = wiffScanPath.substring(0, wiffScanPath.indexOf(wiffScanName) + wiffScanName.length()); // just the path
                assertEquals("Unexpected wiff.scan file path", wiffScanPath, docSampleFiles.get(i).getFilePath());
                i++; j++;
            }

            Set<String> duplicateNames = getDuplicateSkylineSampleFileNames(docSampleFiles);
            Set<String> expectedDuplicates = Set.of(file1.getFileName(),file2.getFileName(),
                    wiff1.getFileName(), wiff1.getFileName() + DOT_SCAN,
                    multiInjectWiff1_0.getFileName(), multiInjectWiff1_0.getFileName() + DOT_SCAN,
                    ValidatorSampleFile.addDotScanToWiffFileName(file4.getFileName())); // Because we have a Sciex2.wiff and a Sciex2.wiff2 both of thich
                                                                                        // will add a Sciex2.wiff.scan
            assertEquals(expectedDuplicates.size(), duplicateNames.size());
            assertTrue(duplicateNames.containsAll(expectedDuplicates));
        }

        @Test
        public void testResolveSampleFilesAcrossFolders()
        {
            ISampleFile fileA = sampleFile("A.raw");
            ISampleFile fileB = sampleFile("B.raw");
            ISampleFile fileC = sampleFile("C.raw");
            ISampleFile fileD = sampleFile("D.raw");
            List<ISampleFile> files = List.of(fileA, fileB, fileC, fileD);

            // Which file names are present under each folder's RawFiles directory.
            // "home" is the document's own container. B is present in both "parent" and "sibling".
            Map<String, Set<String>> folderContents = new HashMap<>();
            folderContents.put("home", Set.of("A.raw"));
            folderContents.put("parent", Set.of("B.raw", "C.raw"));
            folderContents.put("sibling", Set.of("B.raw", "D.raw"));

            List<String> queried = new ArrayList<>();
            var paths = resolveSampleFilePaths(files, List.of("home", "parent", "sibling"), recordingResolver(folderContents, queried));

            assertEquals("A.raw should be found in the home folder", Path.of("home", "A.raw"), paths.get("A.raw"));
            assertEquals("B.raw should be found in parent (first folder with a match wins over sibling)", Path.of("parent", "B.raw"), paths.get("B.raw"));
            assertEquals("C.raw should be found in parent", Path.of("parent", "C.raw"), paths.get("C.raw"));
            assertEquals("D.raw should be found in sibling", Path.of("sibling", "D.raw"), paths.get("D.raw"));
            assertEquals("Folders should be searched in order until all files are found", List.of("home", "parent", "sibling"), queried);
        }

        @Test
        public void testResolveSampleFilesEarlyBreak()
        {
            // All files are in the home folder, so no other folder should be searched.
            List<ISampleFile> files = List.of(sampleFile("A.raw"), sampleFile("B.raw"));
            Map<String, Set<String>> folderContents = Map.of("home", Set.of("A.raw", "B.raw"), "parent", Set.of("A.raw", "B.raw"));

            List<String> queried = new ArrayList<>();
            var paths = resolveSampleFilePaths(files, List.of("home", "parent"), recordingResolver(folderContents, queried));

            assertEquals(Path.of("home", "A.raw"), paths.get("A.raw"));
            assertEquals(Path.of("home", "B.raw"), paths.get("B.raw"));
            assertEquals("Should stop searching once every file is found", List.of("home"), queried);
        }

        @Test
        public void testResolveSampleFilesNotFound()
        {
            List<ISampleFile> files = List.of(sampleFile("A.raw"), sampleFile("Missing.raw"));
            Map<String, Set<String>> folderContents = Map.of("home", Set.of("A.raw"), "parent", Set.of("A.raw"));

            List<String> queried = new ArrayList<>();
            var paths = resolveSampleFilePaths(files, List.of("home", "parent"), recordingResolver(folderContents, queried));

            assertEquals(Path.of("home", "A.raw"), paths.get("A.raw"));
            assertFalse("A file present in no folder should be absent from the map (caller marks it NOT_FOUND)", paths.containsKey("Missing.raw"));
            assertEquals("All folders are searched while a file is still missing", List.of("home", "parent"), queried);
        }

        @Test
        public void testResolveSampleFilesSingleFolder()
        {
            // includeSubfolders = false: the ordered folder list is just the document's own container.
            List<ISampleFile> files = List.of(sampleFile("A.raw"), sampleFile("B.raw"));
            Map<String, Set<String>> folderContents = Map.of("home", Set.of("A.raw"));

            List<String> queried = new ArrayList<>();
            var paths = resolveSampleFilePaths(files, List.of("home"), recordingResolver(folderContents, queried));

            assertEquals(Path.of("home", "A.raw"), paths.get("A.raw"));
            assertFalse("B.raw is not in the only folder, so it stays unresolved", paths.containsKey("B.raw"));
            assertEquals(List.of("home"), queried);
        }

        // A resolver that reports a file as found (with a path of "<folder>/<fileName>") when the folder's
        // contents include it, and records the folders it was asked to search so tests can assert search order.
        private BiFunction<List<ISampleFile>, String, List<SampleFilePath>> recordingResolver(Map<String, Set<String>> folderContents, List<String> queried)
        {
            return (requested, folder) -> {
                queried.add(folder);
                Set<String> contents = folderContents.getOrDefault(folder, Set.of());
                List<SampleFilePath> result = new ArrayList<>();
                for (ISampleFile sf : requested)
                {
                    SampleFilePath path = new SampleFilePath(sf);
                    if (contents.contains(sf.getFileName()))
                    {
                        path.setPath(Path.of(folder, sf.getFileName()));
                    }
                    result.add(path);
                }
                return result;
            };
        }

        private ISampleFile sampleFile(String fileName)
        {
            return new AbstractSampleFile()
            {
                @Override
                public String getFileName()
                {
                    return fileName;
                }

                @Override
                public long getId()
                {
                    return 0;
                }

                @Override
                public long getReplicateId()
                {
                    return 0;
                }

                @Override
                public String getFilePath()
                {
                    return "D:\\Data\\" + fileName;
                }

                @Override
                public Long getInstrumentId()
                {
                    return null;
                }
            };
        }

        private List<ValidatorSampleFile> getUniqueDocSampleFiles(List<ValidatorSampleFile> sampleFiles)
        {
            List<ValidatorSampleFile> docSampleFiles = new ArrayList<>();
            Set<SampleFileKey> sampleFileKeys = new HashSet<>();

            for (ValidatorSampleFile sampleFile: sampleFiles)
            {
                if (sampleFileKeys.contains(sampleFile.getKey()))
                {
                    continue;
                }
                sampleFileKeys.add(sampleFile.getKey());
                docSampleFiles.add(sampleFile);

                addWiffScanIfWiff(docSampleFiles, sampleFile);
            }
            return docSampleFiles;
        }

        private Set<String> getDuplicateSkylineSampleFileNames(List<ValidatorSampleFile> sampleFiles)
        {
            Map<String, Integer> counts = sampleFiles.stream().collect(Collectors.toMap(ValidatorSampleFile::getName, value -> 1, Integer::sum));
            Set<String> duplicates = new HashSet<>();
            counts.entrySet().stream().filter(entry -> entry.getValue() > 1).forEach(entry -> duplicates.add(entry.getKey()));
            return duplicates;
        }

        private ValidatorSampleFile createFile(String path)
        {
            return createFile(path, null, null);
        }

        private ValidatorSampleFile createFile(String path, Date acquiredTime, String instrumentSerialNumber)
        {
            var iSampleFile = new AbstractSampleFile()
            {
                @Override
                public long getId()
                {
                    return 0;
                }

                @Override
                public long getReplicateId()
                {
                    return 0;
                }

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

                @Override
                public Date getAcquiredTime()
                {
                    return acquiredTime;
                }

                @Override
                public String getInstrumentSerialNumber()
                {
                    return instrumentSerialNumber;
                }
            };
            return new ValidatorSampleFile(iSampleFile);
        }
    }
}

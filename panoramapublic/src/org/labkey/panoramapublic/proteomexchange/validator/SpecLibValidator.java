/*
 * Copyright (c) 2022-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.panoramapublic.proteomexchange.validator;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.targetedms.ISpectrumLibrary;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.panoramapublic.PanoramaPublicModule;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.speclib.SpecLibInfo;
import org.labkey.panoramapublic.model.speclib.SpecLibKey;
import org.labkey.panoramapublic.model.validation.DataFile;
import org.labkey.panoramapublic.model.validation.SpecLibSourceFile;
import org.labkey.panoramapublic.model.validation.SpecLibValidation;
import org.labkey.panoramapublic.speclib.LibSourceFile;
import org.labkey.panoramapublic.speclib.LibraryType;
import org.labkey.panoramapublic.speclib.SpecLibReader;
import org.labkey.panoramapublic.speclib.SpecLibReaderException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.labkey.panoramapublic.model.validation.SpecLibSourceFile.LibrarySourceFileType.PEPTIDE_ID;
import static org.labkey.panoramapublic.model.validation.SpecLibSourceFile.LibrarySourceFileType.SPECTRUM;

public class SpecLibValidator extends SpecLibValidation<ValidatorSkylineDocSpecLib>
{
    private static final List<String> RAW_FILE_TYPES = List.of("raw", "wiff", "lcd", "d", "mzxml", "mzml");
    private static final String TSV = "tsv";
    private static final String PARQUET = "parquet";

    private List<ValidatorSkylineDocSpecLib> _docsWithLibrary;
    private SpecLibKeyWithSize _key;
    private SpecLibInfo _specLibInfo;

    public SpecLibValidator() {}

    public SpecLibValidator(ISpectrumLibrary library, @Nullable Long fileSize)
    {
        setSpectrumFiles(new ArrayList<>());
        setIdFiles(new ArrayList<>());
        setLibName(library.getName());
        setFileName(library.getFileNameHint());
        setLibType(library.getLibraryType());
        setSize(fileSize);
        _key = new SpecLibKeyWithSize(library, fileSize);
        _docsWithLibrary = new ArrayList<>();
    }

    public void setSpecLibInfo(SpecLibInfo libInfo)
    {
        setSpecLibInfoId(libInfo != null ? libInfo.getId() : null);
        _specLibInfo = libInfo;
    }

    @Override
    public SpecLibInfo getSpecLibInfo()
    {
        return _specLibInfo;
    }

    @Override
    public @NotNull List<ValidatorSkylineDocSpecLib> getDocsWithLibrary()
    {
        return _docsWithLibrary;
    }

    public void addDocumentLibrary(SkylineDocValidator doc, ISpectrumLibrary specLib)
    {
        ValidatorSkylineDocSpecLib docLib = new ValidatorSkylineDocSpecLib(specLib, doc.getRun());
        docLib.setSpeclibValidationId(getId()); // TODO: id has not been set yet
        docLib.setSkylineDocValidationId(doc.getId());
        docLib.setIncluded(getSize() != null);
        _docsWithLibrary.add(docLib);
    }

    public SpecLibKeyWithSize getKey()
    {
        return _key;
    }

    @Override
    public String toString()
    {
        return String.format("'%s' (%s) library in %d Skyline documents was built with %d raw files; %d peptide Id files. Status: %s",
                getLibName(), getFileName(), _docsWithLibrary.size(), getSpectrumFiles().size(), getIdFiles().size(), getStatusString());
    }

    /**
     * Read the library file to get the names of the source files and get their paths on the server.
     */
    List<String> validate(FileContentService fcs, ExperimentAnnotations expAnnotations)
    {
        if (isMissingInSkyZip())
        {
            // Library file was not found. This library will be marked as incomplete.
            return Collections.emptyList();
        }
        List<String> errors = new ArrayList<>();

        List<LibSourceFile> libSources = null;
        // Read the sources file names from the library file in each document that has the library.
        for (ValidatorSkylineDocSpecLib docLib: getDocsWithLibrary())
        {
            List<LibSourceFile> docLibSources = getLibrarySources(docLib);

            if (libSources == null)
            {
                libSources = docLibSources;
            }
            else if(!areSameSources(libSources, docLibSources))
            {
                // We expect that libraries with the same SpecLibKeyWithSize (library name, library file name, type, file size) will have the same source
                // files. We don't expect to see this error but if we see this error then we will have to include the source file name in determining unique libraries.
                errors.add(String.format("Expected library sources to match in all documents with the library '%s'"
                        + ". But they did not match for the library in the document '%s'. Other documents that have this library are %s.",
                        getKey().toString(),
                        docLib.getRun().getFileName(),
                        StringUtils.join(getDocsWithLibrary().stream().map(dl -> dl.getRun().getFileName()).collect(Collectors.toSet()), ", ")));
            }
        }

        // library sources will be null if the library is not supported, or e.g. the required table was not found in the .blib
        if (errors.isEmpty() && libSources != null)
        {
            validateLibrarySources(libSources, fcs, expAnnotations);
        }

        return errors;
    }

    @Nullable
    private List<LibSourceFile> getLibrarySources(ValidatorSkylineDocSpecLib docLib)
    {
        ISpectrumLibrary isl = docLib.getLibrary();
        SpecLibReader libReader = SpecLibReader.getReader(isl);

        if (libReader != null)
        {
            Path libFilePath = TargetedMSService.get().getLibraryFilePath(docLib.getRun(), isl);
            return getLibSources(libReader, isl, libFilePath, docLib.getRun().getFileName());
        }
        return null;
    }

    @Nullable
    private static List<LibSourceFile> getLibSources(SpecLibReader libReader, ISpectrumLibrary isl, Path libFilePath, String documentName)
    {
        List<LibSourceFile> sourceFiles;
        try
        {
            sourceFiles = libReader.readLibSourceFiles(isl, libFilePath, documentName);
        }
        catch (SpecLibReaderException e)
        {
            throw UnexpectedException.wrap(e, "Error reading source files from library file " + libFilePath.toString());
        }

        if (sourceFiles == null) return null;

        if (sourceFiles.stream().anyMatch(LibSourceFile::isMaxQuantSearch))
        {
            // For libraries built with MaxQuant search results we need to add additional files that are required for library building
            Set<String> idFileNames = sourceFiles.stream().filter(LibSourceFile::hasIdFile).map(LibSourceFile::getIdFile).collect(Collectors.toSet());
            for (String file : LibSourceFile.MAX_QUANT_ID_FILES)
            {
                if (!idFileNames.contains(file))
                {
                    sourceFiles.add(new LibSourceFile(null, file, null));
                }
            }
        }
        else if (sourceFiles.stream().anyMatch(LibSourceFile::isDiannSearch))
        {
            // Building a library with DIA-NN results in Skyline requires a .speclib file and a report file (.parquet or .tsv).
            // The .blib file includes the name of .speclib but not the name of the report file.
            // Building a library without the report file gives this error message in Skyline:
            // "...the Parquet or TSV report is required to read speclib files and must be in the same directory as the speclib
            // and share some leading characters (e.g. somedata-tsv.speclib and somedata-report.parquet)..."

            // At some point Skyline may start including the names of all source files in the .blib SQLite file,
            // so first check if any Parquet or TSV files were listed as sources in the .blib
            boolean hasReportFiles = sourceFiles.stream()
                    .anyMatch(file -> file.hasIdFile()
                            && (TSV.equals(FileUtil.getExtension(file.getIdFile().toLowerCase()))
                                || PARQUET.equals(FileUtil.getExtension(file.getIdFile().toLowerCase()))));
            if (!hasReportFiles)
            {
                // If there is no Parquet or TSV source listed in the .blib, then add a placeholder for the DIA-NN report file.
                sourceFiles.add(new LibSourceFile(null, LibSourceFile.DIANN_REPORT_PLACEHOLDER, null));
            }
        }

        return sourceFiles;
    }

    private static boolean areSameSources(List<LibSourceFile> sources, List<LibSourceFile> docLibSources)
    {
        if (sources != null)
        {
            sources.sort(specLibSourceComparator());
        }
        if (docLibSources != null)
        {
            docLibSources.sort(specLibSourceComparator());
        }
        return Objects.equals(sources, docLibSources);
    }

    private static Comparator<LibSourceFile> specLibSourceComparator()
    {
        return Comparator.comparing(LibSourceFile::getSpectrumSourceFile, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(LibSourceFile::getIdFile, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private void validateLibrarySources(List<LibSourceFile> sources, FileContentService fcs, ExperimentAnnotations expAnnotations)
    {
        Set<String> checkedFiles = new HashSet<>();
        // Since a library can be used with multiple Skyline documents which could be in subfolders we will look for the source files in the main experiment
        // folder as well as any subfolders containing documents that have the library
        Set<Container> containers = getDocsWithLibrary().stream().map(dl -> dl.getRun().getContainer()).collect(Collectors.toSet());
        containers.add(expAnnotations.getContainer());
        Set<Path> rawFilesDirPaths = new HashSet<>();
        for (Container container: containers)
        {
            var rawFilesDirPath = DataValidator.getRawFilesDirPath(container, fcs);
            // We will look in the "RawFiles" directory only if it exists.
            if (Files.exists(rawFilesDirPath))
            {
                rawFilesDirPaths.add(rawFilesDirPath);
            }
        }

        List<SpecLibSourceFile> spectrumFiles = new ArrayList<>();
        List<SpecLibSourceFile> idFiles = new ArrayList<>();

        for (LibSourceFile source: sources)
        {
            String ssf = source.getSpectrumSourceFile();
            if (source.hasSpectrumSourceFile() && !checkedFiles.contains(ssf))
            {
                checkedFiles.add(ssf);
                // Libraries built with MaxQuant or DIA-NN v2.0 results may only have the base raw file names (without extension)
                // stored in the BLIB. If the library source is either MaxQuant or DIA-NN we will compare with base file names of valid raw files.
                boolean allowBaseName = source.isMaxQuantSearch() || source.isDiannSearch();
                Path path = getPath(ssf, rawFilesDirPaths, allowBaseName);
                SpecLibSourceFile sourceFile = new SpecLibSourceFile(ssf, SPECTRUM);
                sourceFile.setSpecLibValidationId(getId());
                sourceFile.setPath(path != null ? path.toString() : DataFile.NOT_FOUND);
                spectrumFiles.add(sourceFile);
            }
            String idFile = source.getIdFile();
            if (source.hasIdFile() && !checkedFiles.contains(idFile))
            {
                if (LibSourceFile.DIANN_REPORT_PLACEHOLDER.equals(idFile)) continue; // We will look for this when we come to the .speclib file

                checkedFiles.add(idFile);
                Path path = getPath(idFile, rawFilesDirPaths, false);
                SpecLibSourceFile sourceFile = new SpecLibSourceFile(idFile, PEPTIDE_ID);
                sourceFile.setSpecLibValidationId(getId());
                sourceFile.setPath(path != null ? path.toString() : DataFile.NOT_FOUND);
                idFiles.add(sourceFile);

                if (source.isDiannSearch())
                {
                    // If this is a DIA-NN .speclib file, check for the required report file (Parquet or TSV).
                    // We are doing this because the .blib does not include the name of the report file.
                    // We only know that: "the Parquet or TSV report  is required to read speclib files and must be in the
                    // same directory as the speclib and share some leading characters
                    // (e.g. somedata-tsv.speclib and somedata-report.parquet)"
                    Path reportFilePath = sourceFile.found() ? getDiannReportFilePath(path) : null;
                    SpecLibSourceFile diannReportSourceFile = new SpecLibSourceFile(LibSourceFile.DIANN_REPORT_PLACEHOLDER, PEPTIDE_ID);
                    diannReportSourceFile.setSpecLibValidationId(getId());
                    diannReportSourceFile.setPath(reportFilePath != null ? reportFilePath.toString() : DataFile.NOT_FOUND);
                    idFiles.add(diannReportSourceFile);
                    checkedFiles.add(idFile);
                }
            }
        }
        setSpectrumFiles(spectrumFiles);
        setIdFiles(idFiles);
    }

    private Path getPath(String name, Set<Path> rawFilesDirPaths, boolean allowBaseName)
    {
        for (Path rawFilesDir: rawFilesDirPaths)
        {
            Path path = findInDirectoryTree(rawFilesDir, name, allowBaseName);
            if (path != null)
            {
                return path;
            }
        }
        return null;
    }

    private static Path getDiannReportFilePath(Path speclibFilePath)
    {
        Path specLibFileDir = speclibFilePath.getParent();
        try (Stream<Path> paths = Files.list(specLibFileDir))
        {
            List<Path> files = paths.filter(path -> Files.isRegularFile(path)).collect(Collectors.toList());
            return getDiannReportFilePath(speclibFilePath.getFileName().toString(), files);
        }
        catch (IOException e)
        {
            throw UnexpectedException.wrap(e, "Error looking for DIA-NN report TSV file in " + specLibFileDir);
        }
    }

    private static Path getDiannReportFilePath(String specLibFileName, List<Path> candidateFiles)
    {
        // First look for a matching Parquet file
        Map<Path, Integer> prefixLengthMap = getCommonPrefixLengthsForParquetFiles(candidateFiles, specLibFileName);
        // Find the Parquet file with the longest common prefix
        Path parquetFile = prefixLengthMap.entrySet().stream()
                .sorted((entry1, entry2) -> Integer.compare(entry2.getValue(), entry1.getValue())) // Sort descending by matching prefix length
                .map(Map.Entry::getKey)  // File paths
                .findFirst() // Get the first file that meets the conditions
                .orElse(null);
        if (parquetFile != null) return parquetFile;


        // Look for a matching TSV file if we did not find a Parquet file
        prefixLengthMap = getCommonPrefixLengthsForTsvFiles(candidateFiles, specLibFileName);

        // Find the TSV file with the longest common prefix that also has the expected column headers in the first line
        return prefixLengthMap.entrySet().stream()
                .sorted((entry1, entry2) -> Integer.compare(entry2.getValue(), entry1.getValue())) // Sort descending by matching prefix length
                .map(Map.Entry::getKey)  // File paths
                .filter(file -> hasRequiredHeaders(file)) // First line should have expected header columns
                .findFirst() // Get the first file that meets the conditions
                .orElse(null);
    }

    private static Map<Path, Integer> getCommonPrefixLengths(List<Path> files, String specLibFileName, String fileExtension)
    {
        String specLibFileBaseName = FileUtil.getBaseName(specLibFileName); // Remove file extension
        Map<Path, Integer> prefixLengthMap = new HashMap<>();
        files.stream()
                .filter(file -> fileExtension.equals(FileUtil.getExtension(file.getFileName().toString().toLowerCase())))
                .forEach(file -> {
                    // Get the longest common prefix length
                    int commonPrefixLength = commonPrefixLength(specLibFileBaseName, FileUtil.getBaseName(file.getFileName().toString()));

                    if (commonPrefixLength > 0)
                    {
                        prefixLengthMap.put(file, commonPrefixLength);
                    }
                });
        return prefixLengthMap;
    }

    private static Map<Path, Integer> getCommonPrefixLengthsForTsvFiles(List<Path> files, String specLibFileName)
    {
        return getCommonPrefixLengths(files, specLibFileName, TSV);
    }

    private static Map<Path, Integer> getCommonPrefixLengthsForParquetFiles(List<Path> files, String specLibFileName)
    {
        return getCommonPrefixLengths(files, specLibFileName, PARQUET);
    }

    private static int commonPrefixLength(String s1, String s2)
    {
        int maxLength = Math.min(s1.length(), s2.length());
        int index = 0;
        while (index < maxLength && s1.charAt(index) == s2.charAt(index))
        {
            index++;
        }
        return index;
    }

    private static boolean hasRequiredHeaders(Path diannReportTsv)
    {
        try
        {
            // Read the first line of the file
            String firstLine = Files.lines(diannReportTsv).findFirst().orElse("");
            // Check if the first line has the expected header columns names
            return List.of(firstLine.trim().split("\t")).containsAll(LibSourceFile.DIANN_REPORT_EXPECTED_HEADERS);
        }
        catch (IOException e)
        {
            throw UnexpectedException.wrap(e, "Error reading the first line of TSV file " + diannReportTsv);
        }
    }

    private Path findInDirectoryTree(java.nio.file.Path rawFilesDirPath, String fileName, boolean allowBaseName)
    {
        try
        {
            Path path = getPath(rawFilesDirPath, fileName, allowBaseName);
            if (path != null)
            {
                return path;
            }
        }
        catch (IOException e)
        {
            throw UnexpectedException.wrap(e, "Error looking for files in " + rawFilesDirPath);
        }

        // Look in subdirectories
        try (Stream<Path> list = Files.walk(rawFilesDirPath).filter(Files::isDirectory))
        {
            for (Path subDir : list.collect(Collectors.toList()))
            {
                Path path = getPath(subDir, fileName, allowBaseName);
                if (path != null)
                {
                    return path;
                }
            }
        }
        catch (IOException e)
        {
            throw UnexpectedException.wrap(e, "Error looking for files in sub-directories of" + rawFilesDirPath);
        }
        return null;
    }

    private @Nullable Path getPath(Path rawFilesDirPath, String fileName, boolean allowBaseName) throws IOException
    {
        Path filePath = rawFilesDirPath.resolve(fileName);
        if(Files.exists(filePath))
        {
            return filePath;
        }

        // Look for zip files, of raw files with matching base names if we are allowing basename matching.
        try (Stream<Path> list = Files.list(rawFilesDirPath).filter(p -> FileUtil.getFileName(p).startsWith(fileName)))
        {
            for (Path path : list.collect(Collectors.toList()))
            {
                String name = FileUtil.getFileName(path);
                if(accept(fileName, name, allowBaseName))
                {
                    return rawFilesDirPath.resolve(name);
                }
            }
        }
        return null;
    }

    private static boolean accept(String fileName, String uploadedFileName)
    {
        return accept(fileName, uploadedFileName, false);
    }

    private static boolean accept(String fileName, String uploadedFileName, boolean allowBaseName)
    {
        // Accept QC_10.9.17.raw OR for QC_10.9.17.raw.zip
        // 170428_DBS_cal_7a.d OR 170428_DBS_cal_7a.d.zip
        // If allowBaseName is set to true, accept
        // B_240207_IO5x75_HeLa_400ng.raw (or another valid raw file extension) for B_240207_IO5x75_HeLa_400ng
        String ext = FileUtil.getExtension(uploadedFileName);
        ext = ext != null ? ext.toLowerCase() : "";
        return fileName.equals(uploadedFileName)
                || ext.equals("zip") && fileName.equals(FileUtil.getBaseName(uploadedFileName))
                || (allowBaseName && fileName.equals(getUploadedRawFileBaseName(uploadedFileName)));
    }

    private static String getUploadedRawFileBaseName(String uploadedFileName)
    {
        String ext = FileUtil.getExtension(uploadedFileName.toLowerCase());
        if (!RAW_FILE_TYPES.stream().anyMatch(type -> type.equals(ext)))
        {
            return null;
        }
        return FileUtil.getBaseName(uploadedFileName);
    }

    public static class SpecLibKeyWithSize
    {
        private final SpecLibKey _key;
        private final Long _size;

        public SpecLibKeyWithSize(ISpectrumLibrary library, @Nullable Long size)
        {
            _size = size;
            _key = SpecLibKey.fromLibrary(library);
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SpecLibKeyWithSize that = (SpecLibKeyWithSize) o;
            return _key.equals(that._key) && Objects.equals(_size, that._size);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(_key, _size);
        }

        public String toString()
        {
            return _key.toString() + (_size == null ? ", NOT_FOUND" : ", size: " + _size);
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testGetLibrarySources() throws IOException
        {
            Path testDataDir = JunitUtil.getSampleData(ModuleLoader.getInstance().getModule(PanoramaPublicModule.class), "TargetedMS/panoramapublic").toPath();
            Path libPath = testDataDir.resolve("maxquant.blib");
            ISpectrumLibrary isl = createLibrary(libPath);
            SpecLibReader libReader = SpecLibReader.getReader(isl);
            assertNotNull(libReader);
            List<LibSourceFile> libSources = getLibSources(libReader, isl, libPath, "no_name_document");
            assertNotNull(libSources);
            assertEquals(7, libSources.size());

            // Files read from the .blib's SpectrumSourceFiles table
            List<String> spectrumSources = List.of("BBM_332_P110_C04_PRM_003.raw", "BBM_332_P110_C04_PRM_004.raw",
                    "BBM_332_P110_C04_PRM_005.raw", "BBM_332_P110_C04_PRM_006.raw", "BBM_332_P110_C04_PRM_007.raw");
            String idFile = "msms.txt";
            for (int i = 0; i < spectrumSources.size(); i++)
            {
                LibSourceFile libSource = libSources.get(i);
                assertTrue(libSource.hasSpectrumSourceFile());
                assertEquals(spectrumSources.get(i), libSource.getSpectrumSourceFile());
                assertTrue(libSource.hasIdFile());
                assertEquals(idFile, libSource.getIdFile());
            }

            // Additional files added for MaxQuant
            Set<String> expectedIdFiles = new HashSet<>(LibSourceFile.MAX_QUANT_ID_FILES);
            for (int i = spectrumSources.size(); i < libSources.size(); i++)
            {
                LibSourceFile libSource = libSources.get(i);
                assertFalse(libSource.hasSpectrumSourceFile());
                assertTrue(libSource.hasIdFile());
                assertTrue(expectedIdFiles.contains(libSource.getIdFile()));
            }
            Set<String> idFilesInSources = libSources.stream().map(LibSourceFile::getIdFile).collect(Collectors.toSet());
            assertEquals(expectedIdFiles, idFilesInSources);
        }

        @Test
        public void testCompareLibSources()
        {
            List<LibSourceFile> source1 = new ArrayList<>();
            List<LibSourceFile> source2 = new ArrayList<>();
            assertTrue(areSameSources(source1, source2));

            LibSourceFile f1 = new LibSourceFile("C:\\LibrarySource\\file1.raw", null, null);
            LibSourceFile f2 = new LibSourceFile("C:\\LibrarySource\\file2.raw", null, null);
            LibSourceFile f3 = new LibSourceFile("C:\\LibrarySource\\file3.raw", "peptides1.pep.xml", null);
            LibSourceFile f4 = new LibSourceFile("C:\\LibrarySource\\file4.raw", "peptides2.pep.xml", null);
            LibSourceFile f5 = new LibSourceFile("C:\\LibrarySource\\file5.raw", "peptides3.pep.xml", null);
            LibSourceFile f6_same_as_f4 = new LibSourceFile("C:\\LibrarySource\\file4.raw", "peptides2.pep.xml", null);

            assertTrue(areSameSources(source1, source2));
            source1.addAll(List.of(f1, f2, f3, f4));
            source2.addAll(List.of(f3, f1, f4, f2));
            assertTrue(areSameSources(source1, source2));
            source1.clear();
            source1.addAll(List.of(f1, f2, f3, f5));
            assertFalse(areSameSources(source1, source2));
            source1.clear();
            source1.addAll(List.of(f1, f2, f3, f6_same_as_f4));
            assertTrue(areSameSources(source1, source2));

            // Test null values for spectrumSourceFile, e.g. when we add a LibSourcefile for MaxQuant files. In this case the idFile variable
            // is set to mqpar.xml or evidence.txt.  The spectrumSourceFile variable remains null.
            LibSourceFile f7_spec_src_null = new LibSourceFile(null, "evidence.txt", null);
            LibSourceFile f8_spec_src_null = new LibSourceFile(null, "msms.txt", null);
            source1.clear();
            source2.clear();
            source1.addAll(List.of(f7_spec_src_null, f8_spec_src_null));
            source2.addAll(List.of(f7_spec_src_null, f8_spec_src_null));
            assertTrue(areSameSources(source1, source2));
        }

        @Test
        public void testAccept()
        {
            // Accept QC_10.9.17.raw OR for QC_10.9.17.raw.zip
            assertFalse(accept("QC_10.9.17.raw", "QC_10.9.17.RAW"));
            assertTrue(accept("QC_10.9.17.raw", "QC_10.9.17.raw"));
            assertTrue(accept("QC_10.9.17.raw", "QC_10.9.17.raw.ZIP"));

            // Accept 170428_DBS_cal_7a.d OR 170428_DBS_cal_7a.d.zip
            assertTrue(accept("170428_DBS_cal_7a.d", "170428_DBS_cal_7a.d"));
            assertTrue(accept("170428_DBS_cal_7a.d", "170428_DBS_cal_7a.d.zip"));

            assertFalse(accept("B_240207_IO5x75_HeLa_400ng", "B_240207_IO5x75_HeLa_400ng.raw"));
            assertTrue(accept("B_240207_IO5x75_HeLa_400ng", "B_240207_IO5x75_HeLa_400ng.raw", true));
            assertFalse(accept("B_240207_IO5x75_HeLa_400ng", "B_240207_IO5x75_HeLa_400ng.txt", true));
        }

        @Test
        public void testCommonPrefixLength() throws IOException
        {
            Path testDataDir = getDiannTestFilesPath();

            // The spec lib file name to compare against
            String specLibFileName = "report-lib.parquet.skyline-for-test.speclib";

            Path tsvFile1 = testDataDir.resolve("report-lib.tsv");
            Path tsvFile2 = testDataDir.resolve("report-lib-for-test.tsv");
            Path tsvFile3 = testDataDir.resolve("report-lib.parquet.tsv");
            Path tsvFile4 = testDataDir.resolve("report-lib.parquet-test.tsv");
            Path tsvFile5 = testDataDir.resolve("no-prefix-match-report.tsv");
            Path nonTsvFile1 = testDataDir.resolve("report-lib.parquet.skyline-for-test.txt");
            Path nonTsvFile2 = testDataDir.resolve("report.txt");
            Path nonTsvFile3 = testDataDir.resolve(specLibFileName);

            List<Path> files = List.of(tsvFile1, tsvFile2, tsvFile3, tsvFile4, tsvFile5, nonTsvFile1, nonTsvFile2, nonTsvFile3);

            Map<Path, Integer> prefixLengthMap = SpecLibValidator.getCommonPrefixLengthsForTsvFiles(files, specLibFileName);
            // Expect 4 TSV files in the list; files without a prefix match, and non-TSV files should be ignored.
            assertEquals("Unexpected size of prefixLengthMap", 4, prefixLengthMap.size());

            // File report-lib.tsv should have a common prefix "report-lib"
            assertTrue(prefixLengthMap.containsKey(tsvFile1));
            assertEquals("report-lib".length(), prefixLengthMap.get(tsvFile1).intValue());

            // File report-lib-test.tsv should have a common prefix "report-lib"
            assertTrue(prefixLengthMap.containsKey(tsvFile2));
            assertEquals("report-lib".length(), prefixLengthMap.get(tsvFile2).intValue());

            // File report-lib.parquet.tsv should have a common prefix "report-lib.parquet"
            assertTrue(prefixLengthMap.containsKey(tsvFile3));
            assertEquals("report-lib.parquet".length(), prefixLengthMap.get(tsvFile3).intValue());

            // File report-lib.parquet-test.tsv should have a common prefix "report-lib.parquet"
            assertTrue(prefixLengthMap.containsKey(tsvFile4));
            assertEquals("report-lib.parquet".length(), prefixLengthMap.get(tsvFile4).intValue());

            // File no-prefix-match-report.tsv should not have a common prefix
            assertFalse(tsvFile5 + " does not share a prefix with " + specLibFileName, prefixLengthMap.containsKey(tsvFile5));

            assertFalse(prefixLengthMap.containsKey(nonTsvFile1));
            assertFalse(prefixLengthMap.containsKey(nonTsvFile2));
            assertFalse(prefixLengthMap.containsKey(nonTsvFile3));

            // List of files that do not share a common prefix with the speclib file
            files = List.of(testDataDir.resolve("abcd.tsv"), testDataDir.resolve("1234.tsv"), testDataDir.resolve("lib.parquet.skyline.tsv"));
            prefixLengthMap = SpecLibValidator.getCommonPrefixLengthsForTsvFiles(files, specLibFileName);
            assertEquals(0, prefixLengthMap.size());

            prefixLengthMap = SpecLibValidator.getCommonPrefixLengthsForTsvFiles(files, specLibFileName);
            assertEquals(0, prefixLengthMap.size());
        }

        @Test
        public void testGetDiannReportFilePath() throws IOException
        {
            Path testDataDir = getDiannTestFilesPath();
            String specLibFileName = "report-lib.parquet.skyline-for-test.speclib";

            Path reportTsvFile = SpecLibValidator.getDiannReportFilePath(specLibFileName, Collections.emptyList());
            assertNull("Unexpected report TSV file path returned. Input file list is empty.", reportTsvFile);

            // TSV Files in the test directory
            Path tsvFile1 = testDataDir.resolve("report.tsv");
            Path tsvFile2 = testDataDir.resolve("report-lib-for-test.tsv");
            Path tsvFile3 = testDataDir.resolve("no-prefix-match-report-for-test.tsv");
            Path tsvFile4 = testDataDir.resolve("report-lib.parquet-missing-headers.txt");
            // Non-TSV files in the test directory
            Path nonTsvFile1 = testDataDir.resolve("report.txt");
            Path nonTsvFile2 = testDataDir.resolve("report-lib.parquet.skyline-for-test.txt");
            Path nonTsvFile3 = testDataDir.resolve(specLibFileName);
            Path nonTsvFile4 = testDataDir.resolve("test_diann_library.blib");

            List<Path> candidateFiles = new ArrayList<>();
            candidateFiles.add(nonTsvFile1);
            candidateFiles.add(nonTsvFile2);
            candidateFiles.add(nonTsvFile3);
            candidateFiles.add(nonTsvFile4);

            assertNull("Unexpected report TSV file path returned. Input list does not have any TSV files",
                    SpecLibValidator.getDiannReportFilePath(specLibFileName, candidateFiles));

            candidateFiles.add(tsvFile3); // TSV file does not share a prefix with the speclib file
            assertNull("Unexpected report TSV file path returned. Input list does not have any TSV files that share a prefix with the speclib file",
                    SpecLibValidator.getDiannReportFilePath(specLibFileName, candidateFiles));

            candidateFiles.add(tsvFile4); // TSV file does not have the required column headers
            assertNull("Unexpected report TSV file path returned. Input list does not have any TSV files that share a prefix with the speclib file" +
                            " and have the required column headers",
                    SpecLibValidator.getDiannReportFilePath(specLibFileName, candidateFiles));

            candidateFiles.add(tsvFile1); // Shares a prefix and has the required column headers
            reportTsvFile = SpecLibValidator.getDiannReportFilePath(specLibFileName, candidateFiles);
            assertNotNull(reportTsvFile);
            assertEquals(tsvFile1, reportTsvFile);

            candidateFiles.add(tsvFile2); // Shares a longer prefix with the speclib file
            reportTsvFile = SpecLibValidator.getDiannReportFilePath(specLibFileName, candidateFiles);
            assertNotNull(reportTsvFile);
            assertEquals(tsvFile2, reportTsvFile);
        }

        private static Path getDiannTestFilesPath() throws IOException
        {
            return JunitUtil.getSampleData(ModuleLoader.getInstance().getModule(PanoramaPublicModule.class),
                    "TargetedMS/panoramapublic/LibraryTest-DiaNN").toPath();
        }

        private ISpectrumLibrary createLibrary(Path path)
        {
            return new ISpectrumLibrary()
            {
                @Override
                public long getId()
                {
                    return 0;
                }

                @Override
                public long getRunId()
                {
                    return 0;
                }

                @Override
                public String getName()
                {
                    return FileUtil.getFileName(path);
                }

                @Override
                public String getFileNameHint()
                {
                    return getName();
                }

                @Override
                public String getSkylineLibraryId()
                {
                    return null;
                }

                @Override
                public String getRevision()
                {
                    return null;
                }

                @Override
                public String getLibraryType()
                {
                    return LibraryType.bibliospec.name();
                }
            };
        }
    }
}

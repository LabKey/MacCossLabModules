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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.labkey.panoramapublic.model.validation.SpecLibSourceFile.LibrarySourceFileType.PEPTIDE_ID;
import static org.labkey.panoramapublic.model.validation.SpecLibSourceFile.LibrarySourceFileType.SPECTRUM;

public class SpecLibValidator extends SpecLibValidation<ValidatorSkylineDocSpecLib>
{
    private List<ValidatorSkylineDocSpecLib> _docsWithLibrary;
    private SpecLibKeyWithSize _key;

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
        if (sourceFiles != null && sourceFiles.stream().anyMatch(LibSourceFile::isMaxQuantSearch))
        {
            // For libraries built with MaxQuant search results we need to add additional files that are required for library building
            Set<String> idFileNames = sourceFiles.stream().filter(LibSourceFile::hasIdFile).map(LibSourceFile::getIdFile).collect(Collectors.toSet());
            for (String file: LibSourceFile.MAX_QUANT_ID_FILES)
            {
                if (!idFileNames.contains(file))
                {
                    sourceFiles.add(new LibSourceFile(null, file, null));
                }
            }
        }
        return sourceFiles;
    }

    private static boolean areSameSources(List<LibSourceFile> sources, List<LibSourceFile> docLibSources)
    {
        if (sources != null)
        {
            sources.sort(Comparator.comparing(LibSourceFile::getSpectrumSourceFile)
                    .thenComparing(LibSourceFile::getIdFile));
        }
        if (docLibSources != null)
        {
            docLibSources.sort(Comparator.comparing(LibSourceFile::getSpectrumSourceFile)
                    .thenComparing(LibSourceFile::getIdFile));
        }
        return Objects.equals(sources, docLibSources);
    }

    private void validateLibrarySources(List<LibSourceFile> sources, FileContentService fcs, ExperimentAnnotations expAnnotations)
    {
        Set<String> checkedFiles = new HashSet<>();
        // Since a library can be used with multiple Skyline documents which could be in subfolders we will look for the source files in the main experiment
        // folder as well as any subfolders containing documents that have the library
        Set<Container> containers = getDocsWithLibrary().stream().map(dl -> dl.getRun().getContainer()).collect(Collectors.toSet());
        containers.add(expAnnotations.getContainer());

        List<SpecLibSourceFile> spectrumFiles = new ArrayList<>();
        List<SpecLibSourceFile> idFiles = new ArrayList<>();

        for (LibSourceFile source: sources)
        {
            String ssf = source.getSpectrumSourceFile();
            if (source.hasSpectrumSourceFile() && !checkedFiles.contains(ssf))
            {
                checkedFiles.add(ssf);
                Path path = getPath(ssf, containers, source.isMaxQuantSearch(), fcs);
                SpecLibSourceFile sourceFile = new SpecLibSourceFile(ssf, SPECTRUM);
                sourceFile.setSpecLibValidationId(getId());
                sourceFile.setPath(path != null ? path.toString() : DataFile.NOT_FOUND);
                spectrumFiles.add(sourceFile);
            }
            String idFile = source.getIdFile();
            if (source.hasIdFile() && !checkedFiles.contains(idFile))
            {
                checkedFiles.add(idFile);
                Path path = getPath(idFile, containers, false, fcs);
                SpecLibSourceFile sourceFile = new SpecLibSourceFile(idFile, PEPTIDE_ID);
                sourceFile.setSpecLibValidationId(getId());
                sourceFile.setPath(path != null ? path.toString() : DataFile.NOT_FOUND);
                idFiles.add(sourceFile);
            }
        }
        setSpectrumFiles(spectrumFiles);
        setIdFiles(idFiles);
    }

    private Path getPath(String name, Set<Container> containers, boolean isMaxquant, FileContentService fcs)
    {
        for (Container container: containers)
        {
            java.nio.file.Path rawFilesDir = getRawFilesDirPath(container, fcs);
            Path path = findInDirectoryTree(rawFilesDir, name, isMaxquant);
            if (path != null)
            {
                return path;
            }
        }
        return null;
    }

    private static java.nio.file.Path getRawFilesDirPath(Container c, FileContentService fcs)
    {
        if(fcs != null)
        {
            java.nio.file.Path fileRoot = fcs.getFileRootPath(c, FileContentService.ContentType.files);
            if (fileRoot != null)
            {
                return fileRoot.resolve(TargetedMSService.RAW_FILES_DIR);
            }
        }
        return null;
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

        // Look for zip files
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

    private static boolean accept(String fileName, String uploadedFileName, boolean allowBasenameOnly)
    {
        // Accept QC_10.9.17.raw OR for QC_10.9.17.raw.zip
        // 170428_DBS_cal_7a.d OR 170428_DBS_cal_7a.d.zip
        String ext = FileUtil.getExtension(uploadedFileName).toLowerCase();
        return fileName.equals(uploadedFileName)
                || ext.equals("zip") && fileName.equals(FileUtil.getBaseName(uploadedFileName))
                || (allowBasenameOnly && fileName.equals(FileUtil.getBaseName(uploadedFileName)));
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
            return _key.toString() + _size == null ? ", NOT_FOUND" : ", size: " + _size;
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
            assertEquals(8, libSources.size());

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

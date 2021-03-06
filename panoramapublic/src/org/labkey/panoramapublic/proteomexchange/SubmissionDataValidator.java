/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.panoramapublic.proteomexchange;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.targetedms.BlibSourceFile;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.FileUtil;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.query.ExperimentAnnotationsManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SubmissionDataValidator
{
    public static final int MIN_ABSTRACT_LENGTH = 50;
    public static final int MIN_TITLE_LENGTH = 30;

    private static final Logger LOG = LogManager.getLogger(SubmissionDataValidator.class);

    public static boolean isValid(ExperimentAnnotations expAnnot)
    {
        boolean metadataValid = metadataComplete(expAnnot);
        boolean hasRawFiles = rawDataUploaded(expAnnot);
        boolean hasValidMods = hasUnimodModifications(expAnnot);
        return metadataValid && hasRawFiles && hasValidMods;
    }

    private static boolean metadataComplete(ExperimentAnnotations expAnnot)
    {
        return getMissingExperimentMetadataFields(expAnnot).size() == 0;
    }

    private static boolean hasUnimodModifications(ExperimentAnnotations expAnnot)
    {
       return getInvalidModifications(expAnnot).size() == 0;
    }

    private static boolean rawDataUploaded(ExperimentAnnotations expAnnotations)
    {
        // Get a list of Skyline documents associated with this experiment
        List<ITargetedMSRun> runs = ExperimentAnnotationsManager.getTargetedMSRuns(expAnnotations);

        Set<String> existingRawFiles = new HashSet<>();
        for(ITargetedMSRun run: runs)
        {
            List<String> missingFiles = getMissingFilesForRun(run, expAnnotations.getContainer(), existingRawFiles);
            if(missingFiles.size() > 0)
            {
                return false;
            }
        }
        return true;
    }

    public static SubmissionDataStatus validateExperiment(ExperimentAnnotations expAnnot)
    {
        SubmissionDataStatus status = new SubmissionDataStatus(expAnnot);
        status.setMissingMetadata(getMissingExperimentMetadataFields(expAnnot));
        getMissingRawFiles(expAnnot, status);

        List<ExperimentModificationGetter.PxModification> invalidMods = getInvalidModifications(expAnnot);
        for (ExperimentModificationGetter.PxModification invalidMod : invalidMods)
        {
            status.addInvalidMod(invalidMod);
        }
        return status;
    }

    public static List<String> getMissingExperimentMetadataFields(ExperimentAnnotations expAnnot)
    {
        List<String> errors = new ArrayList<>();
        if(StringUtils.isBlank(expAnnot.getTitle()))
        {
            errors.add("Title is required.");
        }
        else if(StringUtils.trim(expAnnot.getTitle()).length() < MIN_TITLE_LENGTH)
        {
            errors.add("Title should be at least " + MIN_TITLE_LENGTH + " characters.");
        }

        if (StringUtils.isBlank(expAnnot.getOrganism()))
        {
            errors.add("Organism is required.");
        }
        else
        {
            validateOrganisms(expAnnot, errors);
        }

        if(StringUtils.isBlank(expAnnot.getInstrument()))
        {
            errors.add("Instrument is required.");
        }
        else
        {
            validateInstruments(expAnnot, errors);
        }

        if(StringUtils.isBlank(expAnnot.getKeywords()))
        {
            errors.add("Keywords are required.");
        }
        if(expAnnot.getSubmitter() == null)
        {
            errors.add("Submitter is required.");
        }
        if(expAnnot.getSubmitterAffiliation() == null)
        {
            errors.add("Submitter affiliation is required.");
        }
        if(expAnnot.getLabHead() != null && StringUtils.isBlank(expAnnot.getLabHeadAffiliation()))
        {
            errors.add("Lab Head affiliation is required.");
        }
        if(StringUtils.isBlank(expAnnot.getAbstract()))
        {
            errors.add("Abstract is required.");
        }
        else if(expAnnot.getAbstract().length() < MIN_ABSTRACT_LENGTH)
        {
            errors.add("Abstract should be at least " + MIN_ABSTRACT_LENGTH + " characters.");
        }

        return errors;
    }

    private static List<ExperimentModificationGetter.PxModification> getInvalidModifications(ExperimentAnnotations expAnnot)
    {
        List<ExperimentModificationGetter.PxModification> mods = ExperimentModificationGetter.getModifications(expAnnot);
        List<ExperimentModificationGetter.PxModification> invalidMods = new ArrayList<>();
        for(ExperimentModificationGetter.PxModification mod: mods)
        {
            if(!mod.hasUnimodId())
            {
                invalidMods.add(mod);
            }
        }
        return invalidMods;
    }

    private static void validateInstruments(ExperimentAnnotations expAnnot, List<String> errors)
    {
        List<String> instruments = expAnnot.getInstruments();
        PsiInstrumentParser parser = new PsiInstrumentParser();
        Set<String> notFound = new HashSet<>();
        for(String instrumentName: instruments)
        {
            PsiInstrumentParser.PsiInstrument instrument = null;
            try
            {
                instrument = parser.getInstrument(instrumentName);
            }
            catch (PxException e)
            {
                errors.add("Error reading psi-ms file for validating instruments. " + e.getMessage());
                LOG.error("Error reading psi-ms file for validating instruments in container " + expAnnot.getContainer(), e);
            }

            if(instrument == null)
            {
                notFound.add(instrumentName);
            }
        }
        if(notFound.size() > 0)
        {
            StringBuilder err = new StringBuilder("Unrecognized instrument");
            err.append(notFound.size() > 1 ? "s: " : ": ");
            err.append(StringUtils.join(notFound, ','));
            errors.add(err.toString());
        }
    }

    private static void validateOrganisms(ExperimentAnnotations expAnnot, List<String> errors)
    {
        Map<String, Integer> organisms = expAnnot.getOrganismAndTaxId();

        Set<String> notFound = new HashSet<>();
        for(String orgName: organisms.keySet())
        {
            if(organisms.get(orgName) == null)
            {
                notFound.add(orgName);
            }
        }
        if(notFound.size() > 0)
        {
            StringBuilder err = new StringBuilder("No taxonomy ID found for organism");
            err.append(notFound.size() > 1 ? "s: " : ": ");
            err.append(StringUtils.join(notFound, ','));
            errors.add(err.toString());
        }
    }

    private static void getMissingRawFiles(ExperimentAnnotations expAnnotations, SubmissionDataStatus submissionStatus)
    {
        TargetedMSService targetedMsSvc = TargetedMSService.get();
        ExperimentService expSvc = ExperimentService.get();

        // Get a list of Skyline documents associated with this experiment
        List<ITargetedMSRun> runs = ExperimentAnnotationsManager.getTargetedMSRuns(expAnnotations);

        Set<String> existingRawFiles = new HashSet<>();
        for(ITargetedMSRun run: runs)
        {
            List<String> missingFiles = getMissingFilesForRun(run, expAnnotations.getContainer(), existingRawFiles);
            for(String missingFile: missingFiles)
            {
                submissionStatus.addMissingRawPath(missingFile, run.getFileName());
            }

            // Get missing blib source files
            java.nio.file.Path rawFilesDir = getRawFilesDirPath(run.getContainer());
            for(Map.Entry<String, List<BlibSourceFile>> entry : targetedMsSvc.getBlibSourceFiles(run).entrySet())
            {
                if(isPrositLibrary(entry, run))
                {
                    // Prosit libraries are built from predictions, so no raw files or search results need to be uploaded.
                    continue;
                }
                Set<String> checkedFiles = new HashSet<>();
                Set<String> ssfMissing = new HashSet<>();
                Set<String> idFilesMissing = new HashSet<>();
                for(BlibSourceFile file: entry.getValue())
                {
                    String ssf = file.getSpectrumSourceFile();
                    if (file.hasSpectrumSourceFile() && !checkedFiles.contains(ssf))
                    {
                        boolean isMaxquant = (file.hasIdFile() && file.getIdFile().endsWith("msms.txt")) || file.containsScoreType("MAXQUANT SCORE");
                        if (!hasExpData(FilenameUtils.getName(getFilePath(ssf)), run.getContainer(), rawFilesDir, expSvc, isMaxquant))
                            ssfMissing.add(ssf);
                        checkedFiles.add(ssf);
                    }
                    String idFile = file.getIdFile();
                    if (file.hasIdFile() && !checkedFiles.contains(idFile))
                    {
                        if (!hasExpData(FilenameUtils.getName(getFilePath(idFile)), run.getContainer(), rawFilesDir, expSvc, false))
                            idFilesMissing.add(idFile);
                        checkedFiles.add(idFile);
                    }
                }

                // Source spectrum file can be the same as the ID file if embedded spectra are used.
                // In this case, we only want it added once (as an ID file).
                for(String file: idFilesMissing)
                    ssfMissing.remove(file);

                submissionStatus.addMissingLibFile(entry.getKey(), run.getFileName(), ssfMissing, idFilesMissing);
            }
        }
    }

    private static boolean isPrositLibrary(Map.Entry<String, List<BlibSourceFile>> entry, ITargetedMSRun run)
    {
        List<BlibSourceFile> sourceFiles = entry.getValue();
        // For a library based on Prosit we only expect one row in the SpectrumSourceFiles table,
        // We expect idFileName to be blank amd the value in the fileName column to be "Prositintensity_prosit_publication_v1".
        // The value in the fileName column may be different in Skyline 21.1. This code will be have to be updated then.
        if(sourceFiles.size() == 1 && !sourceFiles.get(0).hasIdFile())
        {
            String fileName = sourceFiles.get(0).getSpectrumSourceFile();
            return "Prositintensity_prosit_publication_v1".equals(fileName) && preSkyline21(run);
        }
        return false;
    }

    private static boolean preSkyline21(ITargetedMSRun run)
    {
        SkylineVersion version = SkylineVersion.parse(run.getSoftwareVersion());
        return version != null && version.getMajorVersion() < 21;
    }

    private static List<String> getMissingFilesForRun(ITargetedMSRun run, Container rootExpContainer, Set<String> existingRawFiles)
    {
        List<String> missingFiles = new ArrayList<>();
        List<String> sampleFiles = TargetedMSService.get().getSampleFilePaths(run.getId());

        java.nio.file.Path rawFilesDir = getRawFilesDirPath(run.getContainer());

        ExperimentService expSvc = ExperimentService.get();

        for(String sampleFilePath: sampleFiles)
        {
            String filePath = getFilePath(sampleFilePath);
            if(existingRawFiles.contains(filePath))
            {
                continue;
            }

            checkExists(run, rootExpContainer, rawFilesDir, filePath, existingRawFiles, missingFiles, expSvc);

            // If this is a SCIEX .wiff file check for the presence of the corresponding .wiff.scan file
            if(isSciexWiff(filePath))
            {
                checkExists(run, rootExpContainer, rawFilesDir, filePath + ".scan", existingRawFiles, missingFiles, expSvc);
            }
        }

        return missingFiles;
    }

    private static boolean isSciexWiff(String fileName)
    {
        return fileName.toLowerCase().endsWith(".wiff");
    }

    private static void checkExists(ITargetedMSRun run, Container rootExpContainer, Path rawFilesDir, String filePath, Set<String> existingRawFiles, List<String> missingFiles, ExperimentService expSvc)
    {
        String fileName = FilenameUtils.getName(filePath);
        if (!hasExpData(fileName, run.getContainer(), rawFilesDir, expSvc, false))
        {
            // If no matching row was found in exp.data and this is NOT a cloud container check for the file on the file system.
            if(!FileContentService.get().isCloudRoot(rootExpContainer))
            {
                if (Files.exists(rawFilesDir) && findInDirectoryTree(rawFilesDir, fileName, rootExpContainer))
                {
                    existingRawFiles.add(filePath);
                    return;
                }
            }
            missingFiles.add(filePath);
        }
        else
        {
            existingRawFiles.add(filePath);
        }
    }

    private static String getFilePath(String filePath)
    {
        // If the file path has a '?' part remove it
        // Example: 2017_July_10_bivalves_292.raw?centroid_ms2=true.
        int idx = filePath.indexOf('?');
        filePath = (idx == -1) ? filePath : filePath.substring(0, idx);

        // If the file path has a '|' part for sample name from multi-injection wiff files remove it.
        // Example: D:\Data\CPTAC_Study9s\Site52_041009_Study9S_Phase-I.wiff|Site52_STUDY9S_PHASEI_6ProtMix_QC_07|6
        idx = filePath.indexOf('|');
        return (idx == -1) ? filePath : filePath.substring(0, idx);
    }

    private static boolean findInDirectoryTree(java.nio.file.Path rawFilesDirPath, String fileName, Container experimentContainer)
    {
        try
        {
            if (rawDataExists(rawFilesDirPath, fileName))
            {
                return true;
            }
        }
        catch (IOException e)
        {
            LOG.error(experimentContainer.getPath() + ": Error looking for raw data associated with Skyline documents in " + rawFilesDirPath.toString(), e);
            return false;
        }

        // Look in subdirectories
        try (Stream<Path> list = Files.walk(rawFilesDirPath).filter(p -> Files.isDirectory(p)))
        {
            for (Path subDir : list.collect(Collectors.toList()))
            {
                if (rawDataExists(subDir, fileName)) return true;
            }
        }
        catch (IOException e)
        {
            LOG.error(experimentContainer + ": Error looking for raw data associated with Skyline documents in sub-directories of" + rawFilesDirPath.toString(), e);
            return false;
        }
        return false;
    }

    private static boolean rawDataExists(Path rawFilesDirPath, String fileName) throws IOException
    {
        Path rawFilePath = rawFilesDirPath.resolve(fileName);
        if(Files.exists(rawFilePath) || Files.isDirectory(rawFilePath))
        {
            return true;
        }

        // Look for zip files
        String nameNoExt = FileUtil.getBaseName(fileName);
        try (Stream<Path> list = Files.list(rawFilesDirPath).filter(p -> FileUtil.getFileName(p).startsWith(nameNoExt)))
        {
            for (Path path : list.collect(Collectors.toList()))
            {
                String name = FileUtil.getFileName(path);
                if(accept(fileName, name))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static java.nio.file.Path getRawFilesDirPath(Container c)
    {
        FileContentService service = FileContentService.get();
        if(service != null)
        {
            java.nio.file.Path fileRoot = service.getFileRootPath(c, FileContentService.ContentType.files);
            if (fileRoot != null)
            {
                return fileRoot.resolve(TargetedMSService.RAW_FILES_DIR);
            }
        }
        return null;
    }

    private static boolean hasExpData(String sampleFileName, Container container, Path rawFilesDir, ExperimentService svc, boolean allowBasenameOnly)
    {
        if(svc == null)
        {
            return false;
        }

        String nameNoExt = FileUtil.getBaseName(sampleFileName);

        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        // Look for files that start with the base filename of the sample file.
        filter.addCondition(FieldKey.fromParts("Name"), nameNoExt, CompareType.STARTS_WITH);
        // Look for data under @files/RawFiles.
        // Use FileUtil.pathToString(); this will remove the access ID is this is a S3 path
        String prefix = FileUtil.pathToString(rawFilesDir);
        if (!prefix.endsWith("/"))
        {
            prefix = prefix + "/";
        }
        filter.addCondition(FieldKey.fromParts("datafileurl"), prefix, CompareType.STARTS_WITH);

        List<String> files = new TableSelector(svc.getTinfoData(), Collections.singleton("Name"), filter, null).getArrayList(String.class);

        for (String expDataFile: files)
        {
            if(accept(sampleFileName, expDataFile, allowBasenameOnly))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean accept(String sampleFileName, String uploadedFileName)
    {
        return accept(sampleFileName, uploadedFileName, false);
    }

    private static boolean accept(String sampleFileName, String uploadedFileName, boolean allowBasenameOnly)
    {
        // Accept QC_10.9.17.raw OR for QC_10.9.17.raw.zip OR QC_10.9.17.zip
        // 170428_DBS_cal_7a.d OR 170428_DBS_cal_7a.d.zip OR 170428_DBS_cal_7a.zip
        String nameNoExt = FileUtil.getBaseName(sampleFileName);
        return sampleFileName.equalsIgnoreCase(uploadedFileName)
                || (sampleFileName + ".zip").equalsIgnoreCase(uploadedFileName)
                || (nameNoExt + ".zip").equalsIgnoreCase(uploadedFileName)
                || (allowBasenameOnly && nameNoExt.equalsIgnoreCase(FileUtil.getBaseName(uploadedFileName)));
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testGetFilePath()
        {
            // Skyline tracks centroiding, lockmass settings etc. as part of the file_path attribute of the <sample_file>
            // element in .sky files. These are appended at the end of the file path as query parameters.
            // Example: C:\Users\lab\Data\2017_July_10_bivalves_140.raw?centroid_ms1=true&centroid_ms2=true.
            String fileName = "2017_July_10_bivalves_140.raw";
            String path = "C:\\Users\\lab\\Data\\2017-Geoduck-SRM-raw\\" + fileName;
            String pathWithParams = path + "?centroid_ms1=true&centroid_ms2=true";

            assertTrue(path.equals(getFilePath(path)));
            assertTrue(path.equals(getFilePath(pathWithParams)));
            assertTrue(fileName.equals(FilenameUtils.getName(getFilePath(pathWithParams))));

            // Skyline stores multi-injection wiff file paths as: <wiff_file_path>|<sample_name>|<sample_index>
            // Example: C:\Analyst Data\Projects\CPTAC\Site54_STUDY9S_PHASE1_6ProtMix_090919\Site54_190909_Study9S_PHASE-1.wiff|Site54_STUDY9S_PHASE1_6ProtMix_QC_03|2
            fileName = "Site54_190909_Study9S_PHASE-1.wiff";
            path = "C:\\Analyst Data\\Projects\\CPTAC\\Site54_STUDY9S_PHASE1_6ProtMix_090919\\" + fileName;
            String pathWithSampleInfo = path + "|Site54_STUDY9S_PHASE1_6ProtMix_QC_03|2";

            assertTrue(path.equals(getFilePath(path)));
            assertTrue(path.equals(getFilePath(pathWithSampleInfo)));
            assertTrue(fileName.equals(FilenameUtils.getName(getFilePath(pathWithSampleInfo))));

            // Add a bogus param with a '|' character
            String pathWithSampleInfoAndParams = pathWithSampleInfo + "?centroid_ms1=true&centroid_ms2=true&madeup_param=a|b";

            assertTrue(path.equals(getFilePath(path)));
            assertTrue(path.equals(getFilePath(pathWithSampleInfoAndParams)));
            assertTrue(fileName.equals(FilenameUtils.getName(getFilePath(pathWithSampleInfoAndParams))));
        }

        @Test
        public void testAccept()
        {
            // Accept QC_10.9.17.raw OR for QC_10.9.17.raw.zip OR QC_10.9.17.zip
            assertTrue(accept("QC_10.9.17.raw", "QC_10.9.17.RAW"));
            assertTrue(accept("QC_10.9.17.raw", "QC_10.9.17.raw.ZIP"));
            assertTrue(accept("QC_10.9.17.raw", "QC_10.9.17.zip"));

            // Accept 170428_DBS_cal_7a.d OR 170428_DBS_cal_7a.d.zip OR 170428_DBS_cal_7a.zip
            assertTrue(accept("170428_DBS_cal_7a.d", "170428_DBS_cal_7a.d"));
            assertTrue(accept("170428_DBS_cal_7a.d", "170428_DBS_cal_7a.d.zip"));
            assertTrue(accept("170428_DBS_cal_7a.d", "170428_DBS_cal_7a.ZIP"));
            assertFalse(accept("170428_DBS_cal_7a.d", "170428_DBS_cal_7a.d.7z"));
        }
    }
}

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
package org.labkey.targetedms.proteomexchange;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.util.FileUtil;
import org.labkey.targetedms.TargetedMSController;
import org.labkey.targetedms.TargetedMSRun;
import org.labkey.targetedms.model.ExperimentAnnotations;
import org.labkey.targetedms.parser.SampleFile;
import org.labkey.targetedms.query.ExperimentAnnotationsManager;
import org.labkey.targetedms.query.ReplicateManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SubmissionDataValidator
{
    public static final int MIN_ABSTRACT_LENGTH = 50;

    private static final Logger LOG = Logger.getLogger(SubmissionDataValidator.class);

    public static boolean isValid(ExperimentAnnotations expAnnot, boolean skipMetaDataCheck, boolean skipRawDataCheck, boolean skipModificationCheck)
    {
        boolean metadataValid = skipMetaDataCheck || metadataComplete(expAnnot);
        boolean hasRawFiles = skipRawDataCheck || rawDataUploaded(expAnnot);
        boolean hasValidMods = skipModificationCheck || hasUnimodModifications(expAnnot);
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
        List<TargetedMSRun> runs = ExperimentAnnotationsManager.getTargetedMSRuns(expAnnotations);

        Set<String> existingRawFiles = new HashSet<>();
        for(TargetedMSRun run: runs)
        {
            List<String> missingFiles = getMissingFilesForRun(run, expAnnotations.getContainer(), existingRawFiles);
            if(missingFiles.size() > 0)
            {
                return false;
            }
        }
        return true;
    }

    public static SubmissionDataStatus validateExperiment(ExperimentAnnotations expAnnot, boolean skipMetaDataCheck, boolean skipRawDataCheck, boolean skipModificationCheck) throws PxException
    {
        SubmissionDataStatus status = new SubmissionDataStatus(expAnnot);
        if(!skipMetaDataCheck)
        {
            status.setMissingMetadata(getMissingExperimentMetadataFields(expAnnot));
        }
        if(!skipRawDataCheck)
        {
            getMissingRawFiles(expAnnot, status);
        }

        if(!skipModificationCheck)
        {
            List<ExperimentModificationGetter.PxModification> invalidMods = getInvalidModifications(expAnnot);
            for (ExperimentModificationGetter.PxModification invalidMod : invalidMods)
            {
                status.addInvalidMod(invalidMod);
            }
        }

        return status;
    }

    private static List<String> getMissingExperimentMetadataFields(ExperimentAnnotations expAnnot)
    {
        List<String> errors = new ArrayList<>();
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
        // Get a list of Skyline documents associated with this experiment
        List<TargetedMSRun> runs = ExperimentAnnotationsManager.getTargetedMSRuns(expAnnotations);

        Set<String> existingRawFiles = new HashSet<>();
        for(TargetedMSRun run: runs)
        {
            List<String> missingFiles = getMissingFilesForRun(run, expAnnotations.getContainer(), existingRawFiles);
            for(String missingFile: missingFiles)
            {
                submissionStatus.addMissingRawPath(missingFile, run.getFileName());
            }
        }
    }

    private static List<String> getMissingFilesForRun(TargetedMSRun run, Container rootExpContainer, Set<String> existingRawFiles)
    {
        List<String> missingFiles = new ArrayList<>();
        List<SampleFile> sampleFiles = ReplicateManager.getSampleFilesForRun(run.getId());

        java.nio.file.Path rawFilesDir = getRawFilesDirPath(run.getContainer());

        for(SampleFile sampleFile: sampleFiles)
        {
            String filePath = getFilePath(sampleFile.getFilePath());
            if(existingRawFiles.contains(filePath))
            {
                continue;
            }

            String fileName = getSampleFileName(filePath);

            if(!Files.exists(rawFilesDir) || !findInDirectoryTree(rawFilesDir, fileName, rootExpContainer))
            {
                missingFiles.add(filePath);
            }
            else
            {
                existingRawFiles.add(filePath);
            }
        }

        // Check if the files have been uploaded to the root experiment container
        if(!rootExpContainer.equals(run.getContainer()))
        {
            rawFilesDir = getRawFilesDirPath(rootExpContainer);
            if(rawFilesDir == null)
            {
                return missingFiles;
            }
            List<String> missingInRoot = new ArrayList<>();
            for(String filePath: missingFiles)
            {
                String fileName = getSampleFileName(filePath);
                if(!findInDirectoryTree(rawFilesDir, fileName, rootExpContainer))
                {
                    missingInRoot.add(fileName);
                }
                else
                {
                    existingRawFiles.add(filePath);
                }
            }
            return missingInRoot;
        }

        return missingFiles;
    }

    static String getSampleFileName(String filePath)
    {
        String fileName = FilenameUtils.getName(filePath);
        if(fileName != null && fileName.indexOf('?') != -1)
        {
            // Example: 2017_July_10_bivalves_292.raw?centroid_ms2=true.  Return just the filename.
            fileName = fileName.substring(0, fileName.indexOf('?'));
        }
        return fileName;
    }

    private static String getFilePath(String filePath)
    {
        // Remove sample name from multi injection files.
        // Example: D:\Data\CPTAC_Study9s\Site52_041009_Study9S_Phase-I.wiff|Site52_STUDY9S_PHASEI_6ProtMix_QC_07|6
        int idx = filePath.indexOf('|');
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
        try (Stream<Path> list = Files.list(rawFilesDirPath).filter(p -> FileUtil.getFileName(p).startsWith(fileName)))
        {
            for (Path path : list.collect(Collectors.toList()))
            {
                String name = FileUtil.getFileName(path);
                if(name.equalsIgnoreCase(fileName + ".zip"))
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
                return fileRoot.resolve(TargetedMSController.FolderSetupAction.RAW_FILE_DIR);
            }
        }
        return null;
    }
}

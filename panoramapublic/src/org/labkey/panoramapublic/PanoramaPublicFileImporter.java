/*
 * Copyright (c) 2023-2026 LabKey Corporation
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
package org.labkey.panoramapublic;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.SubfolderWriter;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.writer.VirtualFile;
import org.labkey.panoramapublic.pipeline.CopyExperimentPipelineJob;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * This importer does a file move instead of copy to the temp directory and creates a symlink in place of the original
 * file.
 */
public class PanoramaPublicFileImporter implements FolderImporter
{
    @Override
    public String getDataType()
    {
        return PanoramaPublicManager.PANORAMA_PUBLIC_FILES;
    }

    @Override
    public String getDescription()
    {
        return "Panorama Public Files";
    }

    @Override
    public void process(@Nullable PipelineJob job, FolderImportContext ctx, VirtualFile root) throws Exception
    {
        Logger log = ctx.getLogger();

        FileContentService fcs = FileContentService.get();
        if (null == fcs)
            return;

        File targetRoot = fcs.getFileRoot(ctx.getContainer());

        if (null == targetRoot)
        {
            log.error("File copy target folder not found: {}", ctx.getContainer().getPath());
            return;
        }

        if (job instanceof CopyExperimentPipelineJob expJob)
        {
            File targetFiles = new File(targetRoot.getPath(), FileContentService.FILES_LINK);

            // Get source files including resolving subfolders
            String divider = FileContentService.FILES_LINK + File.separator + PipelineService.EXPORT_DIR;
            String subProject = root.getLocation().substring(root.getLocation().lastIndexOf(divider) + divider.length());
            subProject = subProject.replace(File.separator + SubfolderWriter.DIRECTORY_NAME, "");

            Path sourcePath = Paths.get(fcs.getFileRoot(expJob.getExportSourceContainer()).getPath(), subProject);
            File sourceFiles = Paths.get(sourcePath.toString(), FileContentService.FILES_LINK).toFile();

            if (!targetFiles.exists())
            {
                log.warn("Panorama public file copy target not found. Creating directory: {}", targetFiles);
                Files.createDirectories(targetFiles.toPath());
            }

            if (expJob.isMoveAndSymlink())
            {
                log.info("Moving files to folder {} and creating symlinks", ctx.getContainer().getPath());
            }
            else
            {
                log.info("Copying files to folder {}", ctx.getContainer().getPath());
            }
            PanoramaPublicSymlinkManager.get().moveAndSymLinkDirectory(expJob, ctx.getContainer(), sourceFiles, targetFiles, log);

            alignDataFileUrls(expJob.getUser(), ctx.getContainer(), log);
            updateSkydDataIds(expJob.getUser(), ctx.getContainer(), log);
        }
    }

    private void alignDataFileUrls(User user, Container targetContainer, Logger log) throws BatchValidationException, ImportException
    {
        log.info("Aligning data files urls in folder: {}", targetContainer.getPath());

        FileContentService fcs = FileContentService.get();
        if (null == fcs)
            return;

        ExperimentService expService = ExperimentService.get();
        List<? extends ExpRun> runs = expService.getExpRuns(targetContainer, null, null);
        boolean errors = false;

        Path fileRootPath = fcs.getFileRootPath(targetContainer, FileContentService.ContentType.files);
        if(fileRootPath == null || !Files.exists(fileRootPath))
        {
            throw new ImportException("File root path for container " + targetContainer.getPath() + " does not exist: " + fileRootPath);
        }

        for (ExpRun run : runs)
        {
            run.setFilePathRootPath(fileRootPath);
            run.save(user);
            log.debug("Setting filePathRoot on copied run: {} to: {}", run.getName(), fileRootPath);

            for (ExpData data : run.getAllDataUsedByRun())
            {
                if (null != data.getRun() && data.getDataFileUrl().contains(FileContentService.FILES_LINK))
                {
                    String[] parts = Objects.requireNonNull(data.getFilePath()).toString().split("Run\\d+");

                    if (parts.length > 1)
                    {
                        String fileName = parts[1];
                        Path newDataPath = Paths.get(fileRootPath.toString(), fileName);

                        if (newDataPath.toFile().exists())
                        {
                            data.setDataFileURI(newDataPath.toUri());
                            data.save(user);
                            log.debug("Setting dataFileUri on copied data: {} to: {}", data.getName(), newDataPath);
                        }
                        else
                        {
                            log.error("Data file not found: {}", newDataPath.toUri());
                            errors = true;
                        }
                    }
                    else
                    {
                        log.error("Unexpected data file path. Could not align dataFileUri. {}", data.getFilePath().toString());
                        errors = true;
                    }
                }
            }
        }
        if (errors)
        {
            throw new ImportException("Data files urls could not be aligned.");
        }
    }

    /**
     * Fixes incorrect skydDataId reference in TargetedMSRun. This happens when the relative locations of the sky.zip
     * and .skyd file are non-standard in the folder being copied.
     *
     * When a sky.zip file or its exploded folder are moved, post-import, so that the relative locations of sky.zip and
     * its corresponding .skyd file are non-standard, two ExpData rows are created for the skyd file in the Panorama Public
     * copy pipeline job.
     * The first ExpData (linked to the ExpRun) is created during XAR import.
     * The second ExpData (not linked to the ExpRun) is created in the SkylineDocumentParser.parseChromatograms() method.
     * Normally, while running the copy pipeline job,  SkylineDocumentParser.parseChromatograms() does not have to create
     * a new ExpData, since an ExpData with the expected path already exists.
     * Having 2 ExpDatas causes:
     *   1. The skydDataId in TargetedMSRun references an ExpData not linked to the ExpRun. It refers to a file in the
     *      'export' directory which gets deleted after folder import.
     *   2. FK violations during cleanup (CopyExperimentFinalTask.cleanupExportDirectory()) prevents deletion of ExpData
     *      corresponding to the skydDataId
     *
     * This method finds a match and updates skydDataId in TargetedMSRun in the case where the skyDataId is not linked
     * to the ExpRun.
     */
    private void updateSkydDataIds(User user, Container targetContainer, Logger log) throws ImportException
    {
        log.info("Updating skydDataIds in folder: {}", targetContainer.getPath());

        boolean errors = false;
        ExperimentService expService = ExperimentService.get();
        List<? extends ExpRun> runs = expService.getExpRuns(targetContainer, null, null);

        TargetedMSService tmsService = TargetedMSService.get();
        for (ExpRun run : runs)
        {
            var targetedmsRun = tmsService.getRunByLsid(run.getLSID(), targetContainer);
            if (targetedmsRun == null) continue;

            var skydDataId = targetedmsRun.getSkydDataId();
            if (skydDataId == null) continue;

            var skydData = expService.getExpData(skydDataId);
            if (skydData == null)
            {
                log.error("Could not find a row for skydDataId {} for run {}", skydDataId, targetedmsRun.getFileName());
                errors = true;
            }
            else if (skydData.getRun() == null)
            {
                // skydData is not associated with an ExpRun. Find an ExpData associated with the ExpRun that matches
                // the skydName and update the skydDataId on the run.
                String skydName = skydData.getName();
                Optional<? extends ExpData> matchingData = run.getAllDataUsedByRun().stream()
                        .filter(data -> Objects.equals(skydName, data.getName()))
                        .findFirst();

                if (matchingData.isPresent())
                {
                    ExpData data = matchingData.get();
                    log.debug("Updating skydDataId for run {} to {}", targetedmsRun.getFileName(), data.getRowId());
                    tmsService.updateSkydDataId(targetedmsRun, data, user);
                }
                else
                {
                    log.error("Could not find matching skyData for run {}", targetedmsRun.getFileName());
                    errors = true;
                }
            }
        }

        if (errors)
        {
            throw new ImportException("Could not update skydDataIds");
        }
    }

    public static class Factory extends AbstractFolderImportFactory
    {
        @Override
        public FolderImporter create()
        {
            return new PanoramaPublicFileImporter();
        }

        @Override
        public int getPriority()
        {
            // We want this to run last to do exp.data.datafileurl cleanup
            return PanoramaPublicManager.PRIORITY_PANORAMA_PUBLIC_FILES;
        }
    }
}

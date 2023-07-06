package org.labkey.panoramapublic;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.provider.FileSystemAuditProvider;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.pipeline.CopyExperimentPipelineJob;
import org.labkey.panoramapublic.query.ExperimentAnnotationsManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class PanoramaPublicSymlinkManager
{
    private static final Logger _log = LogHelper.getLogger(PanoramaPublicSymlinkManager.class, "Handling symlinks between public and private folders");

    // Note: The production server running this code is Linux. Windows requires extra permissions to create symlinks.
    // If running this code on a Windows server, the Panorama Public copy will not use symlinks unless this flag is set to true.
    // Additionally, you will have to run the server (or IntelliJ) as an administrator to run with symlinks.
    private static final boolean DEBUG_SYMLINKS_ON_WINDOWS = false;

    private static final PanoramaPublicSymlinkManager _instance = new PanoramaPublicSymlinkManager();

    // Manage symlinks created when copying files to Panorama Public
    private PanoramaPublicSymlinkManager()
    {
        // prevent external construction with a private default constructor
    }

    public static PanoramaPublicSymlinkManager get()
    {
        return _instance;
    }


    private void handleContainerSymlinks(File source, PanoramaPublicSymlinkHandler handler, Container container, User user)
    {
        for (File file : Objects.requireNonNull(source.listFiles()))
        {
            if (file.isDirectory())
            {
                handleContainerSymlinks(file, handler, container, user);
            }
            else
            {
                Path filePath = file.toPath();
                if (Files.isSymbolicLink(filePath))
                {
                    try {
                        Path target = Files.readSymbolicLink(filePath);
                        handler.handleSymlink(filePath, target, container, user);
                    } catch (IOException x) {
                        _log.error("Unable to resolve symlink target for symlink at " + filePath);
                    }
                }
            }
        }
    }

    public void handleContainerSymlinks(Container container, User user, PanoramaPublicSymlinkHandler handler)
    {
        FileContentService fcs = FileContentService.get();
        if (null != fcs)
        {
            File root = fcs.getFileRoot(container);
            if (null != root)
            {
                handleContainerSymlinks(root, handler, container, user);
            }
        }
    }

    private void handleAllSymlinks(Set<Container> containers, User user, PanoramaPublicSymlinkHandler handler)
    {
        for (Container container : containers)
        {
            Set<Container> tree = ContainerManager.getAllChildren(container);
            for (Container node : tree)
            {
                handleContainerSymlinks(node, user, handler);
            }
        }
    }

    private String normalizeContainerPath(String path)
    {
        File file = new File(path);
        if (file.isAbsolute() || path.startsWith(File.separator))
            return path + File.separator;

        return File.separator + path + File.separator;
    }

    public void beforeContainerDeleted(Container container, User user)
    {
        if (PanoramaPublicManager.canBeSymlinkTarget(container)) // Fire the event only if the container being deleted is in the Panorama Public project.
        {
            // Look for an experiment that includes data in the given container.  This could be an experiment defined
            // in the given container, or in an ancestor container that has 'IncludeSubfolders' set to true.
            ExperimentAnnotations expAnnot = ExperimentAnnotationsManager.getExperimentIncludesContainer(container);
            if (null != expAnnot)
            {
                fireSymlinkCopiedExperimentDelete(expAnnot, container, user);
            }
        }
    }

    /**
     *
     * @param expAnnot experiment associated with the container being deleted
     * @param container container being deleted. This could be a subfolder of the experiment container if the experiment
     *                  is configured to include subfolders.
     */
    private void fireSymlinkCopiedExperimentDelete(ExperimentAnnotations expAnnot, Container container, User user)
    {
        if (expAnnot.getDataVersion() != null && !ExperimentAnnotationsManager.isCurrentVersion(expAnnot))
        {
            // This is an older version of the data on Panorama Public, so the folder should not have any files that are symlink targets.
            return;
        }

        Path deletedContainerPath = FileContentService.get().getFileRootPath(container, FileContentService.ContentType.files);

        List<ExperimentAnnotations> versions = ExperimentAnnotationsManager.getPublishedVersionsOfExperiment(expAnnot.getSourceExperimentId());

        if (versions.size() > 1)
        {
            ExperimentAnnotations nextHighestVersion = versions.stream()
                    .filter(version -> version.getDataVersion() != null && !version.getDataVersion().equals(expAnnot.getDataVersion()))
                    .max(Comparator.comparing(ExperimentAnnotations::getDataVersion)).orElse(null);

            if (nextHighestVersion != null)
            {
                Container versionContainer = nextHighestVersion.getContainer();
                handleContainerSymlinks(versionContainer, user, (link, target, c, u) -> {
                    if (!target.startsWith(deletedContainerPath))
                    {
                        return;
                    }
                    Files.move(target, link, REPLACE_EXISTING); // Move the files back to the next highest version of the experiment
                    addReplaceSymlinkWithTargetAuditEvent(link, target, c, u);

                    _log.info("File moved from " + target + " to " + link);

                    // This should update the symlinks in the submitted folder as well as
                    // symlinks in versions older than this one to point to the files in the next highest version.
                    fireSymlinkUpdate(target, link, container, user);
                });
            }
        }

        // If there were no previous versions then move the files back to the submitted folder.
        // This will also take care of the case where there is a previous version but some files in the folder being
        // deleted are not in the previous version (e.g. new files added to the source folder when data was resubmitted).
        // These files should be moved back to the submitted folder
        Container sourceContainer = ExperimentAnnotationsManager.getSourceExperimentContainer(expAnnot);
        if (null == sourceContainer && null != expAnnot.getSourceExperimentPath())
        {
            // Submitter may have deleted the ExperimentAnnotations in their folder. Try to lookup by sourceExperimentPath
            sourceContainer = ContainerService.get().getForPath(expAnnot.getSourceExperimentPath());
        }
        if (null != sourceContainer)
        {
            handleContainerSymlinks(sourceContainer, user, (link, target, c, u) -> {
                if (!target.startsWith(deletedContainerPath))
                {
                    return;
                }
                Files.move(target, link, REPLACE_EXISTING);
                addReplaceSymlinkWithTargetAuditEvent(link, target, c, u);

                _log.info("File moved from " + target + " to " + link);

                // Symlinks in the source container point to -> current version container on Panorama Public
                // Symlinks in previous versions of the data on Panorama Public point to -> current version container on Panorama Public
                // Only the container with the current version of the data on Panorama Public can have symlink targets.
                // Here we are moving the file back to the source container, which means none of the previous versions had this file.
                // So we don't need to fireSymlinkUpdate since there should not be any other symlinks targeting this file.
            });
        }
    }

    public void fireSymlinkUpdateContainer(Container oldContainer, Container newContainer, User user)
    {
        // Update symlinks to new target
        FileContentService fcs = FileContentService.get();
        if (fcs != null)
        {
            if (fcs.getFileRoot(oldContainer) != null && fcs.getFileRoot(newContainer) != null)
            {
                fireSymlinkUpdateContainer(fcs.getFileRoot(oldContainer).getPath(), fcs.getFileRoot(newContainer).getPath(), oldContainer, user);
            }
        }
    }

    public void fireSymlinkUpdateContainer(String oldContainer, String newContainer, Container container, User user)
    {
        if (PanoramaPublicManager.canBeSymlinkTarget(container))
        {
            String oldContainerPath = normalizeContainerPath(oldContainer);
            String newContainerPath = normalizeContainerPath(newContainer);

            Set<Container> containers = getSymlinkContainers(container);
            handleAllSymlinks(containers, user, (link, target, c, u) -> {
                if (String.valueOf(target).contains(oldContainerPath))
                {
                    Path newTarget = Path.of(target.toString().replace(oldContainerPath, newContainerPath));
                    try
                    {
                        Files.delete(link);
                        Files.createSymbolicLink(link, newTarget);
                        addLinkUpdatedAuditEvent(link, newTarget, c, u);
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }

    private void addLinkUpdatedAuditEvent(Path link, Path newTarget, Container linkContainer, User user)
    {
        addFileAuditEvent(link, linkContainer, user, "Updated symlink target to " + newTarget);
    }

    private void addReplaceSymlinkWithTargetAuditEvent(Path link, Path target, Container linkContainer, User user)
    {
        addFileAuditEvent(link, linkContainer, user, "Replaced symlink with target file " + target);
    }

    private void addReplaceTargetWithSymlinkAuditEvent(Path link, Path target, Container container, User user)
    {
        addFileAuditEvent(link, container, user, "Replaced target file with symlink to " + target);
    }

    private void addFileAuditEvent(Path link, Container container, User user, String comment)
    {
        FileSystemAuditProvider.FileSystemAuditEvent event =  new FileSystemAuditProvider.FileSystemAuditEvent(
                container != null ? container.getId() : null, comment);
        event.setFile(link.toString());
        AuditLogService.get().addEvent(user, event);
    }

    public void fireSymlinkUpdate(Path oldTarget, Path newTarget, Container container, User user)
    {
        fireSymlinkUpdate(oldTarget, newTarget, container, user, null);
    }

    public void fireSymlinkUpdate(Path oldTarget, Path newTarget, Container container, User user, @Nullable Logger log)
    {
        if (PanoramaPublicManager.canBeSymlinkTarget(container)) // Only files in the Panorama Public project can be symlink targets.
        {
            Set<Container> containers = getSymlinkContainers(container);

            handleAllSymlinks(containers, user, (link, target, c, u) -> {
                if (!target.equals(oldTarget))
                    return;

                try
                {
                    Files.delete(link);
                    Files.createSymbolicLink(link, newTarget);
                    addLinkUpdatedAuditEvent(link, newTarget, c, u);
                    if (log != null)
                    {
                        log.info("Target for symlink " + link + " updated to " + newTarget);
                    }
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    public void moveAndSymLinkDirectory(CopyExperimentPipelineJob job, File source, File target, @Nullable Logger log) throws IOException
    {
        if (null == log)
        {
            log = _log;
        }
        FileContentService fcs = FileContentService.get();
        if (null == fcs)
            throw new RuntimeException("Unable to access FileContentService");

        File[] files = source.listFiles();
        if (null != files)
        {
            for (File file : files)
            {
                if (file.isDirectory())
                {
                    Path targetPath = Path.of(target.getPath(), file.getName());
                    if (!Files.exists(targetPath))
                    {
                        Files.createDirectory(targetPath);
                        log.debug("Directory created: " + targetPath);
                    }

                    moveAndSymLinkDirectory(job, file, targetPath.toFile(), log);
                }
                else
                {
                    Path targetPath = Path.of(target.getPath(), file.getName());
                    Path filePath = file.toPath();

                    // If this has already been copied, don't copy the symlink
                    if (Files.isSymbolicLink(filePath) && filePath.compareTo(targetPath) == 0)
                        continue;

                    // Don't move over logs
                    if (FilenameUtils.getExtension(file.getPath()).equals("log"))
                        continue;

                    // Check if move and symlink is manually disabled from the copy form.
                    // If on Windows (not the production server use-case), Windows cannot do symlink without admin permissions so
                    // just copy over the files. Also, if the file is a clib file, special handling is required so just copy it over.
                    if (!job.isMoveAndSymlink() || (!DEBUG_SYMLINKS_ON_WINDOWS && SystemUtils.IS_OS_WINDOWS)
                            || FilenameUtils.getExtension(file.getPath()).equals("clib"))
                    {
                        // If the file is a symlink, copy the target file over the symlink in the source project.
                        if (Files.isSymbolicLink(filePath))
                        {
                            Files.copy(Files.readSymbolicLink(filePath), filePath, REPLACE_EXISTING);
                            log.debug("Copy file over symlink: " + filePath);
                        }

                        // Copy the file to panorama public
                        Files.copy(filePath, targetPath, REPLACE_EXISTING);
                        log.debug("Copied file " + filePath + " to " + targetPath);
                        fcs.fireFileCreateEvent(targetPath, job.getUser(), job.getContainer());

                        continue;
                    }

                    // Symbolic link should move the target file over. This would be for a re-copy to public.
                    if (Files.isSymbolicLink(filePath))
                    {
                        log.debug("Source file is a symlink: " + filePath);

                        Path oldPath = Files.readSymbolicLink(filePath);
                        Files.move(oldPath, targetPath, REPLACE_EXISTING);
                        log.debug("Moved symlink target  " + oldPath + " to " + targetPath);
                        fcs.fireFileCreateEvent(targetPath, job.getUser(), job.getContainer());

                        fireSymlinkUpdate(oldPath, targetPath, job.getContainer(), // job container is the target container on Panorama Public
                                job.getUser(), log);

                        Path symlink = Files.createSymbolicLink(oldPath, targetPath);
                        Container oldTargetContainer = getContainerForFilePath(oldPath);
                        if (oldTargetContainer != null)
                        {
                            // The target of the symlink has been moved from a previous version on Panorama Public to the
                            // new version on Panorama Public, and a symink has been created in the previous version container.
                            // Add an audit event in the previous version container.
                            addReplaceTargetWithSymlinkAuditEvent(symlink, targetPath, oldTargetContainer, job.getUser());
                        }
                        log.debug("Replaced old target with symlink: " + symlink);
                    }
                    else
                    {
                        Files.move(filePath, targetPath, REPLACE_EXISTING);
                        log.debug("Moved file " + filePath + " to " + targetPath);
                        fcs.fireFileCreateEvent(targetPath, job.getUser(), job.getContainer());

                        Files.createSymbolicLink(filePath, targetPath);
                        log.debug("Created symlink " + filePath + " targeting " + targetPath);
                        // We don't need to update any symlinks here since the source container should not have any symlink targets.
                    }
                }
            }
        }
    }

    // Get the container for the given file path.
    // Example: if the file path is C:\Users\vsharma\WORK\LabKey\build\deploy\files\Panorama Public\TestProject V.1\@files\Study9S_Site52_v1.sky.zip
    // this will return the container for "/Panorama Public/TestProject V.1"
    private Container getContainerForFilePath(Path path)
    {
        org.labkey.api.util.Path lkPath = org.labkey.api.util.Path.rootPath;

        Path defaultRootPath = FileContentService.get().getSiteDefaultRoot().toPath();
        if (path.startsWith(defaultRootPath))
        {
            Path rel = defaultRootPath.relativize(path);

            Iterator<Path> iter = rel.iterator();
            while (iter.hasNext())
            {
                Path next = iter.next();
                if (FileContentService.FILES_LINK.equals(next.getFileName().toString()))
                {
                    break;
                }
                lkPath = lkPath.resolve(org.labkey.api.util.Path.parse(next.toString()));
            }
        }

        return org.labkey.api.util.Path.rootPath.equals(lkPath) ? null : ContainerManager.getForPath(lkPath);
    }

    private void verifyFileTreeSymlinks(File source, Map<String, String> linkInvalidTarget, Map<String, String> linkWithSymlinkTarget) throws IOException
    {
        for (File file : Objects.requireNonNull(source.listFiles()))
        {
            if (file.isDirectory())
            {
                verifyFileTreeSymlinks(file, linkInvalidTarget, linkWithSymlinkTarget);
            }
            else
            {
                Path filePath = file.toPath();
                if (Files.isSymbolicLink(filePath))
                {
                    // Verify target file exists and is not a symbolic link
                    Path targetPath = Files.readSymbolicLink(filePath);
                    if (!FileUtil.isFileAndExists(targetPath))
                    {
                        linkInvalidTarget.put(filePath.toString(), targetPath.toString());
                    }
                    else if (Files.isSymbolicLink(targetPath))
                    {
                        linkWithSymlinkTarget.put(filePath.toString(), targetPath.toString());
                    }
                }
            }
        }
    }

    public boolean verifySymlinks() throws IOException
    {
        Map<String, String> linkInvalidTarget = new HashMap<>();
        Map<String, String> linkWithSymlinkTarget = new HashMap<>();
        Set<Container> containers = ContainerManager.getAllChildrenWithModule(ContainerManager.getRoot(), ModuleLoader.getInstance().getModule(PanoramaPublicModule.class));
        for (Container container : containers)
        {
            Set<Container> tree = ContainerManager.getAllChildren(container);
            for (Container node : tree)
            {
                FileContentService fcs = FileContentService.get();
                if (null != fcs && null != fcs.getFileRoot(node))
                {
                    File root = fcs.getFileRoot(node);
                    if (null != root)
                    {
                        verifyFileTreeSymlinks(root, linkInvalidTarget, linkWithSymlinkTarget);
                    }
                }
            }
        }

        if(linkInvalidTarget.size() > 0)
        {
            String linkInvalidTargets = linkInvalidTarget.entrySet().stream().map(String::valueOf).collect(Collectors.joining("\n"));
            _log.error(linkInvalidTarget.size() + " Symlinks with invalid targets: \n" + linkInvalidTargets);
        }

        if(linkWithSymlinkTarget.size() > 0)
        {
            String linkWithSymlinkTargets = linkWithSymlinkTarget.entrySet().stream().map(String::valueOf).collect(Collectors.joining("\n"));
            _log.error(linkWithSymlinkTarget.size() + " Symlinks targeting symlinks: \n" + linkWithSymlinkTargets);
        }

        return linkInvalidTarget.size() == 0 && linkWithSymlinkTarget.size() == 0;
    }

    /**
     * Returns a set of containers that can have symlinks that target files in targetContainer. This includes the source container,
     * and containers with previous versions of the experiment. Only the container with the current version of the experiment
     * can have symlink targets.
     * @param targetContainer
     * @return A set of containers that can have symlinks that target files in targetContainer
     */
    private static Set<Container> getSymlinkContainers(Container targetContainer)
    {
        if (PanoramaPublicManager.canBeSymlinkTarget(targetContainer))
        {
            ExperimentAnnotations expAnnotations = ExperimentAnnotationsManager.getExperimentIncludesContainer(targetContainer);

            if (expAnnotations != null && expAnnotations.getSourceExperimentId() != null)
            {
                Set<Container> symlinkContainers = new HashSet<>();
                Container sourceContainer = ExperimentAnnotationsManager.getSourceExperimentContainer(expAnnotations);
                if (sourceContainer != null)
                {
                    symlinkContainers.add(sourceContainer);
                }
                List<ExperimentAnnotations> exptVersions = ExperimentAnnotationsManager.getPublishedVersionsOfExperiment(expAnnotations.getSourceExperimentId());
                exptVersions.stream().map(ExperimentAnnotations::getContainer)
                        .filter(c -> !c.equals(targetContainer))
                        .forEach(symlinkContainers::add); // Add containers for other versions
                return symlinkContainers;
            }
        }
        return Collections.emptySet();
    }
}

/*
 * Copyright (c) 2019 LabKey Corporation
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

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.files.FileContentService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class PanoramaPublicManager
{
    private static final PanoramaPublicManager _instance = new PanoramaPublicManager();

    private static final Logger _log = LogManager.getLogger(PanoramaPublicManager.class);

    public static String PANORAMA_PUBLIC_FILES = "Panorama Public Files";

    // Register symlinks created when copying files to Panorama Public
    private PanoramaPublicManager()
    {
        // prevent external construction with a private default constructor
    }

    public static PanoramaPublicManager get()
    {
        return _instance;
    }

    public static DbSchema getSchema()
    {
        return DbSchema.get(PanoramaPublicSchema.SCHEMA_NAME, DbSchemaType.Module);
    }

    public static TableInfo getTableInfoExperimentAnnotations()
    {
        return getSchema().getTable(PanoramaPublicSchema.TABLE_EXPERIMENT_ANNOTATIONS);
    }

    public static TableInfo getTableInfoJournal()
    {
        return getSchema().getTable(PanoramaPublicSchema.TABLE_JOURNAL);
    }

    public static TableInfo getTableInfoJournalExperiment()
    {
        return getSchema().getTable(PanoramaPublicSchema.TABLE_JOURNAL_EXPERIMENT);
    }

    public static TableInfo getTableInfoSubmission()
    {
        return getSchema().getTable(PanoramaPublicSchema.TABLE_SUBMISSION);
    }

    public static TableInfo getTableInfoPxXml()
    {
        return getSchema().getTable(PanoramaPublicSchema.TABLE_PX_XML);
    }

    public static TableInfo getTableInfoDataValidation()
    {
        return getSchema().getTable(PanoramaPublicSchema.TABLE_DATA_VALIDATION);
    }

    public static TableInfo getTableInfoSkylineDocValidation()
    {
        return getSchema().getTable(PanoramaPublicSchema.TABLE_SKYLINE_DOC_VALIDATION);
    }

    public static TableInfo getTableInfoSkylineDocSampleFile()
    {
        return getSchema().getTable(PanoramaPublicSchema.TABLE_SKYLINE_DOC_SAMPLE_FILE);
    }

    public static TableInfo getTableInfoModificationValidation()
    {
        return getSchema().getTable(PanoramaPublicSchema.TABLE_MODIFICATION_VALIDATION);
    }

    public static TableInfo getTableInfoSkylineDocModification()
    {
        return getSchema().getTable(PanoramaPublicSchema.TABLE_SKYLINE_DOC_MODIFICATION);
    }

    public static TableInfo getTableInfoSpecLibValidation()
    {
        return getSchema().getTable(PanoramaPublicSchema.TABLE_SPEC_LIB_VALIDATION);
    }

    public static TableInfo getTableInfoSkylineDocSpecLib()
    {
        return getSchema().getTable(PanoramaPublicSchema.TABLE_SKYLINE_DOC_SPEC_LIB);
    }

    public static TableInfo getTableInfoSpecLibSourceFile()
    {
        return getSchema().getTable(PanoramaPublicSchema.TABLE_SPEC_LIB_SOURCE_FILE);
    }

    public static TableInfo getTableInfoCatalogEntry()
    {
        return getSchema().getTable(PanoramaPublicSchema.TABLE_CATALOG_ENTRY);
    }

    public static ITargetedMSRun getRunByLsid(String lsid, Container container)
    {
        return TargetedMSService.get().getRunByLsid(lsid, container);
    }

    public static TableInfo getTableInfoSpecLibInfo()
    {
        return getSchema().getTable(PanoramaPublicSchema.TABLE_SPEC_LIB_INFO);
    }

    public static TableInfo getTableInfoExperimentStructuralModInfo()
    {
        return getSchema().getTable(PanoramaPublicSchema.TABLE_EXPT_STRUCTURAL_MOD_INFO);
    }

    public static TableInfo getTableInfoExperimentIsotopeModInfo()
    {
        return getSchema().getTable(PanoramaPublicSchema.TABLE_EXPT_ISOTOPE_MOD_INFO);
    }

    public static TableInfo getTableInfoIsotopeUnimodInfo()
    {
        return getSchema().getTable(PanoramaPublicSchema.TABLE_ISOTOPE_UNIMOD_INFO);
    }

    public static ActionURL getRawDataTabUrl(Container container)
    {
        return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(container, TargetedMSService.RAW_FILES_TAB);
    }

    private void handleContainerSymlinks(File source, SymlinkHandler handler)
    {
        for (File file : Objects.requireNonNull(source.listFiles()))
        {
            if (file.isDirectory())
            {
                handleContainerSymlinks(file, handler);
            }
            else
            {
                Path filePath = file.toPath();
                if (Files.isSymbolicLink(filePath))
                {
                    try {
                        Path target = Files.readSymbolicLink(filePath);
                        handler.handleSymlink(filePath, target);
                    } catch (IOException x) {
                        _log.error("Unable to resolve symlink target for symlink at " + filePath);
                    }
                }
            }
        }
    }

    private void handleAllSymlinks(SymlinkHandler handler)
    {
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
                        handleContainerSymlinks(root, handler);
                    }
                }
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

    public void fireSymlinkContainerDelete(String container)
    {
        String containerPath = normalizeContainerPath(container);
        handleAllSymlinks((link, target) -> {
            if (String.valueOf(target).contains(containerPath))
            {
                try
                {
                    Files.delete(link);
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public void fireSymlinkUpdateContainer(String oldContainer, String newContainer)
    {
        String oldContainerPath = normalizeContainerPath(oldContainer);
        String newContainerPath = normalizeContainerPath(newContainer);
        handleAllSymlinks((link, target) -> {
            if (String.valueOf(target).contains(oldContainerPath))
            {
                Path newTarget = Path.of(target.toString().replace(oldContainerPath, newContainerPath));
                try
                {
                    Files.delete(link);
                    Files.createSymbolicLink(link, newTarget);
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public void fireSymlinkUpdate(Path oldTarget, Path newTarget)
    {
        handleAllSymlinks((link, target) -> {
            if (!target.equals(oldTarget))
                return;

            try
            {
                Files.delete(link);
                Files.createSymbolicLink(link, newTarget);
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        });
    }

    public void moveAndSymLinkDirectory(User user, Container c, File source, File target, boolean createSourceSymLinks) throws IOException
    {
        FileContentService fcs = FileContentService.get();
        if (null == fcs)
            throw new RuntimeException("Unable to access FileContentService");

        for (File file : Objects.requireNonNull(source.listFiles()))
        {
            if (file.isDirectory())
            {
                Path targetPath = Path.of(target.getPath(), file.getName());
                if (!Files.exists(targetPath))
                {
                    Files.createDirectory(targetPath);
                    _log.info("Directory created: " + targetPath);
                }

                moveAndSymLinkDirectory(user, c, file, targetPath.toFile(), createSourceSymLinks);
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

                // Symbolic link should move the target file over. This would be for a re-copy to public.
                if (Files.isSymbolicLink(filePath))
                {
                    Path oldPath = Files.readSymbolicLink(filePath);
                    Files.move(oldPath, targetPath, REPLACE_EXISTING);
                    fcs.fireFileCreateEvent(targetPath, user, c);

                    fireSymlinkUpdate(oldPath, targetPath);
                    _log.info("File moved from " + oldPath + " to " + targetPath);

                    Path symlink = Files.createSymbolicLink(oldPath, targetPath);
                    _log.info("Symlink created: " + symlink);
                }
                else
                {
                    Files.move(filePath, targetPath, REPLACE_EXISTING);
                    fcs.fireFileCreateEvent(targetPath, user, c);

                    Files.createSymbolicLink(filePath, targetPath);
                    fireSymlinkUpdate(filePath, targetPath);
                }

                if (createSourceSymLinks)
                {
                    Path symlink = Files.createSymbolicLink(filePath, targetPath);
                    _log.info("Symlink created: " + symlink);
                }
            }
        }
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
            _log.error("Symlinks with invalid targets: " + linkInvalidTarget);

        if(linkWithSymlinkTarget.size() > 0)
            _log.error("Symlinks targeting symlinks: " + linkWithSymlinkTarget);

        return linkInvalidTarget.size() == 0 && linkWithSymlinkTarget.size() == 0;
    }
}
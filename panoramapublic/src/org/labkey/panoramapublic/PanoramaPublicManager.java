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
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class PanoramaPublicManager
{
    private static final PanoramaPublicManager _instance = new PanoramaPublicManager();

    private static final Logger _log = LogManager.getLogger(PanoramaPublicManager.class);

    public static String PANORAMA_PUBLIC_FILES = "Panorama Public Files";

    // Register symlinks created when copying files to Panorama Public
    private final List<DataSymlinkListener> _symLinkListeners = new CopyOnWriteArrayList<>();

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

    public void registerSymlinkListener(DataSymlinkListener symlinkListener)
    {
        _symLinkListeners.add(symlinkListener);
    }

    public void removeSymlinkListener(DataSymlinkListener symlinkListener)
    {
        _symLinkListeners.remove(symlinkListener);
    }

    public void fireSymlinkContainerDelete(String container)
    {
        for (DataSymlinkListener symLinkListener : _symLinkListeners)
        {
            // Unregister and delete any symlinks targeting the container
            if (symLinkListener.deleteTargetContainer(container))
                removeSymlinkListener(symLinkListener);

            // Unregister any symlinks in the container
            if (symLinkListener.isSymlinkInContainer(container))
                removeSymlinkListener(symLinkListener);
        }
    }

    public void fireSymlinkUpdateContainer(String oldContainer, String newContainer)
    {
        for (DataSymlinkListener symLinkListener : _symLinkListeners)
        {
            symLinkListener.updateTargetContainer(oldContainer, newContainer);
        }
    }

    private void fireSymlinkUpdate(Path oldTarget, Path newTarget)
    {
        for (DataSymlinkListener symLinkListener : _symLinkListeners)
        {
            symLinkListener.update(oldTarget, newTarget);
        }
    }

    public void moveAndSymLinkDirectory(File source, File target, boolean createSourceSymLinks) throws IOException
    {
        for (File file : source.listFiles())
        {
            if (file.isDirectory())
            {
                Path targetPath = Path.of(target.getPath(), file.getName());
                if (!Files.exists(targetPath))
                {
                    Files.createDirectory(targetPath);
                    _log.info("Directory created: " + targetPath);
                }

                moveAndSymLinkDirectory(file, targetPath.toFile(), createSourceSymLinks);
            }
            else
            {
                Path targetPath = Path.of(target.getPath(), file.getName());

                // If this has already been copied, don't copy the symlink
                if (Files.isSymbolicLink(file.toPath()) && file.toPath().compareTo(targetPath) == 0)
                    continue;

                // Don't move over logs
                if (FilenameUtils.getExtension(file.getPath()).equals("log"))
                    continue;

                // TODO: Does this work on a different file system?
                Files.move(file.toPath(), targetPath, REPLACE_EXISTING);
                fireSymlinkUpdate(file.toPath(), targetPath);

                _log.info("File moved to " + targetPath);
                if (createSourceSymLinks)
                {
                    Path symlink = Files.createSymbolicLink(file.toPath(), targetPath);
                    registerSymlinkListener(new DataSymlinkListener(symlink, targetPath));
                    _log.info("Symlink created: " + symlink);
                }
            }
        }
    }
}
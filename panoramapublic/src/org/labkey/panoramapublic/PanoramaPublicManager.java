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

import org.apache.commons.io.FileUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.panoramapublic.chromlib.ChromLibStateException;
import org.labkey.panoramapublic.chromlib.ChromLibStateManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PanoramaPublicManager
{
    private static final PanoramaPublicManager _instance = new PanoramaPublicManager();

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
        return DbSchema.get(PanoramaPublicSchema.SCHEMA_NAME);
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

    public static TableInfo getTableInfoPxXml()
    {
        return getSchema().getTable(PanoramaPublicSchema.TABLE_PX_XML);
    }

    public static ITargetedMSRun getRunByLsid(String lsid, Container container)
    {
        return TargetedMSService.get().getRunByLsid(lsid, container);
    }

    public static ActionURL getRawDataTabUrl(Container container)
    {
        return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(container, TargetedMSService.RAW_FILES_TAB);
    }

    public static void makePanoramaExperimentalDataFolder(Container container, User user)
    {
        Module targetedMSModule = ModuleLoader.getInstance().getModule(TargetedMSService.MODULE_NAME);
        ModuleProperty moduleProperty = targetedMSModule.getModuleProperties().get(TargetedMSService.FOLDER_TYPE_PROP_NAME);
        if(container.getActiveModules().contains(targetedMSModule))
        {
            moduleProperty.saveValue(user, container, TargetedMSService.FolderType.Experiment.toString());
        }
    }

    public static void makePanoramaLibraryFolder(Container container, Container sourceContainer, User user) throws ChromLibStateException
    {
        Module targetedMSModule = ModuleLoader.getInstance().getModule(TargetedMSService.MODULE_NAME);
        if (container.getActiveModules().contains(targetedMSModule))
        {
            ModuleProperty moduleProperty = targetedMSModule.getModuleProperties().get(TargetedMSService.FOLDER_TYPE_PROP_NAME);
            String sourceValue = moduleProperty.getValueContainerSpecific(sourceContainer);
            String targetValue = moduleProperty.getValueContainerSpecific(container);
            if (TargetedMSService.FolderType.Library.name().equals(sourceValue) || TargetedMSService.FolderType.LibraryProtein.name().equals(sourceValue))
            {
                PropertyManager.PropertyMap sourcePropMap = PropertyManager.getProperties(sourceContainer, "TargetedMS");
                moduleProperty.saveValue(user, container, sourceValue);

                String versionStr = sourcePropMap.get("chromLibRevision");
                if (null != versionStr)
                {
                    // int version = Integer.parseInt(versionStr);
                    sourcePropMap = PropertyManager.getWritableProperties(container, "TargetedMS", true);
                    sourcePropMap.put("chromLibRevision", versionStr);
                    sourcePropMap.save();

                    Path chromLibDir = getChromLibDir(container);
                    if (Files.exists(chromLibDir))
                    {
                        try
                        {
                            Files.list(chromLibDir).forEach(p -> {
                                try
                                {
                                    changeFileName(p, container);
                                }
                                catch (IOException e)
                                {
                                    e.printStackTrace(); // TODO
                                }
                            });
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace(); // TODO
                        }
                    }

                    Path chromLibExportFile = chromLibDir.resolve(FileUtil.makeFileNameWithTimestamp("chrom_lib_export_" + sourceContainer.getRowId(), "tsv"));
                    ChromLibStateManager libManager = new ChromLibStateManager();
                    libManager.exportLibState(sourceContainer, chromLibExportFile.toFile());
                    libManager.importLibState(container, chromLibExportFile.toFile());
                }
            }

            // moduleProperty.saveValue(user, container, TargetedMSService.FolderType.Experiment.toString());
        }
    }

    public static final String LIB_FILE_DIR = "targetedMSLib";
    public static final String CHROM_LIB_FILE_NAME = "chromlib";
    public static final String CHROM_LIB_FILE_EXT = "clib";
    // Example: chromlib_314_rev1.clib
    public static Pattern chromlibPattern = Pattern.compile(CHROM_LIB_FILE_NAME + "_\\d+_rev(\\d+)\\." + CHROM_LIB_FILE_EXT);
    private static void changeFileName(Path path, Container container) throws IOException
    {
        if(path.getFileName().toString().endsWith(CHROM_LIB_FILE_EXT))
        {
            Matcher match = chromlibPattern.matcher(path.getFileName().toString());
            if(match.matches())
            {
                String revision = match.group(1);
                String newFileName = CHROM_LIB_FILE_NAME + "_"+container.getRowId() + "_rev"+revision + "." + CHROM_LIB_FILE_EXT;
                Path targetFile = path.getParent().resolve(newFileName);
                FileUtils.moveFile(path.toFile(), targetFile.toFile());
            }
        }
    }

    public static Path getChromLibFile(Container container, int revision) throws IOException
    {
        Path chromLibDir = getChromLibDir(container);
        return chromLibDir.resolve(CHROM_LIB_FILE_NAME+"_"+container.getRowId()+"_rev"+revision+"."+CHROM_LIB_FILE_EXT);
    }

    private static Path getChromLibDir(Container container)
    {
        PipeRoot root = PipelineService.get().getPipelineRootSetting(container);
        assert root != null;
        return root.getRootNioPath().resolve(LIB_FILE_DIR);
    }
}
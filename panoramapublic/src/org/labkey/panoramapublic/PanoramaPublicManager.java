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
import org.labkey.panoramapublic.query.JournalManager;

public class PanoramaPublicManager
{
    private static final PanoramaPublicManager _instance = new PanoramaPublicManager();

    private static final Logger _log = LogManager.getLogger(PanoramaPublicManager.class);

    public static String PANORAMA_PUBLIC_FILES = "Panorama Public Files";
    public static String PANORAMA_PUBLIC_METADATA = "Panorama Public Experiment Metadata";
    public static int PRIORITY_PANORAMA_PUBLIC_METADATA = 1000;
    public static int PRIORITY_PANORAMA_PUBLIC_FILES = PRIORITY_PANORAMA_PUBLIC_METADATA + 1;

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

    public static boolean canBeSymlinkTarget(Container container)
    {
        // not all webdav files and directories belong to a container
        if (null == container)
            return false;
        // Folders in a journal project (e.g. Panorama Public) are the only ones that can have symlink targets.
        // Folders in other projects can contain symlinks but no symlink targets.
        Container project = container.getProject();
        return project != null ? JournalManager.isJournalProject(project) : false;
    }
}
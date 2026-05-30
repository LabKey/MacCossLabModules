/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.panoramapublic.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.PanoramaPublicSchema;

import java.util.ArrayList;
import java.util.List;

public class DatasetStatusTableInfo extends PanoramaPublicTable
{
    public DatasetStatusTableInfo(@NotNull PanoramaPublicSchema userSchema, ContainerFilter cf)
    {
        super(PanoramaPublicManager.getTableInfoDatasetStatus(), userSchema, cf,
                new ContainerJoin("ExperimentAnnotationsId", PanoramaPublicManager.getTableInfoExperimentAnnotations(), "Id"));

        List<FieldKey> visibleColumns = new ArrayList<>();
        visibleColumns.add(FieldKey.fromParts("Created"));
        visibleColumns.add(FieldKey.fromParts("CreatedBy"));
        visibleColumns.add(FieldKey.fromParts("Modified"));
        visibleColumns.add(FieldKey.fromParts("ModifiedBy"));
        visibleColumns.add(FieldKey.fromParts("ExperimentAnnotationsId", "Link"));
        visibleColumns.add(FieldKey.fromParts("ExperimentAnnotationsId", "Title"));
        visibleColumns.add(FieldKey.fromParts("LastReminderDate"));
        visibleColumns.add(FieldKey.fromParts("ExtensionRequestedDate"));
        visibleColumns.add(FieldKey.fromParts("DeletionRequestedDate"));
        setDefaultVisibleColumns(visibleColumns);
    }
}

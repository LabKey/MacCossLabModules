/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.panoramapublic.model.speclib;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.SafeToRenderEnum;

public enum SpecLibDependencyType implements SafeToRenderEnum
{
    STATISTICALLY_DEPENDENT("Statistically dependent results"),
    TARGETS_AND_FRAGMENTS("Used for choosing targets and fragments"),
    TARGETS_ONLY("Used for choosing targets only"),
    SUPPORTING_INFO("Used only as supporting information"),
    IRRELEVANT("Irrelevant to results");

    private final String _label;

    SpecLibDependencyType(String label)
    {
        _label = label;
    }

    public String getLabel()
    {
        return _label;
    }

    public static @Nullable SpecLibDependencyType getFromName(String name)
    {
        try
        {
            return name != null ? valueOf(name) : null;
        }
        catch(IllegalArgumentException e)
        {
            return null;
        }
    }
}

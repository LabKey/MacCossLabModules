/*
 * Copyright (c) 2022-2026 LabKey Corporation
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
package org.labkey.panoramapublic.model.validation;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public abstract class SkylineDocValidation<S extends SkylineDocSampleFile>
{
    private int _id;
    private int _validationId;
    private long _runId; // Refers to targetedms.runs.id
    private String _name; // Name of the .sky.zip file
    private String _userGivenName;

    public abstract @NotNull List<S> getSampleFiles();

    public int getId()
    {
        return _id;
    }

    public void setId(int id)
    {
        _id = id;
    }

    public int getValidationId()
    {
        return _validationId;
    }

    public void setValidationId(int validationId)
    {
        _validationId = validationId;
    }

    public long getRunId()
    {
        return _runId;
    }

    public void setRunId(long runId)
    {
        _runId = runId;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getUserGivenName()
    {
        return _userGivenName;
    }

    public void setUserGivenName(String userGivenName)
    {
        _userGivenName = userGivenName;
    }

    public boolean isValid()
    {
        return foundAllSampleFiles();
    }

    public boolean foundAllSampleFiles()
    {
        return getSampleFiles().stream().allMatch(f -> !f.isPending() && f.found());
    }

    /**
     * @return true if the document has any missing sample files that are not marked as 'ambiguous'. Ambiguous sample files are those
     * that have the same name in one or more Skyline document but are different files based on the acquired time on the MS instrument.
     * We expect sample files to have unique names if they are different files.
     */
    public boolean hasMissingNonAmbiguousFiles()
    {
        return getSampleFiles().stream().anyMatch(f -> !(f.isPending() || f.found() || f.isAmbiguous()));
    }

    public boolean isPending()
    {
        return getSampleFiles().stream().anyMatch(DataFile::isPending);
    }

    public List<String> getMissingSampleFileNames()
    {
        return getSampleFiles().stream().filter(f -> !f.found()).map(DataFile::getName).collect(Collectors.toList());
    }
}

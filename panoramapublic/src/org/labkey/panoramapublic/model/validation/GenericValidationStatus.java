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

public abstract class GenericValidationStatus <D extends SkylineDocValidation, L extends SpecLibValidation>
{
    private DataValidation _validation;

    public GenericValidationStatus() {}

    public DataValidation getValidation()
    {
        return _validation;
    }

    public void setValidation(DataValidation validation)
    {
        _validation = validation;
    }

    public abstract @NotNull List<L> getSpectralLibraries();
    public abstract @NotNull List<D> getSkylineDocs();
    public abstract @NotNull List<Modification> getModifications();

    public PxStatus getPxStatus()
    {
        boolean allSampleFilesFound = getSkylineDocs().stream().allMatch(SkylineDocValidation::foundAllSampleFiles);
        boolean allModsValid = getModifications().stream().allMatch(Modification::isValid);
        boolean specLibsValid = getSpectralLibraries().stream().allMatch(SpecLibValidation::isValid);
        if (allSampleFilesFound)
        {
            return (allModsValid && specLibsValid) ? PxStatus.Complete : PxStatus.IncompleteMetadata;
        }
        return PxStatus.NotValid;
    }
}

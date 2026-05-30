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
package org.labkey.panoramapublic.proteomexchange.validator;

import org.jetbrains.annotations.NotNull;
import org.labkey.panoramapublic.model.validation.DataValidation;
import org.labkey.panoramapublic.model.validation.GenericValidationStatus;
import org.labkey.panoramapublic.model.validation.Modification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ValidatorStatus extends GenericValidationStatus<SkylineDocValidator, SpecLibValidator>
{
    private final List<SkylineDocValidator> _skylineDocs;
    private final List<Modification> _modifications;
    private final List<SpecLibValidator> _spectralLibraries;

    public ValidatorStatus(DataValidation validation)
    {
        setValidation(validation);
        _skylineDocs = new ArrayList<>();
        _modifications = new ArrayList<>();
        _spectralLibraries = new ArrayList<>();
    }

    public void addSkylineDoc(SkylineDocValidator skylineDocValidation)
    {
        _skylineDocs.add(skylineDocValidation);
    }

    public void addModification(Modification modification)
    {
        _modifications.add(modification);
    }

    public void addLibrary(SpecLibValidator specLib)
    {
        _spectralLibraries.add(specLib);
    }

    @Override
    public @NotNull List<SpecLibValidator> getSpectralLibraries()
    {
        return Collections.unmodifiableList(_spectralLibraries);
    }

    @Override
    public @NotNull List<SkylineDocValidator> getSkylineDocs()
    {
         return Collections.unmodifiableList(_skylineDocs);
    }

    @Override
    public @NotNull List<Modification> getModifications()
    {
        return Collections.unmodifiableList(_modifications);
    }

    public SkylineDocValidator getSkylineDocForRunId(Long runId)
    {
        return _skylineDocs.stream().filter(doc -> doc.getRunId() == runId).findFirst().orElse(null);
    }

    public SkylineDocValidator getSkylineDocForId(int skylineDocValidationId)
    {
        return _skylineDocs.stream().filter(doc -> doc.getId() == skylineDocValidationId).findFirst().orElse(null);
    }
}

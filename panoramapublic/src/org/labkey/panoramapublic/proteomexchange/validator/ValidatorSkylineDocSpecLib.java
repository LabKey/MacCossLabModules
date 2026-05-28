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

import org.labkey.api.targetedms.ISpectrumLibrary;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.panoramapublic.model.validation.SkylineDocSpecLib;

public class ValidatorSkylineDocSpecLib extends SkylineDocSpecLib
{
    private ITargetedMSRun _run;
    private ISpectrumLibrary _library;

    public ValidatorSkylineDocSpecLib() {}

    public ValidatorSkylineDocSpecLib(ISpectrumLibrary library, ITargetedMSRun run)
    {
        _library = library;
        _run = run;
        setSpectrumLibraryId(library.getId());
    }

    public ISpectrumLibrary getLibrary()
    {
        return _library;
    }

    public ITargetedMSRun getRun()
    {
        return _run;
    }
}

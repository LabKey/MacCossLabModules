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
package org.labkey.panoramapublic.pipeline;

import org.apache.logging.log4j.Logger;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.panoramapublic.model.validation.Modification;
import org.labkey.panoramapublic.model.validation.SkylineDocModification;
import org.labkey.panoramapublic.proteomexchange.validator.SkylineDocValidator;
import org.labkey.panoramapublic.proteomexchange.validator.SpecLibValidator;
import org.labkey.panoramapublic.proteomexchange.validator.ValidatorStatus;
import org.labkey.panoramapublic.proteomexchange.validator.DataValidatorListener;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ValidatorListener implements DataValidatorListener
{
    private final Logger _log;
    private final PipelineJob _job;

    public ValidatorListener(PipelineJob job)
    {
        _log = job.getLogger();
        _job = job;
    }

    @Override
    public void started(ValidatorStatus status)
    {
        _job.setStatus("Starting data validation");
        _log.info("Validating data for {} Skyline documents in {} folders", status.getSkylineDocs().size(), status.getSkylineDocs().stream().map(SkylineDocValidator::getRunContainer)
                .filter(Objects::nonNull).distinct().count());
    }

    @Override
    public void validatingDocument(SkylineDocValidator document)
    {
        _job.setStatus("Validating document " + document.getName());
    }

    @Override
    public void sampleFilesValidated(SkylineDocValidator document)
    {
        _log.info("Sample file validation for Skyline document: {}", document.getName());
        if (document.foundAllSampleFiles())
        {
            _log.info("  Found all sample files.");
        }
        else
        {
            _log.info("  MISSING SAMPLE FILES:");
            document.getMissingSampleFileNames().stream().forEach(name -> _log.info("    {}", name));
        }
    }

    @Override
    public void validatingModifications()
    {
        _job.setStatus("Validating modifications");
    }

    @Override
    public void modificationsValidated(ValidatorStatus status)
    {
        _log.info("Modifications validation:");
        if (status.getModifications().isEmpty())
        {
            _log.info("No modifications were found in the submitted Skyline documents.");
        }
        else
        {
            Map<Boolean, List<Modification>> modGroups = status.getModifications().stream().collect(Collectors.partitioningBy(Modification::isValid));
            _log.info("VALID MODIFICATIONS:");
            for (Modification mod : modGroups.get(Boolean.TRUE))
            {
                logModInfo(status, mod);
            }
            _log.info("INVALID MODIFICATIONS (No Unimod ID):");
            for (Modification mod : modGroups.get(Boolean.FALSE))
            {
                logModInfo(status, mod);
            }
        }
    }

    private void logModInfo(ValidatorStatus status, Modification mod)
    {
        _log.info("{}: {}", mod.getId(), mod);
        for (SkylineDocModification docMod: mod.getDocsWithModification())
        {
            SkylineDocValidator doc = status.getSkylineDocForId(docMod.getSkylineDocValidationId());
            if (doc != null)
            {
                _log.info("    {}", doc.getName());
            }
        }
    }

    @Override
    public void validatingSpectralLibraries()
    {
        _job.setStatus("Validating spectral libraries");
    }

    @Override
    public void validatingSpectralLibrary(SpecLibValidator specLib)
    {
        String msg = "Validating spectral library: " + specLib.getFileName();
        _job.setStatus(msg);
        _log.info(msg);
    }

    @Override
    public void spectralLibraryValidated(SpecLibValidator specLib)
    {
        _log.info(specLib.toString());
        if (specLib.hasMissingSpectrumFiles() || specLib.hasMissingIdFiles())
        {
            _log.info("  MISSING FILES:");
            for (String name : specLib.getMissingSpectrumFileNames())
            {
                _log.info("    Spectrum File: {}", name);
            }
            for (String name : specLib.getMissingIdFileNames())
            {
                _log.info("    Peptide Id File: {}", name);
            }
        }
    }

    @Override
    public void spectralLibrariesValidated(ValidatorStatus status)
    {
        if (status.getSpectralLibraries().isEmpty())
        {
            _log.info("Skyline documents in the experiment do not contain any spectral libraries.");
        }
        _log.info("Spectral library validation complete.");
    }

    @Override
    public void error(String message)
    {
        _log.error(message);
    }
}

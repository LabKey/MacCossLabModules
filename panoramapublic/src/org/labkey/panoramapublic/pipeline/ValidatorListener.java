package org.labkey.panoramapublic.pipeline;

import org.apache.logging.log4j.Logger;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.panoramapublic.model.validation.SkylineDocValidation;
import org.labkey.panoramapublic.model.validation.Modification;
import org.labkey.panoramapublic.model.validation.SkylineDocModification;
import org.labkey.panoramapublic.proteomexchange.validator.SkylineDocValidator;
import org.labkey.panoramapublic.proteomexchange.validator.SpecLibValidator;
import org.labkey.panoramapublic.proteomexchange.validator.ValidatorStatus;
import org.labkey.panoramapublic.proteomexchange.validator.DataValidatorListener;

import java.util.List;
import java.util.Map;
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
        _log.info(String.format("Validating data for %d Skyline documents in %d folders", status.getSkylineDocs().size(),
                status.getSkylineDocs().stream().map(SkylineDocValidation::getContainer).distinct().count()));
    }

    @Override
    public void validatingDocument(SkylineDocValidator document)
    {
        _job.setStatus("Validating document " + document.getName());
    }

    @Override
    public void sampleFilesValidated(SkylineDocValidator document, ValidatorStatus status)
    {
        _log.info("Sample file validation for Skyline document: " + document.getName());
        if (document.foundAllSampleFiles())
        {
            _log.info("  Found all sample files.");
        }
        else
        {
            _log.info("  MISSING SAMPLE FILES:");
            document.getMissingSampleFileNames().stream().forEach(name -> _log.info("    " + name));
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
        if (status.getModifications().size() == 0)
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
        _log.info(mod.getId() + ": " + mod);
        for (SkylineDocModification docMod: mod.getDocsWithModification())
        {
            SkylineDocValidator doc = status.getSkylineDocForId(docMod.getSkylineDocValidationId());
            if (doc != null)
            {
                _log.info("    " + doc.getName());
            }
        }
    }

    @Override
    public void validatingSpectralLibraries()
    {
        _job.setStatus("Validating spectral libraries");
    }

    @Override
    public void spectralLibrariesValidated(ValidatorStatus status)
    {
        _log.info("Spectral library validation:");
        if (status.getSpectralLibraries().size() == 0)
        {
            _log.info("No spectral libraries were found in the submitted Skyline documents.");
        }
        else
        {
            for (SpecLibValidator specLib : status.getSpectralLibraries())
            {
                _log.info(specLib.toString());
                if (specLib.hasMissingSpectrumFiles() || specLib.hasMissingIdFiles())
                {
                    _log.info("  MISSING FILES:");
                    for (String name : specLib.getMissingSpectrumFileNames())
                    {
                        _log.info("    Spectrum File: " + name);
                    }
                    for (String name : specLib.getMissingIdFileNames())
                    {
                        _log.info("    Peptide Id File: " + name);
                    }
                }
            }
        }
    }

    @Override
    public void error(String message)
    {
        _log.error(message);
    }
}

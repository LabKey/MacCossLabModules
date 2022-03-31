package org.labkey.panoramapublic.proteomexchange.validator;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.DbScope;
import org.labkey.api.files.FileContentService;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.ISpectrumLibrary;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.UnexpectedException;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.validation.DataValidation;
import org.labkey.panoramapublic.model.validation.Modification;
import org.labkey.panoramapublic.model.validation.Modification.ModType;
import org.labkey.panoramapublic.model.validation.SkylineDocModification;
import org.labkey.panoramapublic.proteomexchange.ExperimentModificationGetter;
import org.labkey.panoramapublic.proteomexchange.validator.SpecLibValidator.SpecLibKeyWithSize;
import org.labkey.panoramapublic.query.DataValidationManager;
import org.labkey.panoramapublic.query.ExperimentAnnotationsManager;
import org.labkey.panoramapublic.query.ModificationInfoManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DataValidator
{
    private final ExperimentAnnotations _expAnnotations;
    private final DataValidation _validation;
    private final DataValidatorListener _listener;

    public DataValidator(@NotNull ExperimentAnnotations expAnnotations, @NotNull DataValidation validation, @NotNull DataValidatorListener listener)
    {
        _expAnnotations = expAnnotations;
        _validation = validation;
        _listener = listener;
    }

    public ValidatorStatus validateExperiment(User user)
    {
            ValidatorStatus status = initValidationStatus(_validation, user);
            _listener.started(status);

            TargetedMSService svc = TargetedMSService.get();
            validate(status, svc, user);

            return status;
    }

    private void validate(ValidatorStatus status, TargetedMSService svc, User user)
    {
        validateSampleFiles(status, svc, user);
        validateModifications(status, user);
        validateLibraries(status, user);
        status.getValidation().setStatus(status.getPxStatus());
        DataValidationManager.updateValidationStatus(status.getValidation(), user);
    }

    private void validateLibraries(ValidatorStatus status, User user)
    {
        _listener.validatingSpectralLibraries();
        // sleep();
        FileContentService fcs = FileContentService.get();
        for (SpecLibValidator specLib: status.getSpectralLibraries())
        {
            try (DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
            {
                specLib.setValidationId(status.getValidation().getId());
                DataValidationManager.saveSpectrumLibrary(specLib, user);

                List<String> errors = specLib.validate(fcs, _expAnnotations);
                if (!errors.isEmpty())
                {
                    _listener.error("There were unexpected errors in validating the library " + specLib.getFileName());
                    errors.forEach(_listener::error);
                }

                specLib.getDocsWithLibrary().forEach(docLib -> saveSkylineDocSpecLib(docLib, specLib, user));
                specLib.getSpectrumFiles().forEach(s -> DataValidationManager.saveSpecLibSourceFile(s, user));
                specLib.getIdFiles().forEach(s -> DataValidationManager.saveSpecLibSourceFile(s, user));

                transaction.commit();
            }
        }

        _listener.spectralLibrariesValidated(status);
    }

    private void saveSkylineDocSpecLib(ValidatorSkylineDocSpecLib docLib, SpecLibValidator specLib, User user)
    {
        docLib.setSpeclibValidationId(specLib.getId());
        DataValidationManager.saveDocSpectrumLibrary(docLib, user);
    }

    private void validateModifications(ValidatorStatus status, User user)
    {
        _listener.validatingModifications();
        // sleep();
        try (DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
        {
            List<ExperimentModificationGetter.PxModification> mods = ExperimentModificationGetter.getModifications(_expAnnotations,
                    false); // Do not look up Unimod to find a match if the modification does not have a Unimod Id in the Skyline document.
            for (ExperimentModificationGetter.PxModification pxMod : mods)
            {
                Modification mod = new Modification(pxMod.getSkylineName(), pxMod.getDbModId(),
                        pxMod.getUnimodIdInt(),
                        pxMod.isMatchInferred(),
                        pxMod.getName(),
                        pxMod.isIsotopicMod() ? ModType.Isotopic : ModType.Structural);
                mod.setValidationId(status.getValidation().getId());
                if (mod.getUnimodId() == null)
                {
                    var modInfo = ModType.Isotopic == mod.getModType() ? ModificationInfoManager.getIsotopeModInfo(mod.getDbModId(), _expAnnotations.getId())
                            : ModificationInfoManager.getStructuralModInfo(mod.getDbModId(), _expAnnotations.getId());
                    if (modInfo != null)
                    {
                        mod.setModInfoId(modInfo.getId());
                        mod.setInferred(true);
                    }
                }

                DataValidationManager.saveModification(mod, user);
                status.addModification(mod);

                Set<Long> runIdsWithMod = pxMod.getRunIds();
                List<SkylineDocModification> docModifications = new ArrayList<>();
                for (Long runId: runIdsWithMod)
                {
                    SkylineDocValidator doc = status.getSkylineDocForRunId(runId);
                    if (doc != null)
                    {
                        docModifications.add(new SkylineDocModification(doc.getId(), mod.getId()));
                    }
                }
                mod.setDocsWithModification(docModifications);
                DataValidationManager.saveSkylineDocModifications(mod, user);
            }

            transaction.commit();
        }
        _listener.modificationsValidated(status);
    }

    private void validateSampleFiles(ValidatorStatus status, TargetedMSService svc, User user)
    {
        for (SkylineDocValidator skyDoc: status.getSkylineDocs())
        {
            _listener.validatingDocument(skyDoc);
            // sleep();
            skyDoc.validateSampleFiles(svc);
            try (DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
            {
                DataValidationManager.updateSampleFileStatus(skyDoc, user);
                transaction.commit();
            }

            _listener.sampleFilesValidated(skyDoc, status);
        }
    }

    private ValidatorStatus initValidationStatus(DataValidation validation, User user)
    {
        try (DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
        {
            ValidatorStatus status = new ValidatorStatus(validation);
            TargetedMSService targetedMsSvc = TargetedMSService.get();
            addSkylineDocs(status, targetedMsSvc);
            DataValidationManager.saveSkylineDocStatus(status, user);
            addSpectralLibraries(status, targetedMsSvc);
            transaction.commit();
            return status;
        }
    }

    private void addSkylineDocs(ValidatorStatus status, TargetedMSService targetedMsSvc)
    {
        // Get a list of Skyline documents associated with this experiment
        List<ITargetedMSRun> runs = ExperimentAnnotationsManager.getTargetedMSRuns(_expAnnotations);

        for(ITargetedMSRun run: runs)
        {
            SkylineDocValidator skyDoc = new SkylineDocValidator(run);
            skyDoc.setName(run.getFileName());
            skyDoc.setRunId(run.getId());
            skyDoc.setContainer(run.getContainer());
            status.addSkylineDoc(skyDoc);

            skyDoc.addSampleFiles(targetedMsSvc);
        }
    }

    private void addSpectralLibraries(ValidatorStatus status, TargetedMSService targetedMsSvc)
    {
        // A library can be used with more than one Skyline document.  Add it only once.
        Map<SpecLibKeyWithSize, SpecLibValidator> spectralLibraries = new HashMap<>();

        for (SkylineDocValidator doc: status.getSkylineDocs())
        {
            List<? extends ISpectrumLibrary> allSpecLibs = targetedMsSvc.getLibraries(doc.getRun());
            for (ISpectrumLibrary lib: allSpecLibs)
            {
                SpecLibValidator sLib = getSpectralLibrary(targetedMsSvc, doc, lib);
                spectralLibraries.putIfAbsent(sLib.getKey(), sLib);
                spectralLibraries.get(sLib.getKey()).addDocumentLibrary(doc, lib);
            }
        }
        spectralLibraries.values().forEach(status::addLibrary);
    }

    private SpecLibValidator getSpectralLibrary(TargetedMSService targetedMsSvc, SkylineDocValidator doc, ISpectrumLibrary lib)
    {
        Path libPath = targetedMsSvc.getLibraryFilePath(doc.getRun(), lib);
        Long librarySize = null;
        if(libPath != null && Files.exists(libPath))
        {
            try
            {
                librarySize = Files.size(libPath);
            }
            catch (IOException e)
            {
                throw UnexpectedException.wrap(e, "Error getting size of the library file '" + libPath + "'.");
            }
        }
        return new SpecLibValidator(lib, librarySize);
    }

//    private void sleep()
//    {
//        try
//        {
//            Thread.sleep(1000);
//        }
//        catch (InterruptedException e)
//        {
//            e.printStackTrace();
//        }
//    }
}

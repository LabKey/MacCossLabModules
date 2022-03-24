package org.labkey.panoramapublic.query;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.provider.FileSystemAuditProvider;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.util.Pair;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.PanoramaPublicSchema;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.validation.DataValidation;
import org.labkey.panoramapublic.model.validation.Modification;
import org.labkey.panoramapublic.model.validation.PxStatus;
import org.labkey.panoramapublic.model.validation.SkylineDoc;
import org.labkey.panoramapublic.model.validation.SkylineDocModification;
import org.labkey.panoramapublic.model.validation.SkylineDocSampleFile;
import org.labkey.panoramapublic.model.validation.SkylineDocSpecLib;
import org.labkey.panoramapublic.proteomexchange.validator.SpecLibValidator;
import org.labkey.panoramapublic.proteomexchange.validator.ValidatorSampleFile;
import org.labkey.panoramapublic.proteomexchange.validator.SkylineDocValidator;
import org.labkey.panoramapublic.model.validation.SpecLib;
import org.labkey.panoramapublic.model.validation.SpecLibSourceFile;
import org.labkey.panoramapublic.model.validation.Status;
import org.labkey.panoramapublic.proteomexchange.validator.ValidatorStatus;
import org.labkey.panoramapublic.proteomexchange.PsiInstrumentParser;
import org.labkey.panoramapublic.proteomexchange.PxException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.panoramapublic.model.validation.SpecLibSourceFile.LibrarySourceFileType.PEPTIDE_ID;
import static org.labkey.panoramapublic.model.validation.SpecLibSourceFile.LibrarySourceFileType.SPECTRUM;

public class DataValidationManager
{
    private static final Logger log = LogHelper.getLogger(DataValidationManager.class, "Data validation for ProteomeXchange");

    public static long getValidationJobCount(int experimentId)
    {
        return new TableSelector(PanoramaPublicManager.getTableInfoDataValidation(),
                new SimpleFilter(FieldKey.fromParts("experimentAnnotationsId"), experimentId), null).getRowCount();
    }

    public static @Nullable DataValidation getLatestValidation(int experimentAnnotationsId, Container container)
    {
        var expAnnotations = ExperimentAnnotationsManager.get(experimentAnnotationsId, container);
        if (expAnnotations != null)
        {
            var filter = new SimpleFilter(FieldKey.fromParts("experimentAnnotationsId"), experimentAnnotationsId);
            var sort = new Sort();
            sort.appendSortColumn(FieldKey.fromParts("id"), Sort.SortDirection.DESC, false);
            return new TableSelector(PanoramaPublicManager.getTableInfoDataValidation(), filter, sort)
                    .setMaxRows(1)
                    .getObject(DataValidation.class);
        }
        return null;
    }

    public static @Nullable DataValidation getValidation(int validationId, Container container)
    {
        var expAnnotations = ExperimentAnnotationsManager.getExperimentInContainer(container);
        if (expAnnotations != null)
        {
            DataValidation validation = new TableSelector(PanoramaPublicManager.getTableInfoDataValidation()).getObject(validationId, DataValidation.class);
            return validation != null && validation.getExperimentAnnotationsId() == expAnnotations.getId() ? validation : null;
        }
        return null;
    }

    public static @NotNull List<DataValidation> getValidations(int experimentAnnotationsId, Container container)
    {
        var expAnnotations = ExperimentAnnotationsManager.get(experimentAnnotationsId, container);
        if (expAnnotations != null)
        {
            var filter = new SimpleFilter(FieldKey.fromParts("experimentAnnotationsId"), experimentAnnotationsId);
            return new TableSelector(PanoramaPublicManager.getTableInfoDataValidation(), filter, null).getArrayList(DataValidation.class);
        }
        return Collections.emptyList();
    }

    public static boolean isValidationOutdated(@NotNull DataValidation validation, @NotNull ExperimentAnnotations expAnnotations, User user)
    {
        List<Long> validatedRunIds = getRunIdsForValidation(validation.getId());
        List<Long> runsInExperiment = ExperimentAnnotationsManager.getTargetedMSRuns(expAnnotations).stream()
                .map(ITargetedMSRun::getId).collect(Collectors.toList());
        Collections.sort(validatedRunIds);
        Collections.sort(runsInExperiment);
        if (!validatedRunIds.equals(runsInExperiment))
        {
            return true;
        }

        var containers = expAnnotations.isIncludeSubfolders() ? ContainerManager.getAllChildren(expAnnotations.getContainer())
                : Set.of(expAnnotations.getContainer());
        for (Container container: containers)
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(FieldKey.fromParts("Created"), validation.getCreated(), CompareType.GT);
            filter.addCondition(FieldKey.fromParts("Container"), container.getId());
            if (AuditLogService.get().getAuditEvents(container, user, FileSystemAuditProvider.EVENT_TYPE, filter, null).size() > 0)
            {
                return true;
            }
        }
        return false;
    }

    public static boolean isLatestValidation(@NotNull DataValidation validation, @NotNull Container container)
    {
        var latestValidation = DataValidationManager.getLatestValidation(validation.getExperimentAnnotationsId(), container);
        return latestValidation != null && latestValidation.getId() == validation.getId();
    }

    public static boolean isPipelineJobRunning(DataValidation validation)
    {
        return isRunningStatus(getPipelineJobStatus(validation));
    }

    public static PipelineStatusFile getPipelineJobStatus(@NotNull DataValidation validation)
    {
        int jobId = validation.getJobId();
        return PipelineService.get().getStatusFile(jobId);
    }

    public static boolean isRunningStatus(PipelineStatusFile status)
    {
        return status.isActive()
                && !PipelineJob.TaskStatus.cancelling.matches(status.getStatus());
    }

    public static @Nullable Status getStatusForJobId(int jobId, Container container)
    {
        var expAnnotations = ExperimentAnnotationsManager.getExperimentInContainer(container);
        if (expAnnotations != null)
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("JobId"), jobId);
            filter.addCondition(FieldKey.fromParts("experimentAnnotationsId"), expAnnotations.getId());
            DataValidation validation = new TableSelector(PanoramaPublicManager.getTableInfoDataValidation(), filter, null).getObject(DataValidation.class);
            return validation != null ? populateStatus(validation) : null;
        }
        return null;
    }

    public static @Nullable Status getStatus(int validationId, Container container)
    {
        DataValidation validation = getValidation(validationId, container);
        return validation != null ? populateStatus(validation) : null;
    }

    public static @NotNull Status getStatus(@NotNull DataValidation validation)
    {
        return populateStatus(validation);
    }

    private static List<Long> getRunIdsForValidation(int validationId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("validationId"), validationId);
        return new TableSelector(PanoramaPublicManager.getTableInfoSkylineDocValidation(),
                Set.of("runId"), filter, null).getArrayList(Long.class);
    }

    @NotNull
    private static Status populateStatus(DataValidation validation)
    {
        Status status = new Status();
        status.setValidation(validation);
        SimpleFilter validationIdFilter = new SimpleFilter(FieldKey.fromParts("ValidationId"), validation.getId());
        status.setSkylineDocs(getSkylineDocs(validationIdFilter));
        status.setModifications(getModifications(validationIdFilter));
        status.setSpecLibs(getSpectrumLibraries(validationIdFilter));
        ExperimentAnnotations experimentAnnotations = ExperimentAnnotationsManager.get(validation.getExperimentAnnotationsId());
        if (experimentAnnotations != null)
        {
            status.setMissingMetadata(getMissingExperimentMetadataFields(experimentAnnotations, true));
            if (status.hasMissingMetadata() && validation.isComplete())
            {
                validation.setStatus(PxStatus.NotValid);
            }
        }
        return status;
    }

    private static List<SkylineDoc> getSkylineDocs(SimpleFilter filter)
    {
        List<SkylineDoc> docs = new TableSelector(PanoramaPublicManager.getTableInfoSkylineDocValidation(), filter, null).getArrayList(SkylineDoc.class);
        for (SkylineDoc doc: docs)
        {
            SimpleFilter skyDocFilter = new SimpleFilter(FieldKey.fromParts("SkylineDocValidationId"), doc.getId());
            doc.setSampleFiles(getSkylineDocSampleFiles(skyDocFilter));
        }
        return docs;
    }

    private static List<SkylineDocSampleFile> getSkylineDocSampleFiles(SimpleFilter filter)
    {
        return new TableSelector(PanoramaPublicManager.getTableInfoSkylineDocSampleFile(), filter, null).getArrayList(SkylineDocSampleFile.class);
    }

    private static List<Modification> getModifications(SimpleFilter filter)
    {
        List<Modification> modifications =  new TableSelector(PanoramaPublicManager.getTableInfoModificationValidation(), filter, null).getArrayList(Modification.class);
        for (Modification mod: modifications)
        {
            mod.setDocsWithModification(getSkylineDocModifications(mod));
        }
        return modifications;
    }

    private static List<SkylineDocModification> getSkylineDocModifications(Modification modification)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ModificationValidationId"), modification.getId());
        return new TableSelector(PanoramaPublicManager.getTableInfoSkylineDocModification(), filter, null).getArrayList(SkylineDocModification.class);
    }

    private static List<SpecLib> getSpectrumLibraries(SimpleFilter filter)
    {
        List<SpecLib> specLibs = new TableSelector(PanoramaPublicManager.getTableInfoSpecLibValidation(), filter, null).getArrayList(SpecLib.class);
        for (SpecLib specLib: specLibs)
        {
            specLib.setSpectrumFiles(getSpectrumSourceFiles(specLib));
            specLib.setIdFiles(getIdSourceFiles(specLib));
            specLib.setDocsWithLibrary(getSkylineDocSpecLibs(specLib));
        }
        return specLibs;
    }

    private static List<SpecLibSourceFile> getSpectrumSourceFiles(SpecLib specLib)
    {
        return getSourceFiles(specLib, SPECTRUM);
    }

    private static List<SpecLibSourceFile> getIdSourceFiles(SpecLib specLib)
    {
        return getSourceFiles(specLib, PEPTIDE_ID);
    }

    private static List<SpecLibSourceFile> getSourceFiles(SpecLib specLib, SpecLibSourceFile.LibrarySourceFileType type)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("SpecLibValidationId"), specLib.getId());
        filter.addCondition(FieldKey.fromParts("SourceType"), type.ordinal());
        return new TableSelector(PanoramaPublicManager.getTableInfoSpecLibSourceFile(), filter, null).getArrayList(SpecLibSourceFile.class);
    }

    private static List<SkylineDocSpecLib> getSkylineDocSpecLibs(SpecLib specLib)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("SpecLibValidationId"), specLib.getId());
        return new TableSelector(PanoramaPublicManager.getTableInfoSkylineDocSpecLib(), filter, null).getArrayList(SkylineDocSpecLib.class);
    }

    public static @Nullable PxStatus getPxStatusForValidationId(int validationId)
    {
        DataValidation validation = new TableSelector(PanoramaPublicManager.getTableInfoDataValidation()).getObject(validationId, DataValidation.class);
        return validation != null ? validation.getStatus() : null;
    }

    public static DataValidation saveDataValidation(DataValidation validation, User user)
    {
        return Table.insert(user, PanoramaPublicManager.getTableInfoDataValidation(), validation);
    }

    public static DataValidation updateDataValidation(DataValidation validation, User user)
    {
        return Table.update(user, PanoramaPublicManager.getTableInfoDataValidation(), validation, validation.getId());
    }

    public static void saveSkylineDocStatus(ValidatorStatus status, User user)
    {
        DataValidation validation = status.getValidation();
        for (SkylineDocValidator doc : status.getSkylineDocs())
        {
            doc.setValidationId(validation.getId());
            doc = Table.insert(user, PanoramaPublicManager.getTableInfoSkylineDocValidation(), doc);

            for (SkylineDocSampleFile sampleFile: doc.getSampleFiles())
            {
               sampleFile.setSkylineDocValidationId(doc.getId());
               Table.insert(user, PanoramaPublicManager.getTableInfoSkylineDocSampleFile(), sampleFile);
            }
        }
    }

    public static void updateSampleFileStatus(SkylineDocValidator skyDoc, User user)
    {
        for (ValidatorSampleFile sampleFile: skyDoc.getSampleFiles())
        {
            Table.update(user, PanoramaPublicManager.getTableInfoSkylineDocSampleFile(), sampleFile, sampleFile.getId());
        }
    }

    public static void saveModification(Modification modification, User user)
    {
        Table.insert(user, PanoramaPublicManager.getTableInfoModificationValidation(), modification);
    }

    public static void saveSkylineDocModifications(Modification modification, User user)
    {
        for (SkylineDocModification skylineDocModification: modification.getDocsWithModification())
        {
            Table.insert(user, PanoramaPublicManager.getTableInfoSkylineDocModification(), skylineDocModification);
        }
    }

    public static void saveSpectrumLibrary(SpecLibValidator specLib, User user)
    {
        Table.insert(user, PanoramaPublicManager.getTableInfoSpecLibValidation(), specLib);
    }

    public static void saveSpecLibSourceFile(SpecLibSourceFile sourceFile, User user)
    {
        Table.insert(user, PanoramaPublicManager.getTableInfoSpecLibSourceFile(), sourceFile);
    }

    public static void saveDocSpectrumLibrary(SkylineDocSpecLib docSpecLib, User user)
    {
        Table.insert(user, PanoramaPublicManager.getTableInfoSkylineDocSpecLib(), docSpecLib);
    }

    public static void updateValidationStatus(DataValidation validation, User user)
    {
        Table.update(user, PanoramaPublicManager.getTableInfoDataValidation(), validation, validation.getId());
    }

    public static void deleteValidations(int experimentAnnotationsId, Container container)
    {
        var validationList = getValidations(experimentAnnotationsId, container);
        try (DbScope.Transaction transaction = PanoramaPublicSchema.getSchema().getScope().ensureTransaction())
        {
            for (var validation: validationList)
            {
                deleteValidation(validation);
            }
            transaction.commit();
        }
    }

    public static void deleteValidation(int validationId, Container container)
    {
        deleteValidation(getValidation(validationId, container));
    }

    public static void deleteValidation(DataValidation validation)
    {
        if (validation != null)
        {
            try (DbScope.Transaction transaction = PanoramaPublicSchema.getSchema().getScope().ensureTransaction())
            {
                clearValidation(validation);
                Table.delete(PanoramaPublicManager.getTableInfoDataValidation(), validation.getId());
                transaction.commit();
            }
        }
    }

    public static void clearValidation(DataValidation validation)
    {
        try(DbScope.Transaction transaction = PanoramaPublicSchema.getSchema().getScope().ensureTransaction())
        {
            deleteModificationValidation(validation.getId());
            deleteSpecLibValidation(validation.getId());
            deleteSkyDocValidation(validation.getId());
            transaction.commit();
        }
    }

    private static void deleteSkyDocValidation(int validationId)
    {
        var filter = new SimpleFilter(FieldKey.fromParts("validationId"), validationId);
        var skyDocValidationIds = new TableSelector(PanoramaPublicManager.getTableInfoSkylineDocValidation(),
                Set.of("Id"), filter, null).getArrayList(Integer.class);

        var skyDocValidationIdsFilter = new SimpleFilter().addInClause(FieldKey.fromParts("SkylineDocValidationId"), skyDocValidationIds);
        Table.delete(PanoramaPublicManager.getTableInfoSkylineDocSampleFile(), skyDocValidationIdsFilter);
        Table.delete(PanoramaPublicManager.getTableInfoSkylineDocValidation(), filter);
    }

    private static void deleteModificationValidation(int validationId)
    {
        var filter = new SimpleFilter(FieldKey.fromParts("validationId"), validationId);
        var modValidationIds = new TableSelector(PanoramaPublicManager.getTableInfoModificationValidation(),
                Set.of("Id"), filter, null).getArrayList(Integer.class);

        var modValidationIdsFilter = new SimpleFilter().addInClause(FieldKey.fromParts("ModificationValidationId"), modValidationIds);
        Table.delete(PanoramaPublicManager.getTableInfoSkylineDocModification(), modValidationIdsFilter);
        Table.delete(PanoramaPublicManager.getTableInfoModificationValidation(), filter);
    }

    private static void deleteSpecLibValidation(int validationId)
    {
        var filter = new SimpleFilter(FieldKey.fromParts("validationId"), validationId);
        var specLibIds = new TableSelector(PanoramaPublicManager.getTableInfoSpecLibValidation(),
                Set.of("Id"), filter, null).getArrayList(Integer.class);

        var specLibIdFilter = new SimpleFilter().addInClause(FieldKey.fromParts("SpecLibValidationId"), specLibIds);
        Table.delete(PanoramaPublicManager.getTableInfoSpecLibSourceFile(), specLibIdFilter);
        Table.delete(PanoramaPublicManager.getTableInfoSkylineDocSpecLib(), specLibIdFilter);
        Table.delete(PanoramaPublicManager.getTableInfoSpecLibValidation(), filter);
    }

    public static final int MIN_ABSTRACT_LENGTH = 50;
    public static final int MIN_TITLE_LENGTH = 30;

    public static List<String> getMissingExperimentMetadataFields(ExperimentAnnotations expAnnot)
    {
        return getMissingExperimentMetadataFields(expAnnot, true).getMessages();
    }

    public static @NotNull MissingMetadata getMissingExperimentMetadataFields(ExperimentAnnotations expAnnot, boolean validateForPx)
    {
        MissingMetadata errors = new MissingMetadata();
        if(StringUtils.isBlank(expAnnot.getTitle()))
        {
            errors.add("Title is required.");
        }
        else if(StringUtils.trim(expAnnot.getTitle()).length() < MIN_TITLE_LENGTH)
        {
            errors.add("Title should be at least " + MIN_TITLE_LENGTH + " characters.");
        }

        if (StringUtils.isBlank(expAnnot.getOrganism()))
        {
            if (validateForPx) errors.addOptional("Organism is required.");
        }
        else
        {
            validateOrganisms(expAnnot, errors);
        }

        if(StringUtils.isBlank(expAnnot.getInstrument()))
        {
            if (validateForPx) errors.addOptional("Instrument is required.");
        }
        else
        {
            validateInstruments(expAnnot, errors);
        }

        if(StringUtils.isBlank(expAnnot.getKeywords()))
        {
            errors.add("Keywords are required.");
        }
        if(expAnnot.getSubmitter() == null)
        {
            errors.add("Submitter is required.");
        }
        if (expAnnot.getSubmitterAffiliation() == null && validateForPx)
        {
            errors.addOptional("Submitter affiliation is required.");
        }
        if (expAnnot.getLabHead() != null && StringUtils.isBlank(expAnnot.getLabHeadAffiliation()) && validateForPx)
        {
            errors.addOptional("Lab Head affiliation is required.");
        }
        if(StringUtils.isBlank(expAnnot.getAbstract()))
        {
            errors.add("Abstract is required.");
        }
        else if(expAnnot.getAbstract().length() < MIN_ABSTRACT_LENGTH)
        {
            errors.add("Abstract should be at least " + MIN_ABSTRACT_LENGTH + " characters.");
        }

        return errors;
    }

    private static void validateOrganisms(ExperimentAnnotations expAnnot, MissingMetadata errors)
    {
        Map<String, Integer> organisms = expAnnot.getOrganismAndTaxId();

        Set<String> notFound = new HashSet<>();
        for(String orgName: organisms.keySet())
        {
            if(organisms.get(orgName) == null)
            {
                notFound.add(orgName);
            }
        }
        if(notFound.size() > 0)
        {
            StringBuilder err = new StringBuilder("No taxonomy ID found for organism");
            err.append(notFound.size() > 1 ? "s: " : ": ");
            err.append(StringUtils.join(notFound, ','));
            errors.add(err.toString());
        }
    }

    private static void validateInstruments(ExperimentAnnotations expAnnot, MissingMetadata errors)
    {
        List<String> instruments = expAnnot.getInstruments();
        PsiInstrumentParser parser = new PsiInstrumentParser();
        Set<String> notFound = new HashSet<>();
        for(String instrumentName: instruments)
        {
            PsiInstrumentParser.PsiInstrument instrument = null;
            try
            {
                instrument = parser.getInstrument(instrumentName);
            }
            catch (PxException e)
            {
                errors.add("Error reading psi-ms file for validating instruments. " + e.getMessage());
                log.error("Error reading psi-ms file for validating instruments in container " + expAnnot.getContainer(), e);
            }

            if(instrument == null)
            {
                notFound.add(instrumentName);
            }
        }
        if(notFound.size() > 0)
        {
            StringBuilder err = new StringBuilder("Unrecognized instrument");
            err.append(notFound.size() > 1 ? "s: " : ": ");
            err.append(StringUtils.join(notFound, ','));
            errors.add(err.toString());
        }
    }

    public static class MissingMetadata
    {
        private final List<Pair<String, Boolean>> _missingFields;

        public MissingMetadata()
        {
            _missingFields = new ArrayList<>();
        }

        public int count()
        {
            return _missingFields.size();
        }

        public void addOptional(String message)
        {
            _missingFields.add(Pair.of(message, Boolean.FALSE));
        }

        public void add(String message)
        {
            _missingFields.add(Pair.of(message, Boolean.TRUE));
        }

        public List<String> getMessages()
        {
            return _missingFields.stream().map(Pair::getKey).collect(Collectors.toList());
        }

        public boolean hasAlwaysRequiredFields()
        {
            return _missingFields.stream().anyMatch(Pair::getValue);
        }
    }
}
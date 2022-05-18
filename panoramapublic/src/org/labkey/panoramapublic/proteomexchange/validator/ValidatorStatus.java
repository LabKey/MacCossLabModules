package org.labkey.panoramapublic.proteomexchange.validator;

import org.jetbrains.annotations.NotNull;
import org.labkey.panoramapublic.model.validation.DataValidation;
import org.labkey.panoramapublic.model.validation.GenericValidationStatus;
import org.labkey.panoramapublic.model.validation.Modification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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

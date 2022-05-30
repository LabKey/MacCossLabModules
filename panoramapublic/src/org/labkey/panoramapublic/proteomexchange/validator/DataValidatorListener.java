package org.labkey.panoramapublic.proteomexchange.validator;

public interface DataValidatorListener
{
    void started(ValidatorStatus status);
    void validatingDocument(SkylineDocValidator document);
    void sampleFilesValidated(SkylineDocValidator document);
    void validatingModifications();
    void modificationsValidated(ValidatorStatus status);
    void validatingSpectralLibraries();
    void validatingSpectralLibrary(SpecLibValidator specLib);
    void spectralLibraryValidated(SpecLibValidator specLib);
    void spectralLibrariesValidated(ValidatorStatus status);
    void error(String message);
}

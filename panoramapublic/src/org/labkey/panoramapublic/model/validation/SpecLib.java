package org.labkey.panoramapublic.model.validation;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.panoramapublic.model.speclib.SpecLibInfo;
import org.labkey.panoramapublic.query.DataValidationManager;

import java.util.Collections;
import java.util.List;

// For table panoramapublic.speclibvalidation
public class SpecLib extends SpecLibValidation<SkylineDocSpecLib>
{
    private List<SkylineDocSpecLib> _docsWithLibrary;
    private SpecLibInfo _specLibInfo;

    public SpecLib() {}

    @Override
    public @NotNull List<SkylineDocSpecLib> getDocsWithLibrary()
    {
        return _docsWithLibrary != null ? Collections.unmodifiableList(_docsWithLibrary) : Collections.emptyList();
    }

    public void setDocsWithLibrary(@NotNull List<SkylineDocSpecLib> docsWithLibrary)
    {
        _docsWithLibrary = docsWithLibrary;
    }

    public void setSpecLibInfo(SpecLibInfo specLibInfo)
    {
        _specLibInfo = specLibInfo;
    }

    @Override
    public SpecLibInfo getSpecLibInfo()
    {
        return _specLibInfo;
    }

    @NotNull
    public JSONObject toJSON(Container expContainer)
    {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", getId());
        jsonObject.put("libName", getLibName());
        jsonObject.put("fileName", getFileName());
        jsonObject.put("libType", getLibType());
        jsonObject.put("size", getSize() != null ? FileUtils.byteCountToDisplaySize(getSize()) : "-");
        boolean isValidWithoutSpecLibInfo = isValidWithoutSpecLibInfo();
        boolean isValidDueToSpecLibInfo = isValidDueToSpecLibInfo();
        boolean isValid = isValidDueToSpecLibInfo || isValidWithoutSpecLibInfo;
        jsonObject.put("valid", isValid);
        jsonObject.put("validWithoutSpecLibInfo", isValidWithoutSpecLibInfo);
        jsonObject.put("hasMissingSourceFiles", (!isValid() && (hasMissingSpectrumFiles() || hasMissingIdFiles())));
        jsonObject.put("status", getStatusString());
        if (getHelpString() != null)
        {
            jsonObject.put("helpMessage", getHelpString());
        }
        if (getSpecLibInfo() != null)
        {
            if (!isValidWithoutSpecLibInfo && isValidDueToSpecLibInfo)
            {
                SpecLibInfo libInfo = getSpecLibInfo();
                String sep = "";
                String infoString = "";
                if (libInfo.isPublicLibrary())
                {
                    infoString += "Source: " + libInfo.getSourceType().getLabel();
                    sep = ", ";
                }
                if (libInfo.isLibraryNotRelevant())
                {
                    infoString += sep + "Dependency: " + libInfo.getDependencyType().getLabel();
                }
                jsonObject.put("specLibInfo", infoString);
            }
            else
            {
                jsonObject.put("specLibInfo", getSpecLibInfo().getInfoString());
            }
            jsonObject.put("specLibInfoId", getSpecLibInfo().getId());
            String helpString = getLibInfoHelpString();
            if (helpString != null)
            {
                jsonObject.put("libInfoHelpMessage", helpString);
            }
        }
        if (!isPrositLibrary())
        {
            jsonObject.put("spectrumFiles", getSourceFilesJSON(getSpectrumFiles(), expContainer));
            jsonObject.put("idFiles", getSourceFilesJSON(getIdFiles(), expContainer));
        }
        else
        {
            jsonObject.put("prositLibrary", true);
        }
        if (!isValid && getSpecLibInfo() == null)
        {
            List<SkylineDocSpecLib> docLibraries = DataValidationManager.getSkylineDocSpecLibs(this);
            if (docLibraries.size() > 0)
            {
                // Add the database Id of a library used with one of the Skyline documents so that we can display a link to
                // add the "Add Library Information". The same library can be used with multiple documents. A new row is
                // created in the targetedms.spectrumlibrary table for each document.
                jsonObject.put("iSpecLibId", docLibraries.get(0).getSpectrumLibraryId());
            }
        }
        return jsonObject;
    }

    @NotNull
    private JSONArray getSourceFilesJSON(List<SpecLibSourceFile> sourceFiles, Container expContainer)
    {
        JSONArray result = new JSONArray();
        for (SpecLibSourceFile sourceFile: sourceFiles)
        {
            result.put(sourceFile.toJSON(expContainer));
        }
        return result;
    }
}

package org.labkey.panoramapublic.model.validation;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.panoramapublic.query.ModificationInfoManager;
import org.labkey.panoramapublic.query.modification.ExperimentModInfo;

import java.util.Collections;
import java.util.List;

// For table panoramapublic.modificationvalidation
public class Modification
{
    private int _id;
    private int _validationId;
    private String _skylineModName;
    private long _dbModId;
    private Integer _unimodId;
    private String _unimodName;
    private boolean _inferred;
    private ModType _modType;
    private Integer _modInfoId;

    private List<SkylineDocModification> _docsWithModification;

    public enum ModType {Structural, Isotopic}

    public Modification() {}

    public Modification(@NotNull String skylineModName, long dbModId, @Nullable Integer unimodId, boolean inferred, @Nullable String unimodName, @NotNull ModType modType)
    {
        _skylineModName = skylineModName;
        _dbModId = dbModId;
        _unimodId = unimodId;
        _inferred = inferred;
        _unimodName = unimodName;
        _modType = modType;
    }

    public int getId()
    {
        return _id;
    }

    public void setId(int id)
    {
        _id = id;
    }

    public int getValidationId()
    {
        return _validationId;
    }

    public void setValidationId(int validationId)
    {
        _validationId = validationId;
    }

    public String getSkylineModName()
    {
        return _skylineModName;
    }

    public void setSkylineModName(String skylineModName)
    {
        _skylineModName = skylineModName;
    }

    public long getDbModId()
    {
        return _dbModId;
    }

    public void setDbModId(long dbModId)
    {
        _dbModId = dbModId;
    }

    public Integer getUnimodId()
    {
        return _unimodId;
    }

    public void setUnimodId(Integer unimodId)
    {
        _unimodId = unimodId;
    }

    public boolean isInferred()
    {
        return _inferred;
    }

    public void setInferred(boolean inferred)
    {
        _inferred = inferred;
    }

    public String getUnimodName()
    {
        return _unimodName;
    }

    public void setUnimodName(String unimodName)
    {
        _unimodName = unimodName;
    }

    public ModType getModType()
    {
        return _modType;
    }

    public void setModType(ModType modType)
    {
        _modType = modType;
    }

    public Integer getModInfoId()
    {
        return _modInfoId;
    }

    public void setModInfoId(Integer modInfoId)
    {
        _modInfoId = modInfoId;
    }

    public boolean isValid()
    {
        return _unimodId != null || _modInfoId != null;
    }

    public String toString()
    {
        return getNameString() + ": " + getUnimodIdStr();
    }

    public String getUnimodIdStr()
    {
        return isValid() ? "UNIMOD:" + _unimodId : "No UNIMOD Id";
    }

    public String getNameString()
    {
        return !StringUtils.isBlank(_unimodName) ? _unimodName + (!_unimodName.equals(_skylineModName) ? " [ " + _skylineModName + " ]" : "") : _skylineModName;
    }

    public @NotNull List<SkylineDocModification> getDocsWithModification()
    {
        return _docsWithModification != null ? Collections.unmodifiableList(_docsWithModification) : Collections.emptyList();
    }

    public void setDocsWithModification(@NotNull List<SkylineDocModification> docsWithModification)
    {
        _docsWithModification = docsWithModification;
    }

    @NotNull
    public JSONObject toJSON()
    {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", getId());
        jsonObject.put("skylineModInfo", getSkylineModInfo());
        jsonObject.put("unimodId", getUnimodId());
        jsonObject.put("unimodName", getUnimodName());
        jsonObject.put("inferred", isInferred());
        jsonObject.put("valid", isValid());
        jsonObject.put("modType", getModType().name());
        jsonObject.put("dbModId", getDbModId());
        jsonObject.put("modInfoId", getModInfoId());
        if (getModInfoId() != null)
        {
            var modInfo = ModType.Isotopic == getModType() ? ModificationInfoManager.getIsotopeModInfo(getModInfoId()) :
                    ModificationInfoManager.getStructuralModInfo(getModInfoId());
            if (modInfo != null)
            {
                List<ExperimentModInfo.UnimodInfo> unimodIdsAndNames = modInfo.getUnimodInfos();
                if (unimodIdsAndNames.size() > 0)
                {
                    jsonObject.put("unimodMatches", getUnimodMatchesJSON(unimodIdsAndNames));
                }
            }
        }
        return jsonObject;
    }

    private String getSkylineModInfo()
    {
        if (getUnimodId() != null && !isInferred())
        {
            return getSkylineModName();
        }

        return (isInferred() ? "**" : "") + getSkylineModName();
    }

    private JSONArray getUnimodMatchesJSON(List<ExperimentModInfo.UnimodInfo> unimodMatches)
    {
        JSONArray jsonArray = new JSONArray();
        for (ExperimentModInfo.UnimodInfo match: unimodMatches)
        {
            JSONObject unimodMatch = new JSONObject();
            unimodMatch.put("unimodId", match.getUnimodId());
            unimodMatch.put("name", match.getUnimodName());
            jsonArray.put(unimodMatch);
        }
        return jsonArray;
    }
}

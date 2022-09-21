package org.labkey.panoramapublic.model.validation;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.old.JSONArray;
import org.json.old.JSONObject;
import org.labkey.panoramapublic.query.modification.ExperimentModInfo;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

// For table panoramapublic.modificationvalidation
public class Modification
{
    private int _id;
    private int _validationId;
    private String _skylineModName;
    private long _dbModId;
    private Integer _unimodId;
    private String _unimodName;
    private boolean _inferred; // True if a match was calculated during data validation.
    private ModType _modType;
    private Integer _modInfoId;

    private List<SkylineDocModification> _docsWithModification;
    private ExperimentModInfo _modInfo;

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

    /**
     * @return true if a Unimod Match was calculated during the data validation process
     */
    public boolean isInferred()
    {
        return _inferred;
    }

    public void setInferred(boolean inferred)
    {
        _inferred = inferred;
    }

    /**
     * @return true if a match was calculated during the data validation process or a match existed in the mod info tables
     * (ExperimentStructuralModInfo or ExperimentIsotopeModInfo)
     */
    public boolean isMatchAssigned()
    {
        return isInferred() || _modInfo != null;
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
        return (_unimodId != null && _unimodName != null) || _modInfoId != null;
    }

    public String toString()
    {
        return getSkylineModName() + ": " + getUnimodStr();
    }

    public String getUnimodStr()
    {
        return isValid() ? StringUtils.join(getUnimodInfoList().stream()
                .map(m -> getUnimodStr(m.getUnimodId(), m.getUnimodName()))
                .collect(Collectors.toList()), ", ") : "No UNIMOD Id";
    }

    public List<ExperimentModInfo.UnimodInfo> getUnimodInfoList()
    {
        if (_unimodId != null)
        {
           return Collections.singletonList(new ExperimentModInfo.UnimodInfo(_unimodId, _unimodName));
        }
        else if (_modInfo != null)
        {
            return _modInfo.getUnimodInfos();
        }
        return Collections.emptyList();
    }

    public static String getUnimodIdStr(int unimodId)
    {
        return "UNIMOD:" + unimodId;
    }

    private static String getUnimodStr(int unimodId, String unimodName)
    {
        return getUnimodIdStr(unimodId) + " - " + unimodName;
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

    public ExperimentModInfo getModInfo()
    {
        return _modInfo;
    }

    public void setModInfo(ExperimentModInfo modInfo)
    {
        _modInfo = modInfo;
    }

    @NotNull
    public JSONObject toJSON()
    {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", getId());
        jsonObject.put("skylineModName", getSkylineModName());
        jsonObject.put("unimodId", getUnimodId());
        jsonObject.put("unimodName", getUnimodName());
        jsonObject.put("matchAssigned", isMatchAssigned());
        jsonObject.put("valid", isValid());
        jsonObject.put("modType", getModType().name());
        jsonObject.put("dbModId", getDbModId());
        jsonObject.put("modInfoId", getModInfoId());
        if (_modInfo != null)
        {
            List<ExperimentModInfo.UnimodInfo> unimodIdsAndNames = _modInfo.getUnimodInfos();
            if (unimodIdsAndNames.size() > 0)
            {
                jsonObject.put("unimodMatches", getUnimodMatchesJSON(unimodIdsAndNames));
                jsonObject.put("modInfoId", _modInfo.getId());
            }
        }
        return jsonObject;
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

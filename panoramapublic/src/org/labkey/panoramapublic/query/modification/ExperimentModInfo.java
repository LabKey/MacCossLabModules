package org.labkey.panoramapublic.query.modification;

import org.labkey.api.util.Pair;
import org.labkey.panoramapublic.model.DbEntity;

import java.util.Collections;
import java.util.List;

public class ExperimentModInfo extends DbEntity
{
    private int _experimentAnnotationsId;
    private long _modId;
    private int _unimodId;
    private String _unimodName;

    public int getExperimentAnnotationsId()
    {
        return _experimentAnnotationsId;
    }

    public void setExperimentAnnotationsId(int experimentAnnotationsId)
    {
        _experimentAnnotationsId = experimentAnnotationsId;
    }

    public int getUnimodId()
    {
        return _unimodId;
    }

    public void setUnimodId(int unimodId)
    {
        _unimodId = unimodId;
    }

    public String getUnimodName()
    {
        return _unimodName;
    }

    public void setUnimodName(String unimodName)
    {
        _unimodName = unimodName;
    }

    public long getModId()
    {
        return _modId;
    }

    public void setModId(long modId)
    {
        _modId = modId;
    }

    public List<Integer> getUnimodIds()
    {
        return Collections.singletonList(_unimodId);
    }

    public List<Pair<Integer, String>> getUnimodIdsAndNames()
    {
        return Collections.singletonList(new Pair<>(_unimodId, _unimodName));
    }

    public boolean isCombinationMod()
    {
        return false;
    }
}

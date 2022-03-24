package org.labkey.panoramapublic.query.modification;


import org.labkey.api.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExperimentStructuralModInfo extends ExperimentModInfo
{
    private Integer _unimodId2;
    private String _unimodName2;

    public Integer getUnimodId2()
    {
        return _unimodId2;
    }

    public void setUnimodId2(Integer unimodId2)
    {
        _unimodId2 = unimodId2;
    }

    public String getUnimodName2()
    {
        return _unimodName2;
    }

    public void setUnimodName2(String unimodName2)
    {
        _unimodName2 = unimodName2;
    }

    @Override
    public List<Integer> getUnimodIds()
    {
        if (_unimodId2 == null)
        {
            return super.getUnimodIds();
        }
        var list = new ArrayList<>(super.getUnimodIds());
        list.add(_unimodId2);
        return Collections.unmodifiableList(list);
    }

    @Override
    public List<Pair<Integer, String>> getUnimodIdsAndNames()
    {
        if (_unimodId2 == null)
        {
            return super.getUnimodIdsAndNames();
        }
        var list = new ArrayList<>(super.getUnimodIdsAndNames());
        list.add(new Pair<>(_unimodId2, _unimodName2));
        return Collections.unmodifiableList(list);
    }

    @Override
    public boolean isCombinationMod()
    {
        return _unimodId2 != null;
    }
}

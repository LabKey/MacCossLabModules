package org.labkey.panoramapublic.query.modification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ExperimentIsotopeModInfo extends ExperimentModInfo
{
    private List<UnimodInfo> _additionalMatches;

    public void addUnimodInfo(UnimodInfo unimodInfo)
    {
       if (_additionalMatches == null)
       {
           _additionalMatches = new ArrayList<>();
       }
       _additionalMatches.add(unimodInfo);
    }

    @Override
    public List<Integer> getUnimodIds()
    {
        if (_additionalMatches == null || _additionalMatches.isEmpty())
        {
            return super.getUnimodIds();
        }
        var list = new ArrayList<>(super.getUnimodIds());
        list.addAll(_additionalMatches.stream().map(UnimodInfo::getUnimodId).collect(Collectors.toList()));
        return Collections.unmodifiableList(list);
    }

    @Override
    public List<UnimodInfo> getUnimodInfos()
    {
        if (_additionalMatches == null || _additionalMatches.isEmpty())
        {
            return super.getUnimodInfos();
        }
        var list = new ArrayList<>(super.getUnimodInfos());
        list.addAll(_additionalMatches);
        return Collections.unmodifiableList(list);
    }

    public List<UnimodInfo> getAdditionalUnimodInfos()
    {
        return _additionalMatches != null ? Collections.unmodifiableList(_additionalMatches) : Collections.emptyList();
    }
}

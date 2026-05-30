/*
 * Copyright (c) 2022-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

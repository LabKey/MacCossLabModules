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
    public List<UnimodInfo> getUnimodInfos()
    {
        if (_unimodId2 == null)
        {
            return super.getUnimodInfos();
        }
        var list = new ArrayList<>(super.getUnimodInfos());
        list.add(new UnimodInfo(_unimodId2, _unimodName2));
        return Collections.unmodifiableList(list);
    }

    @Override
    public boolean isCombinationMod()
    {
        return _unimodId2 != null;
    }
}

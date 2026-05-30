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

    public List<UnimodInfo> getUnimodInfos()
    {
        return Collections.singletonList(new UnimodInfo(_unimodId, _unimodName));
    }

    public boolean isCombinationMod()
    {
        return false;
    }

    public static class UnimodInfo
    {
        private int _unimodId;
        private String _unimodName;
        private int _modInfoId;

        public UnimodInfo() {}

        public UnimodInfo(int unimodId, String unimodName)
        {
            this._unimodId = unimodId;
            this._unimodName = unimodName;
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

        public int getModInfoId()
        {
            return _modInfoId;
        }

        public void setModInfoId(int modInfoId)
        {
            _modInfoId = modInfoId;
        }
    }
}

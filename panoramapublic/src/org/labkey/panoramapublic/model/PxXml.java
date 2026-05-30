/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
package org.labkey.panoramapublic.model;

public class PxXml extends DbEntity
{
    private int _journalExperimentId;
    private String _xml;
    private int _version;
    private String _updateLog;

    public PxXml() {}

    public PxXml(int journalExperimentId, String xml, int version, String updateLog)
    {
        _journalExperimentId = journalExperimentId;
        _xml = xml;
        _version = version;
        _updateLog = updateLog;
    }

    public int getJournalExperimentId()
    {
        return _journalExperimentId;
    }

    public void setJournalExperimentId(int journalExperimentId)
    {
        _journalExperimentId = journalExperimentId;
    }

    public String getXml()
    {
        return _xml;
    }

    public void setXml(String xml)
    {
        _xml = xml;
    }

    public int getVersion()
    {
        return _version;
    }

    public void setVersion(int version)
    {
        _version = version;
    }

    public String getUpdateLog()
    {
        return _updateLog;
    }

    public void setUpdateLog(String updateLog)
    {
        _updateLog = updateLog;
    }
}

/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.panoramapublic.chromlib;

import org.labkey.api.targetedms.RepresentativeDataState;

import java.util.Objects;

public class LibPeptideGroup
{
    private long _id;
    private String _label;
    private Integer _sequenceId;
    private long _runId;
    private RepresentativeDataState _representativeDataState;

    public LibPeptideGroup()
    {
    }

    LibPeptideGroup(long runId, String label, Integer sequenceId, RepresentativeDataState representativeDataState)
    {
        _runId = runId;
        _label = label;
        _sequenceId = sequenceId;
        _representativeDataState = representativeDataState;
    }

    LibPeptideGroup(long dbId, LibPeptideGroup source)
    {
        this(source.getRunId(), source.getLabel(), source.getSequenceId(), source.getRepresentativeDataState());
       _id = dbId;
    }

    public long getId()
    {
        return _id;
    }

    public void setId(long id)
    {
        _id = id;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public Integer getSequenceId()
    {
        return _sequenceId;
    }

    public void setSequenceId(Integer sequenceId)
    {
        _sequenceId = sequenceId;
    }

    public long getRunId()
    {
        return _runId;
    }

    public void setRunId(long runId)
    {
        _runId = runId;
    }

    public RepresentativeDataState getRepresentativeDataState()
    {
        return _representativeDataState;
    }

    public void setRepresentativeDataState(RepresentativeDataState representativeDataState)
    {
        _representativeDataState = representativeDataState;
    }

    public LibPeptideGroupKey getKey()
    {
        return new LibPeptideGroupKey(_label, _sequenceId);
    }

    static class LibPeptideGroupKey
    {
        private final String _label;
        private final Integer _seqId;

        private LibPeptideGroupKey(String label, Integer seqId)
        {
            _label = label;
            _seqId = seqId;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LibPeptideGroupKey that = (LibPeptideGroupKey) o;
            return Objects.equals(_label, that._label) && Objects.equals(_seqId, that._seqId);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(_label, _seqId);
        }

        @Override
        public String toString()
        {
            return "LibPeptideGroupKey{" +
                    "_label='" + _label + '\'' +
                    ", _seqId=" + _seqId +
                    '}';
        }
    }
}

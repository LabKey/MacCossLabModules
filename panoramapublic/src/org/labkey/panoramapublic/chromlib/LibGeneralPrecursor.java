package org.labkey.panoramapublic.chromlib;

import org.labkey.api.targetedms.RepresentativeDataState;

import java.util.Objects;

public abstract class LibGeneralPrecursor
{
    private long _peptideGroupId;

    // Fields from the GeneralPrecursor table
    private long _id;
    private double _mz;
    private int _charge;
    private RepresentativeDataState _representativeDataState;

    public LibGeneralPrecursor()
    {
    }

    LibGeneralPrecursor(long peptideGroupId, double precursorMz, int charge, RepresentativeDataState state)
    {
        _peptideGroupId = peptideGroupId;
        _mz = precursorMz;
        _charge = charge;
        _representativeDataState = state;
    }

    public long getId()
    {
        return _id;
    }

    public void setId(long id)
    {
        _id = id;
    }

    public double getMz()
    {
        return _mz;
    }

    public void setMz(double mz)
    {
        _mz = mz;
    }

    public int getCharge()
    {
        return _charge;
    }

    public void setCharge(int charge)
    {
        _charge = charge;
    }

    public RepresentativeDataState getRepresentativeDataState()
    {
        return _representativeDataState;
    }

    public void setRepresentativeDataState(RepresentativeDataState representativeDataState)
    {
        _representativeDataState = representativeDataState;
    }

    public long getPeptideGroupId()
    {
        return _peptideGroupId;
    }

    public void setPeptideGroupId(long peptideGroupId)
    {
        _peptideGroupId = peptideGroupId;
    }

    public abstract LibPrecursorKey getKey();

    static class LibPrecursorKey
    {
        private final double _mz;
        private final int _charge;
        private final String _precursorKey;

        public LibPrecursorKey(double mz, int charge, String precursorKey)
        {
            _mz = mz;
            _charge = charge;
            _precursorKey = precursorKey;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LibPrecursorKey that = (LibPrecursorKey) o;
            return Double.compare(that._mz, _mz) == 0 && _charge == that._charge && Objects.equals(_precursorKey, that._precursorKey);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(_mz, _charge, _precursorKey);
        }

        @Override
        public String toString()
        {
            return "LibPrecursorKey{" +
                    "_mz=" + _mz +
                    ", _charge=" + _charge +
                    ", _key='" + _precursorKey + '\'' +
                    '}';
        }
    }
}

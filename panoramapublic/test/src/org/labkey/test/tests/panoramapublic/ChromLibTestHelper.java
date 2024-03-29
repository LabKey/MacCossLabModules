package org.labkey.test.tests.panoramapublic;

import org.apache.commons.lang3.StringUtils;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.query.Filter;
import org.labkey.remoteapi.query.Row;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.labkey.test.BaseWebDriverTest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ChromLibTestHelper
{
    private static final String TARGETEDMS = "targetedms";
    private static final String ID = "id";
    private static final String FILENAME = "filename";
    private static final String RUNID = "runid";
    private static final String SEQUENCEID = "sequenceid";
    private static final String LABEL = "label";
    private static final String MZ = "mz";
    private static final String CHARGE = "charge";
    private static final String MODIFIED_SEQ = "modifiedsequence";
    private static final String MASS_MONO = "massmonoisotopic";
    private static final String MASS_AVG = "massaverage";
    private static final String CUSTOM_ION_NAME = "customionname";
    private static final String ION_FORMULA = "ionformula";
    static final String REPRESENTATIVEDATASTATE = "representativedatastate";

    private final BaseWebDriverTest _test;

    public ChromLibTestHelper(BaseWebDriverTest test)
    {
        _test = test;
    }

    public ChromLibState getLibState(String folderPath)
    {
        ChromLibState state = new ChromLibState();
        state.setRuns(getRuns(folderPath));
        return state;
    }

    private List<LibRun> getRuns(String folderPath)
    {
        List<LibRun> runs = new ArrayList<>();
        try {
            Connection conn = _test.createDefaultConnection();
            SelectRowsCommand selectCmd = new SelectRowsCommand(TARGETEDMS, "runs");
            selectCmd.setColumns(List.of(ID, FILENAME, REPRESENTATIVEDATASTATE));
            SelectRowsResponse selectResp = selectCmd.execute(conn, folderPath);

            for (Row row : selectResp.getRowset())
            {
                LibRun run = new LibRun();
                run._id = (Integer)row.getValue(ID);
                run._fileName = (String)row.getValue(FILENAME);
                run._representativeDataState = (Integer)row.getValue(REPRESENTATIVEDATASTATE);
                addPeptideGroups(run, folderPath);
                runs.add(run);
            }
        }
        catch (IOException | CommandException rethrow)
        {
            throw new RuntimeException(rethrow);
        }
        return runs;
    }

    private void addPeptideGroups(LibRun run, String folderPath)
    {
        try {
            Connection conn = _test.createDefaultConnection();
            SelectRowsCommand selectCmd = new SelectRowsCommand(TARGETEDMS, "peptidegroup");
            selectCmd.addFilter(new Filter(RUNID, run._id));
            selectCmd.setColumns(List.of(ID, RUNID, LABEL, SEQUENCEID, REPRESENTATIVEDATASTATE));
            SelectRowsResponse selectResp = selectCmd.execute(conn, folderPath);

            for (Row row : selectResp.getRowset())
            {
                LibPeptideGroup pepGrp = new LibPeptideGroup();
                pepGrp._id = (Integer)row.getValue(ID);
                pepGrp._sequenceId = (Integer)row.getValue(SEQUENCEID);
                pepGrp._label = (String)row.getValue(LABEL);
                pepGrp._representativeDataState = (Integer)row.getValue(REPRESENTATIVEDATASTATE);
                addPrecursors(pepGrp, run, folderPath);
                addMoleculePrecursors(pepGrp, run, folderPath);
                run.addPeptideGroup(pepGrp);
            }
        }
        catch (IOException | CommandException rethrow)
        {
            throw new RuntimeException(rethrow);
        }
    }

    private void addPrecursors(LibPeptideGroup pepGrp, LibRun run, String folderPath)
    {
        try {
            Connection conn = _test.createDefaultConnection();
            SelectRowsCommand selectCmd = new SelectRowsCommand(TARGETEDMS, "precursor");
            selectCmd.addFilter(new Filter("GeneralMoleculeId/PeptideGroupId", pepGrp._id));
            selectCmd.setColumns(List.of(ID, MZ, CHARGE, MODIFIED_SEQ, REPRESENTATIVEDATASTATE));
            SelectRowsResponse selectResp = selectCmd.execute(conn, folderPath);

            for (Row row : selectResp.getRowset())
            {
                LibPrecursor precursor = new LibPrecursor();
                precursor._id= (Integer)row.getValue(ID);
                precursor._mz = (Double)row.getValue(MZ);
                precursor._charge = (Integer)row.getValue(CHARGE);
                precursor._representativeDataState = (Integer)row.getValue(REPRESENTATIVEDATASTATE);
                precursor._modifiedSequence = (String)row.getValue(MODIFIED_SEQ);
                pepGrp.addPrecursor(precursor, run);
            }
        }
        catch (IOException | CommandException rethrow)
        {
            throw new RuntimeException(rethrow);
        }
    }

    private void addMoleculePrecursors(LibPeptideGroup pepGrp, LibRun run, String folderPath)
    {
        try {
            Connection conn = _test.createDefaultConnection();
            SelectRowsCommand selectCmd = new SelectRowsCommand(TARGETEDMS, "moleculeprecursor");
            selectCmd.addFilter(new Filter("GeneralMoleculeId/PeptideGroupId", pepGrp._id));
            selectCmd.setColumns(List.of(ID, MZ, CHARGE, MASS_MONO, MASS_AVG, ION_FORMULA, CUSTOM_ION_NAME, REPRESENTATIVEDATASTATE));
            SelectRowsResponse selectResp = selectCmd.execute(conn, folderPath);

            for (Row row : selectResp.getRowset())
            {
                LibMoleculePrecursor precursor = new LibMoleculePrecursor();
                precursor._id= (Integer)row.getValue(ID);
                precursor._mz = (Double)row.getValue(MZ);
                precursor._charge = (Integer)row.getValue(CHARGE);
                precursor._representativeDataState = (Integer)row.getValue(REPRESENTATIVEDATASTATE);
                precursor._massMonoisotopic = (Double)row.getValue(MASS_MONO);
                precursor._massAverage = (Double)row.getValue(MASS_AVG);
                precursor._ionFormula = (String)row.getValue(ION_FORMULA);
                precursor._customIonName = (String)row.getValue(CUSTOM_ION_NAME);
                pepGrp.addPrecursor(precursor, run);
            }
        }
        catch (IOException | CommandException rethrow)
        {
            throw new RuntimeException(rethrow);
        }
    }

    public static void compareLibState(ChromLibState source, ChromLibState target)
    {
        List<LibRun> runsSource = source.getRuns();
        List<LibRun> runsTarget = target.getRuns();
        assertEquals("Number of runs don't match.  Source: %d; Target: %d", runsSource.size(), runsTarget.size());

        for(LibRun runS: runsSource)
        {
            LibRun runT = runsTarget.stream().filter(r -> runS._fileName.equals(r._fileName)).findFirst().orElse(null);
            assertNotNull("Run '" + runS._fileName + "' was not found in the target list", runT);
            compareRuns(runS, runT);
        }
    }

    private static void compareRuns(LibRun runS, LibRun runT)
    {
        assertEqualsWithMsg("File names", runS._fileName, runT._fileName);
        assertEqualsWithMsg("Representative states", runT._fileName, runS._representativeDataState, runT._representativeDataState);

        Map<LibPeptideGroupKey, List<PeptideGroupPrecursors>> pepGrpsS = runS._peptideGroups;
        Map<LibPeptideGroupKey, List<PeptideGroupPrecursors>> pepGrpsT = runT._peptideGroups;
        assertEqualsWithMsg("Number of peptide groups", runT._fileName, pepGrpsS.size(), pepGrpsT.size());
        for(LibPeptideGroupKey pepGrpKey: pepGrpsS.keySet())
        {
            assertTrue("Target run '" + runT._fileName + "' does not contain peptide group '" + pepGrpKey + "'", pepGrpsT.containsKey(pepGrpKey));
            comparePeptideGroups(pepGrpKey, pepGrpsS.get(pepGrpKey), pepGrpsT.get(pepGrpKey));
        }
    }

    private static void comparePeptideGroups(LibPeptideGroupKey key, List<PeptideGroupPrecursors> pepGrpSList, List<PeptideGroupPrecursors> pepGrpTList)
    {
        assertEqualsWithMsg("Peptide group count for precursor key " + key.toString(), pepGrpSList.size(), pepGrpTList.size());
        for (var i = 0; i < pepGrpSList.size(); i++)
        {
            comparePeptideGroups(key, pepGrpSList.get(i), pepGrpTList.get(i));
        }
    }

    private static void comparePeptideGroups(LibPeptideGroupKey key, PeptideGroupPrecursors pepGrpS, PeptideGroupPrecursors pepGrpT)
    {
        assertEqualsWithMsg("Peptide group states", key.toString(), pepGrpS._representativeDataState, pepGrpT._representativeDataState);
        Map<LibPrecursorKey, Integer> _precursorsS = pepGrpS._precursorKeyStateMap;
        Map<LibPrecursorKey, Integer> _precursorsT = pepGrpT._precursorKeyStateMap;

        assertEqualsWithMsg("Number of precursors", key.toString(), _precursorsS.size(), _precursorsT.size());
        for(LibPrecursorKey precursor: _precursorsS.keySet())
        {
            assertTrue("Target peptide group " + key + " does not contain precursor " + precursor.toString(), _precursorsT.containsKey(precursor));
            assertEqualsWithMsg("Precursor states", precursor.toString(), _precursorsS.get(precursor), _precursorsT.get(precursor));
        }
    }

    private static void assertEqualsWithMsg(String message, Object sourceVal, Object targetVal)
    {
        assertEqualsWithMsg(message, null, sourceVal, targetVal);
    }

    private static void assertEqualsWithMsg(String message, String entity, Object sourceVal, Object targetVal)
    {
        assertEquals(String.format("%s do not match%s. Source: %s; Target: %s.", message, entity == null ? "" : " for " + entity,
                sourceVal, targetVal), sourceVal, targetVal);
    }


    public static class ChromLibState
    {
//        private String _libType;
//        private String _libVersion;
        private List<LibRun> _runs = new ArrayList<>();

        public List<LibRun> getRuns()
        {
            return _runs;
        }

        public void setRuns(List<LibRun> runs)
        {
            _runs = runs;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChromLibState that = (ChromLibState) o;
            return Objects.equals(getRuns(), that.getRuns());
        }
    }

    private static class LibRun
    {
        private int _id;
        private String _fileName;
        private int _representativeDataState;
        private final Map<LibPeptideGroupKey, List<PeptideGroupPrecursors>> _peptideGroups;

        private LibRun()
        {
            _peptideGroups = new HashMap<>();
        }

        public void addPeptideGroup(LibPeptideGroup group)
        {
            var peptideGroupPrecursors = _peptideGroups.computeIfAbsent(group.getKey(), p -> new ArrayList<>());
            peptideGroupPrecursors.add(new PeptideGroupPrecursors(group._representativeDataState, group.getPrecursorStates()));
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LibRun libRun = (LibRun) o;
            return _representativeDataState == libRun._representativeDataState && Objects.equals(_fileName, libRun._fileName)
                    && Objects.equals(_peptideGroups, libRun._peptideGroups);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(_id, _fileName, _representativeDataState, _peptideGroups);
        }
    }

    private static class PeptideGroupPrecursors
    {
        private final int _representativeDataState;
        private final Map<LibPrecursorKey, Integer> _precursorKeyStateMap;

        public PeptideGroupPrecursors(int representativeDataState, Map<LibPrecursorKey, Integer> precursorKeyStateMap)
        {
            _representativeDataState = representativeDataState;
            _precursorKeyStateMap = precursorKeyStateMap;
        }
    }

    private static class LibPeptideGroup
    {
        private int _id;
        private String _label;
        private Integer _sequenceId;
        private int _representativeDataState;
        private final Map<LibPrecursorKey, Integer> _precursors;

        public LibPeptideGroupKey getKey()
        {
            return new LibPeptideGroupKey(_label, _sequenceId);
        }

        private LibPeptideGroup()
        {
            _precursors = new HashMap<>();
        }

        public void addPrecursor(LibGeneralPrecursor precursor, LibRun run)
        {
            if(_precursors.containsKey(precursor.getKey()))
            {
                throw new RuntimeException(String.format("Duplicate precursor '%s' found for peptide group '%s' in run '%s'.", precursor.getKey(), getKey(), run._fileName));
            }
            _precursors.put(precursor.getKey(), precursor._representativeDataState);
        }

        public Map<LibPrecursorKey, Integer> getPrecursorStates()
        {
            return _precursors;
        }
    }

    private static class LibPeptideGroupKey
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
                    "label='" + _label + '\'' +
                    ", seqId=" + _seqId +
                    '}';
        }
    }

    private static class LibPrecursorKey
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
                    "mz=" + _mz +
                    ", charge=" + _charge +
                    ", key='" + _precursorKey + '\'' +
                    '}';
        }
    }

    private static abstract class LibGeneralPrecursor
    {
        // Fields from the GeneralPrecursor table
        protected int _id;
        protected double _mz;
        protected int _charge;
        protected int _representativeDataState;

        public abstract LibPrecursorKey getKey();
    }

    public static class LibMoleculePrecursor extends LibGeneralPrecursor
    {
        // Fields from the Molecule table
        private String _customIonName;
        private String _ionFormula;
        private Double _massMonoisotopic;
        private Double _massAverage;

        @Override
        public LibPrecursorKey getKey()
        {
            return new LibPrecursorKey(_mz, _charge,
                    StringUtils.join(new String[]{_customIonName, _ionFormula, String.valueOf(_massMonoisotopic), String.valueOf(_massAverage)}, ','));
        }
    }

    public static class LibPrecursor extends LibGeneralPrecursor
    {
        // Fields from the Precursor table
        private String _modifiedSequence;

        public String getModifiedSequence()
        {
            return _modifiedSequence;
        }

        public void setModifiedSequence(String modifiedSequence)
        {
            _modifiedSequence = modifiedSequence;
        }

        @Override
        public LibPrecursorKey getKey()
        {
            return new LibPrecursorKey(_mz, _charge, _modifiedSequence);
        }
    }
}

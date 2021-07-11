package org.labkey.panoramapublic.chromlib;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.query.FieldKey;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.RepresentativeDataState;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.writer.PrintWriters;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ChromLibStateManager
{
    private static final String RUN = "Run";
    private static final String RUN_STATE = "Run_State";
    private static final String PEP_GRP = "Peptide_Group";
    private static final String PEP_GRP_SEQ_ID = "Peptide_Group_Seq_Id";
    private static final String PEP_GRP_STATE = "Peptide_Group_State";
    private static final String PREC_MZ = "Precursor_mz";
    private static final String PREC_CHARGE = "Precursor_Charge";
    private static final String PREC_STATE = "Precursor_State";
    private static final String PREC_MOD_SEQ = "Precursor_Modified_Sequence";
    private static final String PREC_MOL_CUSTOM_ION_NAME = "Precursor_Mol_Custom_Ion_Name";
    private static final String PREC_MOL_ION_FORMULA = "Precursor_Mol_Ion_Formula";
    private static final String PREC_MOL_MASS_MONOISOTOPIC = "Precursor_Mol_Mass_Monoisotopic";
    private static final String PREC_MOL_MASS_AVERAGE = "Precursor_Mol_Mass_Average";

    private static final String TAB = "\t";
    private static final List<String> PROTEIN_LIB_HEADERS = List.of(RUN, RUN_STATE, PEP_GRP, PEP_GRP_SEQ_ID, PEP_GRP_STATE);
    private static final List<String> PEP_LIB_HEADERS = List.of(RUN, RUN_STATE, PEP_GRP, PEP_GRP_SEQ_ID, PEP_GRP_STATE,
            PREC_MZ, PREC_CHARGE, PREC_STATE,
            PREC_MOD_SEQ, PREC_MOL_CUSTOM_ION_NAME, PREC_MOL_ION_FORMULA, PREC_MOL_MASS_MONOISOTOPIC, PREC_MOL_MASS_AVERAGE);

    static final String REPRESENTATIVEDATASTATE = "representativedatastate";

    public void exportLibState(File file, Container container, User user) throws ChromLibStateException
    {
        TargetedMSService svc = TargetedMSService.get();
        // Check that this is a chromatogram library folder
        TargetedMSService.FolderType folderType = svc.getFolderType(container);
        if(TargetedMSService.FolderType.LibraryProtein.equals(folderType))
        {
            exportProteinLibraryState(container, file, user, svc);
        }
        else if (TargetedMSService.FolderType.Library.equals(folderType))
        {
            exportPeptideLibraryState(container, file, user, svc);
        }
        else
        {
            throw new ChromLibStateException(String.format("'%s' is not a chromatogram library folder.", container));
        }
    }

    private void exportProteinLibraryState(Container container, File file, User user, TargetedMSService svc) throws ChromLibStateException
    {
        try(PrintWriter writer = PrintWriters.getPrintWriter(file))
        {
            writer.write(StringUtils.join(PROTEIN_LIB_HEADERS, TAB)); // Header row
            writer.println();

            // Get a list of runs in the folder
            List<ITargetedMSRun> runs = svc.getRuns(container);
            for (ITargetedMSRun run : runs)
            {
                // For each run get a list of peptide groups
                List<LibPeptideGroup> peptideGroups = getPeptideGroups(run, svc);
                for (LibPeptideGroup pepGrp : peptideGroups)
                {
                    // For each run / peptide group write the representative state
                    // - run, run_state
                    // - peptidegroup_label, peptidegroup_sequenceid, peptidegroup_state
                    List<String> values = List.of(run.getFileName(), run.getRepresentativeDataState().toString(),
                            pepGrp.getLabel(), (pepGrp.getSequenceId() == null ? "" : String.valueOf(pepGrp.getSequenceId())), pepGrp.getRepresentativeDataState().toString());
                    writer.write(StringUtils.join(values, TAB));
                    writer.println();
                }
            }
        }
        catch (FileNotFoundException e)
        {
            throw new ChromLibStateException(e);
        }
    }

    private void exportPeptideLibraryState(Container container, File file, User user, TargetedMSService svc) throws ChromLibStateException
    {
        try(PrintWriter writer = PrintWriters.getPrintWriter(file))
        {
            writer.write(StringUtils.join(PEP_LIB_HEADERS, TAB)); // Header row
            writer.println();

            // Get a list of runs in the folder
            List<ITargetedMSRun> runs = svc.getRuns(container);
            for (ITargetedMSRun run : runs)
            {
                // For each run get a list of peptide groups
                List<LibPeptideGroup> peptideGroups = getPeptideGroups(run, svc);
                for (LibPeptideGroup pepGrp : peptideGroups)
                {
                    // For each peptide group get a list of precursors
                    List<LibPrecursor> precursors = getPrecursors(pepGrp, container, user, svc);
                    for(LibPrecursor precursor: precursors)
                    {
                        writePeptideLibRow(writer, run, pepGrp, precursor);
                    }
                    List<LibMoleculePrecursor> moleculePrecursors = getMoleculePrecursors(pepGrp, container, user, svc);
                    for(LibMoleculePrecursor precursor: moleculePrecursors)
                    {
                        writePeptideLibRow(writer, run, pepGrp, precursor);
                    }
                }
            }
        }
        catch (FileNotFoundException e)
        {
            throw new ChromLibStateException(e);
        }
    }

    private void writePeptideLibRow(PrintWriter writer, ITargetedMSRun run, LibPeptideGroup pepGrp, LibGeneralPrecursor precursor)
    {
        // For each run / peptide group / precursor write the representative state
        // - run, run_state
        // - peptidegroup_label, peptidegroup_sequenceid
        // - precursor_mz, precursor_charge, precursor_state
        // - precursor_modified_sequence (for Precursors; empty for MoleculePrecursors)
        // - mol_precursor_custom_ion_name, mol_precursor_ion_formula, mol_precursor_mass_monoisotopic, mol_precursor_mass_average (for MoleculePrecursors; empty for Precursors)
        var values = new ArrayList<>(List.of(run.getFileName(), run.getRepresentativeDataState().toString(),
                pepGrp.getLabel(), (pepGrp.getSequenceId() == null ? "" : String.valueOf(pepGrp.getSequenceId())), pepGrp.getRepresentativeDataState().toString(),
                String.valueOf(precursor.getMz()), String.valueOf(precursor.getCharge()), precursor.getRepresentativeDataState().toString()));
        if(precursor instanceof LibPrecursor)
        {
            values.add(((LibPrecursor) precursor).getModifiedSequence());
            values.add("");
            values.add("");
            values.add("");
            values.add("");
        }
        else if(precursor instanceof LibMoleculePrecursor)
        {
            values.add("");
            values.add(((LibMoleculePrecursor) precursor).getCustomIonName());
            values.add(((LibMoleculePrecursor) precursor).getIonFormula());
            values.add(String.valueOf(((LibMoleculePrecursor) precursor).getMassMonoisotopic()));
            values.add(String.valueOf(((LibMoleculePrecursor) precursor).getMassAverage()));
        }
        writer.write(StringUtils.join(values, TAB));
        writer.println();
    }

    public void importLibState(File libStateFile, Container container, User user) throws ChromLibStateException
    {
        TargetedMSService svc = TargetedMSService.get();
        TargetedMSService.FolderType folderType = svc.getFolderType(container);
        if(TargetedMSService.FolderType.LibraryProtein.equals(folderType))
        {
            importProteinLibState(container, user, libStateFile, svc);
        }
        else if (TargetedMSService.FolderType.Library.equals(folderType))
        {
            importPeptideLibraryState(container, user, libStateFile, svc);
        }
        else
        {
            throw new ChromLibStateException(String.format("'%s' is not a chromatogram library folder.", container));
        }
    }

    private void importProteinLibState(Container container, User user, File libStateFile, TargetedMSService svc) throws ChromLibStateException
    {
        try (TabLoader reader = new TabLoader(libStateFile, true))
        {
            CloseableIterator<Map<String, Object>> iterator = reader.iterator();
            try
            {
                // Verify that we have the right columns
                verifyLibColumns(reader.getColumns(), PROTEIN_LIB_HEADERS);
            }
            catch (IOException e)
            {
                throw new ChromLibStateException(String.format("Error reading header columns from file '%s'.", libStateFile));
            }
            while(iterator.hasNext())
            {
                parseProteinLibRow(iterator.next(), container, user, svc);
            }
        }
    }

    private void verifyLibColumns(ColumnDescriptor[] columns, List<String> expectedColulmns) throws ChromLibStateException
    {
        var columnsInFile = Arrays.stream(columns).map(c -> c.name).collect(Collectors.toSet());
        if(!columnsInFile.containsAll(expectedColulmns))
        {
            throw new ChromLibStateException(String.format("Required column headers not found. Expected columns: %s.  Found columns: %s",
                    StringUtils.join(expectedColulmns, ","), StringUtils.join(columnsInFile, ",")));
        }
    }

    private ITargetedMSRun _currentRun;
    private Map<LibPeptideGroupKey, Long> _pepGrpKeyMap;
    private LibPeptideGroup _currentPepGrp;
    private Map<LibPrecursorKey, Long> _precursorKeyMap;

    private void parseProteinLibRow(Map<String, Object> row, Container container, User user, TargetedMSService svc) throws ChromLibStateException
    {
        String skyFile = String.valueOf(row.get(RUN));
        String runState = String.valueOf(row.get(RUN_STATE));
        if(_currentRun == null || !StringUtils.equals(skyFile, _currentRun.getFileName()))
        {
            ITargetedMSRun run = svc.getRunByFileName(skyFile, container);
            if(run == null)
            {
                throw new ChromLibStateException(String.format("Expected a row for Skyline document '%s' in the container '%s'.", skyFile, container));
            }
            _currentRun = run;
            var map = new HashMap<String, ITargetedMSRun.RepresentativeDataState>();
            map.put(REPRESENTATIVEDATASTATE, ITargetedMSRun.RepresentativeDataState.valueOf(runState));
            Table.update(null, svc.getTableInfoRuns(), map, _currentRun.getId());

            if(_pepGrpKeyMap != null)
            {
                _pepGrpKeyMap.clear();
            }
            else
            {
                _pepGrpKeyMap = new HashMap<>();
            }

            List<LibPeptideGroup> dbPepGrps = getPeptideGroups(_currentRun, svc);
            dbPepGrps.forEach(p -> _pepGrpKeyMap.put(p.getKey(), p.getId()));
        }

        LibPeptideGroup pepGrp = parsePeptideGroup(row, _currentRun.getId());
        LibPeptideGroupKey pepGrpKey = pepGrp.getKey();
        Long dbId = _pepGrpKeyMap.get(pepGrpKey);
        if(dbId == null)
        {
            throw new IllegalStateException(String.format("Expected a row for peptide group %s in the Skyline document '%s'. Container '%s'.", pepGrp._label, skyFile, container));
        }
        var map = new HashMap<String, RepresentativeDataState>();
        map.put(REPRESENTATIVEDATASTATE, pepGrp._representativeDataState);
        Table.update(null, svc.getTableInfoPeptideGroup(), map, dbId);
        // Set the representative state for all the precursors to be the same as the state of the peptide group
        pepGrp.setId(dbId);
        updatePrecursorState(pepGrp, container, user, svc);
    }

    private LibPeptideGroup parsePeptideGroup(Map<String, Object> row, long runId)
    {
        String peptideGrp = String.valueOf(row.get(PEP_GRP));
        Integer seqId = null;
        if(row.get(PEP_GRP_SEQ_ID) != null)
        {
            seqId = Integer.parseInt(String.valueOf(row.get(PEP_GRP_SEQ_ID)));
        }
        String pepGrpState = String.valueOf(row.get(PEP_GRP_STATE));
        return new LibPeptideGroup(runId, peptideGrp, seqId, RepresentativeDataState.valueOf(pepGrpState));
    }

    private void updatePrecursorState(LibPeptideGroup pepGrp, Container container, User user, TargetedMSService svc)
    {
        /*
        UPDATE targetedms.generalprecursor gp
        SET representativedatastate = ?
        FROM
        targetedms.generalmolecule gm
        INNER JOIN targetedms.peptidegroup pg ON pg.Id = gm.peptideGroupId
        WHERE gm.Id = gp.generalMoleculeId  AND pg.Id = ?
         */
        SQLFragment sql = new SQLFragment(" UPDATE ")
                .append(svc.getTableInfoGeneralPrecursor(), "gp")
                .append(" SET ").append(REPRESENTATIVEDATASTATE).append(" = ? ").add(pepGrp.getRepresentativeDataState().ordinal())
                .append(" FROM ")
                .append(svc.getTableInfoGeneralMolecule(), "gm")
                .append(" INNER JOIN ")
                .append(svc.getTableInfoPeptideGroup(), "pg").append(" ON pg.Id = gm.peptideGroupId ")
                .append(" WHERE pg.Id = ?").add(pepGrp.getId())
                .append(" AND gm.Id = gp.generalMoleculeId ");

        int updated = new SqlExecutor(svc.getUserSchema(user, container).getDbSchema()).execute(sql);
        System.out.printf("UPDATED rows " + updated);
    }

    private void importPeptideLibraryState(Container container, User user, File libStateFile, TargetedMSService svc) throws ChromLibStateException
    {
        try (TabLoader reader = new TabLoader(libStateFile, true))
        {
            CloseableIterator<Map<String, Object>> iterator = reader.iterator();
            try
            {
                // Verify that we have the right columns
                verifyLibColumns(reader.getColumns(), PEP_LIB_HEADERS);
            }
            catch (IOException e)
            {
                throw new ChromLibStateException(String.format("Error reading header columns from file '%s'.", libStateFile));
            }
            while(iterator.hasNext())
            {
                parsePeptideLibRow(iterator.next(), container, user, svc);
            }
        }
    }

    private void parsePeptideLibRow(Map<String, Object> row, Container container, User user, TargetedMSService svc) throws ChromLibStateException
    {
        String skyFile = String.valueOf(row.get(RUN));
        String runState = String.valueOf(row.get(RUN_STATE));
        if(_currentRun == null || !StringUtils.equals(skyFile, _currentRun.getFileName()))
        {
            ITargetedMSRun run = svc.getRunByFileName(skyFile, container);
            if(run == null)
            {
                throw new ChromLibStateException(String.format("Expected a row for Skyline document '%s' in the container '%s'.", skyFile, container));
            }
            _currentRun = run;
            var map = new HashMap<String, ITargetedMSRun.RepresentativeDataState>();
            map.put(REPRESENTATIVEDATASTATE, ITargetedMSRun.RepresentativeDataState.valueOf(runState));
            Table.update(null, svc.getTableInfoRuns(), map, _currentRun.getId());

            if(_pepGrpKeyMap != null)
            {
                _pepGrpKeyMap.clear();
            }
            else
            {
                _pepGrpKeyMap = new HashMap<>();
            }

            List<LibPeptideGroup> dbPepGrps = getPeptideGroups(_currentRun, svc);
            dbPepGrps.forEach(p -> _pepGrpKeyMap.put(p.getKey(), p.getId()));
        }

        LibPeptideGroup pepGrp = parsePeptideGroup(row, _currentRun.getId());
        if(_currentPepGrp == null || !_currentPepGrp.getKey().equals(pepGrp.getKey()))
        {
            Long dbId = _pepGrpKeyMap.get(pepGrp.getKey());
            if(dbId == null)
            {
                throw new IllegalStateException(String.format("Expected a row for peptide group %s in the Skyline document '%s'. Container '%s'.", pepGrp._label, skyFile, container));
            }

            _currentPepGrp = pepGrp;
            _currentPepGrp.setId(dbId);

            if(_precursorKeyMap != null)
            {
                _precursorKeyMap.clear();
            }
            else
            {
                _precursorKeyMap = new HashMap<>();
            }

            List<LibPrecursor> dbPrecursors = getPrecursors(_currentPepGrp, container, user, svc);
            dbPrecursors.forEach(p -> _precursorKeyMap.put(p.getKey(), p.getId()));
            List<LibMoleculePrecursor> dbMoleculePrecursors = getMoleculePrecursors(_currentPepGrp, container, user, svc);
            dbMoleculePrecursors.forEach(p -> _precursorKeyMap.put(p.getKey(), p.getId()));
        }

        LibGeneralPrecursor precursor = parsePrecursor(row, _currentPepGrp.getId());
        Long generalPrecursorId = _precursorKeyMap.get(precursor.getKey());
        if(generalPrecursorId == null)
        {
            throw new IllegalStateException(String.format("Expected a row for precursor %s in the peptide group %s. Skyline document '%s'. Container '%s'.",
                    precursor.getKey().toString(), pepGrp.getLabel(), skyFile, container));
        }
        var map = new HashMap<String, RepresentativeDataState>();
        map.put(REPRESENTATIVEDATASTATE, precursor.getRepresentativeDataState());
        Table.update(null, svc.getTableInfoGeneralPrecursor(), map, generalPrecursorId);
    }

    private LibGeneralPrecursor parsePrecursor(Map<String, Object> row, long peptideGroupId)
    {
        double precursorMz = Double.parseDouble(String.valueOf(row.get(PREC_MZ)));
        int precursorCharge = Integer.parseInt(String.valueOf(row.get(PREC_CHARGE)));
        String precursorState = String.valueOf(row.get(PREC_STATE));
        String modifiedSeq = String.valueOf(row.get(PREC_MOD_SEQ));
        if(!StringUtils.isBlank(modifiedSeq))
        {
            return new LibPrecursor(peptideGroupId, precursorMz, precursorCharge, RepresentativeDataState.valueOf(precursorState), modifiedSeq);
        }
        else
        {
            String customIonName = String.valueOf(row.get(PREC_MOL_CUSTOM_ION_NAME));
            String ionFormula = String.valueOf(row.get(PREC_MOL_ION_FORMULA));
            Double massMonoIsotopic = Double.parseDouble(String.valueOf(row.get(PREC_MOL_MASS_MONOISOTOPIC)));
            Double massAverage = Double.parseDouble(String.valueOf(row.get(PREC_MOL_MASS_AVERAGE)));
            return new LibMoleculePrecursor(peptideGroupId, precursorMz, precursorCharge, RepresentativeDataState.valueOf(precursorState),
                    customIonName, ionFormula, massMonoIsotopic, massAverage);
        }
    }

    private static List<LibPeptideGroup> getPeptideGroups(ITargetedMSRun run, TargetedMSService svc)
    {
        return new TableSelector(svc.getTableInfoPeptideGroup(),
                Set.of("id", "runId", "label", "sequenceId", "representativedatastate"),
                new SimpleFilter(FieldKey.fromParts("runId"), run.getId()), null)
                .getArrayList(LibPeptideGroup.class);
    }

    private static List<LibPrecursor> getPrecursors(LibPeptideGroup pepGrp, Container c, User user, TargetedMSService svc)
    {
        SQLFragment sql = new SQLFragment(" SELECT gp.Id, gp.mz, gp.charge, gp.representativedatastate, p.modifiedsequence ")
                .append(" FROM ").append(svc.getTableInfoGeneralMolecule(), "gm")
                .append(" INNER JOIN ").append(svc.getTableInfoGeneralPrecursor(), "gp")
                .append(" ON gm.id = gp.generalmoleculeid ")
                .append(" INNER JOIN ").append(svc.getTableInfoPrecursor(), "p")
                .append(" ON gp.id = p.id ")
                .append(" WHERE gm.peptidegroupid = ?").add(pepGrp.getId());

        return new SqlSelector(svc.getUserSchema(user, c).getDbSchema(), sql).getArrayList(LibPrecursor.class);
    }

    private static List<LibMoleculePrecursor> getMoleculePrecursors(LibPeptideGroup pepGrp, Container c, User user, TargetedMSService svc)
    {
        SQLFragment sql = new SQLFragment(" SELECT gp.Id, gp.mz, gp.charge, gp.representativedatastate, m.massMonoisotopic, m.massAverage, m.ionFormula, m.customIonName ")
                .append(" FROM ").append(svc.getTableInfoGeneralMolecule(), "gm")
                .append(" INNER JOIN ").append(svc.getTableInfoGeneralPrecursor(), "gp")
                .append(" ON gm.id = gp.generalmoleculeid ")
                .append(" INNER JOIN ").append(svc.getTableInfoMolecule(), "m")
                .append(" ON gm.id = m.id ")
                .append(" WHERE gm.peptidegroupid = ?").add(pepGrp.getId());

        return new SqlSelector(svc.getUserSchema(user, c).getDbSchema(), sql).getArrayList(LibMoleculePrecursor.class);
    }

    public static class LibPeptideGroup
    {
        private long _id;
        private String _label;
        private Integer _sequenceId;
        private long _runId;
        private RepresentativeDataState _representativeDataState;

        public LibPeptideGroup() {}

        private LibPeptideGroup(long runId, String label, Integer sequenceId, RepresentativeDataState representativeDataState)
        {
            _runId = runId;
            _label = label;
            _sequenceId = sequenceId;
            _representativeDataState = representativeDataState;
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
    }

    public static abstract class LibGeneralPrecursor
    {
        private long _peptideGroupId;

        // Fields from the GeneralPrecursor table
        private long _id;
        private double _mz;
        private int _charge;
        private RepresentativeDataState _representativeDataState;

        public LibGeneralPrecursor() {}

        private LibGeneralPrecursor(long peptideGroupId, double precursorMz, int charge, RepresentativeDataState state)
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
    }

    public static class LibPrecursor extends LibGeneralPrecursor
    {
        // Fields from the Precursor table
        private String _modifiedSequence;

        public LibPrecursor() {}

        private LibPrecursor(long peptideGroupId, double precursorMz, int charge, RepresentativeDataState state, String modifiedSequence)
        {
            super(peptideGroupId, precursorMz, charge, state);
            _modifiedSequence = modifiedSequence;
        }

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
            return new LibPrecursorKey(getMz(), getCharge(), _modifiedSequence);
        }
    }

    public static class LibMoleculePrecursor extends LibGeneralPrecursor
    {
        // Fields from the Molecule table
        private String _customIonName;
        private String _ionFormula;
        private Double _massMonoisotopic; // not null
        private Double _massAverage; // not null

        public LibMoleculePrecursor() {}

        private LibMoleculePrecursor(long peptideGroupId, double precursorMz, int charge, RepresentativeDataState state,
                                     String customIonName, String ionFormula, Double massMonoisotopic, Double massAverage)
        {
            super(peptideGroupId, precursorMz, charge, state);
            _customIonName = customIonName;
            _ionFormula = ionFormula;
            _massMonoisotopic = massMonoisotopic;
            _massAverage = massAverage;
        }

        public String getIonFormula()
        {
            return _ionFormula;
        }

        public void setIonFormula(String ionFormula)
        {
            _ionFormula = ionFormula;
        }

        public Double getMassMonoisotopic()
        {
            return _massMonoisotopic;
        }

        public void setMassMonoisotopic(Double massMonoisotopic)
        {
            _massMonoisotopic = massMonoisotopic;
        }

        public Double getMassAverage()
        {
            return _massAverage;
        }

        public void setMassAverage(Double massAverage)
        {
            _massAverage = massAverage;
        }

        public String getCustomIonName()
        {
            return _customIonName;
        }

        public void setCustomIonName(String customIonName)
        {
            _customIonName = customIonName;
        }

        @Override
        public LibPrecursorKey getKey()
        {
            return new LibPrecursorKey(getMz(), getCharge(),
                    StringUtils.join(new String[]{_customIonName, _ionFormula, String.valueOf(_massMonoisotopic), String.valueOf(_massAverage)}, ','));
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
                    "_mz=" + _mz +
                    ", _charge=" + _charge +
                    ", _key='" + _precursorKey + '\'' +
                    '}';
        }
    }

    /**
     * Prepares a statement for reuse during the import process. For tables that have a lot of rows, this is worth the
     * tradeoff between having more custom code and the perf hit from Table.insert() having to prep a statement for
     * every row
     */
//    private PreparedStatement ensureStatement(PreparedStatement stmt, String sql /*, boolean reselect*/)
//    {
//        if (stmt == null)
//        {
//            try
//            {
//                assert PanoramaPublicManager.getSchema().getScope().isTransactionActive();
//                Connection c = PanoramaPublicManager.getSchema().getScope().getConnection();
//                if (reselect)
//                {
//                    SQLFragment reselectSQL = new SQLFragment(sql);
//                    // All we really need is a ColumnInfo of the right name and type, so choose one of the TableInfos to supply it
//                    TargetedMSManager.getSchema().getSqlDialect().addReselect(reselectSQL, getTableInfoTransitionChromInfo().getColumn("Id"), null);
//                    sql = reselectSQL.getSQL();
//                }
//                stmt = c.prepareStatement(sql);
//            }
//            catch (SQLException e)
//            {
//                throw new RuntimeSQLException(e);
//            }
//        }
//        return stmt;
//    }
}

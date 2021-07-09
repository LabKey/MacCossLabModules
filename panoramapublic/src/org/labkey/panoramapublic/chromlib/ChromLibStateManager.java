package org.labkey.panoramapublic.chromlib;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
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
import org.labkey.api.targetedms.IGeneralPrecursor;
import org.labkey.api.targetedms.IPeptideGroup;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.RepresentativeDataState;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.writer.PrintWriters;
import org.labkey.panoramapublic.PanoramaPublicManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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
    private static final String TAB = "\t";
    private static final List<String> PROTEIN_LIB_HEADERS = List.of(RUN, RUN_STATE, PEP_GRP, PEP_GRP_SEQ_ID, PEP_GRP_STATE);
    private static final List<String> PEP_LIB_HEADERS = List.of(RUN, RUN_STATE, PEP_GRP, PEP_GRP_SEQ_ID, PEP_GRP_STATE, PREC_MZ, PREC_CHARGE, PREC_STATE);

    static final String REPRESENTATIVEDATASTATE = "representativedatastate";

    private static final String runStmtSql = "UPDATE targetedms.runs SET representativedatastate=? WHERE id=?";
    private static final String pepGrpStmtSql = "UPDATE targetedms.peptidegroup SET representativedatastate=? WHERE id=?";
    private static final String precursorStmtSql = "UPDATE targetedms.generalprecursor SET representativedatastate=? WHERE id=?";

    public void exportLibState(Container container, File file) throws ChromLibStateException
    {
        TargetedMSService svc = TargetedMSService.get();
        // Check that this is a chromatogram library folder
        TargetedMSService.FolderType folderType = svc.getFolderType(container);
        if(TargetedMSService.FolderType.LibraryProtein.equals(folderType))
        {
            exportProteinLibraryState(container, file, svc);
        }
        else if (TargetedMSService.FolderType.Library.equals(folderType))
        {
            exportPeptideLibraryState(container, file, svc);
        }
        else
        {
            throw new ChromLibStateException(String.format("'%s' is not a chromatogram library folder.", container));
        }
    }

    private void exportProteinLibraryState(Container container, File file, TargetedMSService svc) throws ChromLibStateException
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
                List<? extends IPeptideGroup> peptideGroups = getPeptideGroups(run, svc);
                for (IPeptideGroup pepGrp : peptideGroups)
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

    private void exportPeptideLibraryState(Container container, File file, TargetedMSService svc) throws ChromLibStateException
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
                List<? extends IPeptideGroup> peptideGroups = getPeptideGroups(run, svc);
                for (IPeptideGroup pepGrp : peptideGroups)
                {
                    // For each peptide group get a list of precursors
                    List<? extends IGeneralPrecursor> precursors = getGeneralPrecursors(pepGrp, svc);
                    for(IGeneralPrecursor precursor: precursors)
                    {
                        // For each run / peptide group / precursor write the representative state
                        // - run, run_state
                        // - peptidegroup_label, peptidegroup_sequenceid, peptidegroup_state
                        // - precursor_mz, precursor_charge, precursor_state
                        List<String> values = List.of(run.getFileName(), run.getRepresentativeDataState().toString(),
                                pepGrp.getLabel(), (pepGrp.getSequenceId() == null ? "" : String.valueOf(pepGrp.getSequenceId())), pepGrp.getRepresentativeDataState().toString(),
                                String.valueOf(precursor.getMz()), String.valueOf(precursor.getCharge()), precursor.getRepresentativeDataState().toString());
                        writer.write(StringUtils.join(values, TAB));
                        writer.println();
                    }
                }
            }
        }
        catch (FileNotFoundException e)
        {
            throw new ChromLibStateException(e);
        }
    }

    public void importLibState(Container container, File libStateFile) throws ChromLibStateException
    {
        TargetedMSService svc = TargetedMSService.get();
        TargetedMSService.FolderType folderType = svc.getFolderType(container);
        if(TargetedMSService.FolderType.LibraryProtein.equals(folderType))
        {
            importProteinLibState(container, libStateFile, svc);
        }
        else if (TargetedMSService.FolderType.Library.equals(folderType))
        {
            importPeptideLibraryState(container, libStateFile, svc);
        }
        else
        {
            throw new ChromLibStateException(String.format("'%s' is not a chromatogram library folder.", container));
        }
    }

    private void importProteinLibState(Container container, File libStateFile, TargetedMSService svc) throws ChromLibStateException
    {
        try (TabLoader reader = new TabLoader(libStateFile, true))
        {
            CloseableIterator<Map<String, Object>> iterator = reader.iterator();
            try
            {
                // Verify that we have the right columns
                verifyProteinLibColumns(reader.getColumns());
            }
            catch (IOException e)
            {
                throw new ChromLibStateException(String.format("Error reading header columns from file '%s'.", libStateFile));
            }
            while(iterator.hasNext())
            {
                parseProteinLibRow(iterator.next(), container, svc);
            }
        }
    }

    private void verifyProteinLibColumns(ColumnDescriptor[] columns) throws ChromLibStateException
    {
        var columnsInFile = Arrays.stream(columns).map(c -> c.name).collect(Collectors.toSet());
        boolean match = PROTEIN_LIB_HEADERS.stream().allMatch(h -> columnsInFile.contains(h));
        if(!match)
        {
            throw new ChromLibStateException(String.format("Required column headers not found. Expected columns: %s.  Found columns: %s",
                    StringUtils.join(PROTEIN_LIB_HEADERS, ","), StringUtils.join(columnsInFile, ",")));
        }
    }

    private ITargetedMSRun _currentRun;
    private Map<LibPeptideGroupKey, Long> _pepGrpKeyMap;

    private void parseProteinLibRow(Map<String, Object> row, Container container, TargetedMSService svc) throws ChromLibStateException
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
            Map map = new HashMap<String, ITargetedMSRun.RepresentativeDataState>();
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

            List<? extends IPeptideGroup> dbPepGrps = svc.getPeptideGroups(_currentRun);
            dbPepGrps.stream().forEach(p -> _pepGrpKeyMap.put(new LibPeptideGroupKey(p.getLabel(), p.getSequenceId()), p.getId()));
        }
//        if(_currentRun != null && !StringUtils.equals(skyFile, _currentRun.getFileName()))
//        {
//            if(_pepGrpKeyMap != null)
//            {
//                _pepGrpKeyMap.clear();
//            }
//            else
//            {
//                _pepGrpKeyMap = new HashMap<>();
//            }
//            ITargetedMSRun run = svc.getRunByFileName(skyFile, container);
//            if(run == null)
//            {
//                throw new ChromLibStateException(String.format("Expected a row for Skyline document '%s' in the container '%s'.", skyFile, container));
//            }
//            Map map = Collections.singletonMap(REPRESENTATIVEDATASTATE, ITargetedMSRun.RepresentativeDataState.valueOf(runState));
//            Table.update(null, svc.getTableInfoRuns(), map, _currentRun.getId());
//            _currentRun = run;
//            List<? extends IPeptideGroup> dbPepGrps = svc.getPeptideGroups(_currentRun);
//            dbPepGrps.stream().forEach(p -> _pepGrpKeyMap.put(new LibPeptideGroupKey(p.getLabel(), p.getSequenceId()), p.getId()));
//        }

        LibPeptideGroup pepGrp = parsePeptideGroup(row, _currentRun.getId());
        LibPeptideGroupKey pepGrpKey = pepGrp.getKey();
        Long dbId = _pepGrpKeyMap.get(pepGrpKey);
        if(dbId == null)
        {
            throw new IllegalStateException(String.format("Expected a row for peptide group %s in the Skyline document '%s'. Container '%s'.", pepGrp._label, skyFile, container));
        }
        Map map = new HashMap<String, RepresentativeDataState>();
        map.put(REPRESENTATIVEDATASTATE, pepGrp._state);
        // Map map = Collections.singletonMap(REPRESENTATIVEDATASTATE, pepGrp._state);
        Table.update(null, svc.getTableInfoPeptideGroup(), map, dbId);
        // Mark all the precursors for this peptide group with the same state
        List<? extends IGeneralPrecursor> precursors = svc.getPrecursors(dbId);
        for(IGeneralPrecursor precursor: precursors)
        {
            Table.update(null, svc.getTableInfoGeneralPrecursor(), map, precursor.getId());
        }
    }



    private void importPeptideLibraryState(Container container, File libStateFile, TargetedMSService svc)
    {
    }
//
//    private void doPeptideGroupUpdates(TargetedMSService svc) throws ChromLibStateException
//    {
//        List<? extends IPeptideGroup> dbPepGrps = svc.getPeptideGroups(_currentRun);
//        if(dbPepGrps.size() != _pepGrpKeyMap.size())
//        {
//            throw new IllegalStateException(String.format("Number of peptide groups do not match for run: %s. Expected %d; found %d",
//                    _currentRun.getFileName(), _pepGrpKeyMap.size(), dbPepGrps.size()));
//        }
//        for(IPeptideGroup dbPepGrp: dbPepGrps)
//        {
//            LibPeptideGroup toUpdate = getPepGrpToUpdate(dbPepGrp);
//            if(toUpdate == null)
//            {
//                throw new ChromLibStateException(String.format("Unexpected peptide group: '%s'", dbPepGrp.getLabel()));
//            }
//
//            Map map = Collections.singletonMap(REPRESENTATIVEDATASTATE, toUpdate._state);
//            Table.update(null, svc.getTableInfoPeptideGroup(), map, dbPepGrp.getId());
//
//            List<? extends IGeneralPrecursor> dbPrecursors = svc.getPrecursors(dbPepGrp);
//            for(IGeneralPrecursor dbPrecursor: dbPrecursors)
//            {
//                LibPrecursor precToUpdate = getPrecursorToUpdate(dbPepGrp, dbPrecursor);
//                if(precToUpdate == null)
//                {
//                    throw new IllegalStateException(String.format("Unexpected precursor (%s, %d) for peptide group: '%s'",
//                            dbPrecursor.getMz(), dbPrecursor.getCharge(), dbPepGrp.getLabel()));
//                }
//                map = Collections.singletonMap(REPRESENTATIVEDATASTATE, precToUpdate._state);
//                Table.update(null, svc.getTableInfoGeneralPrecursor(), map, dbPepGrp.getId());
//            }
//        }
//    }
//
//    private void parsePeptideLibRow(Map<String, Object> row, Container container, TargetedMSService svc)
//    {
//        String skyFile = String.valueOf(row.get(RUN));
//        String runState = String.valueOf(row.get(RUN_STATE));
//        if(_currentRun != null && !StringUtils.equals(skyFile, _currentRun.getFileName()))
//        {
//            if(_pepGrpKeyMap != null)
//            {
//                doUpdates(svc);
//                _pepGrpKeyMap.clear();
//            }
//            else
//            {
//                _pepGrpKeyMap = new HashMap<>();
//            }
//            ITargetedMSRun run = svc.getRunByFileName(skyFile, container);
//            if(run == null)
//            {
//                throw new IllegalStateException(String.format("Expected a row for Skyline document '%s' in the container '%s'.", skyFile, container));
//            }
//            Map map = Collections.singletonMap(REPRESENTATIVEDATASTATE, ITargetedMSRun.RepresentativeDataState.valueOf(runState));
//            Table.update(null, svc.getTableInfoRuns(), map, _currentRun.getId());
//            _currentRun = run;
//            List<? extends IPeptideGroup> currentRunPepGrps = svc.getPeptideGroups(_currentRun);
//            currentRunPepGrps.stream().forEach(p -> _pepGrpKeyMap.put(new LibPeptideGroupKey(p.getLabel(), p.getSequenceId()), new Pair(p.getSequenceId(), null)));
//        }
//
//        LibPeptideGroup pepGrp = parsePeptideGroup(row, _currentRun.getId());
//        LibPeptideGroupKey pepGrpKey = pepGrp.getKey();
//        Pair<Integer, RepresentativeDataState> idStatePair = _pepGrpKeyMap.get(pepGrpKey);
//        if(idStatePair == null)
//        {
//            throw new IllegalStateException(String.format("Expected a row for peptide group %s in the Skyline document '%s'. Container '%s'.", pepGrp._label, skyFile, container));
//        }
//        if(idStatePair.second == null)
//        {
//            Map map = Collections.singletonMap(REPRESENTATIVEDATASTATE, pepGrp._state);
//            Table.update(null, svc.getTableInfoPeptideGroup(), map, idStatePair.second);
//            _pepGrpKeyMap.put(pepGrpKey, new Pair<>(idStatePair.first, pepGrp._state))
//        }
//
//
//        LibPrecursor precursor = parsePrecursor(row);
//        _pepgrpMap.computeIfAbsent(pepGrp, k -> new ArrayList<>());
//        _pepgrpMap.get(pepGrp).add(precursor);
//    }
//
//    private void doUpdates(TargetedMSService svc)
//    {
//        List<? extends IPeptideGroup> dbPepGrps = svc.getPeptideGroups(_currentRun);
//        if(dbPepGrps.size() != _pepgrpMap.size())
//        {
//            throw new IllegalStateException(String.format("Number of peptide groups do not match for run: %s. Expected %d; found %d",
//                    _currentRun.getFileName(), _pepgrpMap.size(), dbPepGrps.size()));
//        }
//        for(IPeptideGroup dbPepGrp: dbPepGrps)
//        {
//            LibPeptideGroup toUpdate = getPepGrpToUpdate(dbPepGrp);
//            if(toUpdate == null)
//            {
//                throw new IllegalStateException(String.format("Unexpected peptide group: '%s'", dbPepGrp.getLabel()));
//            }
//
//            Map map = Collections.singletonMap(REPRESENTATIVEDATASTATE, toUpdate._state);
//            Table.update(null, svc.getTableInfoPeptideGroup(), map, dbPepGrp.getId());
//
//            List<? extends IGeneralPrecursor> dbPrecursors = svc.getPrecursors(dbPepGrp);
//            for(IGeneralPrecursor dbPrecursor: dbPrecursors)
//            {
//                LibPrecursor precToUpdate = getPrecursorToUpdate(dbPepGrp, dbPrecursor);
//                if(precToUpdate == null)
//                {
//                    throw new IllegalStateException(String.format("Unexpected precursor (%s, %d) for peptide group: '%s'",
//                            dbPrecursor.getMz(), dbPrecursor.getCharge(), dbPepGrp.getLabel()));
//                }
//                map = Collections.singletonMap(REPRESENTATIVEDATASTATE, precToUpdate._state);
//                Table.update(null, svc.getTableInfoGeneralPrecursor(), map, dbPepGrp.getId());
//            }
//        }
//    }
//
//    private LibPrecursor parsePrecursor(Map<String, Object> row)
//    {
//        double precursorMz = Double.parseDouble((String) row.get(PREC_MZ));
//        int precursorCharge = Integer.parseInt(String.valueOf(row.get(PREC_CHARGE)));
//        String precursorState = String.valueOf(row.get(PREC_STATE));
//        return new LibPrecursor(precursorMz, precursorCharge, RepresentativeDataState.valueOf(precursorState));
//    }

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

    private static boolean isLibraryFolder(Container container, TargetedMSService svc)
    {
        TargetedMSService.FolderType folderType = svc.getFolderType(container);
        return TargetedMSService.FolderType.Library.equals(folderType) || TargetedMSService.FolderType.LibraryProtein.equals(folderType);
    }

    private static List<? extends IPeptideGroup> getPeptideGroups(ITargetedMSRun run, TargetedMSService svc)
    {
        return new TableSelector(svc.getTableInfoPeptideGroup(),
                new SimpleFilter(FieldKey.fromParts("runId"), run.getId()), null)
                .getArrayList(IPeptideGroup.class);
        // return TargetedMSService.get().getPeptideGroups(run);
    }

    private static List<LibPrecursor> getPrecursors(IPeptideGroup pepGrp, TargetedMSService svc, Container c, User user)
    {
        SQLFragment sql = new SQLFragment(" SELECT gp.Id, gp.mz, gp.charge, p.modifiedsequence ")
                .append(" FROM ").append(svc.getTableInfoGeneralMolecule(), "gm")
                .append(" INNER JOIN ").append(svc.getTableInfoGeneralPrecursor(), "gp")
                .append(" ON gm.id = gp.generalmoleculeid ")
                .append(" INNER JOIN ").append(svc.getTableInfoPrecursor(), "p")
                .append(" ON gp.id = p.id ")
                .append(" WHERE gm.peptidegroupid = ?").add(pepGrp.getId());

        return new SqlSelector(svc.getUserSchema(user, c).getDbSchema(), sql).getArrayList(LibPrecursor.class);
    }

    private static List<LibMoleculePrecursor> getMoleculePrecursors(IPeptideGroup pepGrp, TargetedMSService svc, Container c, User user)
    {
        SQLFragment sql = new SQLFragment(" SELECT gp.Id, gp.mz, gp.charge, m.massMonoisotopic, m.massAverage, m.ionFormula, m.customIonName ")
                .append(" FROM ").append(svc.getTableInfoGeneralMolecule(), "gm")
                .append(" INNER JOIN ").append(svc.getTableInfoGeneralPrecursor(), "gp")
                .append(" ON gm.id = gp.generalmoleculeid ")
                .append(" INNER JOIN ").append(svc.getTableInfoMolecule(), "m")
                .append(" ON gm.id = m.id ")
                .append(" WHERE gm.peptidegroupid = ?").add(pepGrp.getId());

        return new SqlSelector(svc.getUserSchema(user, c).getDbSchema(), sql).getArrayList(LibMoleculePrecursor.class);
    }

    private class LibPrecursor
    {
        // Fields from the GeneralPrecursor table
        private long _precursorId;
        private double _precursorMz;
        private int _charge;
        // Fields from the Precursor table
        private String _modifiedSequence;

        public long getPrecursorId()
        {
            return _precursorId;
        }

        public void setPrecursorId(long precursorId)
        {
            _precursorId = precursorId;
        }

        public double getPrecursorMz()
        {
            return _precursorMz;
        }

        public void setPrecursorMz(double precursorMz)
        {
            _precursorMz = precursorMz;
        }

        public int getCharge()
        {
            return _charge;
        }

        public void setCharge(int charge)
        {
            _charge = charge;
        }

        public String getModifiedSequence()
        {
            return _modifiedSequence;
        }

        public void setModifiedSequence(String modifiedSequence)
        {
            _modifiedSequence = modifiedSequence;
        }
    }

    private class LibMoleculePrecursor
    {
        // Fields from the GeneralPrecursor table
        private long _precursorId;
        private double _precursorMz;
        private int _charge;
        // Fields from the Molecule table
        private Double _massMonoisotopic; // not null
        private Double _massAverage; // not null
        private String _ionFormula;
        private String _customIonName;

        public long getPrecursorId()
        {
            return _precursorId;
        }

        public void setPrecursorId(long precursorId)
        {
            _precursorId = precursorId;
        }

        public double getPrecursorMz()
        {
            return _precursorMz;
        }

        public void setPrecursorMz(double precursorMz)
        {
            _precursorMz = precursorMz;
        }

        public int getCharge()
        {
            return _charge;
        }

        public void setCharge(int charge)
        {
            _charge = charge;
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
    }

    /**
     * Prepares a statement for reuse during the import process. For tables that have a lot of rows, this is worth the
     * tradeoff between having more custom code and the perf hit from Table.insert() having to prep a statement for
     * every row
     */
    private PreparedStatement ensureStatement(PreparedStatement stmt, String sql /*, boolean reselect*/)
    {
        if (stmt == null)
        {
            try
            {
                assert PanoramaPublicManager.getSchema().getScope().isTransactionActive();
                Connection c = PanoramaPublicManager.getSchema().getScope().getConnection();
//                if (reselect)
//                {
//                    SQLFragment reselectSQL = new SQLFragment(sql);
//                    // All we really need is a ColumnInfo of the right name and type, so choose one of the TableInfos to supply it
//                    TargetedMSManager.getSchema().getSqlDialect().addReselect(reselectSQL, getTableInfoTransitionChromInfo().getColumn("Id"), null);
//                    sql = reselectSQL.getSQL();
//                }
                stmt = c.prepareStatement(sql);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
        return stmt;
    }

    private class LibPeptideGroupKey
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

    private class LibPeptideGroup
    {
        private final String _label;
        private final Integer _seqId;
        private final long _runId;
        private final RepresentativeDataState _state;

        private LibPeptideGroup(long runId, String label, Integer seqId, RepresentativeDataState state)
        {
            _runId = runId;
            _label = label;
            _seqId = seqId;
            _state = state;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LibPeptideGroup that = (LibPeptideGroup) o;
            return _runId == that._runId && Objects.equals(_label, that._label) && Objects.equals(_seqId, that._seqId) && _state == that._state;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(_label, _seqId, _runId, _state);
        }

        public LibPeptideGroupKey getKey()
        {
            return new LibPeptideGroupKey(_label, _seqId);
        }
    }

    private class LibPrecursorKey
    {
        private final double _mz;
        private final int _charge;

        public LibPrecursorKey(double mz, int charge)
        {
            _mz = mz;
            _charge = charge;
        }
    }
}

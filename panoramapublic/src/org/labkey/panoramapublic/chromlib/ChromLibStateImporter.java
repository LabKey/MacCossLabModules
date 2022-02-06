package org.labkey.panoramapublic.chromlib;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.RepresentativeDataState;
import org.labkey.api.targetedms.RunRepresentativeDataState;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.panoramapublic.PanoramaPublicSchema;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.labkey.panoramapublic.chromlib.ChromLibStateManager.*;
import static org.labkey.panoramapublic.chromlib.ChromLibStateManager.getMoleculePrecursors;
import static org.labkey.panoramapublic.chromlib.ChromLibStateManager.getPeptideGroups;
import static org.labkey.panoramapublic.chromlib.ChromLibStateManager.getPrecursors;


public abstract class ChromLibStateImporter
{
    protected final Logger _log;
    protected final Container _container;
    protected final User _user;
    protected final TargetedMSService _svc;
    protected ITargetedMSRun _currentRun;
    // Issue 44819: Duplicate proteins in a Skyline document cause an error in importing chromatogram library state
    // There may be more than one protein in a Skyline document with the same key (label, sequenceId). Keep the database
    // ids of all proteins in the document with the same key.
    protected Map<LibPeptideGroup.LibPeptideGroupKey, List<Long>> _pepGrpKeyMap;

    abstract String libTypeString();
    abstract List<String> getExpectedColumns();
    abstract List<Header<?>> getHeaders();
    abstract void updateRows();

    public ChromLibStateImporter(Container container, User user, Logger log, TargetedMSService svc)
    {
        _container = container;
        _user = user;
        _log = log;
        _svc = svc;
    }

    public static void importLibState(File libStateFile, Container container, User user, Logger log) throws ChromLibStateException
    {
        TargetedMSService svc = TargetedMSService.get();
        TargetedMSService.FolderType folderType = svc.getFolderType(container);
        switch (folderType)
        {
            case LibraryProtein -> new ProteinLibStateImporter(container, user, log, svc).importFromFile(libStateFile);
            case Library -> new PeptideLibStateImporter(container, user, log, svc).importFromFile(libStateFile);
            default -> throw new ChromLibStateException(String.format("'%s' is not a chromatogram library folder.", container));
        }
    }

    void importFromFile(File libStateFile) throws ChromLibStateException
    {
        DbScope.Transaction transaction = PanoramaPublicSchema.getSchema().getScope().getCurrentTransaction();
        if (transaction == null)
        {
            throw new IllegalStateException("Callers should start their own transaction");
        }

        _log.info(String.format("Importing %s library state in container '%s' to file '%s'.", libTypeString(), _container.getPath(), libStateFile.getPath()));

        try (TabLoader reader = new TabLoader(libStateFile, true))
        {
            CloseableIterator<Map<String, Object>> iterator = reader.iterator();
            try
            {
                // Verify that we have the right column headers
                var columns = reader.getColumns();
                verifyLibColumns(columns, getExpectedColumns());
                setColumnTypes(columns);
            }
            catch (IOException e)
            {
                throw new ChromLibStateException(String.format("Error reading header columns from file '%s'.", libStateFile));
            }
            while(iterator.hasNext())
            {
                parseLibStateRow(iterator.next());
            }
        }
        updateRows();
        _log.info("Done importing library state.");
    }

    private void verifyLibColumns(ColumnDescriptor[] columns, List<String> expectedColumns) throws ChromLibStateException
    {
        var columnsInFile = Arrays.stream(columns).map(c -> c.name).collect(Collectors.toSet());
        if (!columnsInFile.containsAll(expectedColumns))
        {
            throw new ChromLibStateException(String.format("Required column headers not found. Expected columns: %s.  Found columns: %s",
                    StringUtils.join(expectedColumns, ","), StringUtils.join(columnsInFile, ",")));
        }
    }

    protected void setColumnTypes(ColumnDescriptor[] columns)
    {
        var columnMap = getHeaders().stream().collect(Collectors.toMap(h -> h._name, h -> h._type));
        Arrays.stream(columns).filter(col -> columnMap.containsKey(col.name)).forEach(col -> col.clazz = columnMap.get(col.name));
    }

    void parseLibStateRow(Map<String, Object> row) throws ChromLibStateException
    {
        String skyFile = getSkyFile(row);
        String runState = String.valueOf(row.get(RUN_STATE));
        if (_currentRun == null || !StringUtils.equals(skyFile, _currentRun.getFileName()))
        {
            ITargetedMSRun run = _svc.getRunByFileName(skyFile, _container);
            if (run == null)
            {
                throw new ChromLibStateException(String.format("Expected a row for Skyline document '%s' in the container '%s'.", skyFile, _container));
            }
            _currentRun = run;
            var map = new HashMap<String, RunRepresentativeDataState>();
            map.put(REPRESENTATIVEDATASTATE, RunRepresentativeDataState.valueOf(runState));
            Table.update(null, _svc.getTableInfoRuns(), map, _currentRun.getId());
            _log.info(String.format("Importing library state of %ss in '%s'.", libTypeString(), run.getFileName()));

            onNextRun();

            List<LibPeptideGroup> dbPepGrps = getPeptideGroups(run, _svc);
            for (var dbPepGrp: dbPepGrps)
            {
                var dbIds = _pepGrpKeyMap.computeIfAbsent(dbPepGrp.getKey(), l -> new ArrayList<>());
                dbIds.add(dbPepGrp.getId());
            }
        }
    }

    void onNextRun()
    {
        updateRows();
        if (_pepGrpKeyMap != null)
        {
            _pepGrpKeyMap.clear();
        }
        else
        {
            _pepGrpKeyMap = new HashMap<>();
        }
    }

    LibPeptideGroup parsePeptideGroup(Map<String, Object> row, long runId)
    {
        String peptideGrp = (String) row.get(PEP_GRP);
        Integer seqId = (Integer) row.get(PEP_GRP_SEQ_ID);
        String pepGrpState = (String) row.get(PEP_GRP_STATE);
        return new LibPeptideGroup(runId, peptideGrp, seqId, RepresentativeDataState.valueOf(pepGrpState));
    }

    List<LibPeptideGroup> getPeptideGroupDbMatches(LibPeptideGroup tsvPepGrp, String skyFile, Container container) throws ChromLibStateException
    {
        var dbIds = _pepGrpKeyMap.get(tsvPepGrp.getKey());
        if (dbIds == null || dbIds.size() == 0)
        {
            throw new ChromLibStateException(String.format("Expected a db row for peptide group '%s' in the Skyline document '%s'. Container '%s'.",
                    tsvPepGrp.getLabel(), skyFile, container.getPath()));
        }
        return dbIds.stream().map(dbId -> new LibPeptideGroup(dbId, tsvPepGrp)).collect(Collectors.toList());
    }

    String getSkyFile(Map<String, Object> row)
    {
        return (String) row.get(RUN);
    }

    void updateState(TableInfo tableInfo, List<Long> entityIds, RepresentativeDataState state, Container container, User user, TargetedMSService svc, Logger log)
    {
        SQLFragment sql = new SQLFragment(" UPDATE ")
                .append(tableInfo, "")
                .append(" SET ").append(REPRESENTATIVEDATASTATE).append(" = ? ").add(state.ordinal())
                .append(" WHERE Id ");
        DbSchema schema = svc.getUserSchema(user, container).getDbSchema();
        schema.getSqlDialect().appendInClauseSql(sql, entityIds);
        int updated = new SqlExecutor(schema).execute(sql);
        log.debug("UPDATED " + updated + " rows in " + tableInfo.getName());
    }

    private static class ProteinLibStateImporter extends ChromLibStateImporter
    {
        private Map<RepresentativeDataState, List<Long>> _pepGrpsForState;

        public ProteinLibStateImporter(Container container, User user, Logger log, TargetedMSService svc)
        {
            super(container, user, log, svc);
            _pepGrpsForState = new HashMap<>();
        }

        @Override
        String libTypeString()
        {
            return "protein";
        }

        @Override
        List<String> getExpectedColumns()
        {
            return PROTEIN_LIB_COLS;
        }

        @Override
        List<Header<?>> getHeaders()
        {
            return PROTEIN_LIB_HEADERS;
        }

        @Override
        void onNextRun()
        {
            super.onNextRun();
            _pepGrpsForState = new HashMap<>();
        }

        @Override
        void updateRows()
        {
            for (RepresentativeDataState state: _pepGrpsForState.keySet())
            {
                updatePepGrpState(_pepGrpsForState.get(state), state, _container, _user, _svc, _log);
            }
            _pepGrpsForState.clear();
        }

        @Override
        void parseLibStateRow(Map<String, Object> row) throws ChromLibStateException
        {
            super.parseLibStateRow(row);

            var pepGrpMatches = getPeptideGroupDbMatches(parsePeptideGroup(row, _currentRun.getId()), getSkyFile(row), _container);
            if (pepGrpMatches.size() > 1)
            {
                // We do not expect duplicate proteins in a Skyline document in a protein library folder.
                // This is enforced during document import (SkylineDocImporter.insertPeptideGroup).
                throw new ChromLibStateException(String.format("Expected one db row for peptide group '%s' in the Skyline document '%s'. Container '%s'. " +
                                "Found %d rows (Ids: %s).",
                        pepGrpMatches.get(0).getLabel(), getSkyFile(row), _container.getPath(), pepGrpMatches.size(),
                        StringUtils.join(pepGrpMatches.stream().map(LibPeptideGroup::getId).collect(Collectors.toList()), ',')));
            }
            var pepGrp = pepGrpMatches.get(0);
            if (pepGrp.getRepresentativeDataState() != RepresentativeDataState.NotRepresentative)
            {
                List<Long> pepGrpIdsForState = _pepGrpsForState.computeIfAbsent(pepGrp.getRepresentativeDataState(), l -> new ArrayList<>());
                pepGrpIdsForState.add(pepGrp.getId());
            }
        }

        private void updatePepGrpState(List<Long> pepGrpIds, RepresentativeDataState state, Container container, User user, TargetedMSService svc, Logger log)
        {
            updateState(svc.getTableInfoPeptideGroup(), pepGrpIds, state, container, user, svc, log);
            // Set the representative state for all the precursors to be the same as the state of the peptide groups
            updatePrecursorState(pepGrpIds, state, container, user, svc, log);
        }

        private void updatePrecursorState(List<Long> pepGrpIds, RepresentativeDataState state, Container container, User user, TargetedMSService svc, Logger log)
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
                    .append(" SET ").append(REPRESENTATIVEDATASTATE).append(" = ? ").add(state.ordinal())
                    .append(" FROM ")
                    .append(svc.getTableInfoGeneralMolecule(), "gm")
                    .append(" INNER JOIN ")
                    .append(svc.getTableInfoPeptideGroup(), "pg").append(" ON pg.Id = gm.peptideGroupId ")
                    .append(" WHERE pg.Id ");
            PanoramaPublicSchema.getSchema().getSqlDialect().appendInClauseSql(sql, pepGrpIds);
            sql.append(" AND gm.Id = gp.generalMoleculeId ");

            int updated = new SqlExecutor(svc.getUserSchema(user, container).getDbSchema()).execute(sql);
            log.debug("UPDATED " + updated + " rows in " + svc.getTableInfoGeneralMolecule().getName());
        }
    }

    private static class PeptideLibStateImporter extends ChromLibStateImporter
    {
        private PeptideGroupKeyPrecursorMatches _currentPepGrpMatches;
        private Map<RepresentativeDataState, List<Long>> _precursorsForState;

        public PeptideLibStateImporter(Container container, User user, Logger log, TargetedMSService svc)
        {
            super(container, user, log, svc);
            _precursorsForState = new HashMap<>();
        }

        @Override
        String libTypeString()
        {
            return "peptide";
        }

        @Override
        List<String> getExpectedColumns()
        {
            return PEP_LIB_COLS;
        }

        @Override
        List<Header<?>> getHeaders()
        {
            return PEP_LIB_HEADERS;
        }

        @Override
        void parseLibStateRow(Map<String, Object> row) throws ChromLibStateException
        {
            super.parseLibStateRow(row);

            LibPeptideGroup tsvPepGrp = parsePeptideGroup(row, _currentRun.getId());
            if (_currentPepGrpMatches == null || !_currentPepGrpMatches.getKey().equals(tsvPepGrp.getKey()))
            {
                _currentPepGrpMatches = getPepGrpPrecursorMatches(tsvPepGrp, getSkyFile(row), _container);
            }

            LibGeneralPrecursor precursor = _currentPepGrpMatches.getPrecursorDbMatch(parsePrecursor(row), getSkyFile(row), _container);
            if (RepresentativeDataState.NotRepresentative != precursor.getRepresentativeDataState())
            {
                List<Long> precursorIdsForState = _precursorsForState.computeIfAbsent(precursor.getRepresentativeDataState(), l -> new ArrayList<>());
                precursorIdsForState.add(precursor.getId());
            }
        }

        private PeptideGroupKeyPrecursorMatches getPepGrpPrecursorMatches(LibPeptideGroup tsvPepGrp, String skyFile, Container container) throws ChromLibStateException
        {
            var pepGrps = getPeptideGroupDbMatches(tsvPepGrp, skyFile, container);
            var pepGrpPrecursors = new ArrayList<PeptideGroupPrecursors>();
            for (var pepGrp: pepGrps)
            {
                var precursorKeyMap = new HashMap<LibGeneralPrecursor.LibPrecursorKey, Long>();

                List<LibPrecursor> dbPrecursors = getPrecursors(pepGrp, _container, _user, _svc);
                dbPrecursors.forEach(p -> precursorKeyMap.put(p.getKey(), p.getId()));
                List<LibMoleculePrecursor> dbMoleculePrecursors = getMoleculePrecursors(pepGrp, _container, _user, _svc);
                dbMoleculePrecursors.forEach(p -> precursorKeyMap.put(p.getKey(), p.getId()));

                pepGrpPrecursors.add(new PeptideGroupPrecursors(pepGrp, precursorKeyMap));
            }
            return new PeptideGroupKeyPrecursorMatches(tsvPepGrp.getKey(), pepGrpPrecursors);
        }

        private void updatePrecursorState(List<Long> precursorIds, RepresentativeDataState state, Container container, User user, TargetedMSService svc, Logger log)
        {
            updateState(svc.getTableInfoGeneralPrecursor(), precursorIds, state, container, user, svc, log);
        }

        @Override
        void onNextRun()
        {
            super.onNextRun();
            _currentPepGrpMatches = null;
            _precursorsForState = new HashMap<>();
        }

        @Override
        void updateRows()
        {
            for (RepresentativeDataState state: _precursorsForState.keySet())
            {
                updatePrecursorState(_precursorsForState.get(state), state, _container, _user, _svc, _log);
            }
            _precursorsForState.clear();
        }

        private LibGeneralPrecursor parsePrecursor(Map<String, Object> row)
        {
            double precursorMz = (Double) row.get(PREC_MZ);
            int precursorCharge = (Integer) row.get(PREC_CHARGE);
            String precursorState = (String) row.get(PREC_STATE);
            String modifiedSeq = (String) row.get(PREC_MOD_SEQ);

            LibGeneralPrecursor precursor;
            if (!StringUtils.isBlank(modifiedSeq))
            {
                precursor = new LibPrecursor(0, precursorMz, precursorCharge, RepresentativeDataState.valueOf(precursorState), modifiedSeq);
            }
            else
            {
                String customIonName = (String) row.get(PREC_MOL_CUSTOM_ION_NAME);
                String ionFormula = (String) row.get(PREC_MOL_ION_FORMULA);
                Double massMonoIsotopic = (Double) row.get(PREC_MOL_MASS_MONOISOTOPIC);
                Double massAverage = (Double) row.get(PREC_MOL_MASS_AVERAGE);
                precursor = new LibMoleculePrecursor(0, precursorMz, precursorCharge, RepresentativeDataState.valueOf(precursorState),
                        customIonName, ionFormula, massMonoIsotopic, massAverage);
            }

            return precursor;
        }
    }

    // Class that stores information for database precursors in peptide groups that match the given peptide group key.
    // In a peptide library folder, duplicate proteins having the same key are allowed. When we are looking for a
    // database match for a precursor read from the TSV, we should look at all the peptide groups that match the
    // peptide group key (label, sequenceId) in the TSV row. Precursors in a document imported to a peptide library
    // should be unique (SkylineDocImporter.insertPrecursor) so we expect to find only one match for each precursor.
    // Issue 44819: Duplicate proteins in a Skyline document cause an error in importing chromatogram library state
    private static class PeptideGroupKeyPrecursorMatches
    {
        private final LibPeptideGroup.LibPeptideGroupKey _pepGrpKey;
        private final List<PeptideGroupPrecursors> _pepGrpPrecursorsList;

        public PeptideGroupKeyPrecursorMatches(LibPeptideGroup.LibPeptideGroupKey pepGrpKey, List<PeptideGroupPrecursors> pepGrpPrecursorsList)
        {
            _pepGrpKey = pepGrpKey;
            _pepGrpPrecursorsList = pepGrpPrecursorsList;
        }

        LibPeptideGroup.LibPeptideGroupKey getKey()
        {
            return _pepGrpKey;
        }

        LibGeneralPrecursor getPrecursorDbMatch(LibGeneralPrecursor tsvPrecursor, String skyFile, Container container)
        {
            var dbPepGrpMatches = _pepGrpPrecursorsList.stream().filter(p -> p.hasPrecursorDbMatch(tsvPrecursor)).collect(Collectors.toList());

            if (dbPepGrpMatches.size() == 0)
            {
                throw new IllegalStateException(String.format("Expected a db row for precursor %s in the peptide group '%s'. Skyline document '%s'. Folder '%s'.",
                        tsvPrecursor.getKey(), _pepGrpKey.toString(), skyFile, container.getPath()));
            }
            if (dbPepGrpMatches.size() > 1)
            {
                // We do not expect duplicate precursors in a peptide library.
                // This is enforced during document import (SkylineDocImporter.insertPrecursor).
                throw new IllegalStateException(String.format("Expected one db row for precursor %s in the peptide group '%s'. Skyline document '%s'. Folder '%s'. Found %d matches.",
                        tsvPrecursor.getKey(), _pepGrpKey.toString(), skyFile, container.getPath(), dbPepGrpMatches.size()));
            }

            var pepGrpMatch = dbPepGrpMatches.get(0);
            tsvPrecursor.setPeptideGroupId(pepGrpMatch.getDbPepGrpId());
            tsvPrecursor.setId(pepGrpMatch.getPrecursorDbId(tsvPrecursor));
            return tsvPrecursor;
        }
    }

    private static class PeptideGroupPrecursors
    {
        private final LibPeptideGroup _pepGrp;
        private final Map<LibGeneralPrecursor.LibPrecursorKey, Long> _precursorKeyMap;

        public PeptideGroupPrecursors(LibPeptideGroup pepGrp, Map<LibGeneralPrecursor.LibPrecursorKey, Long> precursorKeyMap)
        {
            _pepGrp = pepGrp;
            _precursorKeyMap = precursorKeyMap;
        }

        boolean hasPrecursorDbMatch(LibGeneralPrecursor precursor)
        {
            return _precursorKeyMap.get(precursor.getKey()) != null;
        }

        public long getDbPepGrpId()
        {
            return _pepGrp.getId();
        }

        public Long getPrecursorDbId(LibGeneralPrecursor precursor)
        {
            return _precursorKeyMap.get(precursor.getKey());
        }
    }
}

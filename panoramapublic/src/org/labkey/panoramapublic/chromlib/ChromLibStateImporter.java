package org.labkey.panoramapublic.chromlib;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.RepresentativeDataState;
import org.labkey.api.targetedms.RunRepresentativeDataState;
import org.labkey.api.targetedms.TargetedMSService;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.labkey.panoramapublic.chromlib.ChromLibStateManager.PEP_GRP;
import static org.labkey.panoramapublic.chromlib.ChromLibStateManager.PEP_GRP_SEQ_ID;
import static org.labkey.panoramapublic.chromlib.ChromLibStateManager.PEP_GRP_STATE;
import static org.labkey.panoramapublic.chromlib.ChromLibStateManager.PEP_LIB_HEADERS;
import static org.labkey.panoramapublic.chromlib.ChromLibStateManager.PREC_CHARGE;
import static org.labkey.panoramapublic.chromlib.ChromLibStateManager.PREC_MOD_SEQ;
import static org.labkey.panoramapublic.chromlib.ChromLibStateManager.PREC_MOL_CUSTOM_ION_NAME;
import static org.labkey.panoramapublic.chromlib.ChromLibStateManager.PREC_MOL_ION_FORMULA;
import static org.labkey.panoramapublic.chromlib.ChromLibStateManager.PREC_MOL_MASS_AVERAGE;
import static org.labkey.panoramapublic.chromlib.ChromLibStateManager.PREC_MOL_MASS_MONOISOTOPIC;
import static org.labkey.panoramapublic.chromlib.ChromLibStateManager.PREC_MZ;
import static org.labkey.panoramapublic.chromlib.ChromLibStateManager.PREC_STATE;
import static org.labkey.panoramapublic.chromlib.ChromLibStateManager.PROTEIN_LIB_HEADERS;
import static org.labkey.panoramapublic.chromlib.ChromLibStateManager.REPRESENTATIVEDATASTATE;
import static org.labkey.panoramapublic.chromlib.ChromLibStateManager.RUN;
import static org.labkey.panoramapublic.chromlib.ChromLibStateManager.RUN_STATE;
import static org.labkey.panoramapublic.chromlib.ChromLibStateManager.getMoleculePrecursors;
import static org.labkey.panoramapublic.chromlib.ChromLibStateManager.getPeptideGroups;
import static org.labkey.panoramapublic.chromlib.ChromLibStateManager.getPrecursors;


public abstract class ChromLibStateImporter
{
    private ITargetedMSRun _currentRun;
    private Map<LibPeptideGroup.LibPeptideGroupKey, Long> _pepGrpKeyMap;
    private final Logger _log;

    abstract String libTypeString();
    abstract List<String> getExpectedColumns() throws ChromLibStateException;

    public ChromLibStateImporter(Logger log)
    {
        _log = log;
    }

    public static void importLibState(File libStateFile, Container container, User user, Logger log) throws ChromLibStateException
    {
        TargetedMSService svc = TargetedMSService.get();
        TargetedMSService.FolderType folderType = svc.getFolderType(container);
        if(TargetedMSService.FolderType.LibraryProtein.equals(folderType))
        {
            new ProteinLibStateImporter(log).importFromFile(container, user, libStateFile, svc);
        }
        else if (TargetedMSService.FolderType.Library.equals(folderType))
        {
            new PeptideLibStateImporter(log).importFromFile(container, user, libStateFile, svc);
        }
        else
        {
            throw new ChromLibStateException(String.format("'%s' is not a chromatogram library folder.", container));
        }
    }

    void importFromFile(Container container, User user, File libStateFile, TargetedMSService svc) throws ChromLibStateException
    {
        _log.info(String.format("Importing %s library state in container '%s' to file '%s'.", libTypeString(), container.getPath(), libStateFile.getPath()));

        try (TabLoader reader = new TabLoader(libStateFile, true))
        {
            CloseableIterator<Map<String, Object>> iterator = reader.iterator();
            try
            {
                // Verify that we have the right column headers
                verifyLibColumns(reader.getColumns(), getExpectedColumns());
            }
            catch (IOException e)
            {
                throw new ChromLibStateException(String.format("Error reading header columns from file '%s'.", libStateFile));
            }
            while(iterator.hasNext())
            {
                parseLibStateRow(iterator.next(), container, user, svc, _log);
            }
        }
        _log.info("Done importing library state.");
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

    void parseLibStateRow(Map<String, Object> row, Container container, User user, TargetedMSService svc, Logger log) throws ChromLibStateException
    {
        String skyFile = getSkyFile(row);
        String runState = String.valueOf(row.get(RUN_STATE));
        if(_currentRun == null || !StringUtils.equals(skyFile, _currentRun.getFileName()))
        {
            ITargetedMSRun run = svc.getRunByFileName(skyFile, container);
            if(run == null)
            {
                throw new ChromLibStateException(String.format("Expected a row for Skyline document '%s' in the container '%s'.", skyFile, container));
            }
            _currentRun = run;
            var map = new HashMap<String, RunRepresentativeDataState>();
            map.put(REPRESENTATIVEDATASTATE, RunRepresentativeDataState.valueOf(runState));
            Table.update(null, svc.getTableInfoRuns(), map, _currentRun.getId());
            _log.info(String.format("Importing library state of %ss in '%s'.", libTypeString(), run.getFileName()));

            onRunChanged();

            List<LibPeptideGroup> dbPepGrps = getPeptideGroups(run, svc);
            dbPepGrps.forEach(p -> _pepGrpKeyMap.put(p.getKey(), p.getId()));
        }
    }

    void onRunChanged()
    {
        if(_pepGrpKeyMap != null)
        {
            _pepGrpKeyMap.clear();
        }
        else
        {
            _pepGrpKeyMap = new HashMap<>();
        }
    }

    LibPeptideGroup parsePeptideGroup(Map<String, Object> row, long runId, Container container) throws ChromLibStateException
    {
        String peptideGrp = String.valueOf(row.get(PEP_GRP));
        Integer seqId = null;
        if(row.get(PEP_GRP_SEQ_ID) != null)
        {
            seqId = Integer.parseInt(String.valueOf(row.get(PEP_GRP_SEQ_ID)));
        }
        String pepGrpState = String.valueOf(row.get(PEP_GRP_STATE));
        var pepGrp = new LibPeptideGroup(runId, peptideGrp, seqId, RepresentativeDataState.valueOf(pepGrpState));
        LibPeptideGroup.LibPeptideGroupKey pepGrpKey = pepGrp.getKey();
        Long dbId = _pepGrpKeyMap.get(pepGrpKey);
        if(dbId == null)
        {
            throw new ChromLibStateException(String.format("Expected a row for peptide group %s in the Skyline document '%s'. Container '%s'.", pepGrp.getLabel(), getSkyFile(row), container));
        }
        pepGrp.setId(dbId);
        return pepGrp;
    }

    String getSkyFile(Map<String, Object> row)
    {
        return String.valueOf(row.get(RUN));
    }

    ITargetedMSRun getCurrentRun()
    {
        return _currentRun;
    }

    private static class ProteinLibStateImporter extends ChromLibStateImporter
    {
        public ProteinLibStateImporter(Logger log)
        {
            super(log);
        }

        @Override
        String libTypeString()
        {
            return "protein";
        }

        @Override
        List<String> getExpectedColumns()
        {
            return PROTEIN_LIB_HEADERS;
        }

        @Override
        void parseLibStateRow(Map<String, Object> row, Container container, User user, TargetedMSService svc, Logger log) throws ChromLibStateException
        {
            super.parseLibStateRow(row, container, user, svc, log);

            LibPeptideGroup pepGrp = parsePeptideGroup(row, getCurrentRun().getId(), container);
            var map = new HashMap<String, RepresentativeDataState>();
            map.put(REPRESENTATIVEDATASTATE, pepGrp.getRepresentativeDataState());
            Table.update(null, svc.getTableInfoPeptideGroup(), map, pepGrp.getId());
            // Set the representative state for all the precursors to be the same as the state of the peptide group
            updatePrecursorState(pepGrp, container, user, svc, log);
        }

        private void updatePrecursorState(LibPeptideGroup pepGrp, Container container, User user, TargetedMSService svc, Logger log)
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
            log.debug("UPDATED rows " + updated);
        }
    }

    private static class PeptideLibStateImporter extends ChromLibStateImporter
    {
        private LibPeptideGroup _currentPepGrp;
        private Map<LibGeneralPrecursor.LibPrecursorKey, Long> _precursorKeyMap;

        public PeptideLibStateImporter(Logger log)
        {
            super(log);
        }

        @Override
        String libTypeString()
        {
            return "peptide";
        }

        @Override
        List<String> getExpectedColumns()
        {
            return PEP_LIB_HEADERS;
        }

        @Override
        void parseLibStateRow(Map<String, Object> row, Container container, User user, TargetedMSService svc, Logger log) throws ChromLibStateException
        {
            super.parseLibStateRow(row, container, user, svc, log);

            LibPeptideGroup pepGrp = parsePeptideGroup(row, getCurrentRun().getId(), container);
            if(_currentPepGrp == null || !_currentPepGrp.getKey().equals(pepGrp.getKey()))
            {
                _currentPepGrp = pepGrp;

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
                        precursor.getKey().toString(), pepGrp.getLabel(), getSkyFile(row), container));
            }
            var map = new HashMap<String, RepresentativeDataState>();
            map.put(REPRESENTATIVEDATASTATE, precursor.getRepresentativeDataState());
            Table.update(null, svc.getTableInfoGeneralPrecursor(), map, generalPrecursorId);
        }

        @Override
        void onRunChanged()
        {
            super.onRunChanged();
            _currentPepGrp = null;
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
    }
}

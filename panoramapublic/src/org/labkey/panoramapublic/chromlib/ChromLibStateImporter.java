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

import static org.labkey.panoramapublic.chromlib.ChromLibStateManager.*;

public class ChromLibStateImporter
{
    private ITargetedMSRun _currentRun;
    private Map<LibPeptideGroupKey, Long> _pepGrpKeyMap;
    private LibPeptideGroup _currentPepGrp;
    private Map<LibPrecursorKey, Long> _precursorKeyMap;

    private final Logger _log;

    public ChromLibStateImporter(Logger log)
    {
        _log = log;
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
                // Verify that we have the right column headers
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
            var map = new HashMap<String, RunRepresentativeDataState>();
            map.put(REPRESENTATIVEDATASTATE, RunRepresentativeDataState.valueOf(runState));
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
            throw new IllegalStateException(String.format("Expected a row for peptide group %s in the Skyline document '%s'. Container '%s'.", pepGrp.getLabel(), skyFile, container));
        }
        var map = new HashMap<String, RepresentativeDataState>();
        map.put(REPRESENTATIVEDATASTATE, pepGrp.getRepresentativeDataState());
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
        _log.debug("UPDATED rows " + updated);
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
            var map = new HashMap<String, RunRepresentativeDataState>();
            map.put(REPRESENTATIVEDATASTATE, RunRepresentativeDataState.valueOf(runState));
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
                throw new IllegalStateException(String.format("Expected a row for peptide group %s in the Skyline document '%s'. Container '%s'.", pepGrp.getLabel(), skyFile, container));
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
}

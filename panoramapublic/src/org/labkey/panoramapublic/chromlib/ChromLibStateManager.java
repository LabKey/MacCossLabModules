package org.labkey.panoramapublic.chromlib;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.RepresentativeDataState;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.FileUtil;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ChromLibStateManager
{
    static final String RUN = "Run";
    static final String RUN_STATE = "Run_State";
    static final String PEP_GRP = "Peptide_Group";
    static final String PEP_GRP_SEQ_ID = "Peptide_Group_Seq_Id";
    static final String PEP_GRP_STATE = "Peptide_Group_State";
    static final String PREC_MZ = "Precursor_mz";
    static final String PREC_CHARGE = "Precursor_Charge";
    static final String PREC_STATE = "Precursor_State";
    static final String PREC_MOD_SEQ = "Precursor_Modified_Sequence";
    static final String PREC_MOL_CUSTOM_ION_NAME = "Precursor_Mol_Custom_Ion_Name";
    static final String PREC_MOL_ION_FORMULA = "Precursor_Mol_Ion_Formula";
    static final String PREC_MOL_MASS_MONOISOTOPIC = "Precursor_Mol_Mass_Monoisotopic";
    static final String PREC_MOL_MASS_AVERAGE = "Precursor_Mol_Mass_Average";

    static final List<String> PROTEIN_LIB_HEADERS = List.of(RUN, RUN_STATE, PEP_GRP, PEP_GRP_SEQ_ID, PEP_GRP_STATE);
    static final List<String> PEP_LIB_HEADERS = List.of(RUN, RUN_STATE, PEP_GRP, PEP_GRP_SEQ_ID, PEP_GRP_STATE,
            PREC_MZ, PREC_CHARGE, PREC_STATE,
            PREC_MOD_SEQ, PREC_MOL_CUSTOM_ION_NAME, PREC_MOL_ION_FORMULA, PREC_MOL_MASS_MONOISOTOPIC, PREC_MOL_MASS_AVERAGE);

    static final String REPRESENTATIVEDATASTATE = "representativedatastate";

    public void copyLibraryState(Container sourceContainer, Container targetContainer, Logger log, User user) throws ChromLibStateException
    {
        Path chromLibDir = getChromLibDir(targetContainer);
        Path chromLibExportFile = chromLibDir.resolve(FileUtil.makeFileNameWithTimestamp("chrom_lib_state_export_" + sourceContainer.getRowId(), "tsv"));
        new ChromLibStateExporter(log).exportLibState(chromLibExportFile.toFile(), sourceContainer, user);
        new ChromLibStateImporter(log).importLibState(chromLibExportFile.toFile(), targetContainer, user);
    }

    public static Path getChromLibDir(Container container)
    {
        PipeRoot root = PipelineService.get().getPipelineRootSetting(container);
        assert root != null;
        return root.getRootNioPath().resolve(TargetedMSService.CHROM_LIB_FILE_DIR);
    }

    static List<LibPeptideGroup> getPeptideGroups(ITargetedMSRun run, TargetedMSService svc)
    {
        return new TableSelector(svc.getTableInfoPeptideGroup(),
                Set.of("id", "runId", "label", "sequenceId", "representativedatastate"),
                new SimpleFilter(FieldKey.fromParts("runId"), run.getId()), null)
                .getArrayList(LibPeptideGroup.class);
    }

    static List<LibPrecursor> getPrecursors(LibPeptideGroup pepGrp, Container c, User user, TargetedMSService svc)
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

    static List<LibMoleculePrecursor> getMoleculePrecursors(LibPeptideGroup pepGrp, Container c, User user, TargetedMSService svc)
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

        LibPeptideGroup(long runId, String label, Integer sequenceId, RepresentativeDataState representativeDataState)
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

        LibPrecursor(long peptideGroupId, double precursorMz, int charge, RepresentativeDataState state, String modifiedSequence)
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

        LibMoleculePrecursor(long peptideGroupId, double precursorMz, int charge, RepresentativeDataState state,
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

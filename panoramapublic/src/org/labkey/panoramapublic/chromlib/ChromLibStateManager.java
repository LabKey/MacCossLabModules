package org.labkey.panoramapublic.chromlib;

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
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.FileUtil;

import java.nio.file.Path;
import java.util.List;
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
        ChromLibStateExporter.exportLibState(chromLibExportFile.toFile(), sourceContainer, user, log);
        ChromLibStateImporter.importLibState(chromLibExportFile.toFile(), targetContainer, user, log);
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
}

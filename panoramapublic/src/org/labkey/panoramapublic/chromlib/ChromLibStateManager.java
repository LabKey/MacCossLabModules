package org.labkey.panoramapublic.chromlib;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.RepresentativeDataState;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.FileUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    static final String REPRESENTATIVEDATASTATE = "representativedatastate";

    static final StringHeader H_RUN  = new StringHeader(RUN);
    static final StringHeader H_RUN_STATE  = new StringHeader(RUN_STATE);
    static final StringHeader H_PEP_GRP = new StringHeader(PEP_GRP);
    static final IntegerHeader H_PEP_GRP_SEQ_ID = new IntegerHeader(PEP_GRP_SEQ_ID);
    static final StringHeader H_PEP_GRP_STATE = new StringHeader(PEP_GRP_STATE);
    static final DoubleHeader H_PREC_MZ = new DoubleHeader(PREC_MZ);
    static final IntegerHeader H_PREC_CHARGE = new IntegerHeader(PREC_CHARGE);
    static final StringHeader H_PREC_STATE = new StringHeader(PREC_STATE);
    static final StringHeader H_PREC_MOD_SEQ = new StringHeader(PREC_MOD_SEQ);
    static final StringHeader H_PREC_MOL_CUSTOM_ION_NAME = new StringHeader(PREC_MOL_CUSTOM_ION_NAME);
    static final StringHeader H_PREC_MOL_ION_FORMULA = new StringHeader(PREC_MOL_ION_FORMULA);
    static final DoubleHeader H_PREC_MOL_MASS_MONOISOTOPIC = new DoubleHeader(PREC_MOL_MASS_MONOISOTOPIC);
    static final DoubleHeader H_PREC_MOL_MASS_AVERAGE = new DoubleHeader(PREC_MOL_MASS_AVERAGE);

    static final List<Header<?>> PROTEIN_LIB_HEADERS = List.of(H_RUN, H_RUN_STATE, H_PEP_GRP, H_PEP_GRP_SEQ_ID, H_PEP_GRP_STATE);
    static final List<String> PROTEIN_LIB_COLS = PROTEIN_LIB_HEADERS.stream().map(h -> h._name).collect(Collectors.toList());
    static final List<Header<?>> PEP_LIB_HEADERS = List.of(H_RUN, H_RUN_STATE, H_PEP_GRP, H_PEP_GRP_SEQ_ID, H_PEP_GRP_STATE,
            H_PREC_MZ, H_PREC_CHARGE, H_PREC_STATE,
            H_PREC_MOD_SEQ, H_PREC_MOL_CUSTOM_ION_NAME, H_PREC_MOL_ION_FORMULA, H_PREC_MOL_MASS_MONOISOTOPIC, H_PREC_MOL_MASS_AVERAGE);
    static final List<String> PEP_LIB_COLS = PEP_LIB_HEADERS.stream().map(h -> h._name).collect(Collectors.toList());

    public static class Header<T>
    {
        final String _name;
        final Class<T> _type;

        private Header(String name, Class<T> type)
        {
            _name = name;
            _type = type;
        }
    }

    public final static class StringHeader extends Header<String>
    {
        private StringHeader(String name)
        {
            super(name, String.class);
        }
    }

    public final static class IntegerHeader extends Header<Integer>
    {
        private IntegerHeader(String name)
        {
            super(name, Integer.class);
        }
    }

    public final static class DoubleHeader extends Header<Double>
    {
        private DoubleHeader(String name)
        {
            super(name, Double.class);
        }
    }

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
        return root.getRootNioPath().resolve(TargetedMSService.CHROM_LIB_FILE_DIR);
    }

    static List<LibPeptideGroup> getPeptideGroups(ITargetedMSRun run, TargetedMSService svc)
    {
        return new TableSelector(svc.getTableInfoPeptideGroup(),
                new LinkedHashSet<>(Arrays.asList("id", "runId", "label", "sequenceId", "representativedatastate")),
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
        SQLFragment sql = new SQLFragment(" SELECT gp.Id, gp.mz, gp.charge, gp.representativedatastate, mp.massMonoisotopic, mp.massAverage, mp.ionFormula, mp.customIonName ")
                .append(" FROM ").append(svc.getTableInfoGeneralMolecule(), "gm")
                .append(" INNER JOIN ").append(svc.getTableInfoGeneralPrecursor(), "gp")
                .append(" ON gm.id = gp.generalmoleculeid ")
                .append(" INNER JOIN ").append(svc.getTableInfoMoleculePrecursor(), "mp")
                .append(" ON gp.id = mp.id ")
                .append(" WHERE gm.peptidegroupid = ?").add(pepGrp.getId());

        return new SqlSelector(svc.getUserSchema(user, c).getDbSchema(), sql).getArrayList(LibMoleculePrecursor.class);
    }

    public static void renameClibFileForContainer(Container container, TargetedMSService svc, Logger log) throws ChromLibStateException
    {
        Path chromLibDir = ChromLibStateManager.getChromLibDir(container);
        if (Files.exists(chromLibDir))
        {
            try(Stream<Path> files = Files.list(chromLibDir).filter(p -> FileUtil.getFileName(p).endsWith(TargetedMSService.CHROM_LIB_FILE_EXT)))
            {
                for (Path libFile : files.collect(Collectors.toSet()))
                {
                    try
                    {
                        changeFileName(libFile, container, svc, log);
                    }
                    catch (IOException e)
                    {
                        throw new ChromLibStateException("Error changing chromatogram library file name", e);
                    }
                }
            }
            catch (IOException e)
            {
                throw new ChromLibStateException(String.format("Error listing chromatogram library files in folder '%s'.", chromLibDir), e);
            }
        }
    }

    private static void changeFileName(Path path, Container container, TargetedMSService svc, Logger log) throws IOException
    {
        Integer revision = svc.parseChromLibRevision(path.getFileName().toString());
        if (revision != null)
        {
            String newFileName = svc.getChromLibFileName(container, revision);
            Path targetFile = path.getParent().resolve(newFileName);

            log.info(String.format("Changing chromatogram library file name from '%s' to '%s'.", path.getFileName().toString(), newFileName));
            if (!Files.exists(targetFile))
            {
                FileUtils.moveFile(path.toFile(), targetFile.toFile());
            }
        }
    }

    // TODO: The existing methods in ConflictResultsManager in the targetedms module should be moved to the TargetedMS API
    public static long getConflictCount(User user, Container container, TargetedMSService.FolderType folderType) {

        long conflictCount = 0;

        if (folderType == TargetedMSService.FolderType.LibraryProtein)
        {
            conflictCount = getProteinConflictCount(user, container);
        }
        else if (folderType == TargetedMSService.FolderType.Library)
        {
            conflictCount = getGeneralMoleculeConflictCount(container, user);
        }

        return conflictCount;
    }

    private static long getProteinConflictCount(User user, Container container)
    {
        TargetedMSService svc = TargetedMSService.get();
        UserSchema schema = svc.getUserSchema(user, container);
        TableInfo table = schema.getTable(svc.getTableInfoPeptideGroup().getName());
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(REPRESENTATIVEDATASTATE), RepresentativeDataState.Conflicted.ordinal());
        return new TableSelector(table, filter, null).getRowCount();
    }

    private static long getGeneralMoleculeConflictCount(Container container, User user)
    {
        TargetedMSService svc = TargetedMSService.get();
        SQLFragment sqlFragment = new SQLFragment();
        sqlFragment.append("SELECT DISTINCT(gm.Id) FROM ");
        sqlFragment.append(svc.getTableInfoGeneralMolecule(), "gm");
        sqlFragment.append(", ");
        sqlFragment.append(svc.getTableInfoRuns(), "r");
        sqlFragment.append(", ");
        sqlFragment.append(svc.getTableInfoPeptideGroup(), "pg");
        sqlFragment.append(", ");
        sqlFragment.append(svc.getTableInfoGeneralPrecursor(), "pc");
        sqlFragment.append(" WHERE ");
        sqlFragment.append("gm.PeptideGroupId = pg.Id AND ");
        sqlFragment.append("pg.RunId = r.Id AND ");
        sqlFragment.append("pc.GeneralMoleculeId = gm.Id  AND ");
        sqlFragment.append("r.Deleted = ? AND r.Container = ? ");
        sqlFragment.append("AND pc.RepresentativeDataState = ? ");

        sqlFragment.add(false);
        sqlFragment.add(container.getId());
        sqlFragment.add(RepresentativeDataState.Conflicted.ordinal());

        SqlSelector sqlSelector = new SqlSelector(svc.getUserSchema(user, container).getDbSchema(), sqlFragment);
        return sqlSelector.getRowCount();
    }
}

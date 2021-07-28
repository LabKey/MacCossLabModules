package org.labkey.panoramapublic.chromlib;

import org.apache.logging.log4j.Logger;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.RunRepresentativeDataState;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.writer.PrintWriters;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public abstract class ChromLibStateExporter
{
    private final Logger _log;
    private static final String TAB = "\t";

    public ChromLibStateExporter(Logger log)
    {
        _log = log;
    }

    abstract String libTypeString();
    abstract List<String> headers();
    abstract void exportLibStateForRun(ITargetedMSRun run, TargetedMSService svc, Logger log, PrintWriter writer, Container container, User user);

    public static void exportLibState(@NotNull File file, @NotNull Container container, User user, Logger log) throws ChromLibStateException
    {
        TargetedMSService svc = TargetedMSService.get();
        TargetedMSService.FolderType folderType = svc.getFolderType(container);
        if(TargetedMSService.FolderType.LibraryProtein.equals(folderType))
        {
            new ProteinLibStateExporter(log).exportLibraryState(container, file, user, svc);
        }
        else if (TargetedMSService.FolderType.Library.equals(folderType))
        {
            new PeptideLibStateExporter(log).exportLibraryState(container, file, user, svc);
        }
        else
        {
            throw new ChromLibStateException(String.format("'%s' is not a chromatogram library folder.", container));
        }
    }

    void exportLibraryState(Container container, File file, User user, TargetedMSService svc) throws ChromLibStateException
    {
        _log.info(String.format("Exporting %s library state in container '%s' to file '%s'.", libTypeString(), container.getPath(), file.getPath()));

        try(PrintWriter writer = PrintWriters.getPrintWriter(file))
        {
            writer.write(StringUtils.join(headers(), TAB)); // Header row
            writer.println();

            // Get a list of runs in the folder
            List<ITargetedMSRun> runs = svc.getRuns(container);
            for (ITargetedMSRun run : runs)
            {
                if(run.getRepresentativeDataState() == RunRepresentativeDataState.NotRepresentative)
                {
                    _log.info(String.format("'%s' does not contain any library %ss. Ignoring.", run.getFileName(), libTypeString()));
                    continue;
                }
                _log.info(String.format("Exporting library state of %ss in '%s'.", libTypeString(), run.getFileName()));
                exportLibStateForRun(run, svc, _log, writer, container, user);
            }
        }
        catch (FileNotFoundException e)
        {
            throw new ChromLibStateException(e);
        }
        _log.info("Done exporting library state.");
    }

    private static class ProteinLibStateExporter extends ChromLibStateExporter
    {
        public ProteinLibStateExporter(Logger log)
        {
            super(log);
        }

        @Override
        String libTypeString()
        {
            return "protein";
        }

        @Override
        List<String> headers()
        {
            return ChromLibStateManager.PROTEIN_LIB_HEADERS;
        }

        @Override
        void exportLibStateForRun(ITargetedMSRun run, TargetedMSService svc, Logger log, PrintWriter writer, Container container, User user)
        {
            // For each run get a list of peptide groups
            List<LibPeptideGroup> peptideGroups = ChromLibStateManager.getPeptideGroups(run, svc);
            log.info(String.format("Found %d peptide groups.", peptideGroups.size()));
            for (LibPeptideGroup pepGrp : peptideGroups)
            {
                // For each run / peptide group write the representative state
                // - run, run_state
                // - peptidegroup_label, peptidegroup_sequenceid, peptidegroup_state
                List<String> values = List.of(run.getFileName(), run.getRepresentativeDataState().toString(),
                        pepGrp.getLabel(), (pepGrp.getSequenceId() == null ? "" : String.valueOf(pepGrp.getSequenceId())),
                        pepGrp.getRepresentativeDataState().toString());
                writer.write(StringUtils.join(values, TAB));
                writer.println();
            }
        }
    }

    private static class PeptideLibStateExporter extends ChromLibStateExporter
    {
        public PeptideLibStateExporter(Logger log)
        {
            super(log);
        }

        @Override
        String libTypeString()
        {
            return "peptide";
        }

        @Override
        List<String> headers()
        {
            return ChromLibStateManager.PEP_LIB_HEADERS;
        }

        @Override
        void exportLibStateForRun(ITargetedMSRun run, TargetedMSService svc, Logger log, PrintWriter writer, Container container, User user)
        {
            // For each run get a list of peptide groups
            List<LibPeptideGroup> peptideGroups = ChromLibStateManager.getPeptideGroups(run, svc);
            for (LibPeptideGroup pepGrp : peptideGroups)
            {
                // For each peptide group get a list of precursors
                List<LibPrecursor> precursors = ChromLibStateManager.getPrecursors(pepGrp, container, user, svc);
                for(LibPrecursor precursor: precursors)
                {
                    writePeptideLibRow(writer, run, pepGrp, precursor);
                }
                // For each peptide group get a list of molecule precursors
                List<LibMoleculePrecursor> moleculePrecursors = ChromLibStateManager.getMoleculePrecursors(pepGrp, container, user, svc);
                for(LibMoleculePrecursor precursor: moleculePrecursors)
                {
                    writePeptideLibRow(writer, run, pepGrp, precursor);
                }
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
                values.add(""); // custom ion name (only for molecule precursors)
                values.add(""); // ion formula (only for molecule precursors)
                values.add(""); // mass monoisotopic (only for molecule precursors)
                values.add(""); // mass averate (only for molecule precursors)
            }
            else if(precursor instanceof LibMoleculePrecursor)
            {
                values.add(""); // modified sequence (only for proteomic precursors)
                values.add(((LibMoleculePrecursor) precursor).getCustomIonName());
                values.add(((LibMoleculePrecursor) precursor).getIonFormula());
                values.add(String.valueOf(((LibMoleculePrecursor) precursor).getMassMonoisotopic()));
                values.add(String.valueOf(((LibMoleculePrecursor) precursor).getMassAverage()));
            }
            writer.write(StringUtils.join(values, TAB));
            writer.println();
        }
    }
}

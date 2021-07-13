package org.labkey.panoramapublic.chromlib;

import org.apache.logging.log4j.Logger;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.writer.PrintWriters;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class ChromLibStateExporter
{
    private final Logger _log;
    private static final String TAB = "\t";

    public ChromLibStateExporter(Logger log)
    {
        _log = log;
    }

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
            writer.write(StringUtils.join(ChromLibStateManager.PROTEIN_LIB_HEADERS, TAB)); // Header row
            writer.println();

            // Get a list of runs in the folder
            List<ITargetedMSRun> runs = svc.getRuns(container);
            for (ITargetedMSRun run : runs)
            {
                // For each run get a list of peptide groups
                List<ChromLibStateManager.LibPeptideGroup> peptideGroups = ChromLibStateManager.getPeptideGroups(run, svc);
                for (ChromLibStateManager.LibPeptideGroup pepGrp : peptideGroups)
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
            writer.write(StringUtils.join(ChromLibStateManager.PEP_LIB_HEADERS, TAB)); // Header row
            writer.println();

            // Get a list of runs in the folder
            List<ITargetedMSRun> runs = svc.getRuns(container);
            for (ITargetedMSRun run : runs)
            {
                // For each run get a list of peptide groups
                List<ChromLibStateManager.LibPeptideGroup> peptideGroups = ChromLibStateManager.getPeptideGroups(run, svc);
                for (ChromLibStateManager.LibPeptideGroup pepGrp : peptideGroups)
                {
                    // For each peptide group get a list of precursors
                    List<ChromLibStateManager.LibPrecursor> precursors = ChromLibStateManager.getPrecursors(pepGrp, container, user, svc);
                    for(ChromLibStateManager.LibPrecursor precursor: precursors)
                    {
                        writePeptideLibRow(writer, run, pepGrp, precursor);
                    }
                    List<ChromLibStateManager.LibMoleculePrecursor> moleculePrecursors = ChromLibStateManager.getMoleculePrecursors(pepGrp, container, user, svc);
                    for(ChromLibStateManager.LibMoleculePrecursor precursor: moleculePrecursors)
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

    private void writePeptideLibRow(PrintWriter writer, ITargetedMSRun run, ChromLibStateManager.LibPeptideGroup pepGrp, ChromLibStateManager.LibGeneralPrecursor precursor)
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
        if(precursor instanceof ChromLibStateManager.LibPrecursor)
        {
            values.add(((ChromLibStateManager.LibPrecursor) precursor).getModifiedSequence());
            values.add("");
            values.add("");
            values.add("");
            values.add("");
        }
        else if(precursor instanceof ChromLibStateManager.LibMoleculePrecursor)
        {
            values.add("");
            values.add(((ChromLibStateManager.LibMoleculePrecursor) precursor).getCustomIonName());
            values.add(((ChromLibStateManager.LibMoleculePrecursor) precursor).getIonFormula());
            values.add(String.valueOf(((ChromLibStateManager.LibMoleculePrecursor) precursor).getMassMonoisotopic()));
            values.add(String.valueOf(((ChromLibStateManager.LibMoleculePrecursor) precursor).getMassAverage()));
        }
        writer.write(StringUtils.join(values, TAB));
        writer.println();
    }
}

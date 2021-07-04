package org.labkey.panoramapublic.chromlib;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.query.FieldKey;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.targetedms.IGeneralPrecursor;
import org.labkey.api.targetedms.IPeptideGroup;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.writer.PrintWriters;
import org.postgresql.PGRefCursorResultSet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChromLibStateManager
{
    public void exportLibState(Container container, File file)
    {
        TargetedMSService svc = TargetedMSService.get();
        // Check that this is a chromatogram library folder
        if (!isLibraryFolder(container, svc))
        {
            return;
        }

//        IPeptideGroup pepGrp = null;
//        IGeneralPrecursor precursor = null;
        try(PrintWriter writer = PrintWriters.getPrintWriter(file))
        {
//            List<String> values = List.of("FileName", "Representative_Run",
//                    pepGrp.getLabel(), (pepGrp.getSequenceId() == null ? "" : String.valueOf(pepGrp.getSequenceId())), pepGrp.getRepresentativeDataState().toString(),
//                    String.valueOf(precursor.getMz()), String.valueOf(precursor.getCharge()), precursor.getRepresentativeDataState().toString());
//            writer.write(StringUtils.join(values, "\t"));
            // Get a list of runs in the folder
            List<ITargetedMSRun> runs = svc.getRuns(container);
            for (ITargetedMSRun run : runs)
            {
                // For each run get a list of peptide groups
                List<? extends IPeptideGroup> peptideGroups = getPeptideGroups(run, svc);

                // For each peptide group get a list of precursors
                for (IPeptideGroup pepGrp : peptideGroups)
                {
                    List<? extends IGeneralPrecursor> precursors = getGeneralPrecursors(pepGrp, svc);
                    // For each precursor write the representative state
                    // - run, run_state
                    // - peptidegroup_label, peptidegroup_sequenceid, peptidegroup_state
                    // - precursor_mz, precursor_charge, precursor_state
                    for(IGeneralPrecursor precursor: precursors)
                    {
                        List<String> values = List.of(run.getFileName(), run.getRepresentativeDataState().toString(),
                                pepGrp.getLabel(), (pepGrp.getSequenceId() == null ? "" : String.valueOf(pepGrp.getSequenceId())), pepGrp.getRepresentativeDataState().toString(),
                                String.valueOf(precursor.getMz()), String.valueOf(precursor.getCharge()), precursor.getRepresentativeDataState().toString());
                        writer.write(StringUtils.join(values, "\t"));
                        writer.println();
                    }
                }
            }
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
        }
    }

    public void importLibState(Container container, File libStateFile)
    {
        try (TabLoader reader = new TabLoader(libStateFile))
        {
            CloseableIterator<Map<String, Object>> iterator = reader.iterator();
            while(iterator.hasNext())
            {
                Map<String, Object> rowVals =  iterator.next();
            }
        }
    }

    private static boolean isLibraryFolder(Container container, TargetedMSService svc)
    {
        TargetedMSService.FolderType folderType = svc.getFolderType(container);
        return TargetedMSService.FolderType.Library.equals(folderType) || TargetedMSService.FolderType.LibraryProtein.equals(folderType);
    }

    private static List<? extends IPeptideGroup> getPeptideGroups(ITargetedMSRun run, TargetedMSService svc)
    {
//        return new TableSelector(svc.getTableInfoPeptideGroup(),
//                new SimpleFilter(FieldKey.fromParts("runId"), run.getId()), null)
//                .getArrayList(IPeptideGroup.class);
        return TargetedMSService.get().getPeptideGroups(run);
    }

    private static List<? extends IGeneralPrecursor> getGeneralPrecursors(IPeptideGroup pepGrp, TargetedMSService svc)
    {
//        List<Integer> genMolIds = new TableSelector(svc.getTableInfoGeneralMolecule(), Set.of("id"),
//                new SimpleFilter(FieldKey.fromParts("peptideGroupId"), pepGrp.getId()), null).getArrayList(Integer.class);
//        List<IGeneralPrecursor> precursors = new ArrayList<>();
//        for(Integer genModId: genMolIds)
//        {
//            precursors.addAll(new TableSelector(svc.getTableInfoGeneralPrecursor(),
//                    new SimpleFilter(FieldKey.fromParts("generalMoleculeId"), genModId), null)
//                    .getArrayList(IGeneralPrecursor.class));
//        }
//        return precursors;
        return TargetedMSService.get().getPrecursors(pepGrp);
    }
}

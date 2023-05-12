package org.labkey.panoramapublic;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.security.User;
import org.labkey.api.writer.VirtualFile;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.JournalSubmission;
import org.labkey.panoramapublic.model.speclib.SpecLibInfo;
import org.labkey.panoramapublic.pipeline.CopyExperimentJobSupport;
import org.labkey.panoramapublic.pipeline.CopyExperimentPipelineJob;
import org.labkey.panoramapublic.query.ExperimentAnnotationsManager;
import org.labkey.panoramapublic.query.ModificationInfoManager;
import org.labkey.panoramapublic.query.SpecLibInfoManager;
import org.labkey.panoramapublic.query.SubmissionManager;
import org.labkey.panoramapublic.query.modification.ExperimentIsotopeModInfo;
import org.labkey.panoramapublic.query.modification.ExperimentStructuralModInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 Creates a new row in panoramapublic.ExperimentAnnotations and links it to the new experiment created during folder import.
 This importer should run before {@link PanoramaPublicFileImporter} so that if there is an error in aligning datafileurls in
 {@link PanoramaPublicFileImporter#alignDataFileUrls(User, Container, VirtualFile, Logger)} we can delete the container and
 get all the files copied back to the source container. {@link PanoramaPublicListener#containerDeleted(Container, User)}
 will fire fireSymlinkCopiedExperimentDelete only if there is an entry in panoramapublic.experimentannotations.

 Copies other metadata related to spectral libraries and modifications from the source container.
 */
public class PanoramaPublicMetadataImporter implements FolderImporter
{
    @Override
    public String getDataType()
    {
        return PanoramaPublicManager.PANORAMA_PUBLIC_METADATA;
    }

    @Override
    public String getDescription()
    {
        return null;
    }

    @Override
    public void process(@Nullable PipelineJob job, FolderImportContext ctx, VirtualFile root) throws Exception
    {
        if (job instanceof CopyExperimentPipelineJob)
        {
            Logger log = ctx.getLogger();
            CopyExperimentJobSupport jobSupport = job.getJobSupport(CopyExperimentJobSupport.class);

            Container container = job.getContainer();

            // If a row already exists in panoramapublic.experimentannotations then don't do anything.
            if (ExperimentAnnotationsManager.getExperimentInContainer(container) != null)
            {
                return;
            }

            // Get the experiment that was just created in the target folder as part of folder import.
            User user = job.getUser();
            List<? extends ExpExperiment> experiments = ExperimentService.get().getExperiments(container, user, false, false);
            if (experiments.size() == 0)
            {
                throw new PipelineJobException("No experiments found in the folder " + container.getPath());
            }
            else if (experiments.size() > 1)
            {
                throw new PipelineJobException("More than one experiment found in the folder " + container.getPath());
            }
            ExpExperiment experiment = experiments.get(0);

            try (DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
            {
                // Get the ExperimentAnnotations in the source container
                ExperimentAnnotations sourceExperiment = jobSupport.getExpAnnotations();
                // Get the submission request
                JournalSubmission js = SubmissionManager.getJournalSubmission(sourceExperiment.getId(), jobSupport.getJournal().getId(), sourceExperiment.getContainer());
                if (js == null)
                {
                    throw new PipelineJobException("Could not find a submission request for experiment Id " + sourceExperiment.getId() + " and journal Id " + jobSupport.getJournal().getId()
                            + " in the folder '" + sourceExperiment.getContainer() + "'");
                }

                ExperimentAnnotations targetExperiment = createNewExperimentAnnotations(experiment, sourceExperiment, user, log);

                // Get a list of all the ExpRuns imported to subfolders of this folder.
                int[] runRowIdsInSubfolders = getAllExpRunRowIdsInSubfolders(container);
                if (runRowIdsInSubfolders.length > 0)
                {
                    // The folder export and import process, creates a new experiment in exp.experiment.
                    // However, only runs in the top-level folder are added to this experiment.
                    // We will add to the experiment all the runs imported to subfolders.
                    log.info("Adding runs imported in subfolders.");
                    ExperimentAnnotationsManager.addSelectedRunsToExperiment(experiment, runRowIdsInSubfolders, user);
                }

                // Copy any Spectral library information provided by the user in the source container
                copySpecLibInfos(sourceExperiment, targetExperiment, user);

                // Copy any modifications related information provided by the user in the source container
                copyModificationInfos(sourceExperiment, targetExperiment, user);

                transaction.commit();
            }
        }
    }

    private static int[] getAllExpRunRowIdsInSubfolders(Container container)
    {
        Set<Container> children = ContainerManager.getAllChildren(container);
        ExperimentService expService = ExperimentService.get();
        List<Integer> expRunRowIds = new ArrayList<>();
        for(Container child: children)
        {
            if(container.equals(child))
            {
                continue;
            }
            List<? extends ExpRun> runs = expService.getExpRuns(child, null, null);
            for(ExpRun run: runs)
            {
                expRunRowIds.add(run.getRowId());
            }
        }
        int[] intIds = new int[expRunRowIds.size()];
        for(int i = 0; i < expRunRowIds.size(); i++)
        {
            intIds[i] = expRunRowIds.get(i);
        }
        return intIds;
    }

    private ExperimentAnnotations createNewExperimentAnnotations(ExpExperiment experiment, ExperimentAnnotations sourceExperiment,
                                                                 User user, Logger log)
    {
        log.info("Creating a new TargetedMS experiment entry in panoramapublic.ExperimentAnnotations.");
        ExperimentAnnotations targetExperiment = createExperimentCopy(sourceExperiment);
        targetExperiment.setExperimentId(experiment.getRowId());
        targetExperiment.setContainer(experiment.getContainer());
        targetExperiment.setSourceExperimentId(sourceExperiment.getId());
        targetExperiment.setSourceExperimentPath(sourceExperiment.getContainer().getPath());

        return ExperimentAnnotationsManager.save(targetExperiment, user);
    }

    private ExperimentAnnotations createExperimentCopy(ExperimentAnnotations source)
    {
        ExperimentAnnotations copy = new ExperimentAnnotations();
        copy.setTitle(source.getTitle());
        copy.setExperimentDescription(source.getExperimentDescription());
        copy.setSampleDescription(source.getSampleDescription());
        copy.setOrganism(source.getOrganism());
        copy.setInstrument(source.getInstrument());
        copy.setSpikeIn(source.getSpikeIn());
        copy.setCitation(source.getCitation());
        copy.setAbstract(source.getAbstract());
        copy.setPublicationLink(source.getPublicationLink());
        copy.setIncludeSubfolders(source.isIncludeSubfolders());
        copy.setKeywords(source.getKeywords());
        copy.setLabHead(source.getLabHead());
        copy.setSubmitter(source.getSubmitter());
        copy.setLabHeadAffiliation(source.getLabHeadAffiliation());
        copy.setSubmitterAffiliation(source.getSubmitterAffiliation());
        copy.setPubmedId(source.getPubmedId());
        return copy;
    }

    private void copySpecLibInfos(ExperimentAnnotations sourceExperiment, ExperimentAnnotations targetExperiment, User user)
    {
        List<SpecLibInfo> specLibInfos = SpecLibInfoManager.getForExperiment(sourceExperiment.getId(), sourceExperiment.getContainer());
        for (SpecLibInfo info: specLibInfos)
        {
            info.setId(0);
            info.setExperimentAnnotationsId(targetExperiment.getId());
            SpecLibInfoManager.save(info, user);
        }
    }

    private void copyModificationInfos(ExperimentAnnotations sourceExperiment, ExperimentAnnotations targetExperiment, User user)
    {
        List<ExperimentStructuralModInfo> strModInfos = ModificationInfoManager.getStructuralModInfosForExperiment(sourceExperiment.getId(), sourceExperiment.getContainer());
        for (ExperimentStructuralModInfo info: strModInfos)
        {
            info.setId(0);
            info.setExperimentAnnotationsId(targetExperiment.getId());
            ModificationInfoManager.saveStructuralModInfo(info, user);
        }

        List<ExperimentIsotopeModInfo> isotopeModInfos = ModificationInfoManager.getIsotopeModInfosForExperiment(sourceExperiment.getId(), sourceExperiment.getContainer());
        for (ExperimentIsotopeModInfo info: isotopeModInfos)
        {
            info.setId(0);
            info.setExperimentAnnotationsId(targetExperiment.getId());
            ModificationInfoManager.saveIsotopeModInfo(info, user);
        }
    }

    public static class Factory extends AbstractFolderImportFactory
    {
        @Override
        public FolderImporter create()
        {
            return new PanoramaPublicMetadataImporter();
        }

        @Override
        public int getPriority()
        {
            // We want this to run AFTER an entry is created in exp.experiment and BEFORE PanoramaPublicFileImporter
            return PanoramaPublicManager.PRIORITY_PANORAMA_PUBLIC_METADATA;
        }
    }
}

package org.labkey.panoramapublic.pipeline;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.targetedms.TargetedMSService.FolderType;
import org.labkey.api.util.FileType;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.chromlib.ChromLibStateException;
import org.labkey.panoramapublic.chromlib.ChromLibStateManager;
import org.labkey.panoramapublic.model.ExperimentAnnotations;

import java.util.Collections;
import java.util.List;

import static org.labkey.api.targetedms.TargetedMSService.FolderType.Library;
import static org.labkey.api.targetedms.TargetedMSService.FolderType.LibraryProtein;

public class CopyLibraryStateTask extends PipelineJob.Task<CopyLibraryStateTask.Factory>
{

    private CopyLibraryStateTask(CopyLibraryStateTask.Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @Override
    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        CopyExperimentJobSupport support = job.getJobSupport(CopyExperimentJobSupport.class);
        try
        {
            job.getLogger().info("");
            job.getLogger().info("Checking for library folders.");
            updateFolderType(job, support);
        }
        catch (Throwable t)
        {
            throw new PipelineJobException(t);
        }

        return new RecordedActionSet();
    }

    private void updateFolderType(PipelineJob job, CopyExperimentJobSupport jobSupport) throws PipelineJobException
    {
        // Get the experiment that was just created in the target folder as part of the folder import.
        var container = job.getContainer();
        User user = job.getUser();
        List<? extends ExpExperiment> experiments = ExperimentService.get().getExperiments(container, user, false, false);
        if (experiments.size() == 0)
        {
            throw new PipelineJobException(String.format("No experiments found in the container '%s'.", container.getPath()));
        }
        else if (experiments.size() >  1)
        {
            throw new PipelineJobException(String.format("More than one experiment found in the container '%s'.", container.getPath()));
        }
        ExpExperiment experiment = experiments.get(0);

        ExperimentAnnotations sourceExperiment = jobSupport.getExpAnnotations();
        Logger log = job.getLogger();
        try(DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
        {
            Container target = experiment.getContainer();
            TargetedMSService svc = TargetedMSService.get();
            updateFolderType(target, sourceExperiment.getContainer(), user, svc, log);
            transaction.commit();
        }
    }

    private void updateFolderType(Container c, Container sourceContainer, User user, TargetedMSService svc, Logger log) throws PipelineJobException
    {
        doUpdate(c, sourceContainer, user, svc, log);

        List<Container> children = ContainerManager.getChildren(c);
        for (Container child: children)
        {
            Container source = ContainerManager.getChild(sourceContainer, child.getName());
            if (source == null)
            {
               throw new PipelineJobException(String.format("Cannot update folder type. Could not find the source container for the subfolder '%s'.", child.getPath()));
            }
            updateFolderType(child, source, user, svc, log);
        }
    }

    private void doUpdate(Container c, Container sourceContainer, User user, TargetedMSService svc, Logger log) throws PipelineJobException
    {
        FolderType folderType = svc.getFolderType(sourceContainer);
        if (LibraryProtein.equals(folderType) || Library.equals(folderType))
        {
            log.info(String.format("Copying '%s' state from source folder '%s' into target folder '%s' .",
                    (LibraryProtein.equals(folderType) ? "Protein Library" : "Peptide Library"),
                    sourceContainer.getPath(),
                    c.getPath()));
            copyLibraryState(c, sourceContainer, user, svc, log);
        }
    }

    private static void copyLibraryState(Container container, Container sourceContainer,
                                         User user, TargetedMSService svc, Logger log) throws PipelineJobException
    {
        PropertyManager.PropertyMap sourcePropMap = PropertyManager.getProperties(sourceContainer, TargetedMSService.MODULE_NAME);
        String versionStr = sourcePropMap.get(TargetedMSService.PROP_CHROM_LIB_REVISION);
        if (null != versionStr)
        {
            log.info(String.format("Setting the value of property '%s' to '%s'.", TargetedMSService.PROP_CHROM_LIB_REVISION, versionStr));
            PropertyManager.PropertyMap targetPropMap = PropertyManager.getWritableProperties(container, TargetedMSService.MODULE_NAME, true);
            targetPropMap.put(TargetedMSService.PROP_CHROM_LIB_REVISION, versionStr);
            targetPropMap.save();

            try
            {
                new ChromLibStateManager().copyLibraryState(sourceContainer, container, log, user);
            }
            catch (ChromLibStateException e)
            {
                throw new PipelineJobException(String.format("Error copying chromatogram library state from source folder '%s' to '%s'", sourceContainer, container), e);
            }

            try
            {
                ChromLibStateManager.renameClibFileForContainer(container, svc, log);
            }
            catch (ChromLibStateException e)
            {
                throw new PipelineJobException(String.format("Error renaming .clib files to match target container '%s'", container), e);
            }
        }
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, CopyLibraryStateTask.Factory>
    {
        public Factory()
        {
            super(CopyLibraryStateTask.class);
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new CopyLibraryStateTask(this, job);
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        @Override
        public String getStatusName()
        {
            return "UPDATE FOLDER TYPE";
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}

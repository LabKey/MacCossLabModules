package org.labkey.panoramapublic.pipeline;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.targetedms.TargetedMSService.FolderType;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.chromlib.ChromLibStateException;
import org.labkey.panoramapublic.chromlib.ChromLibStateManager;
import org.labkey.panoramapublic.model.ExperimentAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.labkey.api.targetedms.TargetedMSService.FolderType.Experiment;
import static org.labkey.api.targetedms.TargetedMSService.FolderType.Library;
import static org.labkey.api.targetedms.TargetedMSService.FolderType.LibraryProtein;

public class UpdateFolderTypeTask extends PipelineJob.Task<UpdateFolderTypeTask.Factory>
{
    private static final Module _targetedMSModule = ModuleLoader.getInstance().getModule(TargetedMSService.MODULE_NAME);
    private static final ModuleProperty _folderTypeProp = _targetedMSModule.getModuleProperties().get(TargetedMSService.FOLDER_TYPE_PROP_NAME);

    private UpdateFolderTypeTask(UpdateFolderTypeTask.Factory factory, PipelineJob job)
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
            job.getLogger().info("Updating the folder type.");
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
            updateFolderType(child, sourceContainer, user, svc, log);
        }
    }

    private void doUpdate(Container c, Container sourceContainer, User user, TargetedMSService svc, Logger log) throws PipelineJobException
    {
        FolderType folderType = svc.getFolderType(sourceContainer);
        if (Experiment.equals(folderType))
        {
            log.info(String.format("Setting the TargetedMS folder type to 'Experimental Data' for container '%s'.", c.getPath()));
            _folderTypeProp.saveValue(user, c, Experiment.toString());
        }
        else if (Library.equals(folderType) || LibraryProtein.equals(folderType))
        {
            log.info(String.format("Updating the TargetedMS folder type to '%s' for container '%s'.",
                    (LibraryProtein.equals(folderType) ? "Protein Library" : "Peptide Library"),
                    c.getPath()));
            makePanoramaLibraryFolder(c, sourceContainer, folderType, user, svc, log);
        }
    }

    private static void makePanoramaLibraryFolder(Container container, Container sourceContainer, FolderType sourceFolderType,
                                                  User user, TargetedMSService svc, Logger log) throws PipelineJobException
    {
        _folderTypeProp.saveValue(user, container, sourceFolderType.name());

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

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, UpdateFolderTypeTask.Factory>
    {
        public Factory()
        {
            super(UpdateFolderTypeTask.class);
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new UpdateFolderTypeTask(this, job);
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

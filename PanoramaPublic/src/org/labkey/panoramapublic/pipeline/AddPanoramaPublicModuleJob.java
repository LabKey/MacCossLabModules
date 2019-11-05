package org.labkey.panoramapublic.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.panoramapublic.PanoramaPublicModule;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class AddPanoramaPublicModuleJob extends PipelineJob
{

    private boolean _dryRun;

    // For serialization
    protected AddPanoramaPublicModuleJob()
    {
    }

    public AddPanoramaPublicModuleJob(ViewBackgroundInfo info, @NotNull PipeRoot root, boolean dryRun)
    {
        super("Panorama Public", info, root);
        setLogFile(new File(root.getRootPath(), FileUtil.makeFileNameWithTimestamp("PanoramaPublic", "log")));
        _dryRun = dryRun;
    }

    @Override
    public void run()
    {
        setStatus(TaskStatus.running);
        var containers = ContainerManager.getAllChildren(getContainer(), getUser());
        int total = containers.size();
        getLogger().info("Total number of containers: " + total);

        int done = 0;
        int updated = 0;

        FolderType tmsFolderType = FolderTypeManager.get().getFolderType(TargetedMSService.FOLDER_TYPE_NAME);
        PanoramaPublicModule panPublicModule = ModuleLoader.getInstance().getModule(PanoramaPublicModule.class);

        for (Container container : containers)
        {
            done++;

            if(done%100 == 0)
            {
                getLogger().info(done + "/" + total + " done.");
            }

            if(!container.getFolderType().equals(tmsFolderType))
            {
                getLogger().debug("Not a TargetedMS container: " + container.getPath());
                continue;
            }

            Set<Module> activeModules = new HashSet<>(container.getActiveModules());
            if(!activeModules.contains(panPublicModule))
            {
                if(!_dryRun)
                {
                    getLogger().info("Adding module to: " + container.getPath());
                    activeModules.add(panPublicModule);
                    container.setActiveModules(activeModules);
                }
                else
                {
                    getLogger().info("Would add module to: " + container.getPath());
                }

                updated++;
            }
            else
            {
                getLogger().debug("Has PanoramaPublic module: " + container.getPath());
            }
        }
        getLogger().info(done + "/" + total + " done.");
        getLogger().info("Added PanoramaPublic module to " + updated + " containers.");
        setStatus(TaskStatus.complete);
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return "Add PanoramaPublic module to TargetedMS containers." + (_dryRun ? " (dry run)" : "");
    }
}

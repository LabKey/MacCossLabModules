package org.labkey.panoramapublic.pipeline;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.query.ExperimentAnnotationsManager;
import org.labkey.panoramapublic.security.PanoramaPublicSubmitterPermission;
import org.labkey.panoramapublic.security.PanoramaPublicSubmitterRole;

public class AssignSubmitterPermissionJob extends PipelineJob
{
    private boolean _dryRun;
    private Container _container;

    // For serialization
    protected AssignSubmitterPermissionJob()
    {
    }

    public AssignSubmitterPermissionJob(ViewBackgroundInfo info, @NotNull PipeRoot root, boolean dryRun, Container container)
    {
        super("Panorama Public", info, root);
        setLogFile(root.getRootNioPath().resolve(FileUtil.makeFileNameWithTimestamp("PanoramaPublic-assign-submitter-role", "log")));
        _dryRun = dryRun;
        _container = container;
    }

    @Override
    public void run()
    {
        setStatus(PipelineJob.TaskStatus.running);
        if (_container != null)
        {
            assignRole();
            setStatus(PipelineJob.TaskStatus.complete);
        }
        else
        {
            getLogger().error("Input container was null. Exiting...");
        }
    }

    private void assignRole()
    {
        var containers = ContainerManager.getAllChildren(_container, getUser());
        getLogger().info("Total number of folders: " + containers.size());

        int done = 0;
        int updated = 0;
        int total = containers.size();

        try (DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
        {
            for (Container container : containers)
            {
                ExperimentAnnotations expAnnotations = ExperimentAnnotationsManager.getExperimentInContainer(container);
                if (expAnnotations != null && expAnnotations.isJournalCopy())
                {
                    boolean submitterUpdated = addPermission(expAnnotations.getSubmitterUser(), "Submitter", container, _dryRun, getLogger());
                    boolean labHeadUpdated = addPermission(expAnnotations.getLabHeadUser(), "Lab Head", container, _dryRun, getLogger());
                    if (submitterUpdated || labHeadUpdated)
                    {
                        updated++;
                    }
                }
                else
                {
                    getLogger().info(String.format("'%s' does not contain a valid experiment", container.getPath()));
                    continue;
                }
                done++;

                if (done % 100 == 0)
                {
                    getLogger().info(done + "/" + total + " done.");
                }
            }
            transaction.commit();
        }

        getLogger().info(done + "/" + total + " done.");
        getLogger().info("Assigned PanoramaPublicSubmitterRole in " + updated + " containers.");
    }

    private boolean addPermission(User user, String userType, Container container, boolean dryRun, Logger logger)
    {
        if (user == null) return false;

        if (!container.hasPermission(user, PanoramaPublicSubmitterPermission.class))
        {
            if (!dryRun)
            {
                logger.info(String.format("'%s', %s: %s - %s", container.getPath(), userType, user.getEmail(), "assigning"));
                MutableSecurityPolicy newPolicy = new MutableSecurityPolicy(container, container.getPolicy());
                newPolicy.addRoleAssignment(user, PanoramaPublicSubmitterRole.class, false);
                SecurityPolicyManager.savePolicy(newPolicy);
                return true;
            }
            else
            {
                logger.info(String.format("'%s', %s: %s - %s", container.getPath(), userType, user.getEmail(), "would assign (dry run)"));
            }
        }
        else
        {
            logger.info(String.format("'%s', %s: %s - %s", container.getPath(), userType, user.getEmail(), "already assigned"));
        }
        return false;
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return "Assign PanoramaPublicSubmitterRole to data submitters and lab heads" + (_dryRun ? " (dry run)" : "");
    }
}

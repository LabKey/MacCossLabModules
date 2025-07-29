package org.labkey.nextflow;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.nextflow.pipeline.NextFlowPipelineJob;
import org.springframework.validation.BindException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.labkey.api.action.SpringActionController.ERROR_MSG;

public class NextFlowManager
{
    public static final String NEXTFLOW_CONFIG = "nextflow-config";
    private static final String NEXTFLOW_ENABLE_PROP_CATEGORY = "nextflow-enable";

    private static final String NEXTFLOW_ACCOUNT_NAME = "accountName";
    private static final String NEXTFLOW_CONFIG_FILE_PATH = "nextFlowConfigFilePath";
    private static final String NEXTFLOW_IDENTITY = "identity";
    private static final String NEXTFLOW_CREDENTIAL = "credential";
    private static final String NEXTFLOW_S3_BUCKET_PATH = "s3BucketPath";
    private static final String NEXTFLOW_API_KEY = "apiKey";

    public static final String SCHEMA_NAME = "nextflow";

    private static final String IS_NEXTFLOW_ENABLED = "enabled";

    private static final NextFlowManager _instance = new NextFlowManager();

    private NextFlowManager()
    {
        // prevent external construction with a private default constructor
    }

    public static NextFlowManager get()
    {
        return _instance;
    }


    private void checkArgs(NextFlowConfiguration config, BindException errors)
    {
        if (StringUtils.isEmpty(config.getNextFlowConfigFilePath()))
            errors.rejectValue("nextFlowConfigFilePath", ERROR_MSG, "NextFlow config file path is required");

        Path configPath = Paths.get(config.getNextFlowConfigFilePath());
        if (!Files.isDirectory(configPath))
        {
            errors.rejectValue("nextFlowConfigFilePath", ERROR_MSG, "NextFlow config file path must be a directory");
        }

        // Not yet used
//        if (StringUtils.isEmpty(config.getAccountName()))
//            errors.rejectValue("accountName", ERROR_MSG, "AWS account name is required");
//        if (StringUtils.isEmpty(config.getIdentity()))
//            errors.rejectValue("identity", ERROR_MSG, "AWS identity is required");
//        if (StringUtils.isEmpty(config.getCredential()))
//            errors.rejectValue("credential", ERROR_MSG, "AWS credential is required");

        if (StringUtils.isEmpty(config.getS3BucketPath()))
            errors.rejectValue("credential", ERROR_MSG, "S3 bucket path is required");
    }

    public NextFlowConfiguration getConfiguration()
    {
        PropertyManager.PropertyMap props = PropertyManager.getEncryptedStore().getWritableProperties(NEXTFLOW_CONFIG, false);
        if (props != null)
        {
            NextFlowConfiguration configuration = new NextFlowConfiguration();
            configuration.setAccountName(props.get(NEXTFLOW_ACCOUNT_NAME));
            configuration.setNextFlowConfigFilePath(props.get(NEXTFLOW_CONFIG_FILE_PATH));
            configuration.setIdentity(props.get(NEXTFLOW_IDENTITY));
            configuration.setCredential(props.get(NEXTFLOW_CREDENTIAL));
            configuration.setS3BucketPath(props.get(NEXTFLOW_S3_BUCKET_PATH));
            configuration.setApiKey(props.get(NEXTFLOW_API_KEY));
            return configuration;
        }

        return null;
    }

    /**
     * Checks in the specified container and traverses up the container tree to determine if NextFlow integration
     * is enabled directly or in a parent container.
     */
    public boolean isEnabled(Container c)
    {
        do
        {
            PropertyManager.PropertyMap map = PropertyManager.getProperties(c, NEXTFLOW_ENABLE_PROP_CATEGORY);
            if (map.containsKey(IS_NEXTFLOW_ENABLED))
            {
                return Boolean.parseBoolean(map.get(IS_NEXTFLOW_ENABLED));
            }
            c = c.getParent();
        }
        while (c != null);

        return false;
    }

    /**
     * @return configured state for the container (or null if not configured there), for whether NextFlow is enabled
     */
    public Boolean getEnabledState(Container c)
    {
        PropertyManager.PropertyMap map = PropertyManager.getProperties(c, NEXTFLOW_ENABLE_PROP_CATEGORY);
        if (map.containsKey(IS_NEXTFLOW_ENABLED))
        {
            return Boolean.parseBoolean(map.get(IS_NEXTFLOW_ENABLED));
        }
        return null;
    }

    public void saveConfig(NextFlowConfiguration configuration, BindException errors)
    {
        checkArgs(configuration, errors);

        if (!errors.hasErrors())
            saveConfiguration(configuration);
    }

    private void saveConfiguration( NextFlowConfiguration configuration)
    {
        try (DbScope.Transaction tx = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            Map<String, String> properties = new HashMap<>();
            properties.put(NEXTFLOW_CONFIG_FILE_PATH, configuration.getNextFlowConfigFilePath());
            properties.put(NEXTFLOW_IDENTITY, configuration.getIdentity());
            properties.put(NEXTFLOW_CREDENTIAL, configuration.getCredential());
            properties.put(NEXTFLOW_S3_BUCKET_PATH, configuration.getS3BucketPath());
            properties.put(NEXTFLOW_ACCOUNT_NAME, configuration.getAccountName());
            properties.put(NEXTFLOW_API_KEY, configuration.getApiKey());

            PropertyManager.WritablePropertyMap props = PropertyManager.getEncryptedStore().getWritableProperties(NEXTFLOW_CONFIG, true);
            props.clear();
            props.putAll(properties);
            props.save();

            tx.commit();
        }
    }

    public void saveEnabledState(Container container, Boolean enabled)
    {
        PropertyManager.WritablePropertyMap map = PropertyManager.getWritableProperties(container, NEXTFLOW_ENABLE_PROP_CATEGORY, true);
        if (enabled == null)
        {
            map.delete();
        }
        else
        {
            map.put(IS_NEXTFLOW_ENABLED, enabled.toString());
            map.save();
        }
    }

    private DbSchema getDbSchema()
    {
        return DbSchema.get(SCHEMA_NAME, DbSchemaType.Module);
    }

    private Long getJobId(NextFlowPipelineJob job)
    {
        PipelineStatusFile file = PipelineService.get().getStatusFile(job.getJobGUID());
        return file == null ? null : file.getRowId();
    }

    public int getInvocationCount(NextFlowPipelineJob job)
    {
        return getInvocationCount(getJobId(job));
    }

    private int getInvocationCount(long jobId)
    {
        Integer result = new SqlSelector(getDbSchema(), new SQLFragment("SELECT InvocationCount FROM nextflow.Job WHERE JobId = ?", jobId)).getObject(Integer.class);
        return result != null ? result.intValue() : 0;
    }

    public int incrementInvocationCount(NextFlowPipelineJob job)
    {
        long jobId = getJobId(job);
        int current = getInvocationCount(jobId);
        current++;
        if (current == 1)
        {
            new SqlExecutor(getDbSchema()).execute(new SQLFragment("INSERT INTO nextflow.Job (JobId, InvocationCount) VALUES (?, ?)", jobId, current));
        }
        else
        {
            new SqlExecutor(getDbSchema()).execute(new SQLFragment("UPDATE nextflow.Job SET InvocationCount = ? WHERE JobId = ?", current, jobId));
        }
        return current;
    }
}

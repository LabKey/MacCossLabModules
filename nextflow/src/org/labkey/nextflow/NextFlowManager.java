package org.labkey.nextflow;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyStore;
import org.springframework.validation.BindException;

import java.util.HashMap;
import java.util.Map;

import static org.labkey.api.action.SpringActionController.ERROR_MSG;

public class NextFlowManager
{
    public static final String NEXTFLOW_CONFIG = "nextflow-config";
    public static final String NEXTFLOW_ENABLE = "nextflow-enable";

    private static final String NEXTFLOW_ACCOUNT_NAME = "accountName";
    private static final String NEXTFLOW_CONFIG_FILE_PATH = "nextFlowConfigFilePath";
    private static final String NEXTFLOW_IDENTIRY = "identity";
    private static final String NEXTFLOW_CREDENTIAL = "credential";
    private static final String NEXTFLOW_S3_BUCKET_PATH = "s3BucketPath";

    private static final NextFlowManager _instance = new NextFlowManager();

    // Normal store is used for enabled/disabled module
    private static final PropertyStore _normalStore = PropertyManager.getNormalStore();

    // Encrypted store is used for aws settings & nextflow file configuration
    private static final PropertyStore _encryptedStore = PropertyManager.getEncryptedStore();

    private NextFlowManager()
    {
        // prevent external construction with a private default constructor
    }

    public static NextFlowManager get()
    {
        return _instance;
    }


    private void checkArgs(String nextFlowConfigFilePath, String name,  String identity, String credential,String s3BucketPath, BindException errors)
    {
        if (StringUtils.isEmpty(nextFlowConfigFilePath))
            errors.rejectValue("nextFlowConfigFilePath", ERROR_MSG, "NextFlow config file path is required");

        if (StringUtils.isEmpty(name))
            errors.rejectValue("name", ERROR_MSG, "AWS account name is required");

        if (StringUtils.isEmpty(identity))
            errors.rejectValue("identity", ERROR_MSG, "AWS identity is required");

        if (StringUtils.isEmpty(credential))
            errors.rejectValue("credential", ERROR_MSG, "AWS credential is required");

    }

    public NextFlowController.NextFlowConfiguration getConfiguration()
    {
        PropertyManager.PropertyMap props = _encryptedStore.getWritableProperties(NEXTFLOW_CONFIG, false);
        if (props != null)
        {
            NextFlowController.NextFlowConfiguration configuration = new NextFlowController.NextFlowConfiguration();
            configuration.setAccountName(props.get(NEXTFLOW_ACCOUNT_NAME));
            configuration.setNextFlowConfigFilePath(props.get(NEXTFLOW_CONFIG_FILE_PATH));
            configuration.setIdentity(props.get(NEXTFLOW_IDENTIRY));
            configuration.setCredential(props.get(NEXTFLOW_CREDENTIAL));
            configuration.setS3BucketPath(props.get(NEXTFLOW_S3_BUCKET_PATH));
            return configuration;
        }

        return null;
    }

    public void addConfiguration(NextFlowController.NextFlowConfiguration configuration, BindException errors)
    {
        checkArgs(configuration.getNextFlowConfigFilePath(), configuration.getAccountName(), configuration.getIdentity(), configuration.getCredential(), configuration.getS3BucketPath(), errors);

        // Check the config exists
        PropertyManager.PropertyMap config = _encryptedStore.getWritableProperties(NEXTFLOW_CONFIG, true);
        if (config.containsKey(configuration.getAccountName()))
            errors.rejectValue("name", ERROR_MSG, "Config already exists");

        if (!errors.hasErrors())
            saveConfiguration(configuration);
    }

    private void saveConfiguration( NextFlowController.NextFlowConfiguration configuration)
    {
        try (DbScope.Transaction tx = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            Map<String, String> properties = new HashMap<>();
            properties.put(NEXTFLOW_CONFIG_FILE_PATH, configuration.getNextFlowConfigFilePath());
            properties.put(NEXTFLOW_IDENTIRY, configuration.getIdentity());
            properties.put(NEXTFLOW_CREDENTIAL, configuration.getCredential());
            properties.put(NEXTFLOW_S3_BUCKET_PATH, configuration.getS3BucketPath());
            properties.put(NEXTFLOW_ACCOUNT_NAME, configuration.getAccountName());

            PropertyManager.PropertyMap props = _encryptedStore.getWritableProperties(NEXTFLOW_CONFIG, true);
            props.clear();
            props.putAll(properties);
            props.save();

            tx.commit();
        }
    }

}

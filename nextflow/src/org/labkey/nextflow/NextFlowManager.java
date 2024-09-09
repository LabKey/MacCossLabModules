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
    private static final String CATEGORY = "nextflow-config";
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

    public void addConfiguration(NextFlowController.NextFlowConfiguration configuration, BindException errors)
    {
        checkArgs(configuration.getAccountName(), configuration.getIdentity(), configuration.getCredential(), configuration.getS3BucketPath(), errors);

        // Check the account exists in the list of accounts
        PropertyManager.PropertyMap accounts = _normalStore.getWritableProperties(CATEGORY, true);
        if (accounts.containsKey(configuration.getAccountName()))
            errors.rejectValue("name", ERROR_MSG, "account already exists");

        if (!errors.hasErrors())
            saveConfiguration(accounts, configuration);
    }

    private void saveConfiguration(@NotNull PropertyManager.PropertyMap accounts, NextFlowController.NextFlowConfiguration configuration)
    {
        try (DbScope.Transaction tx = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            String category = category(configuration.getAccountName());
            accounts.put(configuration.getAccountName(), category);
            accounts.save();

            Map<String, Object> properties = new HashMap<>();
            properties.put("nextFlowConfigFilePath", configuration.getNextFlowConfigFilePath());
            properties.put("identity", configuration.getIdentity());
            properties.put("credential", configuration.getCredential());
            properties.put("s3BucketPath", configuration.getS3BucketPath());
            properties.put("accountName", configuration.getAccountName());

            PropertyManager.PropertyMap props = _encryptedStore.getWritableProperties(category, true);
            props.clear();
            props.putAll(props);
            props.save();

            tx.commit();
        }
    }

    private String category(@NotNull String name)
    {
        return CATEGORY + ":" + name;
    }

}

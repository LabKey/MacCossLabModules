package org.labkey.lincs.psp;

public class LincsPspException extends Exception
{
    public static final String NO_PSP_CONFIG = "PSP endpoint configuration was not saved in this container. Skipping POST to PSP.";

    public LincsPspException(String message)
    {
        super(message);
    }

    public LincsPspException(String message, Throwable t)
    {
        super(message, t);
    }

    public LincsPspException(String message, String json)
    {
        super(buildMessage(message, json));
    }

    public LincsPspException(String message, String json, Throwable t)
    {
        super(buildMessage(message, json), t);
    }

    private static String buildMessage(String message, String json)
    {
        return message + ". Server returned JSON: " + json;
    }

    public boolean noSavedPspConfig()
    {
        return getMessage() != null && getMessage().contains(NO_PSP_CONFIG);
    }
}

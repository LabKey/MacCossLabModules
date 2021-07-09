package org.labkey.panoramapublic.chromlib;

public class ChromLibStateException extends Exception
{
    public ChromLibStateException(String message)
    {
        super(message);
    }

    public ChromLibStateException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public ChromLibStateException(Throwable cause)
    {
        super(cause);
    }
}

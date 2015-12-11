package org.labkey.lincs;

/**
 * User: vsharma
 * Date: 12/8/2015
 * Time: 10:39 AM
 */
public class LincsAnnotation
{
    private String _name;
    private String _displayName;
    private boolean _advanced;
    private boolean _ignored;

    public LincsAnnotation() {}

    public LincsAnnotation(String name)
    {
        _name = name;
    }

    public LincsAnnotation(String name, String displayName, boolean advanced, boolean ignored)
    {
        this(name);
        _displayName = displayName;
        _advanced = advanced;
        _ignored = ignored;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getName()
    {
        return _name;
    }

    public String getDisplayName()
    {
        return _displayName;
    }

    public void setDisplayName(String displayName)
    {
        _displayName = displayName;
    }

    public boolean isAdvanced()
    {
        return _advanced;
    }

    public void setAdvanced(boolean advanced)
    {
        _advanced = advanced;
    }

    public boolean isIgnored()
    {
        return _ignored;
    }

    public void setIgnored(boolean ignored)
    {
        _ignored = ignored;
    }
}

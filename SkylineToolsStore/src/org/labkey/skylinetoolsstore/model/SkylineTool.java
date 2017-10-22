package org.labkey.skylinetoolsstore.model;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.Pair;
import org.labkey.skylinetoolsstore.SkylineToolsStoreController;

import javax.imageio.ImageIO;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Objects;

public class SkylineTool extends Entity
{
    private String _zipName;
    private String _name;
    private String _authors;
    private String _organization;
    private String _provider;
    private String _version;
    private String _languages;
    private String _description;
    private String _identifier;
    private byte[] _icon = null;
    private Integer _downloads = 0;
    private boolean _latest = false;
    private Integer _rowId;

    public SkylineTool()
    {
    }

    public SkylineTool(BufferedReader reader) throws IOException
    {
        parseProperties(reader);
    }

    public String getZipName()
    {
        return _zipName;
    }

    public void setZipName(String zipName)
    {
        _zipName = zipName;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getAuthors()
    {
        return _authors;
    }

    public void setAuthors(String authors)
    {
        _authors = authors;
    }

    public String getOrganization()
    {
        return _organization;
    }

    public void setOrganization(String organization)
    {
        _organization = organization;
    }

    public String getProvider()
    {
        return _provider;
    }

    public void setProvider(String provider)
    {
        _provider = provider;
    }

    public String getVersion()
    {
        return _version;
    }

    public void setLanguages(String languages)
    {
        _languages = languages;
    }

    public String getLanguages()
    {
        return _languages;
    }

    public void setVersion(String version)
    {
        _version = version;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getIdentifier()
    {
        return _identifier;
    }

    public void setIdentifier(String identifier)
    {
        _identifier = identifier;
    }

    public Integer getDownloads()
    {
        return _downloads;
    }

    public void setDownloads(Integer downloads)
    {
        _downloads = downloads;
    }

    public boolean getLatest()
    {
        return _latest;
    }

    public void setLatest(boolean latest)
    {
        _latest = latest;
    }

    public byte[] getIcon()
    {
        return _icon;
    }

    public void setIcon(byte[] icon)
    {
        _icon = icon;
    }

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }

    public boolean equals(Object obj)
    {
        if (!(obj instanceof SkylineTool))
            return false;
        SkylineTool p = (SkylineTool)obj;

        return Objects.equals(_name, p.getName()) &&
               Objects.equals(_authors, p.getAuthors()) &&
               Objects.equals(_provider, p.getProvider()) &&
               Objects.equals(_version, p.getVersion()) &&
               Objects.equals(_languages, p.getLanguages()) &&
               Objects.equals(_description, p.getDescription()) &&
               Objects.equals(_identifier, p.getIdentifier());
    }

    protected Pair<String, String> getNextProperty(BufferedReader reader) throws IOException
    {
        String line;
        int splitter = 0;
        while ((line = reader.readLine()) != null)
        {
            line = line.trim();
            if (!line.isEmpty() && line.charAt(0) != '#' && (splitter = line.indexOf('=')) != -1)
                break;
        }

        if (line == null)
            return null;

        String propName = line.substring(0, splitter).trim();
        String propValue = (splitter != line.length() - 1) ? line.substring(splitter + 1).trim() : "";

        while (propValue.endsWith("\\") && (line = reader.readLine()) != null)
        {
            line = line.trim();
            if (line.isEmpty() || line.charAt(0) == '#')
                continue;

            propValue = propValue.substring(0, propValue.length() - 1) + '\n' + line;
        }

        return new Pair<>(propName, propValue);
    }

    public void setProperty(String propName, String propValue)
    {
        switch (propName.toLowerCase())
        {
            case "name":
                this.setName(propValue);
                break;
            case "version":
                this.setVersion(propValue);
                break;
            case "author":
                this.setAuthors(propValue);
                break;
            case "languages":
                this.setLanguages(propValue);
                break;
            case "organization":
                this.setOrganization(propValue);
                break;
            case "description":
                // Description may have quotes around it
                if (propValue.length() > 1 && propValue.startsWith("\"") && propValue.endsWith("\""))
                    propValue = propValue.substring(1, propValue.length() - 1).trim();
                this.setDescription(propValue);
                break;
            case "provider":
                this.setProvider(propValue);
                break;
            case "identifier":
                this.setIdentifier(propValue);
                break;
        }
    }

    public void parseProperties(BufferedReader reader) throws IOException
    {
        Pair<String, String> pair;
        while ((pair = getNextProperty(reader)) != null)
            setProperty(pair.first, pair.second);
    }

    public ArrayList<String> getMissingValues()
    {
        // Name, version, and identifier are required
        ArrayList<String> missingValues = new ArrayList();
        if (StringUtils.trimToNull(_name) == null)
            missingValues.add("Name");
        if (StringUtils.trimToNull(_version) == null)
            missingValues.add("Version");
        if (StringUtils.trimToNull(_identifier) == null)
            missingValues.add("Identifier");
        return missingValues;
    }

    public void writeIconToFile(File file, String format) throws IOException
    {
        if (_icon == null)
            return;

        try (ByteArrayInputStream iconInputStream = new ByteArrayInputStream(_icon);
             FileOutputStream iconOutputStream = new FileOutputStream(file))
        {
            ImageIO.write(ImageIO.read(iconInputStream), format, iconOutputStream);
        }
        catch (IOException e)
        {
            throw e;
        }
    }

    public String getFolderUrl()
    {
        return AppProps.getInstance().getContextPath() + "/files" + lookupContainer().getPath() + "/";
    }

    public String getIconUrl()
    {
        return (SkylineToolsStoreController.makeFile(lookupContainer(), "icon.png").exists()) ?
            getFolderUrl() + "icon.png" :
            AppProps.getInstance().getContextPath() + "/skylinetoolsstore/img/placeholder.png";
    }

    public String getPrettyCreated()
    {
        Calendar uploadCal = Calendar.getInstance();
        uploadCal.setTime(getCreated());
        return new SimpleDateFormat("MMM d, yyyy").format(uploadCal.getTime());
    }

    public InputStream getInfoPropertiesStream(InputStream in, String propName, String newValue) throws IOException
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));

        newValue = newValue.replace("\r\n", "\\\r\n");

        boolean foundProperty = false;

        StringBuilder sb = new StringBuilder();
        String line = reader.readLine();
        while (line != null)
        {
            String originalLine = line;
            line = line.trim();
            int splitter;
            String curPropName;
            if (!line.isEmpty() && line.charAt(0) != '#' && (splitter = line.indexOf('=')) != -1
                && (curPropName = line.substring(0, splitter).trim()).equalsIgnoreCase(propName))
            {
                foundProperty = true;
                boolean wasMultiline = false;
                while ((line = reader.readLine()) != null && line.trim().endsWith("\\"))
                    wasMultiline = true;
                if (!newValue.isEmpty())
                {
                    sb.append(curPropName).append(" = ").append(newValue).append(reader.ready() ? "\r\n" : "");
                }
                // Consume the last line of a property value that had multiple lines
                if (!wasMultiline)
                    continue;
            }
            else
                sb.append(originalLine).append(reader.ready() ? "\r\n" : "");

            line = reader.readLine();
        }

        if (!foundProperty)
            sb.append("\r\n").append(propName).append(" = ").append(newValue);

        return new ByteArrayInputStream(sb.toString().getBytes());
    }

    public Container getContainerParent()
    {
        Container c = lookupContainer();
        if(c != null)
        {
            return c.getParent();
        }
        return null;
    }
}

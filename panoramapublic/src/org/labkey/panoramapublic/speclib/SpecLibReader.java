package org.labkey.panoramapublic.speclib;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.targetedms.ISpectrumLibrary;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.FileUtil;
import org.sqlite.SQLiteConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import static org.labkey.panoramapublic.speclib.LibraryType.*;

public abstract class SpecLibReader
{
    static
    {
        try {
            Class.forName("org.sqlite.JDBC");
        }
        catch(ClassNotFoundException e)
        {
            throw new RuntimeException("Could not find SQLite driver", e);
        }
    }

    public static SpecLibReader getReader(ISpectrumLibrary library)
    {
        if (isBiblioSpec(library))
        {
            return new BlibReader();
        }
        else if (isEncyclopeDia(library))
        {
            return new ElibReader();
        }
        return null;
    }

    abstract List<LibSourceFile> readLibSourceFiles(String libFile) throws SQLException;

    /**
     * @param run ITargetedMSRun object representing a Skyline document
     * @param library library
     * @return list of source files used to build the given library if it is a supported library type (BiblioSpec, EncyclopeDIA), null otherwise
     * @throws SpecLibReaderException if the library file path does not exist of if there is an error reading the library.
     */
    public @Nullable List<LibSourceFile> readLibSourceFiles(ITargetedMSRun run, ISpectrumLibrary library) throws SpecLibReaderException
    {
        Path libFilePath = TargetedMSService.get().getLibraryFilePath(run, library);
        return readLibSourceFiles(library, libFilePath, run.getFileName());
    }

    public @Nullable List<LibSourceFile> readLibSourceFiles(ISpectrumLibrary library, Path libFilePath, String skylineDocName) throws SpecLibReaderException
    {
        if (libFilePath == null)
        {
            throw new SpecLibReaderException(String.format("Could not get the path for library '%s' in the Skyline document '%s'.", library.getFileNameHint(), skylineDocName));
        }
        if (!Files.exists(libFilePath))
        {
            throw new SpecLibReaderException("Library file path does not exist: " + libFilePath);
        }

        try
        {
            long size = Files.size(libFilePath);
            // Don't try to read 0-byte library files. There was a bug in the Panorama Public code that created
            // 0-byte .blib files when trying to open a connection to a spectrum library file missing from the .sky.zip
            // https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=43061
            if (size == 0)
            {
                return Collections.emptyList();
            }
            return readLibSourceFiles(FileUtil.getAbsolutePath(libFilePath));
        }
        catch (IOException e)
        {
            throw new SpecLibReaderException("Error getting the size of library file " + libFilePath, e);
        }
        catch (SQLException sqlEx)
        {
            throw new SpecLibReaderException("Error reading library file " + libFilePath, sqlEx);
        }
    }

    public static boolean isBiblioSpec(ISpectrumLibrary library)
    {
        return bibliospec_lite.name().equalsIgnoreCase(library.getLibraryType())
                || bibliospec.name().equalsIgnoreCase(library.getLibraryType());
    }

    public static boolean isEncyclopeDia(ISpectrumLibrary library)
    {
        return elib.name().equalsIgnoreCase(library.getLibraryType());
    }

    Connection getConnection(String libFile) throws SQLException
    {
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        return DriverManager.getConnection("jdbc:sqlite:/" + libFile, config.toProperties());
    }
}

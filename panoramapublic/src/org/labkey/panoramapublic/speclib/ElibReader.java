package org.labkey.panoramapublic.speclib;

import org.jetbrains.annotations.Nullable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ElibReader extends SpecLibReader
{
    @Override
    @Nullable List<LibSourceFile> readLibSourceFiles(String libFile) throws SQLException
    {
        try (Connection conn = getConnection(libFile))
        {
            List<LibSourceFile> sourceFiles = new ArrayList<>();

            // EncyclopeDIA libraries only have spectral source files, no peptide Id files
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT DISTINCT SourceFile FROM entries"))
            {
                while (rs.next())
                {
                    String fileName = rs.getString(1);
                    sourceFiles.add(new LibSourceFile(fileName, null, null));
                }
            }

            return sourceFiles;
        }
    }
}

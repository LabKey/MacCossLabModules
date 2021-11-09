package org.labkey.panoramapublic.speclib;

import org.jetbrains.annotations.Nullable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BlibReader extends SpecLibReader
{
    @Override
    public @Nullable List<LibSourceFile> readLibSourceFiles(String libFile) throws SQLException
    {
        try (Connection conn = getConnection(libFile))
        {
            if (!hasTable(conn, "SpectrumSourceFiles"))
            {
                return null;
            }

            List<LibSourceFile> sourceFiles = new ArrayList<>();

            Map<Integer, Set<String>> scoreTypes = new HashMap<>(); // file id -> score types
            if(hasTable(conn, "ScoreTypes")) // Older .blib files do not have a ScoreTypes table
            {
                try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT DISTINCT r.fileID, s.scoreType FROM RefSpectra as r JOIN ScoreTypes s ON r.scoreType = s.id"))
                {
                    while (rs.next())
                    {
                        int fileId = rs.getInt(1);
                        String scoreType = rs.getString(2);
                        scoreTypes.putIfAbsent(fileId, new HashSet<>());
                        scoreTypes.get(fileId).add(scoreType);
                    }
                }
            }
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM SpectrumSourceFiles"))
            {
                int idColumn = -1;
                int fileNameColumn = -1;
                int idFileNameColumn = -1;
                ResultSetMetaData metadata = rs.getMetaData();
                for (int i = 1; i <= metadata.getColumnCount(); i++)
                {
                    switch (metadata.getColumnName(i).toLowerCase())
                    {
                        case "id" -> idColumn = i;
                        case "filename" -> fileNameColumn = i;
                        case "idfilename" -> idFileNameColumn = i;
                    }
                }
                while (rs.next())
                {
                    Integer id = rs.getInt(idColumn);
                    String fileName = rs.getString(fileNameColumn);
                    String idFileName = idFileNameColumn >= 0 ? rs.getString(idFileNameColumn) : null;
                    sourceFiles.add(new LibSourceFile(fileName, idFileName, scoreTypes.getOrDefault(id, null)));
                }
            }

            return sourceFiles;
        }
    }

    private static boolean hasTable(Connection conn, String tableName) throws SQLException
    {
        String tableCheckStmt = "SELECT name FROM sqlite_master WHERE type='table' AND name='" + tableName + "'";

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(tableCheckStmt))
        {
            if (rs.next())
            {
                return true;
            }
        }
        return false;
    }
}

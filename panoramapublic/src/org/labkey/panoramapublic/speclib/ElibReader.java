/*
 * Copyright (c) 2021-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

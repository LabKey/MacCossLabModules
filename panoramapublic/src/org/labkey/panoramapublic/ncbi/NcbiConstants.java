/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.panoramapublic.ncbi;

import org.jetbrains.annotations.Nullable;

public class NcbiConstants
{
    /** Regex for validating PubMed IDs (1-8 digits). Not used for PMC IDs which may be longer. */
    public static final String PUBMED_ID = "^[0-9]{1,8}$"; // https://libguides.library.arizona.edu/c.php?g=406096&p=2779570

    public enum DB
    {
        PubMed("PubMed"),
        PMC("PubMed Central");

        private final String _label;

        DB(String label)
        {
            _label = label;
        }

        public String getLabel()
        {
            return _label;
        }

        public static @Nullable DB fromString(@Nullable String database)
        {
            if (database == null) return null;
            for (DB db : values())
            {
                if (db.name().equals(database)) return db;
            }
            return null;
        }
    }

    public static String getPubmedLink(String pubmedId)
    {
        // Example: https://pubmed.ncbi.nlm.nih.gov/29331002
        return "https://pubmed.ncbi.nlm.nih.gov/" + pubmedId;
    }

    public static String getPmcLink(String pmcId)
    {
        return "https://www.ncbi.nlm.nih.gov/pmc/articles/" + pmcId;
    }
}

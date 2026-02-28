package org.labkey.panoramapublic.ncbi;

import org.jetbrains.annotations.Nullable;

public class NcbiConstants
{
    /** Regex for validating numeric PubMed/PMC IDs (1-8 digits). */
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

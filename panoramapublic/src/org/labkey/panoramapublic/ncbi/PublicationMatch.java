package org.labkey.panoramapublic.ncbi;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.panoramapublic.model.DatasetStatus;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Container for publication search article match
 */
public class PublicationMatch
{
    public static final String MATCH_PX_ID = "ProteomeXchange ID";
    public static final String MATCH_PANORAMA_URL = "Panorama URL";
    public static final String MATCH_DOI = "DOI";
    public static final String MATCH_AUTHOR = "Author";
    public static final String MATCH_TITLE = "Title";

    private final String _publicationId;
    private final PublicationType _publicationType;
    private final boolean _matchesProteomeXchangeId;
    private final boolean _matchesPanoramaUrl;
    private final boolean _matchesDoi;
    private final boolean _matchesAuthor;
    private final boolean _matchesTitle;
    private final @Nullable Date _publicationDate;

    public enum PublicationType
    {
        PMID("PubMed ID"),
        PMC("PMC ID");

        private final String _label;

        PublicationType(String label)
        {
            _label = label;
        }

        /** Human-readable label, e.g. "PubMed ID" or "PMC ID". */
        public String getLabel()
        {
            return _label;
        }

        public static @Nullable PublicationType fromString(@Nullable String value)
        {
            if (value == null) return null;
            for (PublicationType type : values())
            {
                if (type.name().equals(value)) return type;
            }
            return null;
        }
    }

    public PublicationMatch(String publicationId, PublicationType publicationType,
                            boolean matchesProteomeXchangeId, boolean matchesPanoramaUrl, boolean matchesDoi,
                            boolean matchesAuthor, boolean matchesTitle,
                            @Nullable Date publicationDate)
    {
        _publicationId = publicationId;
        _publicationType = publicationType;
        _matchesProteomeXchangeId = matchesProteomeXchangeId;
        _matchesPanoramaUrl = matchesPanoramaUrl;
        _matchesDoi = matchesDoi;
        _matchesAuthor = matchesAuthor;
        _matchesTitle = matchesTitle;
        _publicationDate = publicationDate;
    }

    public String getPublicationId()
    {
        return _publicationId;
    }

    public PublicationType getPublicationType()
    {
        return _publicationType;
    }

    public boolean matchesProteomeXchangeId()
    {
        return _matchesProteomeXchangeId;
    }

    public boolean matchesPanoramaUrl()
    {
        return _matchesPanoramaUrl;
    }

    public boolean matchesDoi()
    {
        return _matchesDoi;
    }

    public boolean matchesAuthor()
    {
        return _matchesAuthor;
    }

    public boolean matchesTitle()
    {
        return _matchesTitle;
    }

    public @Nullable Date getPublicationDate()
    {
        return _publicationDate;
    }

    /**
     * String representation of what matched for this article.
     * This is what gets stored in the PublicationMatchInfo database column.
     * Example: "ProteomeXchange ID, Panorama URL, Author, Title"
     *
     * <p>Use {@link #fromMatchInfo(String)} to reconstruct the individual flags.
     */
    public String getMatchInfo()
    {
        List<String> parts = new ArrayList<>();
        if (_matchesProteomeXchangeId) parts.add(MATCH_PX_ID);
        if (_matchesPanoramaUrl)       parts.add(MATCH_PANORAMA_URL);
        if (_matchesDoi)               parts.add(MATCH_DOI);
        if (_matchesAuthor)            parts.add(MATCH_AUTHOR);
        if (_matchesTitle)             parts.add(MATCH_TITLE);
        return String.join(", ", parts);
    }

    public String getPublicationUrl()
    {
        if (_publicationType == PublicationType.PMC)
        {
            return "https://www.ncbi.nlm.nih.gov/pmc/articles/" + _publicationId;
        }
        return "https://pubmed.ncbi.nlm.nih.gov/" + _publicationId;
    }

    public String getPublicationLabel()
    {
        return _publicationType.getLabel() + " " + _publicationId;
    }

    /**
     * Build a PublicationMatch from a persisted DatasetStatus.
     */
    public static @Nullable PublicationMatch fromDatasetStatus(@NotNull DatasetStatus datasetStatus)
    {
        if (StringUtils.isBlank(datasetStatus.getPotentialPublicationId()))
        {
            return null;
        }
        PublicationType type = PublicationType.fromString(datasetStatus.getPublicationType());
        if (type == null)
        {
            return null;
        }
        String matchInfo = datasetStatus.getPublicationMatchInfo();
        boolean pxId = false, url = false, doi = false, author = false, title = false;
        if (!StringUtils.isBlank(matchInfo))
        {
            for (String segment : matchInfo.split(","))
            {
                switch (segment.trim())
                {
                    case MATCH_PX_ID        -> pxId   = true;
                    case MATCH_PANORAMA_URL -> url    = true;
                    case MATCH_DOI          -> doi    = true;
                    case MATCH_AUTHOR       -> author = true;
                    case MATCH_TITLE        -> title  = true;
                    default -> { /* unknown token – ignore */ }
                }
            }
        }
        return new PublicationMatch(datasetStatus.getPotentialPublicationId(), type,
                pxId, url, doi, author, title, null);
    }
}

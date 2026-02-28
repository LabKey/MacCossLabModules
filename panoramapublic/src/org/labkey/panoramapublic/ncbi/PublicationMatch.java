package org.labkey.panoramapublic.ncbi;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.panoramapublic.model.DatasetStatus;
import org.labkey.panoramapublic.ncbi.NcbiConstants.DB;

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
    private final DB _publicationType;
    private final boolean _matchesProteomeXchangeId;
    private final boolean _matchesPanoramaUrl;
    private final boolean _matchesDoi;
    private final boolean _matchesAuthor;
    private final boolean _matchesTitle;
    private final @Nullable Date _publicationDate;
    private @Nullable String _citation;


    public PublicationMatch(String publicationId, DB publicationType,
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

    public DB getPublicationType()
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

    public @Nullable String getCitation()
    {
        return _citation;
    }

    public void setCitation(@Nullable String citation)
    {
        _citation = citation;
    }

    /**
     * String representation of what matched for this article.
     * This is what gets stored in the PublicationMatchInfo database column.
     * Example: "ProteomeXchange ID, Panorama URL, Author, Title"
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
        if (_publicationType == DB.PMC)
        {
            return NcbiConstants.getPmcLink(_publicationId);
        }
        return NcbiConstants.getPubmedLink(_publicationId);
    }

    public String getPublicationLabel()
    {
        return _publicationType.getLabel() + " " + _publicationId;
    }

    public JSONObject toJson()
    {
        JSONObject json = new JSONObject();
        json.put("publicationId", _publicationId);
        json.put("publicationType", _publicationType.name());
        json.put("publicationLabel", getPublicationLabel());
        json.put("publicationUrl", getPublicationUrl());
        json.put("matchInfo", getMatchInfo());
        if (_citation != null)
        {
            json.put("citation", _citation);
        }
        return json;
    }

    /**
     * Build a PublicationMatch from a publication ID, type, and match info string.
     * @see #getMatchInfo()
     */
    public static PublicationMatch fromMatchInfo(@NotNull String publicationId, @NotNull DB publicationType, @Nullable String matchInfo)
    {
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
        return new PublicationMatch(publicationId, publicationType, pxId, url, doi, author, title, null);
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
        DB type = DB.fromString(datasetStatus.getPublicationType());
        if (type == null)
        {
            return null;
        }
        return fromMatchInfo(datasetStatus.getPotentialPublicationId(), type, datasetStatus.getPublicationMatchInfo());
    }
}

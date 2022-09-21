package org.labkey.panoramapublic.datacite;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.old.JSONArray;
import org.json.old.JSONObject;
import org.labkey.api.security.User;
import org.labkey.api.util.DOM;
import org.labkey.api.util.DateUtil;
import org.labkey.panoramapublic.model.ExperimentAnnotations;

import java.util.Calendar;

import static org.labkey.api.util.DOM.BR;
import static org.labkey.api.util.DOM.DIV;

/**
 * This class encapsulates the metadata that will be submitted to DataCite when a DOI is made 'findable'
 */
public class DoiMetadata
{
    private String _url; // The Panorama Public access URL for the data
    private String _labHead;
    private String _submitter;
    private String _title;
    private String _abstract;
    private int _year;
    private String _doi;
    private String _prefix;

    private DoiMetadata() {}

    public static DoiMetadata from(ExperimentAnnotations expAnnot) throws DataCiteException
    {
        return from(expAnnot, null);
    }

    public static DoiMetadata from(ExperimentAnnotations expAnnot, @Nullable String prefix) throws DataCiteException
    {
        DoiMetadata metadata = new DoiMetadata();
        metadata._url = expAnnot.getShortUrl().renderShortURL();
        User labHead = expAnnot.getLabHeadUser();
        if(labHead != null)
        {
            metadata._labHead = getUserName(labHead, "Lab Head");
        }
        User submitter = expAnnot.getSubmitterUser();
        if(submitter == null)
        {
            throw new DataCiteException("Submitter not found for experiment");
        }
        metadata._submitter = getUserName(submitter, "Submitter");
        metadata._title = expAnnot.getTitle();
        metadata._abstract = expAnnot.getAbstract();
        metadata._year = DateUtil.now().get(Calendar.YEAR);
        metadata._doi = expAnnot.getDoi();
        metadata._prefix = prefix;
        if (metadata._doi == null && StringUtils.isBlank(metadata._prefix))
        {
            throw new DataCiteException("Given experiment does not have a DOI. A DOI prefix is required to generate a new DOI for the experiment.");
        }
        return metadata;
    }

    private static String getUserName(@NotNull User user, @NotNull String userType) throws DataCiteException
    {
        if(StringUtils.isBlank(user.getFirstName()))
        {
            throw new DataCiteException("First name is missing for " + userType + " " + user.getEmail());
        }
        if(StringUtils.isBlank(user.getLastName()))
        {
            throw new DataCiteException("Last name is missing for " + userType + " " + user.getEmail());
        }
        return user.getLastName() + ", " + user.getFirstName();
    }

    public JSONObject getJson()
    {
        /* Example
            {
              "data": {
                "id": "10.5438/0012",
                "type": "dois",
                "attributes": {
                  "event": "publish",
                  "doi": "10.5438/0012",
                  "creators": [{
                    "name": "DataCite Metadata Working Group"
                  }],
                  "titles": [{
                    "title": "DataCite Metadata Schema Documentation for the Publication and Citation of Research Data v4.0"
                  }],
                  "publisher": "DataCite e.V.",
                  "publicationYear": 2016,
                  "types": {
                    "resourceTypeGeneral": "Text"
                  },
                  "url": "https://schema.datacite.org/meta/kernel-4.0/index.html",
                  "schemaVersion": "http://datacite.org/schema/kernel-4"
                }
              }
            }
         */
        JSONObject data = new JSONObject();
        if (_doi != null)
        {
            data.put("id", _doi);
        }
        data.put("type", "dois");
        data.put("attributes", getAttributes());

        return new JSONObject().put("data", data);
    }

    private JSONObject getAttributes()
    {
        JSONObject attribs = new JSONObject();
        if (_doi != null)
        {
            // A DOI is already assigned to the experiment.  We are generating JSON for making the DOI 'findable' (public).
            attribs.put("event", "publish");
            attribs.put("doi", _doi);
            attribs.put("publicationYear", _year);
        }
        else if (_prefix != null)
        {
            // The experiment does not have a DOI. We are generating JSON to create a new 'draft' (private) DOI.
            attribs.put("prefix", _prefix);
        }

        // Always include these fields for 'draft' as well as 'findable' DOIs.
        attribs.put("publisher", "Panorama Public");
        attribs.put("url", _url);
        attribs.put("types", new JSONObject().put("resourceTypeGeneral", "Dataset"));

        if (_doi != null)
        {
            JSONArray creators = new JSONArray();
            creators.put(new JSONObject().put("name", _submitter).put("nameType", "Personal"));
            if(_labHead != null)
            {
                creators.put(new JSONObject().put("name", _labHead).put("nameType", "Personal"));
            }
            attribs.put("creators", creators);
            attribs.put("titles", new JSONArray().put(new JSONObject().put("title", _title)));

            JSONArray descriptions = new JSONArray();
            descriptions.put(new JSONObject().put("description", _abstract).put("descriptionType", "Abstract"));
            attribs.put("descriptions", descriptions);
        }

        return attribs;
    }

    public @NotNull DOM.Renderable getHtmlString()
    {
        String creators = _submitter;
        if(_labHead != null)
        {
            creators += "; " + _labHead;
        }
        return DIV("DOI: " + _doi,
                BR(), "Publisher: Panorama Public",
                BR(), "Publication Year: " + _year,
                BR(), "URL " + _url,
                BR(), "Type: Dataset",
                BR(), "Title: " + _title,
                BR(), "Creators: " + creators,
                BR(), "JSON: " + getJson().toString());
    }
}

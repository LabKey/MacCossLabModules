package org.labkey.panoramapublic.model.validation;

import org.jetbrains.annotations.NotNull;
import org.json.old.JSONObject;
import org.labkey.panoramapublic.model.DbEntity;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.query.DataValidationManager;
import org.labkey.panoramapublic.query.ExperimentAnnotationsManager;

import java.text.SimpleDateFormat;

// For table panoramapublic.datavalidation
public class DataValidation extends DbEntity
{
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-d HH:mm");

    private int _experimentAnnotationsId;
    private int _jobId;
    private PxStatus _status;

    public DataValidation() {}

    public DataValidation (int experimentAnnotationsId)
    {
        _experimentAnnotationsId = experimentAnnotationsId;
    }

    public int getExperimentAnnotationsId()
    {
        return _experimentAnnotationsId;
    }

    public void setExperimentAnnotationsId(int experimentAnnotationsId)
    {
        _experimentAnnotationsId = experimentAnnotationsId;
    }

    public int getJobId()
    {
        return _jobId;
    }

    public void setJobId(int jobId)
    {
        _jobId = jobId;
    }

    public PxStatus getStatus()
    {
        return _status;
    }

    public void setStatus(PxStatus status)
    {
        _status = status;
    }

    public PxStatus getStatusIncludingExptMetadata(ExperimentAnnotations expAnnotations)
    {
        if (isComplete() && DataValidationManager.getMissingExperimentMetadataFields(expAnnotations).size() > 0)
        {
            return PxStatus.NotValid;
        }
        return _status;
    }

    public boolean isComplete()
    {
        return getStatus() != null;
    }

    public String getFormattedDate()
    {
        return getCreated() != null ? DATE_FORMAT.format(getCreated()) : "";
    }

    @NotNull
    public JSONObject toJSON()
    {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", getId());
        jsonObject.put("date", getFormattedDate());
        jsonObject.put("experimentAnnotationsId", getExperimentAnnotationsId());
        jsonObject.put("status", _status != null ? _status.getLabel() : "In Progress");
        jsonObject.put("statusId", _status != null ? _status.ordinal() : -1);

        var exptAnnotations = ExperimentAnnotationsManager.get(_experimentAnnotationsId);
        if (exptAnnotations != null)
        {
            jsonObject.put("folder", exptAnnotations.getContainer().getName());
        }
        return jsonObject;
    }
}

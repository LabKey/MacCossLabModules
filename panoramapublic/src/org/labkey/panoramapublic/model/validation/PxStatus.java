package org.labkey.panoramapublic.model.validation;

public enum PxStatus
{
    NotValid("Data is not valid for a ProteomeXchange submission"),
    Incomplete("Incomplete Submission"), // “Unsupported dataset by repository” (MS:1002857)
    IncompleteMetadata("Incomplete data and/or metadata"), // "supported by repository but incomplete data and/or metadata" (MS:1003087)
    Complete("Complete"); // "Supported dataset by repository" (MS:1002856)

    private final String _label;

    PxStatus(String label)
    {
        _label = label;
    }

    public String getLabel()
    {
        return _label;
    }

    public boolean incompleteSubmission()
    {
        return ordinal() < Complete.ordinal();
    }

    public boolean invalidSubmission()
    {
        return ordinal() < IncompleteMetadata.ordinal();
    }
}

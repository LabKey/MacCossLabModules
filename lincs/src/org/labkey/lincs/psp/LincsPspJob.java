package org.labkey.lincs.psp;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.lincs.LincsModule;

public class LincsPspJob
{
    private int id;
    private long _runId;
    private Container _container;
    private Integer _pipelineJobId;

    private String _pspJobId;
    private String pspJobName;
    private String _status;
    private String _error;
    private byte _progress; // 0 = nothing done; 1 = L2 done; 3 = L2 and L3 done; 7 = L4 & L3 and L2 done
    private String _json;

    private static final String JOB_CHECK_TIMEOUT = "Job check timed out";
    private static final String PSP_JOB_DONE = "POUR : succeeded in uploading all GCTs to Panorama";

    public LincsPspJob() {}

    public LincsPspJob(Container container, long runId)
    {
        _container = container;
        _runId = runId;
    }

    public int getId()
    {
        return id;
    }

    public void setId(int id)
    {
        this.id = id;
    }

    public long getRunId()
    {
        return _runId;
    }

    public void setRunId(int runId)
    {
        _runId = runId;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public Integer getPipelineJobId()
    {
        return _pipelineJobId;
    }

    public void setPipelineJobId(Integer pipelineJobId)
    {
        _pipelineJobId = pipelineJobId;
    }

    public String getPspJobId()
    {
        return _pspJobId;
    }

    public void setPspJobId(String pspJobId)
    {
        _pspJobId = pspJobId;
    }

    public String getPspJobName()
    {
        return pspJobName;
    }

    public void setPspJobName(String pspJobName)
    {
        this.pspJobName = pspJobName;
    }

    public String getStatus()
    {
        return _status;
    }

    public void setStatus(String status)
    {
        _status = status;
    }

    public String getError()
    {
        return _error;
    }

    public void setError(String error)
    {
        _error = error;
    }

    public byte getProgress()
    {
        return _progress;
    }

    public void setProgress(byte progress)
    {
        _progress = progress;
    }

    public String getJson()
    {
        return _json;
    }

    public void setJson(String json)
    {
        _json = json;
        if(_json != null)
        {
            // JSONObject.toString() escapes '/' and '\'.
            // So https://panoramaweb-dr.gs.washington.edu/lincs/LINCS-DCIC/PSP/P100/runGCTReportApi.view?runId=32394
            // becomes
            // https:\/\/panoramaweb-dr.gs.washington.edu\/lincs\/LINCS-DCIC\/PSP\/P100\/runGCTReportApi.view?runId=32394
            _json = _json.replace("\\/", "/");
        }
    }

    public boolean hasError()
    {
        return !StringUtils.isBlank(_error);
    }

    public boolean isSuccess()
    {
        return PSP_JOB_DONE.equals(getStatus());
    }

    public void updateLevelStatus(LincsModule.LincsLevel level, String message)
    {
        switch (level)
        {
            case Two:
                _progress = (byte) (_progress | 1);
                break;
            case Three:
                _progress = (byte) (_progress | 2);
                break;
            case Four:
                _progress = (byte) (_progress | 4);
                break;
        }
        setError(message);
    }

    public boolean isLevel2Done()
    {
        return (_progress & 1) == 1;
    }

    public void addError(String error)
    {
        String err = getError() == null ? error : (error +". " + getError());
        setError(err);
    }

    public void setJobCheckTimeout()
    {
        addError(JOB_CHECK_TIMEOUT);
    }

    public boolean isJobCheckTimeoutError()
    {
        return getError() == null ? false : getError().contains(JOB_CHECK_TIMEOUT);
    }

    public boolean canRetry()
    {
        return isSuccess() || hasError();
    }
}

package org.labkey.lincs.cromwell;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CromwellMetadata
{
    public List<Call> _calls;

    public CromwellMetadata()
    {
        _calls = new ArrayList<>();
    }

    public void addCall(Call call)
    {
        _calls.add(call);
    }

    public static class Call
    {
        private String name;
        private String status;
        private Date  start;
        private Date end;

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getStatus()
        {
            return status;
        }

        public void setStatus(String status)
        {
            this.status = status;
        }

        public Date getStart()
        {
            return start;
        }

        public void setStart(Date start)
        {
            this.start = start;
        }

        public Date getEnd()
        {
            return end;
        }

        public void setEnd(Date end)
        {
            this.end = end;
        }
    }
}

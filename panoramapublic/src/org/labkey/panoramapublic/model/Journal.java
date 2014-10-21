package org.labkey.targetedms.model;


import org.labkey.api.data.Container;

import java.util.Date;

/**
 * User: vsharma
 * Date: 08/07/14
 * Time: 12:50 PM
 */
public class Journal
{
    private Integer _id;
    private String _name;
    private Integer _labkeyGroupId;
    private Container _project;
    private Date _created;
    private int _createdBy;

    public Integer getId()
    {
        return _id;
    }

    public void setId(Integer id)
    {
        _id = id;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public Integer getLabkeyGroupId()
    {
        return _labkeyGroupId;
    }

    public void setLabkeyGroupId(Integer labkeyGroupId)
    {
        _labkeyGroupId = labkeyGroupId;
    }

    public Container getProject()
    {
        return _project;
    }

    public void setProject(Container project)
    {
        _project = project;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public int getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(int createdBy)
    {
        _createdBy = createdBy;
    }
}

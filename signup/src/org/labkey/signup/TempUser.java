package org.labkey.signup;
import org.labkey.api.data.Container;

// Class TempUser contains information needed to add a user to the LabKey database as a User later on but has addition
// field @key for use with confirmation
public class TempUser
{
    private String key;
    private String email;
    private String firstName;
    private String lastName;
    private String organization;
    private Container _container;
    private Integer _labkeyUserId;

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public String getOrganization()
    {
        return organization;
    }

    public void setOrganization(String organization)
    {
        this.organization = organization;
    }

    public int getUserId()
    {
        return userId;
    }

    public void setUserId(int userId)
    {
        this.userId = userId;
    }

    private int userId;

    public String getEmail()
    {
        return email;
    }

    public void setEmail(String email)
    {
        this.email = email;
    }

    public String getFirstName()
    {
        return firstName;
    }

    public void setFirstName(String firstName)
    {
        this.firstName = firstName;
    }

    public String getLastName()
    {
        return lastName;
    }

    public void setLastName(String lastName)
    {
        this.lastName = lastName;
    }

    public String getKey()
    {
        return key;
    }

    public void setKey(String key)
    {
        this.key = key;
    }

    public Integer getLabkeyUserId()
    {
        return _labkeyUserId;
    }

    public void setLabkeyUserId(Integer labkeyUserId)
    {
        _labkeyUserId = labkeyUserId;
    }
}

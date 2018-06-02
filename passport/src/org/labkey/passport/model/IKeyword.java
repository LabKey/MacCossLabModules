package org.labkey.passport.model;

public class IKeyword
{
    public String id;
    public String categoryId;
    public String label;
    public String category;

    public IKeyword(String id, String categoryId, String label, String category)
    {
        this.id = id;
        this.categoryId = categoryId;
        this.label = label;
        this.category = category;
    }
}

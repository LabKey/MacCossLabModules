package org.labkey.skylinetoolsstore.model;

import org.labkey.api.data.Entity;

import java.util.Objects;

public class Rating extends Entity
{
    private Integer _rating;
    private Integer _toolId;
    private String _review;
    private String _title;
    private Integer _rowId;

    public Rating()
    {
    }

    public Rating(int rating, String review, int toolId, String title)
    {
        _rating = rating;
        _review = review;
        _toolId = toolId;
        _title = title;
    }

    public Integer getRating()
    {
        return _rating;
    }

    public void setRating(Integer rating)
    {
        _rating = rating;
    }

    public String getReview()
    {
        return _review;
    }

    public void setReview(String review)
    {
        _review = review;
    }

    public Integer getToolId()
    {
        return _toolId;
    }

    public void setToolId(Integer toolId)
    {
        _toolId = toolId;
    }

    public String getTitle()
    {
        return _title;
    }

    public void setTitle(String title)
    {
        _title = title;
    }

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }

    public boolean equals(Object obj)
    {
        if (!(obj instanceof Rating))
            return false;
        Rating p = (Rating)obj;

        return Objects.equals(_rating, p.getRating()) &&
               Objects.equals(_toolId, p.getToolId()) &&
               Objects.equals(_review, p.getReview()) &&
               Objects.equals(_title, p.getTitle());
    }

}

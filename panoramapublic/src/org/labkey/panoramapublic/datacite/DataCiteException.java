package org.labkey.panoramapublic.datacite;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;

public class DataCiteException extends Exception
{
    private DataCiteResponse _response;

    public DataCiteException(String message)
    {
        super(message);
    }

    public DataCiteException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public DataCiteException(@NotNull String message, @NotNull DataCiteResponse response)
    {
        super("Request failed - " + message + ". Code: " + response.getResponseCode() + "; Message " + response.getMessage() + "; Body: " + response.getResponseBody());
        _response = response;
    }

    public HtmlString getHtmlString()
    {
        if(_response != null)
        {
           return HtmlStringBuilder.of(HtmlString.unsafe("<div>"))
                    .append("Response code: ").append(_response.getResponseCode())
                    .append(HtmlString.BR)
                    .append("Message: ").append(_response.getMessage())
                    .append(HtmlString.BR)
                    .append(_response.getResponseBody())
                    .append(HtmlString.BR).append(HtmlString.BR)
                    .append("Exception: ")
                    .append(HtmlString.BR)
                    .append(ExceptionUtil.renderException(this))
                    .append(HtmlString.unsafe("</div>"))
                    .getHtmlString();
        }
        else
        {
            return ExceptionUtil.renderException(this);
        }
    }
}

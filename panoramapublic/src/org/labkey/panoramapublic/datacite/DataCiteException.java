package org.labkey.panoramapublic.datacite;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.HtmlString;

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
        super("Request failed." + message + ". Code: " + response.getResponseCode() + "; Message " + response.getMessage() + "; Body: " + response.getResponseBody());
        _response = response;
    }

    public HtmlString getHtmlString()
    {
        if(_response != null)
        {
            String s = "<div>Code: " + _response.getResponseCode() + " <br/> Message: " + _response.getMessage() + " <br/> " + _response.getResponseBody();
            return HtmlString.unsafe(s);
        }
        else
        {
            return ExceptionUtil.renderException(this);
        }
    }
}

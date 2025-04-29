package org.labkey.panoramapublic.bluesky;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;

public class BlueskyException extends Exception
{
    private BlueskyResponse _response;

    public BlueskyException(String message)
    {
        super(message);
    }

    public BlueskyException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public BlueskyException(String message, String blueskyAccount, String blueskyEndpoint, Throwable cause)
    {
        super(String.format("%s. Account: %s; Endpoint: %s",
                        message,
                        blueskyAccount != null ? blueskyAccount : "MISSING",
                        blueskyEndpoint != null ? blueskyEndpoint : "MISSING"),
                cause);
    }

    public BlueskyException(@NotNull String message, @NotNull BlueskyResponse response)
    {
        super(String.format("Request failed - %s. Code: %s; Account: %s; Endpoint: %s; Message: %s; Body: %s",
                message,
                response.getStatusCode(),
                response.getAccount(),
                response.getEndpoint(),
                response.getMessage(),
                response.getResponseBody()));
        _response = response;
    }

    public HtmlString getHtmlString()
    {
        if(_response != null)
        {
           return HtmlStringBuilder.of(HtmlString.unsafe("<div>"))
                    .append("Bluesky account: ").append(_response.getAccount())
                    .append(HtmlString.BR)
                    .append("Bluesky endpoint: ").append(_response.getEndpoint())
                    .append(HtmlString.BR)
                    .append("Response status code: ").append(_response.getStatusCode())
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

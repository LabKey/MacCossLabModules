/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.panoramapublic.ncbi;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.logging.LogHelper;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends HTTP GET requests to NCBI for {@link NcbiPublicationSearchServiceImpl}. Retries transient
 * failures, and removes the NCBI API key from anything it logs or puts in an exception message.
 */
public class NcbiHttpClient
{
    private static final Logger LOG = LogHelper.getLogger(NcbiHttpClient.class, "HTTP requests to NCBI for the Panorama Public publication search");

    private static final int TIMEOUT_MS = 10000; // 10 seconds

    // NCBI eutils fails intermittently, even well under the rate limit. Retry the transient
    // failures listed on isRetryable a few times with exponential backoff before giving up.
    private static final int MAX_HTTP_ATTEMPTS = 3; // initial try + 2 retries
    private static final int RETRY_BASE_DELAY_MS = 500; // exponential backoff base

    private static final int TOO_MANY_REQUESTS = 429; // NCBI's response when the request rate is exceeded
    private static final int MAX_ERROR_BODY_CHARS = 500;
    // Read past the logged length by more than the length of an NCBI API key (36 characters), so a key
    // that crosses the cut is read whole and can be redacted.
    private static final int MAX_ERROR_BODY_READ_CHARS = MAX_ERROR_BODY_CHARS + 100;

    private static final String REDACTED = "REDACTED";
    private static final Pattern API_KEY_PARAM = Pattern.compile("api_key=([^&\\s]*)");

    /**
     * Execute an HTTP GET request and return the response body as a string. Retry warnings are
     * written to {@code log}.
     * @throws IOException if the request fails or the server returns a non-2xx response
     */
    public String getString(String url, @NotNull Logger log) throws IOException
    {
        for (int attempt = 1; ; attempt++)
        {
            try
            {
                return executeGet(url);
            }
            catch (IOException e)
            {
                if (attempt >= MAX_HTTP_ATTEMPTS || !isRetryable(e))
                {
                    throw e;
                }
                long delayMs = retryDelayMs(attempt);
                log.warn("NCBI request failed (attempt {} of {}). Retrying in {} ms. URL: {}. Cause: {}",
                        attempt, MAX_HTTP_ATTEMPTS, delayMs, redactApiKey(url, apiKeyFrom(url)), e.toString());
                if (!sleepMs(delayMs))
                {
                    // An interrupted thread cannot wait, so the remaining attempts would run back to
                    // back. Give up instead.
                    throw e;
                }
            }
        }
    }

    // Overridden in unit tests to drive the retry loop in getString(), and by
    // MockNcbiPublicationSearchService to return canned responses, without real HTTP calls.
    protected String executeGet(String url) throws IOException
    {
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
            .setConnectTimeout(Timeout.ofMilliseconds(TIMEOUT_MS))
            .setSocketTimeout(Timeout.ofMilliseconds(TIMEOUT_MS))
            .build();

        RequestConfig requestConfig = RequestConfig.custom()
            .setResponseTimeout(Timeout.ofMilliseconds(TIMEOUT_MS))
            .build();

        BasicHttpClientConnectionManager connectionManager = new BasicHttpClientConnectionManager();
        connectionManager.setConnectionConfig(connectionConfig);

        try (CloseableHttpClient client = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(connectionManager)
                // Without this, the number of requests would be up to twice MAX_HTTP_ATTEMPTS. isRetryable
                // has to cover what this turns off.
                .disableAutomaticRetries()
                .build())
        {
            HttpGet getRequest = new HttpGet(url);
            return client.execute(getRequest, response -> {
                int status = response.getCode();
                if (status < 200 || status >= 300)
                {
                    throw new HttpResponseException(status,
                            errorDetail(status, response.getReasonPhrase(), readErrorBody(status, response),
                                    apiKeyFrom(url)));
                }
                return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            });
        }
    }

    /**
     * Read the body of a client error response, up to {@link #MAX_ERROR_BODY_READ_CHARS} characters.
     * 5xx bodies are large, uninformative HTML error pages, so they are skipped. Returns null if
     * the body cannot be read.
     */
    private static @Nullable String readErrorBody(int status, ClassicHttpResponse response)
    {
        if (status < 400 || status >= 500 || response.getEntity() == null)
        {
            return null;
        }

        try
        {
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8, MAX_ERROR_BODY_READ_CHARS);
        }
        catch (IOException | org.apache.hc.core5.http.ParseException e)
        {
            return null;
        }
    }

    /**
     * Build the message for a non-2xx HttpResponseException. A 4xx appends the response body, so
     * NCBI's reason (e.g. "API key invalid") reaches the log. Other statuses use only the reason
     * phrase. The API key is removed from the body, which is third-party text bound for a log.
     */
    static String errorDetail(int status, String reasonPhrase, @Nullable String body, @Nullable String apiKey)
    {
        if (status >= 400 && status < 500 && !StringUtils.isBlank(body))
        {
            return reasonPhrase + " - " + StringUtils.abbreviate(redactApiKey(body.strip(), apiKey), MAX_ERROR_BODY_CHARS);
        }
        return reasonPhrase;
    }

    /**
     * Replace the NCBI API key wherever it appears in text bound for a log.
     */
    static @Nullable String redactApiKey(@Nullable String text, @Nullable String apiKey)
    {
        if (text == null)
        {
            return null;
        }
        String redacted = text.replaceAll("(api_key=)[^&\\s]*", "$1" + REDACTED);
        if (!StringUtils.isBlank(apiKey))
        {
            redacted = redacted.replace(apiKey.trim(), REDACTED);
        }
        return redacted;
    }

    /**
     * Returns the value of the api_key query parameter in the given URL, or null if there is none.
     */
    static @Nullable String apiKeyFrom(String url)
    {
        Matcher matcher = API_KEY_PARAM.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Read timeouts, connection resets, closed connections, 5xx responses and 429 are transient NCBI
     * failures worth retrying. Other 4xx errors are permanent.
     */
    private static boolean isRetryable(IOException e)
    {
        if (e instanceof SocketTimeoutException || e instanceof SocketException || e instanceof NoHttpResponseException)
        {
            return true;
        }
        if (e instanceof HttpResponseException hre)
        {
            return hre.getStatusCode() >= 500 || hre.getStatusCode() == TOO_MANY_REQUESTS;
        }
        return false;
    }

    // 500ms after the first failure, then doubling.
    private static long retryDelayMs(int attempt)
    {
        return (long) RETRY_BASE_DELAY_MS << (attempt - 1);
    }

    /**
     * @return false if the thread was interrupted, in which case the caller should stop rather than
     * carry on without the delay it asked for.
     */
    protected boolean sleepMs(long ms)
    {
        try
        {
            Thread.sleep(ms);
            return true;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testIsRetryable()
        {
            // Read timeouts and 5xx responses are transient NCBI failures -> retry
            assertTrue(isRetryable(new SocketTimeoutException("Read timed out")));
            assertTrue(isRetryable(new HttpResponseException(500, "Internal Server Error")));
            assertTrue(isRetryable(new HttpResponseException(503, "Service Unavailable")));

            // A connection reset or closed by the server is transient -> retry
            assertTrue("A connection reset should be retried", isRetryable(new SocketException("Connection reset")));
            assertTrue("A connection closed without a response should be retried",
                    isRetryable(new NoHttpResponseException("The target server failed to respond")));

            // 429 is NCBI's response when the request rate is exceeded, which backoff is for
            assertTrue(isRetryable(new HttpResponseException(429, "Too Many Requests")));

            // Other 4xx and generic IO errors are permanent -> fail fast
            assertFalse(isRetryable(new HttpResponseException(400, "Bad Request")));
            assertFalse(isRetryable(new HttpResponseException(404, "Not Found")));
            assertFalse(isRetryable(new IOException("Stream closed")));
        }

        @Test
        public void testRetryDelayMs()
        {
            // Exponential backoff of 500ms then 1000ms. With MAX_HTTP_ATTEMPTS at 3 the loop sleeps
            // after the first two failures and rethrows after the third, so those are the only
            // delays a request can wait.
            assertEquals(500, retryDelayMs(1));
            assertEquals(1000, retryDelayMs(2));
            assertEquals("A request sleeps once per failed attempt except the last, so only the"
                    + " delays asserted above are reachable", 2, MAX_HTTP_ATTEMPTS - 1);
        }

        @Test
        public void testErrorDetail()
        {
            // 4xx: the response body is appended so the cause (e.g. an invalid API key) is logged
            String detail = errorDetail(400, "Bad Request", "{\"error\":\"API key invalid\"}", null);
            assertTrue(detail.contains("Bad Request"));
            assertTrue(detail.contains("API key invalid"));

            // 5xx: body omitted (uninformative)
            assertEquals("Internal Server Error", errorDetail(500, "Internal Server Error", "<html>oops</html>", null));

            // 4xx with blank or null body: just the reason phrase, no trailing separator
            assertEquals("Bad Request", errorDetail(400, "Bad Request", "", null));
            assertEquals("Bad Request", errorDetail(400, "Bad Request", null, null));

            // A body that quotes the request back must not carry the key into the log
            String echoed = errorDetail(400, "Bad Request", "invalid key SECRET123 for api_key=SECRET123", "SECRET123");
            assertFalse("errorDetail must not put the API key in the message", echoed.contains("SECRET123"));

            // A key that spans the truncation point must not leave its prefix in the message
            String padding = "x".repeat(MAX_ERROR_BODY_CHARS - 10);
            String spanning = errorDetail(400, "Bad Request", padding + " SECRET123456789 trailing", "SECRET123456789");
            assertFalse("errorDetail must not put part of the API key in the message", spanning.contains("SECRET"));
        }

        @Test
        public void testRedactApiKey()
        {
            // The key is stripped from an eutils URL, and the rest of the URL is left intact
            String url = "https://eutils.ncbi.nlm.nih.gov/esearch.fcgi?db=pmc&api_key=SECRET123&term=PXD001";
            String redacted = redactApiKey(url, "SECRET123");
            assertFalse("The redacted URL must not contain the key", redacted.contains("SECRET123"));
            assertTrue("The redacted URL must keep its other parameters", redacted.contains("term=PXD001"));
            assertTrue(redacted.contains("db=pmc"));

            // The key is stripped even when it appears without the api_key= prefix
            assertFalse(redactApiKey("rejected key SECRET123", "SECRET123").contains("SECRET123"));

            // A URL with no key is unchanged, and null text stays null
            String noKey = "https://eutils.ncbi.nlm.nih.gov/esearch.fcgi?db=pmc&term=PXD001";
            assertEquals(noKey, redactApiKey(noKey, null));
            assertNull(redactApiKey(null, "SECRET123"));

            // The key is recovered from the URL so callers do not have to read the settings
            assertEquals("SECRET123", apiKeyFrom(url));
            assertNull(apiKeyFrom(noKey));
        }

        @Test
        public void testGetStringRetriesTransientFailures() throws IOException
        {
            // executeGet returns a 5xx twice, then succeeds. getString should retry and return the body.
            int[] attempts = {0};
            NoWaitClient client = new NoWaitClient()
            {
                @Override
                protected String executeGet(String url) throws IOException
                {
                    if (++attempts[0] < 3)
                        throw new HttpResponseException(503, "Service Unavailable");
                    return "body";
                }
            };
            assertEquals("body", client.getString("http://test", LOG));
            assertEquals("Should retry until the 3rd attempt succeeds", 3, attempts[0]);
            assertEquals("The loop should wait 500ms then 1000ms", List.of(500L, 1000L), client.sleeps);
        }

        @Test
        public void testGetStringStopsWhenInterrupted()
        {
            // An interrupted thread cannot wait, so retrying would send the remaining attempts back
            // to back. getString should give up after the first failure instead.
            int[] attempts = {0};
            NcbiHttpClient client = new NcbiHttpClient()
            {
                @Override
                protected String executeGet(String url) throws IOException
                {
                    attempts[0]++;
                    Thread.currentThread().interrupt();
                    throw new HttpResponseException(503, "Service Unavailable");
                }
            };

            try
            {
                client.getString("http://test", LOG);
                fail("Expected the interrupted request to be rethrown");
            }
            catch (IOException expected)
            {
                assertEquals("An interrupted request should not be retried", 1, attempts[0]);
            }
            finally
            {
                // Clear the flag so it cannot reach whatever test runs next.
                Thread.interrupted();
            }
        }

        @Test
        public void testGetStringGivesUpAfterMaxAttempts()
        {
            // executeGet always returns a 5xx. getString should try MAX_HTTP_ATTEMPTS times, then rethrow.
            int[] attempts = {0};
            NoWaitClient client = new NoWaitClient()
            {
                @Override
                protected String executeGet(String url) throws IOException
                {
                    attempts[0]++;
                    throw new HttpResponseException(500, "Internal Server Error");
                }
            };
            try
            {
                client.getString("http://test", LOG);
                fail("Expected HttpResponseException after exhausting retries");
            }
            catch (HttpResponseException e)
            {
                assertEquals(500, e.getStatusCode());
            }
            catch (IOException e)
            {
                fail("Expected HttpResponseException, got " + e);
            }
            assertEquals("Should attempt exactly MAX_HTTP_ATTEMPTS times", MAX_HTTP_ATTEMPTS, attempts[0]);
            assertEquals("One wait fewer than attempts, and no wait after the last", List.of(500L, 1000L), client.sleeps);
        }

        @Test
        public void testGetStringDoesNotRetryClientErrors()
        {
            // A 4xx is permanent. getString should fail immediately without retrying.
            int[] attempts = {0};
            NoWaitClient client = new NoWaitClient()
            {
                @Override
                protected String executeGet(String url) throws IOException
                {
                    attempts[0]++;
                    throw new HttpResponseException(400, "Bad Request");
                }
            };
            try
            {
                client.getString("http://test", LOG);
                fail("Expected HttpResponseException for a 4xx");
            }
            catch (HttpResponseException e)
            {
                assertEquals(400, e.getStatusCode());
            }
            catch (IOException e)
            {
                fail("Expected HttpResponseException, got " + e);
            }
            assertEquals("4xx must not be retried", 1, attempts[0]);
            assertTrue("A 4xx must not wait", client.sleeps.isEmpty());
        }

        /**
         * Runs the retry loop without waiting, and records the delays it asked for. A test can then
         * check the delays the loop used, which retryDelayMs on its own cannot show.
         */
        static class NoWaitClient extends NcbiHttpClient
        {
            private final List<Long> sleeps = new ArrayList<>();

            @Override
            protected boolean sleepMs(long ms)
            {
                sleeps.add(ms);
                return true;
            }
        }
    }
}

package com.transloadit.sdk;

import com.transloadit.sdk.exceptions.LocalOperationException;
import com.transloadit.sdk.exceptions.RequestException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import org.mockserver.client.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.matchers.Times;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.RequestDefinition;

import javax.validation.constraints.AssertTrue;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
//CHECKSTYLE:OFF
import java.util.Map;  // Suppress warning as the Map import is needed for the JavaDoc Comments
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockserver.model.HttpError.error;
//CHECKSTYLE:ON

/**
 * Unit test for {@link Request} class. Api-Responses are simulated by mocking the server's response.
 */
public class RequestTest extends MockHttpService {
    /**
     * Links to {@link Request} instance to perform the tests on.
     */
    private Request request;

    /**
     * MockServer can be run using the MockServerRule.
     */
    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this, true, PORT);

    /**
     * MockServerClient makes HTTP requests to a MockServer instance.
     */
    private MockServerClient mockServerClient;

    /**
     * Assings a new {@link Request} instance to Request variable before each individual test and resets
     * the mockServerClient.
     */
    @Before
    public void setUp() throws Exception {
        request = new Request(transloadit);
        mockServerClient.reset();
    }


    /**
     * Checks the result of the {@link Request#get(String)}  method by verifying the format of the GET request
     * the MockServer receives.
     * @throws Exception if request building goes wrong.
     */
    @Test
    public void get() throws Exception {
        request.get("/foo");

        mockServerClient.verify(HttpRequest.request()
                .withPath("/foo")
                .withMethod("GET")
                .withHeader("Transloadit-Client", "java-sdk:0.3.0"));

    }

    /**
     * Checks the result of the {@link Request#post(String, Map)}  method by verifying the format of the
     * POST request the MockServer receives.
     * @throws Exception if request building goes wrong.
     */
    @Test
    public void post() throws Exception {
        request.post("/foo", new HashMap<String, Object>());

        mockServerClient.verify(HttpRequest.request()
                .withPath("/foo").withMethod("POST"));
    }


    /**
     * Checks the result of the {@link Request#delete(String, Map)} )}  method by verifying the format of the
     * DELETE request the MockServer receives.
     * @throws Exception if request building goes wrong.
     */
    @Test
    public void delete() throws Exception {
        request.delete("/foo", new HashMap<String, Object>());

        mockServerClient.verify(HttpRequest.request()
                .withPath("/foo").withMethod("DELETE"));
    }

    /**
     * Checks the result of the {@link Request#put(String, Map)}  method by verifying the format of the PUT request
     * the MockServer receives.
     * @throws Exception if request building goes wrong.on
     */
    @Test
    public void put() throws Exception {
        request.put("/foo", new HashMap<String, Object>());

        mockServerClient.verify(HttpRequest.request()
                .withPath("/foo").withMethod("PUT"));
    }

    /**
     * Tests if the method {@link Request#qualifiedForRetry(Exception)} determines correctly if a retry attempt after an
     * exception should be performed.
     * Special test environment needed as it could interfere with other tests.
     */
    @Test
    public void qualifiedForRetry() {
        Transloadit transloadit2 = new Transloadit("KEY", "SECRET",
                "http://localhost:" + 9040);
        transloadit2.setRetryAttemptsRequestException(5);
        Request newRequest = new Request(transloadit2);
        Exception e = new SocketTimeoutException("connect timed out");
        assertTrue(newRequest.qualifiedForRetry(e));

        Exception e2 = new ArrayStoreException("foo bar");
        assertFalse(newRequest.qualifiedForRetry(e2));

        transloadit2.setRetryAttemptsRequestException(0);
        newRequest = new Request(transloadit2);
        assertFalse(newRequest.qualifiedForRetry(e));
    }

    /**
     * Tests if {@link Request#retryAfterSpecificErrors(Object[])} retries correctly after an exception.
     * Also need special settings for each test.
     */
    @Test
    public void retryAfterSpecificErrors() throws LocalOperationException, RequestException {
        Transloadit transloadit2 = new Transloadit("KEY", "SECRET",
                "http://localhost:" + 9040);

        ArrayList<String> errors = transloadit2.getQualifiedErrorsForRetry();
        errors.add("java.io.IOException: unexpected end of stream on http://localhost:9040/");
        transloadit2.setQualifiedErrorsForRetry(errors);
        transloadit2.setRetryAttemptsRequestException(1);

        // GET REQUESTS
        Request testRequest = new Request(transloadit2);
        mockServerClient.when(HttpRequest.request()
                .withPath("/foo").withMethod("GET"), Times.exactly(2)).error(
                error().withDropConnection(true));
            testRequest.get("/foo");

        // POST REQUESTS
        testRequest = new Request(transloadit2);
        mockServerClient.when(HttpRequest.request()
                .withPath("/foo").withMethod("POST"), Times.exactly(2)).error(
                error().withDropConnection(true));
        testRequest.post("/foo", new HashMap<String, Object>());

        // PUT REQUEST
        testRequest = new Request(transloadit2);
        mockServerClient.when(HttpRequest.request()
                .withPath("/foo").withMethod("PUT"), Times.exactly(2)).error(
                error().withDropConnection(true));
        testRequest.put("/foo", new HashMap<String, Object>());

        // DELETE REQUEST
        testRequest = new Request(transloadit2);
        mockServerClient.when(HttpRequest.request()
                .withPath("/foo").withMethod("DELETE"), Times.exactly(2)).error(
                error().withDropConnection(true));
        testRequest.delete("/foo", new HashMap<String, Object>());

    }


}


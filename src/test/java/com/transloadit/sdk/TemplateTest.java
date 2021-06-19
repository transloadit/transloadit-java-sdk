package com.transloadit.sdk;

import com.transloadit.sdk.exceptions.LocalOperationException;
import com.transloadit.sdk.exceptions.RequestException;
import com.transloadit.sdk.response.Response;
import org.junit.Rule;
import org.junit.Test;

import org.mockserver.client.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockserver.model.RegexBody.regex;

/**
 * Unit test for {@link Template} class. Api-Responses are simulated by mocking the server's response.
 */
public class TemplateTest extends MockHttpService {
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
     * Tests if a new Template returns its correct name by calling {@link Template#getName()}.
     * The name gets defined by its instantiation.
     */
    @Test
    public void getName() {
        Template template = transloadit.newTemplate("foo");
        assertEquals(template.getName(), "foo");
    }

    /**
     * Tests functionality of the {@link Template#setName(String)} method. Sets a new name after instantiation of a new
     * {@link Template} object and verifies the result with the {@link Template#getName()} method.
     */
    @Test
    public void setName() {
        Template template = transloadit.newTemplate("foo");
        assertEquals(template.getName(), "foo");

        template.setName("bar");
        assertEquals(template.getName(), "bar");
    }

    /**
     * Verifies the structure of the POST request to the Transloadit API which gets generated by the
     * {@link Template#save()} method. Also tests if the API response gets parsed correctly.
     * @throws LocalOperationException if building the request goes wrong.
     * @throws RequestException  if communication with the server goes wrong
     * @throws IOException if Test resource "template.json" is missing.
     */
    @Test
    public void save() throws LocalOperationException, RequestException, IOException {
        mockServerClient.when(HttpRequest.request()
                .withPath("/templates").withMethod("POST")
                .withBody(regex("[\\w\\W]*\"name\":\"template_name\"[\\w\\W]*")))
                .respond(HttpResponse.response().withBody(getJson("template.json")));

        Template template = new Template(transloadit, "template_name");
        Response newTemplate = template.save();

        assertEquals(newTemplate.json().get("ok"), "TEMPLATE_FOUND");

        mockServerClient.reset();
    }
}

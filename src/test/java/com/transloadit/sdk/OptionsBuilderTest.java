package com.transloadit.sdk;

import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;


/**
 * Unit test for {@link OptionsBuilder} class. Options built by the OptionsBuilder class are verified with
 * specified patterns.
 */
public class OptionsBuilderTest {
    /**
     * Links to {@link OptionsBuilder} instance to perform the tests on.
     */
    private OptionsBuilder optionsBuilder;

    /**
     * Assings a new {@link OptionsBuilder} instance to the optionsBuilder variable before each individual test.
     */
    @BeforeEach
    public void setUp() {
        optionsBuilder = new OptionsBuilder();
        optionsBuilder.steps = new Steps();
        optionsBuilder.options = new HashMap<String, Object>();
    }


    /**
     * Checks functionality of the {@link OptionsBuilder#addStep(String, String, Map)} method by specifying a Step name
     * and a robot. Test will succeed if  {@link OptionsBuilder}.{@link Steps#getStep(String)} returns
     * the specified values.
     */
    @Test
    public void addStep() {
        optionsBuilder.addStep("encode", "/video/encode", new HashMap<String, Object>());
        optionsBuilder.addStep("noRobotName", new HashMap<String, Object>());

        Assertions.assertEquals(optionsBuilder.steps.getStep("encode").robot, "/video/encode");
        Assertions.assertNotNull(optionsBuilder.steps.getStep("noRobotName"));
    }

    /**
     * Checks the functionality of {@link OptionsBuilder#removeStep(String)} method.
     * Therefore it adds a new {@link Steps Step} to the {@link OptionsBuilder} with specified parameters.
     * After calling {@link OptionsBuilder#removeStep(String)} the deleted Step gets searched in the
     * {@link OptionsBuilder OptionsBuilder's} {@link OptionsBuilder#steps steps} attribute.
     * The test is passed if the {@link Steps Step} can't be found.
     */
    @Test
    public void removeStep() {
        optionsBuilder.addStep("encode", "/video/encode", new HashMap<String, Object>());
        Assertions.assertTrue(optionsBuilder.steps.all.containsKey("encode"));

        optionsBuilder.removeStep("encode");
        Assertions.assertFalse(optionsBuilder.steps.all.containsKey("encode"));
    }

    /**
     * This Test checks the functionality of the {@link OptionsBuilder#addOptions(Map)} method by adding a Map of
     * options to the {@link OptionsBuilder} and comparing the stored values with the map of origin.
     */
    @Test
    public void addOptions() {
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("foo", "bar");
        options.put("red", "color");

        optionsBuilder.addOptions(options);
        Assertions.assertEquals(options, optionsBuilder.options);
    }

    /**
     * This Test works just as {@link OptionsBuilderTest#addOptions()} except it just verifies a single option.
     */
    @Test
    public void addOption() {
        optionsBuilder.addOption("foo", "bar");
        Assertions.assertEquals(optionsBuilder.options.get("foo"), "bar");
    }

    /**
     * This test cheks the functionality of the {@link OptionsBuilder#addField(String, Object)} and
     * {@link OptionsBuilder#addFields(Map)} method by adding values and verifying their existence.
     */
    @Test
    public void addFieldAndAddFields() {
        HashMap<String, Object> testWords = new HashMap<>();
        testWords.put("baz", "qux");
        testWords.put("needle", "haystack");

        optionsBuilder.addFields(testWords);
        optionsBuilder.addField("foo", "foo");
        optionsBuilder.addField("foo", "bar");  // this should overwrite the value

        Assertions.assertTrue(optionsBuilder.options.containsKey("fields"));
        Assertions.assertInstanceOf(JSONObject.class, optionsBuilder.options.get("fields"));

        JSONObject fields = (JSONObject) optionsBuilder.options.get("fields");

        Assertions.assertEquals(fields.get("foo"), "bar");  // test overwrite
        Assertions.assertEquals(fields.get("baz"), "qux");
        Assertions.assertTrue(fields.has("needle"));  // test if older entries are preserved
        Assertions.assertFalse(fields.has("haystack"));  // test if key - value are not interchanged
    }

}

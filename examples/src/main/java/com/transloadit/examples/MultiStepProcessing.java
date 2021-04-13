package com.transloadit.examples;

import com.transloadit.sdk.Assembly;
import com.transloadit.sdk.AssemblyListener;
import com.transloadit.sdk.Transloadit;
import com.transloadit.sdk.exceptions.LocalOperationException;
import com.transloadit.sdk.exceptions.RequestException;
import com.transloadit.sdk.response.AssemblyResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.HashMap;
import java.util.Map;


/**
 * This Example demonstrates a multi-step Assembly:
 *  - Step1 converts two given audio files to mp3 with a bitrate of 128 kbit/s (128000 bits/s)
 *  - Step2 concatenates Step1's results to one file
 *  - Step3 derives a Waveform Image from Step2's output
 */
public class MultiStepProcessing {
    public static void main(String[] args) {

        // New Transloadit Instance
        Transloadit transloadit = new Transloadit("TRANSLOADIT_KEY", "TRANSLOADIT_SECRET");
        Assembly assembly = transloadit.newAssembly();

        //Add Files and define Field name
        assembly.addFile(new File(MultiStepProcessing.class.getResource("/dutch-anthem.mp3").getFile()),"a1");
        assembly.addFile(new File(MultiStepProcessing.class.getResource("/german-anthem-0.mp3").getFile()),"a2");

        // Step1 Reduce File's Bitrates
        Map<String, Object> step1 = new HashMap<>();
        step1.put("preset","mp3");
        step1.put("bitrate",128000);

        assembly.addStep("encode","/audio/encode",step1);

        // Step2 Concatenation
            /* Building "use" parameter as JSONObject
                -   name = Name of previous step
                -   fields = Name of outfile from previous step
                -   as = audio_<number> defines oder of Concatenation
                => Needs to be stored under key "steps" as it defines every substep
             */
        JSONObject outerJsonObject = new JSONObject();
        outerJsonObject.append("steps",new JSONObject().put("name","encode" ).put("fields","a1").put("as","audio_1"));
        outerJsonObject.append("steps",new JSONObject().put("name","encode" ).put("fields","a2").put("as","audio_2"));

        Map<String, Object> step2 = new HashMap<>();
        step2.put("preset", "mp3");
        step2.put("use",outerJsonObject);

        assembly.addStep("concat","/audio/concat", step2);

        // Step 3 Waveform

        Map<String,Object> step3 = new HashMap<>();
        step3.put("use","concat");
        step3.put("width",1920);
        step3.put("height",720);
        step3.put("outer_color","ff00c7ff");

        assembly.addStep("waveform","/audio/waveform",step3);


        // Register AssemblyListener => Informs User on Assembly completion
        assembly.setAssemblyListener(new AssemblyListener() {
            @Override
            public void onAssemblyFinished(AssemblyResponse response) {
                JSONArray waveformStatus = response.getStepResult("waveform");
                System.out.println("Result of Operation:");
                printStatus(waveformStatus);
                System.exit(0);
            }

            public void printStatus(JSONArray status) {
                for (int i = 0; i < status.length(); i++) {
                    JSONObject obj = status.getJSONObject(i);
                    System.out.printf(
                            "Resulting file: %s %s, Download at:\n %s%n",
                            obj.getString("basename"), obj.getString("ext"), obj.getString("ssl_url"));
                }
            }

            @Override
            public void onError(Exception error) {
                System.out.println("Error");
                error.printStackTrace();
            }
        });

        try{
            System.out.println("Processing... ");
            assembly.save();
        } catch (LocalOperationException | RequestException e) {
            e.printStackTrace();
        }
    }

}

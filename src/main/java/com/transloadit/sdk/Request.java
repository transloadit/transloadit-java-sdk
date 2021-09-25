package com.transloadit.sdk;

import com.transloadit.sdk.exceptions.LocalOperationException;
import com.transloadit.sdk.exceptions.RequestException;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.Nullable;
import org.joda.time.Instant;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

/**
 * Transloadit tailored Http Request class.
 */
public class Request {
    private Transloadit transloadit;
    private OkHttpClient httpClient = new OkHttpClient();
    private String version;
    private int retryAttemptsRateLimitLeft;
    private int retryAttemptsRequestExceptionLeft;
    private ArrayList<String> qualifiedErrorsForRetry;

    enum RequestType { GET, POST, PUT, DELETE }
    private RequestType requestType;

    /**
     * Constructs a new instance of the {@link Request} object in to prepare a new HTTP-Request to the Transloadit API.
     * @param transloadit The {@link Transloadit} Client
     */
    Request(Transloadit transloadit) {
        this.transloadit = transloadit;
        retryAttemptsRateLimitLeft = transloadit.getRetryAttemptsRateLimit();
        retryAttemptsRequestExceptionLeft = transloadit.getRetryAttemptsRequestException();
        qualifiedErrorsForRetry = transloadit.getQualifiedErrorsForRetry();
        Properties prop = new Properties();
        InputStream in = getClass().getClassLoader().getResourceAsStream("version.properties");
        try {
            prop.load(in);
            version = "java-sdk:" + prop.getProperty("versionNumber").replace("'", "");
            in.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (NullPointerException npe) {
            version = "java-sdk:unknown";
        }
    }

    /**
     * Makes http GET request.
     * @param url url to make request to
     * @param params data to add to params field
     * @return {@link okhttp3.Response}
     * @throws RequestException
     * @throws LocalOperationException
     */
    okhttp3.Response get(String url, Map<String, Object> params)
            throws RequestException, LocalOperationException {
        requestType = RequestType.GET;
        String fullUrl = getFullUrl(url);
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(addUrlParams(fullUrl, toPayload(params)))
                .addHeader("Transloadit-Client", version)
                .build();

        try {
            return httpClient.newCall(request).execute();
        } catch (IOException e) {
            if (qualifiedForRetry(e)) {
                Object[] args = {url, params};
                return retryAfterSpecificErrors(args);
            } else {
                throw new RequestException(e);
            }
        }
    }

    /**
     * Makes HTTP GET request by calling {@link #get(String , Map<String, Object> ) } with empty params field.
     * @param url url to make request to
     * @return {@link okhttp3.Response}
     * @throws RequestException
     * @throws LocalOperationException
     */
    okhttp3.Response get(String url) throws RequestException, LocalOperationException {
        return get(url, new HashMap<String, Object>());
    }

    /**
     * Makes http POST request.
     * @param url url to make request to
     * @param params data to add to params field
     * @param extraData data to send along with request body, outside of params field.
     * @param files files to be uploaded along with the request.
     * @param fileStreams filestreams to be uploaded along with the request.
     * @return {@link okhttp3.Response}
     * @throws RequestException
     * @throws LocalOperationException
     */
    okhttp3.Response post(String url, Map<String, Object> params,
                          @Nullable Map<String, String> extraData,
                          @Nullable Map<String, File> files, @Nullable Map<String, InputStream> fileStreams)
            throws RequestException, LocalOperationException {

        requestType = RequestType.POST;
        Map<String, String> payload = toPayload(params);
        if (extraData != null) {
            payload.putAll(extraData);
        }

        okhttp3.Request request = new okhttp3.Request.Builder().url(getFullUrl(url))
                .post(getBody(payload, files, fileStreams))
                .addHeader("Transloadit-Client", version)
                .build();

        try {
            Response response = httpClient.newCall(request).execute();
            // Intercept Rate Limit Errors
                if (response.code() == 413 && retryAttemptsRateLimitLeft > 0) {
                    return retryRateLimit(response, url, params, extraData, files, fileStreams);
                }

            return response;
        } catch (IOException e) {
            if (qualifiedForRetry(e)) {
                Object[] args = {url, params, extraData, files, fileStreams};
                return retryAfterSpecificErrors(args);
            } else {
                throw new RequestException(e);
            }
        }
    }

    /**
     * Makes http POST request by calling {@link #post(String, Map, Map, Map, Map)} with only url and params field
     * being set.
     * @param url url to make request to
     * @param params data to add to params field
     * @return {okhttp3.Response}
     * @throws RequestException
     * @throws LocalOperationException
     */
    okhttp3.Response post(String url, Map<String, Object> params)
            throws RequestException, LocalOperationException {
        return post(url, params, null, null, null);
    }

    /**
     * Makes http DELETE request.
     * @param url url to makes request to
     * @param params data to add to params field
     * @return {@link okhttp3.Response}
     * @throws RequestException
     * @throws LocalOperationException
     */
    okhttp3.Response delete(String url, Map<String, Object> params)
            throws RequestException, LocalOperationException {
        requestType = RequestType.DELETE;
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(getFullUrl(url))
                .delete(getBody(toPayload(params), null))
                .addHeader("Transloadit-Client", version)
                .build();

        try {
            return httpClient.newCall(request).execute();
        } catch (IOException e) {
            if (qualifiedForRetry(e)) {
                Object[] args = {url, params};
                return retryAfterSpecificErrors(args);
            } else {
                throw new RequestException(e);
            }
        }
    }

    /**
     * Makes http PUT request.
     * @param url
     * @param data
     * @return
     * @throws RequestException
     * @throws LocalOperationException
     * @return {@link okhttp3.Response}
     */
    okhttp3.Response put(String url, Map<String, Object> data)
            throws RequestException, LocalOperationException {

        requestType = RequestType.PUT;
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(getFullUrl(url))
                .put(getBody(toPayload(data), null))
                .addHeader("Transloadit-Client", version)
                .build();

        try {
            return httpClient.newCall(request).execute();
        } catch (IOException e) {
            if (qualifiedForRetry(e)) {
                Object[] args = {url, data};
                return retryAfterSpecificErrors(args);
            } else {
                throw new RequestException(e);
            }
        }
    }

    /**
     * Converts url path to the Transloadit full url.
     * Returns the url passed if it is already full.
     *
     * @param url
     * @return String
     */
    private String getFullUrl(String url) {
        return url.startsWith("https://") || url.startsWith("http://") ? url : transloadit.getHostUrl() + url;
    }

    private String addUrlParams(String url, Map<String, ? extends Object> params) throws LocalOperationException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ? extends Object> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }

            try {
                sb.append(URLEncoder.encode(entry.getKey(), "UTF-8")).append('=')
                        .append(URLEncoder.encode((String) entry.getValue(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                throw new LocalOperationException(e);
            }
        }

        return url + "?" + sb.toString();
    }

    /**
     * Builds okhttp3 compatible request body with the data passed.
     *
     * @param data data to add to request body
     * @param files files to upload
     * @param fileStreams fileStreams to upload
     * @return {@link RequestBody}
     */
    private RequestBody getBody(Map<String, String> data, @Nullable Map<String, File> files, @Nullable Map<String,
            InputStream> fileStreams) throws LocalOperationException {
        MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);

        if (files != null) {
            for (Map.Entry<String, File> entry : files.entrySet()) {
                File file = entry.getValue();
                String mimeType = URLConnection.guessContentTypeFromName(file.getName());

                if (mimeType == null) {
                    mimeType = "application/octet-stream";
                }

                builder.addFormDataPart(entry.getKey(), file.getName(),
                        RequestBody.create(MediaType.parse(mimeType), file));
            }
        }

        if (fileStreams != null) {
            for (Map.Entry<String, InputStream> entry : fileStreams.entrySet()) {
                byte[] bytes;
                InputStream stream = entry.getValue();
                try {
                    bytes = new byte[stream.available()];
                    stream.read(bytes);

                } catch (IOException e) {
                    throw new LocalOperationException(e);
                }
                builder.addFormDataPart(entry.getKey(), null,
                        RequestBody.create(MediaType.parse("application/octet-stream"), bytes));
            }
        }

        for (Map.Entry<String, String> entry : data.entrySet()) {
            builder.addFormDataPart(entry.getKey(), entry.getValue());
        }

        return builder.build();
    }

    private RequestBody getBody(Map<String, String> data, @Nullable Map<String, File> files)
            throws LocalOperationException {
        return getBody(data, files, null);
    }

    /**
     * Returns data tree structured as Transloadit expects it.
     *
     * @param data
     * @return {@link Map}
     * @throws LocalOperationException
     */
    private Map<String, String> toPayload(Map<String, Object> data) throws LocalOperationException {
        Map<String, Object> dataClone = new HashMap<String, Object>(data);
        dataClone.put("auth", getAuthData());

        Map<String, String> payload = new HashMap<String, String>();
        payload.put("params", jsonifyData(dataClone));

        if (transloadit.shouldSignRequest) {
            payload.put("signature", getSignature(jsonifyData(dataClone)));
        }
        return payload;
    }

    /**
     * converts Map of data to json string.
     *
     * @param data map data to converted to json
     * @return {@link String}
     */
    private String jsonifyData(Map<String, ? extends Object> data) {
        JSONObject jsonData = new JSONObject(data);

        return jsonData.toString();
    }

    /**
     *
     * @return Map containing authentication key and the time it expires
     */
    private Map<String, String> getAuthData() {
        Map<String, String> authData = new HashMap<String, String>();
        authData.put("key", transloadit.key);

        Instant expiryTime = Instant.now().plus(transloadit.duration * 1000);
        DateTimeFormatter formatter = DateTimeFormat
                .forPattern("Y/MM/dd HH:mm:ss+00:00")
                .withZoneUTC();

        authData.put("expires", formatter.print(expiryTime));

        return authData;
    }

    /**
     *
     * @param message String data that needs to be encrypted.
     * @return signature generate based on the message passed and the transloadit secret.
     */
    private String getSignature(String message) throws LocalOperationException {
        byte[] kSecret = transloadit.secret.getBytes(Charset.forName("UTF-8"));
        byte[] rawHmac = hmacSHA1(kSecret, message);
        byte[] hexBytes = new Hex().encode(rawHmac);

        return new String(hexBytes, Charset.forName("UTF-8"));
    }

    private byte[] hmacSHA1(byte[] key, String data) throws LocalOperationException {
        final String algorithm = "HmacSHA1";
        Mac mac;

        try {
            mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
        } catch (NoSuchAlgorithmException e) {
            throw new LocalOperationException(e);
        } catch (InvalidKeyException e) {
            throw new LocalOperationException(e);
        }
        return mac.doFinal(data.getBytes(Charset.forName("UTF-8")));
    }

    /**
     * Helper method, which performs a retryRateLimit action if a POST request has hit the servers rate limit.
     * All parameters of the failed POST request should be provided to this method.
     * @param response response to retryRateLimit
     * @param url url to make request to
     * @param params data to add to params field
     * @param extraData data to send along with request body, outside of params field.
     * @param files files to be uploaded along with the request.
     * @param fileStreams filestreams to be uploaded along with the request.
     * @return {@link okhttp3.Response}
     */
    private okhttp3.Response retryRateLimit(Response response, String url, Map<String, Object> params,
                                            @Nullable Map<String, String> extraData,
                                            @Nullable Map<String, File> files,
                                            @Nullable Map<String, InputStream> fileStreams)
            throws IOException, LocalOperationException, RequestException {
        retryAttemptsRateLimitLeft--;
        long timeToWait = 60000; // default server cooldown

        JSONObject json = new JSONObject(response.body().string());

        // Use server provided retryRateLimit time if available
        if (json.has("info") && json.getJSONObject("info").has("retryIn")) {
            String retryIn = json.getJSONObject("info").get("retryIn").toString();
            if (!retryIn.isEmpty()) {
                int randInt = new Random().nextInt(1000);
                timeToWait = Long.parseLong(retryIn) * 1000 + randInt;
            }
        }
        try {
            Thread.sleep(timeToWait);
        } catch (InterruptedException e) {
            throw new LocalOperationException(e);
        }
        return this.post(url, params, extraData, files, fileStreams);
    }

    protected okhttp3.Response retryAfterSpecificErrors(Object[] args) throws LocalOperationException, RequestException {
        okhttp3.Response response = null;
        retryAttemptsRequestExceptionLeft--;
        System.out.println("Retry " + requestType.toString() + " , Attempts left: "
                + retryAttemptsRequestExceptionLeft);

        try {
            int timeToWait = new Random().nextInt(1000) + 1000;
            Thread.sleep(timeToWait);
        } catch (InterruptedException e) {
            throw new LocalOperationException(e);
        }

        switch (requestType) {
            case GET: response = get((String) args[0], (Map) args[1]);
                        break;

            case POST: String url = (String) args[0];
                        Map<String, Object> params = (Map<String, Object>) args[1];
                        Map<String, String> extraData;

                        if (args[2] != null) {
                            extraData = (Map<String, String>) args[2];
                        } else {
                            extraData = null;
                        }

                        Map<String, File> files;
                        if (args[3] != null) {
                            files = (Map<String, File>) args[3];
                        } else {
                            files = null;
                        }

                        Map<String, InputStream> fileStreams;
                        if (args[4] != null) {
                            fileStreams = (Map<String, InputStream>) args[4];
                        } else {
                            fileStreams = null;
                        }
                        response = post(url, params, extraData, files, fileStreams);
                        break;

            case PUT: response = put((String) args[0], (Map<String, Object>) args[1]);
                        break;
            case DELETE: response = delete((String) args[0], (Map<String, Object>) args[1]);
            default: break;
        }
        return response;
    }

    /**
     * Determines whether the thrown Exception Qualifies for a retry Attempt.
     * @param e Thrown Exception
     * @return true / false
     */
    protected boolean qualifiedForRetry(Exception e) {
        String fullInformation = e.toString();
        for (String s : qualifiedErrorsForRetry) {
            if (fullInformation.contains(s)) {
                if (retryAttemptsRequestExceptionLeft > 0) {
                    return true;
                }
            }
        }
        return false;
    }
}

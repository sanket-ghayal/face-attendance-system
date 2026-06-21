package com.attendance.api;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public class ApiClient {
    public static final String BASE       = "http://127.0.0.1:5000/api";
    public static final String STREAM_URL = BASE + "/stream/feed";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private static final Duration TIMEOUT = Duration.ofSeconds(180);

    private static JSONObject post(String path, JSONObject body) throws Exception {
        String b = body != null ? body.toString() : "{}";
        var req = HttpRequest.newBuilder().uri(URI.create(BASE + path))
                .timeout(TIMEOUT).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(b)).build();
        return new JSONObject(HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body());
    }
    private static JSONArray getArr(String path) throws Exception {
        var req = HttpRequest.newBuilder().uri(URI.create(BASE + path))
                .timeout(TIMEOUT).GET().build();
        return new JSONArray(HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body());
    }
    private static JSONObject getObj(String path) throws Exception {
        var req = HttpRequest.newBuilder().uri(URI.create(BASE + path))
                .timeout(TIMEOUT).GET().build();
        return new JSONObject(HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body());
    }
    private static JSONObject delete(String path) throws Exception {
        var req = HttpRequest.newBuilder().uri(URI.create(BASE + path))
                .timeout(TIMEOUT).DELETE().build();
        return new JSONObject(HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body());
    }

    public static boolean isBackendRunning() {
        try { return "ok".equals(getObj("/health").optString("status")); }
        catch (Exception e) { return false; }
    }
    public static JSONObject registerStudent(String id, String name, String course) throws Exception {
        JSONObject b = new JSONObject();
        b.put("student_id",id); b.put("name",name); b.put("course",course);
        return post("/students", b);
    }
    public static JSONObject captureStudent(String id) throws Exception {
        JSONObject b = new JSONObject(); b.put("student_id",id);
        return post("/capture", b);
    }
    public static JSONObject trainModel()    throws Exception { return post("/train", null); }
    public static JSONObject startStream()   throws Exception { return post("/stream/start", null); }
    public static JSONObject stopStream()    throws Exception { return post("/stream/stop", null); }
    public static JSONObject getStreamStatus() throws Exception { return getObj("/stream/status"); }
    public static JSONArray getAttendance(String date) throws Exception {
        return getArr("/attendance" + (date!=null&&!date.isEmpty() ? "?date="+date : ""));
    }
    public static JSONArray getPresentOnly(String date) throws Exception {
        return getArr("/attendance/present" + (date!=null&&!date.isEmpty() ? "?date="+date : ""));
    }
    public static JSONArray getAttendanceHistory(int days) throws Exception {
        return getArr("/attendance/history?days=" + days);
    }
    public static JSONObject deleteAttendance(String studentId, String date) throws Exception {
        return delete("/attendance/" + studentId + "/" + date);
    }
}

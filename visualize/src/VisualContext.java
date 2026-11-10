package local.visualize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class VisualContext {
    private static final int MAX_SESSIONS = 12;
    private static final int MAX_RESULTS = 16;
    private static final int MAX_PAYLOAD = 6000;
    private static final Map<String, LinkedHashMap<ResultKey, Result>> SESSIONS = new LinkedHashMap<>(16, .75f, true);
    private static final Map<Object, String> REQUESTS = new WeakHashMap<>();
    private static final Map<String, String> RETRIES = new LinkedHashMap<>(16, .75f, true);

    private VisualContext() {}

    public static final class Scope {
        final String session;
        final int message, fragment, offset;

        private Scope(String session, int message, int fragment, int offset) {
            this.session = session;
            this.message = message;
            this.fragment = fragment;
            this.offset = offset;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Scope)) return false;
            Scope scope = (Scope) other;
            return message == scope.message && fragment == scope.fragment && offset == scope.offset
                    && session.equals(scope.session);
        }

        @Override public int hashCode() { return Objects.hash(session, message, fragment, offset); }
    }

    public static Scope scope(String session, Integer message, int fragment, int offset) {
        return session == null || session.isEmpty() || message == null || message <= 0
                ? null : new Scope(session, message, fragment, offset);
    }

    private static final class ResultKey {
        final Scope scope;
        final String component;

        ResultKey(Scope scope, String component) { this.scope = scope; this.component = component; }

        @Override public boolean equals(Object other) {
            if (!(other instanceof ResultKey)) return false;
            ResultKey key = (ResultKey) other;
            return scope.equals(key.scope) && component.equals(key.component);
        }

        @Override public int hashCode() { return Objects.hash(scope, component); }
    }

    private static final class Result {
        final JSONObject data;
        final String encoded;
        Integer sentFrom;

        Result(JSONObject data) { this.data = data; encoded = escape(data.toString()); }
    }

    static synchronized void record(Scope scope, String component, String title, String type, JSONObject payload) {
        if (scope == null) return;
        JSONObject data = json("card", shortText(title, 90), "message_id", scope.message, "type", type, "data", payload);
        Result next = new Result(data);
        if (next.encoded.length() > MAX_PAYLOAD - 3) {
            try { payload.put("truncated", true); }
            catch (JSONException invalid) { return; }
            while (next.encoded.length() > MAX_PAYLOAD - 3) {
                JSONArray longest = null;
                for (Iterator<String> keys = payload.keys(); keys.hasNext();) {
                    JSONArray entries = payload.optJSONArray(keys.next());
                    if (entries != null && (longest == null || entries.length() > longest.length())) longest = entries;
                }
                if (longest == null || longest.length() == 0) return;
                longest.remove(longest.length() - 1);
                next = new Result(data);
            }
        }
        LinkedHashMap<ResultKey, Result> results = SESSIONS.computeIfAbsent(scope.session, key -> new LinkedHashMap<>());
        ResultKey key = new ResultKey(scope, component);
        Result previous = results.get(key);
        if (previous != null && previous.encoded.equals(next.encoded)) return;
        results.remove(key);
        results.put(key, next);
        trim(results, MAX_RESULTS);
        trim(SESSIONS, MAX_SESSIONS);
    }

    static synchronized boolean hasUnsent(Scope scope) {
        if (scope == null) return false;
        Map<ResultKey, Result> results = SESSIONS.get(scope.session);
        if (results == null) return false;
        for (Map.Entry<ResultKey, Result> entry : results.entrySet()) {
            if (entry.getKey().scope.equals(scope) && entry.getValue().sentFrom == null) return true;
        }
        return false;
    }

    static synchronized void clear(Scope scope) {
        if (scope == null) return;
        Map<ResultKey, Result> results = SESSIONS.get(scope.session);
        if (results != null) results.entrySet().removeIf(entry ->
                entry.getKey().scope.equals(scope) && entry.getValue().sentFrom == null);
    }

    static synchronized String forRequest(String session, Integer parent, boolean retry, Object request) {
        if (session == null || parent == null || parent <= 0) return "";
        if (REQUESTS.containsKey(request)) return REQUESTS.get(request);
        String retryKey = session + "\u0000" + parent;
        if (retry) {
            String snapshot = RETRIES.getOrDefault(retryKey, "");
            REQUESTS.put(request, snapshot);
            return snapshot;
        }
        Map<ResultKey, Result> results = SESSIONS.get(session);
        JSONArray payload = new JSONArray();
        if (results != null) {
            ArrayList<Map.Entry<ResultKey, Result>> recent = new ArrayList<>(results.entrySet());
            Collections.reverse(recent);
            int remaining = MAX_PAYLOAD - 2;
            for (Map.Entry<ResultKey, Result> entry : recent) {
                Result result = entry.getValue();
                if (entry.getKey().scope.message > parent || result.encoded.length() + 1 > remaining) continue;
                if (result.sentFrom != null && !parent.equals(result.sentFrom)) continue;
                payload.put(result.data);
                result.sentFrom = parent;
                remaining -= result.encoded.length() + 1;
            }
        }
        String snapshot = payload.length() == 0 ? "" : escape(payload.toString());
        REQUESTS.put(request, snapshot);
        RETRIES.put(retryKey, snapshot);
        trim(RETRIES, MAX_RESULTS * 2);
        return snapshot;
    }

    private static void trim(Map<?, ?> values, int maximum) {
        Iterator<?> iterator = values.keySet().iterator();
        while (values.size() > maximum && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static String escape(String json) { return json.replace("<", "\\u003c").replace(">", "\\u003e"); }

    static String shortText(String value, int limit) {
        if (value.length() <= limit) return value;
        int end = limit - 1;
        if (Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end) + "…";
    }

    static JSONObject json(Object... pairs) {
        JSONObject json = new JSONObject();
        try {
            for (int i = 0; i < pairs.length; i += 2) {
                Object value = pairs[i + 1];
                json.put((String) pairs[i], value == null ? JSONObject.NULL : value);
            }
        } catch (JSONException invalid) {
            throw new IllegalArgumentException("Invalid interaction result", invalid);
        }
        return json;
    }
}

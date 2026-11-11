package io.github.walktheearth.txtlightstick;

import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Colour keyframes over time, with linear RGB interpolation (mirrors the Python library). */
class Timeline {

    static class Keyframe implements Comparable<Keyframe> {
        double t;
        int color; // opaque ARGB

        Keyframe(double t, int color) {
            this.t = t;
            this.color = color;
        }

        @Override
        public int compareTo(Keyframe other) {
            return Double.compare(t, other.t);
        }
    }

    double duration = 10.0;
    final List<Keyframe> keyframes = new ArrayList<>();

    void setDuration(double seconds) {
        duration = Math.max(1.0, seconds);
        for (Keyframe frame : keyframes) {
            if (frame.t > duration) frame.t = duration;
        }
        sort();
    }

    Keyframe add(double t, int color) {
        Keyframe frame = new Keyframe(clamp(t), color);
        keyframes.add(frame);
        sort();
        return frame;
    }

    void move(Keyframe frame, double t) {
        frame.t = clamp(t);
        sort();
    }

    void remove(Keyframe frame) {
        keyframes.remove(frame);
    }

    void sort() {
        Collections.sort(keyframes);
    }

    int sample(double t) {
        if (keyframes.isEmpty()) return Color.BLACK;
        Keyframe first = keyframes.get(0);
        if (t <= first.t) return first.color;
        Keyframe last = keyframes.get(keyframes.size() - 1);
        if (t >= last.t) return last.color;
        for (int i = 0; i < keyframes.size() - 1; i++) {
            Keyframe left = keyframes.get(i);
            Keyframe right = keyframes.get(i + 1);
            if (t >= left.t && t <= right.t) {
                double span = right.t - left.t;
                double f = span <= 0 ? 1 : (t - left.t) / span;
                return blend(left.color, right.color, f);
            }
        }
        return last.color;
    }

    static int blend(int from, int to, double f) {
        int r = (int) Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * f);
        int g = (int) Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * f);
        int b = (int) Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f);
        return Protocol.rgb(r, g, b);
    }

    private double clamp(double t) {
        return Math.max(0, Math.min(duration, t));
    }

    String toJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("duration", duration);
            JSONArray array = new JSONArray();
            for (Keyframe frame : keyframes) {
                JSONObject item = new JSONObject();
                item.put("t", Math.round(frame.t * 1000.0) / 1000.0);
                item.put("color", String.format(Locale.US, "#%06x", frame.color & 0xFFFFFF));
                array.put(item);
            }
            root.put("keyframes", array);
            return root.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    static Timeline fromJson(String text) {
        Timeline timeline = new Timeline();
        if (text == null || text.isEmpty()) return timeline;
        try {
            JSONObject root = new JSONObject(text);
            timeline.duration = Math.max(1.0, root.optDouble("duration", 10.0));
            JSONArray array = root.optJSONArray("keyframes");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.getJSONObject(i);
                    timeline.keyframes.add(
                            new Keyframe(item.getDouble("t"), Color.parseColor(item.getString("color"))));
                }
            }
            timeline.sort();
        } catch (JSONException | IllegalArgumentException ignored) {
            // Corrupt prefs: fall back to an empty timeline.
        }
        return timeline;
    }
}

package io.github.walktheearth.txtlightstick;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

public class MainActivity extends Activity implements BleLightStick.Listener, TimelineView.Listener {

    private static final int REQUEST_BLE = 1;
    private static final long PLAY_STEP_MS = 100;
    private static final String[] EFFECT_NAMES = {"Solid", "Blink", "Breath", "Strobe"};
    private static final int[] EFFECT_VALUES = {
            Protocol.SOLID, Protocol.BLINK, Protocol.BREATH, Protocol.STROBE
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int[] channels = {255, 255, 255};
    private final SeekBar[] bars = new SeekBar[3];
    private final TextView[] valueLabels = new TextView[3];

    private SharedPreferences prefs;
    private BleLightStick ble;
    private Timeline timeline;
    private TimelineView timelineView;
    private TextView status;
    private Button connectButton;
    private Button playButton;
    private Button stopButton;
    private View swatch;
    private TextView durationLabel;
    private int effect = Protocol.SOLID;
    private boolean updatingControls;
    private boolean scanning;
    private boolean playing;
    private long playStarted;
    private int lastSentColor = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("txtlightstick", MODE_PRIVATE);
        timeline = Timeline.fromJson(prefs.getString("timeline", null));
        int savedColor = prefs.getInt("color", Color.WHITE);
        channels[0] = Color.red(savedColor);
        channels[1] = Color.green(savedColor);
        channels[2] = Color.blue(savedColor);
        effect = prefs.getInt("effect", Protocol.SOLID);
        boolean validEffect = false;
        for (int value : EFFECT_VALUES) {
            if (value == effect) validEffect = true;
        }
        if (!validEffect) effect = Protocol.SOLID;
        ble = new BleLightStick(this);
        buildUi();
        setChannels(currentColor());
        connectButton.setText("Connect lightstick");
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFFF7F8FA);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, dp(24), pad, dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("TXT LIGHTSTICK", 22, 0xFF101828);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setLetterSpacing(0.08f);
        add(root, title, 0);

        TextView subtitle = text("Ver.2 · Bluetooth colour studio", 13, 0xFF667085);
        add(root, subtitle, 4);

        status = text("Not connected", 13, 0xFF667085);
        add(root, status, 16);

        connectButton = button("Connect lightstick", 0xFF2962FF, Color.WHITE);
        connectButton.setOnClickListener(v -> onConnectClicked());
        add(root, connectButton, 10, dp(46));

        addSection(root, "COLOUR");

        swatch = new View(this);
        swatch.setBackground(rounded(currentColor(), 12));
        add(root, swatch, 4, dp(56));

        for (int i = 0; i < 3; i++) {
            addChannelRow(root, i);
        }

        LinearLayout effectRow = new LinearLayout(this);
        effectRow.setOrientation(LinearLayout.HORIZONTAL);
        effectRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView effectLabel = text("Effect", 14, 0xFF344054);
        effectRow.addView(effectLabel, new LinearLayout.LayoutParams(dp(56),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, EFFECT_NAMES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        int selected = 0;
        for (int i = 0; i < EFFECT_VALUES.length; i++) {
            if (EFFECT_VALUES[i] == effect) selected = i;
        }
        spinner.setSelection(selected);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                effect = EFFECT_VALUES[position];
                prefs.edit().putInt("effect", effect).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        effectRow.addView(spinner, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        add(root, effectRow, 12);

        Button send = button("Send colour now", 0xFF101828, Color.WHITE);
        send.setOnClickListener(v -> sendCurrent());
        add(root, send, 12, dp(46));

        addSection(root, "TIMELINE");

        TextView hint = text("Tap the strip to add a colour stop, drag a stop to move it.",
                12, 0xFF667085);
        add(root, hint, 4);

        timelineView = new TimelineView(this);
        timelineView.setTimeline(timeline);
        timelineView.setListener(this);
        GradientDrawable timelineBackground = rounded(Color.WHITE, 12);
        timelineBackground.setStroke(dp(1), 0xFFE4E7EC);
        timelineView.setBackground(timelineBackground);
        add(root, timelineView, 12, dp(170));

        LinearLayout timelineButtons = new LinearLayout(this);
        timelineButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button delete = button("Delete stop", 0xFFF2F4F7, 0xFF344054);
        delete.setOnClickListener(v -> {
            if (!timelineView.deleteSelected()) {
                status.setText("Select a stop first");
            }
        });
        playButton = button("▶  Play", 0xFF2962FF, Color.WHITE);
        playButton.setOnClickListener(v -> startPlayback());
        stopButton = button("■  Stop", 0xFFF2F4F7, 0xFF344054);
        stopButton.setOnClickListener(v -> stopPlayback(true));
        timelineButtons.addView(delete, weighted());
        timelineButtons.addView(playButton, weightedWithMargin(8));
        timelineButtons.addView(stopButton, weightedWithMargin(8));
        add(root, timelineButtons, 12, dp(46));

        durationLabel = text(String.format(java.util.Locale.US, "Duration %d s",
                Math.round(timeline.duration)), 13, 0xFF344054);
        add(root, durationLabel, 16);
        SeekBar durationBar = new SeekBar(this);
        durationBar.setMax(55);
        durationBar.setProgress(Math.max(0, (int) Math.round(timeline.duration) - 5));
        durationBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                timeline.setDuration(progress + 5);
                durationLabel.setText(String.format(java.util.Locale.US, "Duration %d s", progress + 5));
                timelineView.invalidate();
                saveAll();
            }
        });
        add(root, durationBar, 2);
        setContentView(scroll);
    }

    private void addChannelRow(LinearLayout root, int index) {
        String[] names = {"R", "G", "B"};
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(names[index], 13, 0xFF344054);
        row.addView(name, new LinearLayout.LayoutParams(dp(24), ViewGroup.LayoutParams.WRAP_CONTENT));
        SeekBar bar = new SeekBar(this);
        bar.setMax(255);
        bar.setProgress(channels[index]);
        bars[index] = bar;
        bar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (updatingControls || !fromUser) return;
                channels[index] = progress;
                onColorEdited();
            }
        });
        row.addView(bar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView value = text(String.valueOf(channels[index]), 13, 0xFF667085);
        value.setGravity(Gravity.END);
        valueLabels[index] = value;
        row.addView(value, new LinearLayout.LayoutParams(dp(38), ViewGroup.LayoutParams.WRAP_CONTENT));
        add(root, row, 6);
    }

    private void onColorEdited() {
        for (int i = 0; i < 3; i++) {
            valueLabels[i].setText(String.valueOf(channels[i]));
        }
        swatch.setBackground(rounded(currentColor(), 12));
        if (timelineView.hasSelection()) {
            timelineView.setSelectedColor(currentColor());
        }
        saveAll();
    }

    private void setChannels(int color) {
        updatingControls = true;
        channels[0] = Color.red(color);
        channels[1] = Color.green(color);
        channels[2] = Color.blue(color);
        for (int i = 0; i < 3; i++) {
            bars[i].setProgress(channels[i]);
            valueLabels[i].setText(String.valueOf(channels[i]));
        }
        updatingControls = false;
        swatch.setBackground(rounded(currentColor(), 12));
    }

    @Override
    public int currentColor() {
        return Protocol.rgb(channels[0], channels[1], channels[2]);
    }

    private void sendCurrent() {
        if (!ble.isReady()) {
            status.setText("Connect the lightstick first");
            return;
        }
        ble.write(Protocol.ledPacket(effect, channels[0], channels[1], channels[2]));
        status.setText(String.format(java.util.Locale.US, "Sent #%02x%02x%02x (%s)",
                channels[0], channels[1], channels[2], EFFECT_NAMES[effectIndex()]));
    }

    private int effectIndex() {
        for (int i = 0; i < EFFECT_VALUES.length; i++) {
            if (EFFECT_VALUES[i] == effect) return i;
        }
        return 0;
    }

    private void onConnectClicked() {
        if (ble.isReady()) {
            ble.disconnect();
            return;
        }
        if (scanning) {
            handler.removeCallbacks(scanTimeout);
            ble.stopScan();
            scanning = false;
            connectButton.setText("Connect lightstick");
            status.setText("Scan cancelled");
            return;
        }
        String[] permissions = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}
                : new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(permissions, REQUEST_BLE);
                return;
            }
        }
        startScan();
    }

    private void startScan() {
        scanning = true;
        connectButton.setText("Cancel scan");
        if (!ble.scanAndConnect(this)) {
            scanning = false;
            connectButton.setText("Connect lightstick");
            return;
        }
        handler.postDelayed(scanTimeout, 15000);
    }

    private final Runnable scanTimeout = () -> {
        if (!ble.isReady()) {
            ble.stopScan();
            scanning = false;
            connectButton.setText("Connect lightstick");
            status.setText("No lightstick found. Turn it on in Bluetooth mode "
                    + "(hold buttons 1 and 2 for 2 s).");
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQUEST_BLE) return;
        for (int result : results) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                status.setText("Bluetooth permissions are required");
                return;
            }
        }
        startScan();
    }

    private void startPlayback() {
        if (playing) return;
        if (!ble.isReady()) {
            status.setText("Connect the lightstick first");
            return;
        }
        if (timeline.keyframes.isEmpty()) {
            status.setText("Tap the timeline to add colour stops first");
            return;
        }
        playing = true;
        lastSentColor = -1;
        playStarted = SystemClock.elapsedRealtime();
        playButton.setEnabled(false);
        handler.post(playTick);
    }

    private final Runnable playTick = new Runnable() {
        @Override
        public void run() {
            if (!playing) return;
            double t = (SystemClock.elapsedRealtime() - playStarted) / 1000.0;
            if (t >= timeline.duration) {
                stopPlayback(true);
                return;
            }
            timelineView.setPlayhead(t);
            int color = timeline.sample(t);
            if (color != lastSentColor) {
                ble.write(Protocol.ledPacket(Protocol.SOLID,
                        Color.red(color), Color.green(color), Color.blue(color)));
                lastSentColor = color;
            }
            handler.postDelayed(this, PLAY_STEP_MS);
        }
    };

    private void stopPlayback(boolean turnOff) {
        playing = false;
        handler.removeCallbacks(playTick);
        timelineView.clearPlayhead();
        playButton.setEnabled(true);
        lastSentColor = -1;
        if (turnOff && ble.isReady()) {
            ble.write(Protocol.ledPacket(Protocol.SOLID, 0, 0, 0));
            status.setText("Playback stopped");
        }
    }

    private void saveAll() {
        prefs.edit()
                .putString("timeline", timeline.toJson())
                .putInt("color", currentColor())
                .putInt("effect", effect)
                .apply();
    }

    @Override
    public void onStatus(String message) {
        runOnUiThread(() -> status.setText(message));
    }

    @Override
    public void onReady(String name) {
        runOnUiThread(() -> {
            handler.removeCallbacks(scanTimeout);
            scanning = false;
            connectButton.setText("Disconnect");
            status.setText("Connected to " + name);
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> {
            stopPlayback(false);
            scanning = false;
            connectButton.setText("Connect lightstick");
            status.setText("Disconnected");
        });
    }

    @Override
    public void onChanged() {
        saveAll();
    }

    @Override
    public void onSelected(int color) {
        setChannels(color);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        ble.disconnect();
    }

    // ---- small view helpers ----

    private TextView text(String value, float sizeSp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        return view;
    }

    private Button button(String label, int background, int textColor) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTextColor(textColor);
        button.setBackgroundTintList(ColorStateList.valueOf(background));
        return button;
    }

    private void addSection(LinearLayout root, String label) {
        TextView view = text(label, 11, 0xFF667085);
        view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        view.setLetterSpacing(0.12f);
        add(root, view, 26);
    }

    private void add(LinearLayout root, View view, int topDp) {
        add(root, view, topDp, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void add(LinearLayout root, View view, int topDp, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height);
        params.topMargin = dp(topDp);
        root.addView(view, params);
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
    }

    private LinearLayout.LayoutParams weightedWithMargin(int leftDp) {
        LinearLayout.LayoutParams params = weighted();
        params.leftMargin = dp(leftDp);
        return params;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}

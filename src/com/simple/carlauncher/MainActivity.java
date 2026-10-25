package com.simple.carlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

public class MainActivity extends Activity implements NowPlaying.Listener {

    private static final String MAPS = "com.google.android.apps.maps/com.google.android.maps.MapsActivity";
    private static final String EQ = "com.nwd.audioset/.home_horizontalActivity";
    private static final String SETTINGS = "com.android.car.setting/.home_horizontalActivity";

    private static final long WEATHER_EVERY_MS = 30 * 60 * 1000;

    private final Handler handler = new Handler();
    private final RadioClient radio = new RadioClient();
    private NowPlaying nowPlaying;

    private TextView weatherTemp;
    private ImageView weatherIcon;
    private ImageView car, carLights;
    private TextView oilInfo;
    private GradientDrawable oilBg;
    private long lastWeatherFetch;

    private TextView fmBand, fmFreq, fmUnit;
    private ImageView art, playBtn;
    private TextView songSource, songTitle, songArtist;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Layout: left icon sidebar + main area (status bar, then a 2x2 grid of cards).
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(0, dp(10), dp(12), dp(10));
        root.setBackground(Ui.background(this));

        root.addView(buildSidebar(), new LinearLayout.LayoutParams(dp(64), -1));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(12), 0, 0, 0);
        main.addView(buildStatusBar(), new LinearLayout.LayoutParams(-1, dp(40)));

        LinearLayout row1 = new LinearLayout(this);
        row1.addView(buildMapCard(), weight(1.25f, 0));
        row1.addView(buildFmCard(), weight(1f, dp(12)));
        main.addView(row1, vweight(1f, dp(8)));

        LinearLayout row2 = new LinearLayout(this);
        row2.addView(buildMusicCard(), weight(1f, 0));
        row2.addView(buildAppsCard(), weight(1.25f, dp(12)));
        main.addView(row2, vweight(1f, dp(12)));

        root.addView(main, new LinearLayout.LayoutParams(0, -1, 1f));
        setContentView(root);

        radio.bind(this);
        nowPlaying = NowPlaying.get(this);
        startService(new Intent(this, VolumeOverlay.class));
        startService(new Intent(this, GreeterService.class));
        startService(new Intent(this, Odometer.class));
    }

    // ---------- Sidebar ----------

    private View buildSidebar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setGravity(Gravity.CENTER_HORIZONTAL);
        bar.setBackgroundColor(0x33000000);
        bar.setPadding(0, dp(6), 0, dp(6));

        RingView ring = new RingView(this);
        bar.addView(ring, new LinearLayout.LayoutParams(dp(44), dp(44)));
        Ui.pressable(ring, 0.88f);
        ring.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));

        bar.addView(sideIcon(R.drawable.ic_compass, () -> Ui.launch(this, MAPS)));
        bar.addView(sideIcon(R.drawable.ic_bluetooth, () -> startActivity(new Intent(this, BtActivity.class))));
        bar.addView(sideIcon(R.drawable.ic_radio, () -> startActivity(new Intent(this, RadioActivity.class))));
        bar.addView(sideIcon(R.drawable.ic_w_moon, () -> startActivity(new Intent(this, ScreenOffActivity.class))));
        bar.addView(sideIcon(R.drawable.ic_gear, () -> Ui.launch(this, SETTINGS)));
        return bar;
    }

    private View sideIcon(int icon, Runnable action) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(0xE6FFFFFF);
        iv.setPadding(dp(12), dp(12), dp(12), dp(12));
        Ui.pressable(iv, 0.85f);
        iv.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(56), dp(56));
        lp.topMargin = dp(8);
        iv.setLayoutParams(lp);
        return iv;
    }

    // ---------- Status bar (clock + weather + Bluetooth) ----------

    private View buildStatusBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("hh:mm a");
        time.setTextSize(17);
        time.setTextColor(Color.WHITE);
        time.setTypeface(Typeface.DEFAULT_BOLD);
        time.setIncludeFontPadding(false);
        bar.addView(time, new LinearLayout.LayoutParams(-2, -2));

        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        weatherIcon = new ImageView(this);
        weatherIcon.setImageResource(R.drawable.ic_w_cloud);
        weatherIcon.setColorFilter(Color.WHITE);
        bar.addView(weatherIcon, new LinearLayout.LayoutParams(dp(20), dp(20)));

        weatherTemp = Ui.text(this, "--°", 15, Color.WHITE);
        weatherTemp.setPadding(dp(6), 0, dp(18), 0);
        bar.addView(weatherTemp, new LinearLayout.LayoutParams(-2, -2));

        bar.addView(Ui.iconButton(this, R.drawable.ic_bluetooth, 36, false,
                () -> startActivity(new Intent(this, BtActivity.class))),
                new LinearLayout.LayoutParams(dp(36), dp(36)));

        Ui.pressable(weatherTemp, 0.98f);
        weatherTemp.setOnClickListener(v -> fetchWeather(true));
        return bar;
    }

    private void showWeather(Weather w) {
        if (w == null) return;
        weatherIcon.setImageResource(w.icon());
        weatherIcon.setColorFilter(w.icon() == R.drawable.ic_w_sun ? 0xFFFFD25A
                : w.icon() == R.drawable.ic_w_moon ? 0xFFDDE4FF : Color.WHITE);
        weatherTemp.setText(w.temp + "°");
    }

    private void fetchWeather(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && lastWeatherFetch != 0 && now - lastWeatherFetch < WEATHER_EVERY_MS) return;
        lastWeatherFetch = now;
        Weather.fetch(this, this::showWeather);
    }

    private final Runnable periodic = new Runnable() {
        public void run() {
            fetchWeather(false);
            handler.postDelayed(this, 60 * 1000);
        }
    };

    // ---------- Car lights (ILL wire) ----------

    private final android.content.BroadcastReceiver illReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, Intent i) {
            android.util.Log.d("CarLights", "ILL broadcast extras=" + i.getExtras());
            showCarLights(i.getByteExtra("extra_ill_state", (byte) 0) != 0);
        }
    };

    /** The kernel keeps the light state as bit 2 of mcu_no_source_device_state. */
    private boolean carLightsOn() {
        int state = android.provider.Settings.System.getInt(getContentResolver(), "mcu_no_source_device_state", 0);
        return (state & 2) != 0;
    }

    private void showCarLights(boolean on) {
        // Cross-fade between the "lights off" and "lights on" photos.
        carLights.animate().alpha(on ? 1f : 0f).setDuration(600).start();
        car.animate().alpha(on ? 0f : 1f).setDuration(600).start();
    }

    private void updateOil() {
        if (!Odometer.isConfigured(this)) {
            oilInfo.setText("Aceite: configurar");
            oilBg.setColor(0x26FFFFFF);
            return;
        }
        int left = Odometer.remainingKm(this);
        oilInfo.setText(left < 0 ? "Aceite: ¡ya toca el cambio!" : "Aceite: faltan " + OilActivity.fmt(left) + " km");
        oilBg.setColor(left < 0 ? 0xCCE53935 : left <= Odometer.WARN_KM ? 0xCCFF9800 : 0x26FFFFFF);
    }

    // ---------- Map (car photo + navigation shortcut) ----------

    private View buildMapCard() {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(Ui.card(this));
        card.setClipToOutline(true);

        // The user's own car (photo with background removed); headlights glow when the car's lights are on.
        FrameLayout carBox = new FrameLayout(this);
        car = new ImageView(this);
        car.setImageResource(R.drawable.optra);
        car.setScaleType(ImageView.ScaleType.FIT_CENTER);
        carBox.addView(car, new FrameLayout.LayoutParams(-1, -1));
        carLights = new ImageView(this);
        carLights.setImageResource(R.drawable.optra_lights);
        carLights.setScaleType(ImageView.ScaleType.FIT_CENTER);
        carLights.setAlpha(0f);
        carBox.addView(carLights, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-1, -1);
        clp.setMargins(dp(30), dp(40), dp(30), dp(40));
        card.addView(carBox, clp);

        TextView label = Ui.text(this, "NAVEGACIÓN", 12, Color.WHITE);
        label.setLetterSpacing(0.1f);
        label.setPadding(dp(12), dp(5), dp(12), dp(5));
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(0x40000000);
        chip.setCornerRadius(dp(14));
        label.setBackground(chip);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        llp.setMargins(dp(12), dp(12), 0, 0);
        card.addView(label, llp);

        // Oil-change status pill; opens the oil screen.
        oilInfo = Ui.text(this, "", 13, Color.WHITE);
        oilInfo.setPadding(dp(14), dp(6), dp(14), dp(6));
        oilBg = new GradientDrawable();
        oilBg.setCornerRadius(dp(14));
        oilInfo.setBackground(oilBg);
        Ui.pressable(oilInfo, 0.92f);
        oilInfo.setOnClickListener(v -> startActivity(new Intent(this, OilActivity.class)));
        FrameLayout.LayoutParams olp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        olp.setMargins(dp(12), 0, 0, dp(12));
        card.addView(oilInfo, olp);

        Ui.pressable(card, 0.98f);
        card.setOnClickListener(v -> Ui.launch(this, MAPS));
        return card;
    }

    // ---------- FM ----------

    private View buildFmCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(Ui.card(this));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER);
        ImageView radioIcon = new ImageView(this);
        radioIcon.setImageResource(R.drawable.ic_radio);
        radioIcon.setColorFilter(0xFFFF9F5A);
        head.addView(radioIcon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        fmBand = Ui.text(this, "RADIO FM", 15, Color.WHITE);
        fmBand.setPadding(dp(8), 0, 0, 0);
        head.addView(fmBand);
        card.addView(head);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.addView(Ui.iconButton(this, R.drawable.ic_prev, 52, false, () -> stationStep(false)),
                new LinearLayout.LayoutParams(dp(52), dp(52)));
        fmFreq = Ui.text(this, "--.-", 50, Color.WHITE);
        fmFreq.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(fmFreq, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(Ui.iconButton(this, R.drawable.ic_next, 52, false, () -> stationStep(true)),
                new LinearLayout.LayoutParams(dp(52), dp(52)));
        row.setPadding(dp(12), dp(10), dp(12), 0);
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));

        fmUnit = Ui.text(this, "MHz", 15, 0x99FFFFFF);
        card.addView(fmUnit);

        Ui.pressable(card, 0.97f);
        card.setOnClickListener(v -> startActivity(new Intent(this, RadioActivity.class)));
        return card;
    }

    private void stationStep(boolean next) {
        radio.preset(next);
        handler.postDelayed(this::updateRadio, 300);
    }

    private void updateRadio() {
        RadioClient.Station st = radio.current();
        if (st == null) return;
        fmBand.setText(st.psName.isEmpty() ? "RADIO " + st.bandLabel() : st.bandLabel() + " · " + st.psName);
        fmFreq.setText(st.frequencyLabel());
        fmUnit.setText(st.isAm() ? "kHz" : "MHz");
    }

    private final Runnable radioPoll = new Runnable() {
        public void run() {
            updateRadio();
            handler.postDelayed(this, 1000);
        }
    };

    // ---------- Music (any source) ----------

    private View buildMusicCard() {
        FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF6A3BFF, 0xFFC04DE8, 0xFF2B1A7A});
        bg.setCornerRadius(dp(18));
        card.setBackground(bg);
        card.setClipToOutline(true);

        art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(art, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x33000000, 0x00000000, 0xCC100A30}));
        card.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        songSource = Ui.text(this, "MÚSICA", 12, Color.WHITE);
        songSource.setLetterSpacing(0.1f);
        songSource.setPadding(dp(12), dp(5), dp(12), dp(5));
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(0x40000000);
        chip.setCornerRadius(dp(14));
        songSource.setBackground(chip);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        slp.setMargins(dp(14), dp(14), 0, 0);
        card.addView(songSource, slp);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(16), 0, dp(16), dp(14));
        songTitle = Ui.text(this, "", 19, Color.WHITE);
        songTitle.setTypeface(Typeface.DEFAULT_BOLD);
        bottom.addView(songTitle);
        songArtist = Ui.text(this, "", 14, 0xCCFFFFFF);
        songArtist.setPadding(0, dp(2), 0, dp(10));
        bottom.addView(songArtist);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.addView(Ui.iconButton(this, R.drawable.ic_prev, 46, false, () -> nowPlaying.skip(false)),
                new LinearLayout.LayoutParams(dp(46), dp(46)));
        playBtn = Ui.iconButton(this, R.drawable.ic_play, 60, true, () -> nowPlaying.playPause());
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(60), dp(60));
        plp.setMargins(dp(16), 0, dp(16), 0);
        controls.addView(playBtn, plp);
        controls.addView(Ui.iconButton(this, R.drawable.ic_next, 46, false, () -> nowPlaying.skip(true)),
                new LinearLayout.LayoutParams(dp(46), dp(46)));
        bottom.addView(controls);
        card.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        Ui.pressable(card, 0.98f);
        card.setOnClickListener(v -> nowPlaying.open(this));
        return card;
    }

    @Override
    public void onNowPlayingChanged() {
        NowPlaying.Source s = nowPlaying.current();
        NowPlaying.Track t = s == null ? null : nowPlaying.track(s);
        boolean empty = t == null || t.title.isEmpty();
        songSource.setText(s == null ? "MÚSICA" : nowPlaying.label(s).toUpperCase());
        songTitle.setText(empty ? "Nada sonando" : t.title);
        songArtist.setText(empty ? "Toca para abrir la música" : t.artist);
        playBtn.setImageResource(t != null && t.playing ? R.drawable.ic_pause : R.drawable.ic_play);

        if (t != null && t.art != null) {
            art.setPadding(0, 0, 0, 0);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.clearColorFilter();
            art.setAlpha(1f);
            art.setImageBitmap(t.art);
        } else {
            art.setPadding(dp(80), dp(40), dp(80), dp(120));
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.setImageResource(s == NowPlaying.Source.BT ? R.drawable.ic_bluetooth : R.drawable.ic_music);
            art.setColorFilter(Color.WHITE);
            art.setAlpha(0.25f);
        }
    }

    // ---------- Apps (shortcut tiles) ----------

    private View buildAppsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(Ui.card(this));

        TextView title = Ui.text(this, "APLICACIONES", 12, 0xB3FFFFFF);
        title.setLetterSpacing(0.1f);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        card.addView(title, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row1 = new LinearLayout(this);
        row1.addView(buildTile(R.drawable.ic_compass, "Mapas", 0xFF4D8DFF,
                () -> Ui.launch(this, MAPS)), weight(1f, 0));
        row1.addView(buildTile(R.drawable.ic_bluetooth, "Bluetooth", 0xFF5AA9FF,
                () -> startActivity(new Intent(this, BtActivity.class))), weight(1f, dp(8)));
        row1.addView(buildTile(R.drawable.ic_radio, "Radio", 0xFFFF9F5A,
                () -> startActivity(new Intent(this, RadioActivity.class))), weight(1f, dp(8)));
        card.addView(row1, vweight(1f, dp(8)));

        LinearLayout row2 = new LinearLayout(this);
        row2.addView(buildTile(R.drawable.ic_music, "Música", 0xFFC04DE8,
                () -> nowPlaying.open(this)), weight(1f, 0));
        row2.addView(buildTile(R.drawable.ic_eq, "Ecualizador", 0xFF2EC4B6,
                () -> Ui.launch(this, EQ)), weight(1f, dp(8)));
        row2.addView(buildTile(R.drawable.ic_add, "Más apps", 0xFF8E6BFF,
                () -> startActivity(new Intent(this, AppsActivity.class))), weight(1f, dp(8)));
        card.addView(row2, vweight(1f, dp(10)));

        return card;
    }

    private View buildTile(int icon, String label, int tint, Runnable action) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(0x22FFFFFF);
        tile.setBackground(bg);

        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(tint);
        tile.addView(iv, new LinearLayout.LayoutParams(dp(32), dp(32)));

        TextView tv = Ui.text(this, label, 12, Color.WHITE);
        tv.setPadding(0, dp(6), 0, 0);
        tile.addView(tv, new LinearLayout.LayoutParams(-2, -2));

        Ui.pressable(tile, 0.94f);
        tile.setOnClickListener(v -> action.run());
        return tile;
    }

    // ---------- Lifecycle & helpers ----------

    @Override
    protected void onResume() {
        super.onResume();
        updateOil();
        showWeather(Weather.cached(this));
        registerReceiver(illReceiver, new android.content.IntentFilter("com.nwd.ACTION_ILL_STATE_CHANGE"));
        boolean lights = carLightsOn();
        android.util.Log.d("CarLights", "onResume lights=" + lights);
        carLights.setAlpha(lights ? 1f : 0f);
        car.setAlpha(lights ? 0f : 1f);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        handler.post(periodic);
        handler.post(radioPoll);
        nowPlaying.addListener(this);
        onNowPlayingChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(illReceiver);
        handler.removeCallbacks(periodic);
        handler.removeCallbacks(radioPoll);
        nowPlaying.removeListener(this);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) fetchWeather(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        radio.unbind(this);
    }

    private LinearLayout.LayoutParams weight(float w, int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, w);
        lp.leftMargin = left;
        return lp;
    }

    private LinearLayout.LayoutParams vweight(float w, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, w);
        lp.topMargin = top;
        return lp;
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }

    static void pressable(View v) {
        Ui.pressable(v, 0.94f);
    }

    @Override
    public void onBackPressed() {
        // Home screen: nothing to go back to.
    }
}

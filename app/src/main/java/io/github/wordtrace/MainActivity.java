package io.github.wordtrace;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.io.*;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {
    public static volatile boolean visible;
    private LinearLayout page, history;
    private TextView status;
    private MaterialButton start, finish;
    private SessionStore store;
    private SharedPreferences preferences;
    private String pendingExport;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<Intent> saveFile = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != RESULT_OK || result.getData() == null || pendingExport == null) return;
        Uri target = result.getData().getData(); String path = pendingExport;
        io.execute(() -> {
            try (OutputStream out = getContentResolver().openOutputStream(target)) {
                if (out == null) throw new IOException(); Files.copy(new File(path).toPath(), out);
                runOnUiThread(() -> inform("文件已保存到所选位置"));
            } catch (IOException e) { runOnUiThread(() -> inform("保存失败，请重新选择位置")); }
        });
    });
    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), allowed -> showOverlay());
    private final Runnable refresh = new Runnable() {
        private boolean wasActive, wasSaving;
        @Override public void run() {
            if (!visible) return;
            updateState();
            if ((wasActive && !CaptureService.active) || (wasSaving && !CaptureService.saving)) loadHistory();
            wasActive = CaptureService.active; wasSaving = CaptureService.saving;
            main.postDelayed(this, 1000);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("settings", MODE_PRIVATE); store = new SessionStore(this);
        if (state != null) pendingExport = state.getString("pendingExport");
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightNavigationBars(getResources().getBoolean(R.bool.light_bars));
        build();
    }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("pendingExport", pendingExport); }
    private void build() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        android.widget.FrameLayout frame = new FrameLayout(this); scroll.addView(frame);
        page = column(); page.setPadding(dp(24), dp(20), dp(24), dp(40));
        FrameLayout.LayoutParams content = new FrameLayout.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels, dp(760)), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        frame.addView(page, content); setContentView(scroll);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            androidx.core.graphics.Insets system = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(system.left, system.top, system.right, system.bottom); return insets;
        });
        LinearLayout header = row();
        TextView brand = text("wordtrace", 28, true); header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        MaterialButton help = button("使用说明", false); help.setOnClickListener(v -> showHelp()); header.addView(help); page.addView(header);
        gap(page, 28);
        page.addView(text("专心记忆，\n让单词留下来。", 30, true));
        TextView intro = text("在不背单词中学习，用悬浮窗记录遇见的英文。", 15, false); intro.setTextColor(color(R.color.secondary_ink)); add(page, intro, 12);
        LinearLayout capture = column(); capture.setPadding(dp(20), dp(20), dp(20), dp(20)); capture.setBackground(background(R.color.primary_container, 16)); add(page, capture, 24);
        status = text("准备开始", 18, true); capture.addView(status);
        add(capture, text("点击悬浮窗「开始」后授权屏幕共享；\n点击「结束」自动保存单词和次数。", 14, false), 8);
        start = button("打开悬浮窗", true); add(capture, start, 16); start.setOnClickListener(v -> begin());
        finish = button("结束当前记录并保存", false); add(capture, finish, 4);
        finish.setOnClickListener(v -> {
            if (CaptureService.active) { startService(new Intent(this, CaptureService.class).setAction(CaptureService.STOP)); stopService(new Intent(this, OverlayService.class)); }
            else inform("当前没有正在进行的记录");
        });
        add(page, text("识别偏好", 21, true), 28);
        MaterialSwitch large = new MaterialSwitch(this); large.setText("优先识别大字目标词"); large.setTextSize(16); large.setMinHeight(dp(56)); large.setChecked(preferences.getBoolean("large", false));
        large.setOnCheckedChangeListener((v, checked) -> { preferences.edit().putBoolean("large", checked).apply(); if (CaptureService.active) inform("将在下次记录生效"); }); add(page, large, 8);
        TextView hint = text("关闭时记录全部英文；开启后按字号筛选，适合目标词较大的页面。", 13, false); hint.setTextColor(color(R.color.secondary_ink)); page.addView(hint);
        MaterialButton region = button("设置识别区域", false); region.setOnClickListener(v -> editRegion()); add(page, region, 12);
        MaterialButton ignored = button("设置忽略词", false); ignored.setOnClickListener(v -> editIgnored()); add(page, ignored, 4);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1)); dividerParams.topMargin = dp(24); page.addView(divider(), dividerParams);
        LinearLayout title = row(); title.addView(text("学习记录", 21, true), new LinearLayout.LayoutParams(0, -2, 1));
        MaterialButton reload = button("刷新", false); reload.setOnClickListener(v -> loadHistory()); title.addView(reload); add(page, title, 16);
        history = column(); page.addView(history);
        TextView privacy = text("离线识别 · 不保存截图 · 无联网权限", 12, false); privacy.setTextColor(color(R.color.secondary_ink)); add(page, privacy, 32);
    }
    private void updateState() {
        status.setText(CaptureService.saving ? "正在保存记录…" : CaptureService.active ? (CaptureService.paused ? "已暂停 · " : "记录中 · ") + CaptureService.unique + " 个单词" : OverlayService.running ? "悬浮窗已就绪" : "准备开始");
        start.setText(OverlayService.running ? "悬浮窗已开启 · 返回学习" : "打开悬浮窗"); start.setEnabled(!CaptureService.saving);
        finish.setEnabled(CaptureService.active);
    }
    private void begin() {
        if (OverlayService.running) { moveTaskToBack(true); return; }
        if (!Settings.canDrawOverlays(this)) {
            new MaterialAlertDialogBuilder(this).setTitle("允许显示悬浮窗")
                .setMessage("WordTrace 需要在不背单词上方显示开始、暂停和结束按钮。请在接下来的设置中开启「显示在其他应用上层」。")
                .setNegativeButton("暂不", null).setPositiveButton("去设置", (d,w) -> {
                    try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); }
                    catch (ActivityNotFoundException e) { inform("请在系统设置 → 应用 → WordTrace 中允许悬浮窗"); }
                }).show(); return;
        }
        if (!preferences.getBoolean("disclosure", false)) {
            new MaterialAlertDialogBuilder(this).setTitle("只在你开始后读取屏幕")
                .setMessage("记录期间，WordTrace 会获取你授权的屏幕画面，并在本机识别英文。只保存单词和次数，不保存截图、不上传内容。\n\n建议在系统授权时仅选择不背单词；若选择整个屏幕，请避开不希望被记录的内容。你可随时暂停或结束。")
                .setNegativeButton("取消", null).setPositiveButton("了解，继续", (d,w) -> { preferences.edit().putBoolean("disclosure", true).apply(); requestNotifications(); }).show();
        } else requestNotifications();
    }
    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            && !preferences.getBoolean("notificationAsked", false)) {
            preferences.edit().putBoolean("notificationAsked", true).apply(); notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else showOverlay();
    }
    private void showOverlay() {
        try { ContextCompat.startForegroundService(this, new Intent(this, OverlayService.class)); inform("已打开悬浮窗，切换到不背单词后点「开始」"); }
        catch (RuntimeException e) { inform("悬浮窗启动失败，请检查系统悬浮窗权限"); }
        updateState();
    }
    private void editRegion() {
        LinearLayout form = column(); form.setPadding(dp(24), dp(8), dp(24), 0);
        form.addView(text("按屏幕高度选择范围，0% 为顶部、100% 为底部。只想记目标词时，可先尝试上半屏；横屏后建议重新设置。", 14, false));
        TextView value = text("", 16, true); add(form, value, 16);
        RangeSlider slider = new RangeSlider(this); slider.setValueFrom(0); slider.setValueTo(100); slider.setStepSize(5); slider.setMinSeparationValue(10);
        slider.setValues((float)preferences.getInt("top", 0), (float)preferences.getInt("bottom", 100));
        value.setText(getString(R.string.region_summary, slider.getValues().get(0).intValue(), slider.getValues().get(1).intValue()));
        slider.addOnChangeListener((s,v,user) -> value.setText(getString(R.string.region_summary, s.getValues().get(0).intValue(), s.getValues().get(1).intValue()))); form.addView(slider);
        new MaterialAlertDialogBuilder(this).setTitle("识别区域").setView(form).setNegativeButton("取消", null)
            .setNeutralButton("全屏", (d,w) -> { preferences.edit().putInt("top",0).putInt("bottom",100).apply(); inform("下次记录将识别全屏"); })
            .setPositiveButton("保存", (d,w) -> { preferences.edit().putInt("top",slider.getValues().get(0).intValue()).putInt("bottom",slider.getValues().get(1).intValue()).apply(); inform("已保存，下次记录生效"); }).show();
    }
    private void editIgnored() {
        LinearLayout form = column(); form.setPadding(dp(24), dp(8), dp(24), 0);
        form.addView(text("这些英文词不会计入记录。用空格、逗号或换行分隔，例如 next、review。设置在下次记录生效。", 14, false));
        TextInputLayout field = new TextInputLayout(this); field.setHint("忽略的英文词");
        TextInputEditText edit = new TextInputEditText(field.getContext()); edit.setMinLines(2); edit.setMaxLines(5); edit.setText(preferences.getString("ignored", "")); field.addView(edit); add(form, field, 16);
        new MaterialAlertDialogBuilder(this).setTitle("忽略词").setView(form).setNegativeButton("取消", null)
            .setPositiveButton("保存", (d,w) -> preferences.edit().putString("ignored", String.valueOf(edit.getText())).apply()).show();
    }
    private void loadHistory() {
        io.execute(() -> {
            if (!CaptureService.active && !CaptureService.saving) { try { store.recover(); } catch (IOException ignored) {} }
            List<SessionStore.Session> sessions = store.list();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                history.removeAllViews();
                if (sessions.isEmpty()) {
                    add(history, text("第一份单词记录，从下一次学习开始。", 16, true), 16);
                    add(history, text("结束后会在这里生成词表，可查看频次、分享，或保存为 CSV / TXT。", 14, false), 8); return;
                }
                for (SessionStore.Session s : sessions) {
                    MaterialButton item = button(date(s.started) + "\n" + s.words.size() + " 词 · " + s.total() + " 次" + (s.status.equals("interrupted") ? " · 中断后恢复" : s.status.equals("recording") ? " · 记录中" : ""), false);
                    item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); item.setMinHeight(dp(76)); item.setOnClickListener(v -> showSession(s)); add(history, item, 8);
                }
            });
        });
    }
    private void showSession(SessionStore.Session s) {
        LinearLayout box = column(); box.setPadding(dp(24), 0, dp(24), 0);
        box.addView(text(s.words.size() + " 个单词 · 共出现 " + s.total() + " 次", 16, true));
        TextView detail = text("同屏重复词记一次；按频次从高到低排列。", 12, false); add(box, detail, 8);
        EditText search = new EditText(this); search.setHint("搜索单词"); search.setSingleLine(true); search.setMinHeight(dp(48)); add(box, search, 8);
        ListView list = new ListView(this); ArrayList<String> rows = new ArrayList<>();
        s.words.forEach((word,count) -> rows.add(word + "    × " + count));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows); list.setAdapter(adapter);
        box.addView(list, new LinearLayout.LayoutParams(-1, dp(240)));
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence c,int st,int count,int after) {}
            public void onTextChanged(CharSequence c,int st,int before,int count) { adapter.getFilter().filter(c); }
            public void afterTextChanged(android.text.Editable e) {}
        });
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle(date(s.started)).setView(box).setNegativeButton("返回", null)
            .setPositiveButton("导出", (d,w) -> chooseExport(s)).setNeutralButton("删除", (d,w) -> confirmDelete(s)).create(); dialog.show();
        if (s.status.equals("recording")) dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setEnabled(false);
    }
    private void chooseExport(SessionStore.Session s) {
        new MaterialAlertDialogBuilder(this).setTitle("导出单词记录").setItems(new String[]{"分享 CSV（含次数）", "分享 TXT（含次数）", "另存为 CSV", "另存为 TXT"}, (d,which) -> {
            io.execute(() -> {
                String ext = which % 2 == 0 ? "csv" : "txt";
                try { File file = store.export(s, ext); runOnUiThread(() -> exportAction(file, ext, which < 2)); }
                catch (IOException e) { runOnUiThread(() -> inform("导出失败，请检查设备剩余空间")); }
            });
        }).show();
    }
    private void exportAction(File file, String ext, boolean share) {
        String mime = ext.equals("csv") ? "text/csv" : "text/plain";
        if (share) {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
            Intent intent = new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("WordTrace", uri)); startActivity(Intent.createChooser(intent, "分享单词记录"));
        } else {
            pendingExport = file.getAbsolutePath();
            try { saveFile.launch(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE, file.getName())); }
            catch (ActivityNotFoundException e) { inform("设备未提供文件选择器，请使用分享导出"); }
        }
    }
    private void confirmDelete(SessionStore.Session s) {
        new MaterialAlertDialogBuilder(this).setTitle("删除这次记录？").setMessage("应用内的词表和导出文件将被删除，已另存或分享的副本不受影响。")
            .setNegativeButton("取消", null).setPositiveButton("删除", (d,w) -> io.execute(() -> {
                try { store.delete(s); runOnUiThread(this::loadHistory); } catch (IOException e) { runOnUiThread(() -> inform("删除失败，请重试")); }
            })).show();
    }
    private void showHelp() {
        new MaterialAlertDialogBuilder(this).setTitle("让 WordTrace 陪你记单词")
            .setMessage("1. 允许悬浮窗，点击「打开悬浮窗」。\n2. 切换到不背单词，点悬浮窗「开始」。\n3. 同意系统屏幕共享。支持时优先只共享不背单词。\n4. 正常背词；需要时暂停，完成后点「结束」。\n5. 回到学习记录，查看词表并分享或另存。\n\n频次规则\n同一词持续显示只记一次。同屏重复只记一次；在有效识别帧中消失至少约 2 秒后再次出现，加一次。按小写归并，不合并单复数。\n\n使用提示\n约每秒识别一次，快速翻页可能漏词。OCR 可能误认音标、短词或漏掉小字；大字模式是字号筛选，并非不背单词专用接口。可结合识别区域与忽略词减少干扰。受保护的页面无法识别。\n\n结束后自动生成 CSV 和 TXT，保存在应用内；卸载会删除，请及时另存。锁屏、系统终止共享会结束记录；意外退出后可恢复最后保存的词表。\n\n隐私\n所有识别在本机进行，没有联网权限，不保存截图。返回本应用时自动跳过识别，防止重复记录词表。\n\nWordTrace 1.0.0 · MIT\n离线 OCR 使用 Google ML Kit（遵循其独立条款）。本项目与不背单词无隶属关系。")
            .setPositiveButton("知道了", null).show();
    }
    @Override protected void onResume() {
        super.onResume(); visible = true; loadHistory(); main.post(refresh);
        String error = preferences.getString("error", null);
        if (error != null) { preferences.edit().remove("error").apply(); new MaterialAlertDialogBuilder(this).setTitle("记录提示").setMessage(error).setPositiveButton("知道了",null).show(); }
    }
    @Override protected void onPause() { visible = false; main.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onDestroy() { io.shutdown(); super.onDestroy(); }
    private void inform(String message) { Snackbar.make(page, message, Snackbar.LENGTH_LONG).show(); }
    private String date(long timestamp) { return new SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(new Date(timestamp)); }
    private int color(int id) { return ContextCompat.getColor(this, id); }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private TextView text(String value, float size, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color(R.color.ink)); t.setLineSpacing(dp(3), 1f); if (bold) t.setTypeface(null, Typeface.BOLD); return t;
    }
    private MaterialButton button(String label, boolean primary) {
        MaterialButton b = new MaterialButton(this, null, primary ? com.google.android.material.R.attr.materialButtonStyle : com.google.android.material.R.attr.borderlessButtonStyle);
        b.setText(label); b.setTextSize(15); b.setAllCaps(false); b.setMinHeight(dp(48)); b.setCornerRadius(dp(12)); return b;
    }
    private void add(LinearLayout parent, View child, int margin) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.topMargin = dp(margin); parent.addView(child,p); }
    private void gap(LinearLayout parent, int size) { parent.addView(new View(this), new LinearLayout.LayoutParams(1,dp(size))); }
    private View divider() { View v = new View(this); v.setBackgroundColor(color(R.color.outline)); v.setMinimumHeight(dp(1)); return v; }
    private GradientDrawable background(int color, int radius) { GradientDrawable b = new GradientDrawable(); b.setColor(color(color)); b.setCornerRadius(dp(radius)); return b; }
}

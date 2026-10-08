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
    private TextView status, recognitionSummary, captureDetail;
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
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(getResources().getBoolean(R.bool.light_bars));
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
        ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.ic_launcher); logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        logo.setBackground(background(R.color.primary,8)); logo.setClipToOutline(true);
        LinearLayout.LayoutParams logoSize = new LinearLayout.LayoutParams(dp(32), dp(32)); logoSize.rightMargin = dp(10); header.addView(logo, logoSize);
        TextView brand = text("wordtrace", 24, true); header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        MaterialButton help = button("使用说明", false); help.setOnClickListener(v -> showHelp()); header.addView(help); page.addView(header);
        TextView intro = text("把学过的单词，留成一份词表。", 14, false); intro.setTextColor(color(R.color.secondary_ink)); add(page, intro, 8);
        LinearLayout capture = column(); capture.setPadding(dp(20), dp(20), dp(20), dp(20)); capture.setBackground(background(R.color.primary_container, 16)); add(page, capture, 28);
        status = text("准备记录", 20, true); capture.addView(status);
        captureDetail = text("打开小球，在学习页面开始。", 14, false); captureDetail.setTextColor(color(R.color.secondary_ink)); add(capture, captureDetail, 8);
        start = button("打开悬浮球", true); start.setMinHeight(dp(56)); add(capture, start, 20); start.setOnClickListener(v -> begin());
        finish = button("结束并保存", false); add(capture, finish, 8);
        finish.setOnClickListener(v -> {
            if (CaptureService.active) { startService(new Intent(this, CaptureService.class).setAction(CaptureService.STOP)); stopService(new Intent(this, OverlayService.class)); }
        });
        MaterialButton settings = button("识别设置", false); settings.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); settings.setOnClickListener(v -> editRecognition()); add(page, settings, 16);
        recognitionSummary = text("", 14, false); recognitionSummary.setTextColor(color(R.color.secondary_ink)); page.addView(recognitionSummary);
        LinearLayout.LayoutParams ruleLayout = new LinearLayout.LayoutParams(-1, dp(1)); ruleLayout.topMargin = dp(28);
        page.addView(divider(), ruleLayout);
        add(page, text("我的词表", 20, true), 28); history = column(); page.addView(history);
        TextView privacy = text("离线识别 · 自动去重 · 不保存截图\nWordTrace " + BuildConfig.VERSION_NAME, 12, false); privacy.setTextColor(color(R.color.secondary_ink)); add(page, privacy, 32);
    }
    private void updateState() {
        status.setText(CaptureService.saving ? "正在保存记录…" : CaptureService.active ? (CaptureService.paused ? "已暂停 · " : "记录中 · ") + CaptureService.unique + " 个单词" : OverlayService.running ? "悬浮球已就绪" : "准备记录");
        captureDetail.setText(CaptureService.active ? (CaptureService.latestWord.isEmpty() ? "正在等待清晰的大字目标词" : "最近记下：" + CaptureService.latestWord) : "打开小球，在学习页面开始。");
        start.setText(OverlayService.running ? "悬浮球已开启 · 返回学习" : "打开悬浮球"); start.setEnabled(!CaptureService.saving);
        finish.setVisibility(CaptureService.active || CaptureService.saving ? View.VISIBLE : View.GONE);
        finish.setEnabled(CaptureService.active && !CaptureService.saving);
        recognitionSummary.setText(preferences.getBoolean("targetWords", true) ? "大字目标词 · 每个单词只保存一次" : "全部英文 · 每个单词只保存一次");
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
                .setMessage("记录期间，WordTrace 会获取你授权的屏幕画面，并在本机识别英文。只保存去重后的单词，不保存截图、不上传内容。\n\n请只在学习时开始，离开学习页面前暂停。屏幕共享可能中断系统录屏，请先结束录屏。")
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
        try { ContextCompat.startForegroundService(this, new Intent(this, OverlayService.class)); inform("已打开悬浮球，切换到不背单词后轻触小球开始"); }
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
    private void editRecognition() {
        new MaterialAlertDialogBuilder(this).setTitle("识别设置")
            .setItems(new String[]{"识别内容", "识别区域", "忽略词"}, (d, which) -> {
                if (which == 1) editRegion();
                else if (which == 2) editIgnored();
                else new MaterialAlertDialogBuilder(this).setTitle("识别内容")
                    .setSingleChoiceItems(new String[]{"大字目标词（推荐）", "全部英文"}, preferences.getBoolean("targetWords", true) ? 0 : 1, (dialog, index) -> {
                        preferences.edit().putBoolean("targetWords", index == 0).apply(); dialog.dismiss(); updateState();
                        if (CaptureService.active) inform("下次记录生效");
                    }).setNegativeButton("返回", null).show();
            }).setNegativeButton("返回", null).show();
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
                    ImageView icon = new ImageView(this); icon.setImageResource(R.drawable.ic_wordlist); icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    LinearLayout.LayoutParams iconSize = new LinearLayout.LayoutParams(dp(32),dp(32)); iconSize.topMargin = dp(24); history.addView(icon,iconSize);
                    add(history, text("还没有词表", 16, true), 16);
                    add(history, text("点「打开悬浮球」开始。结束后，你的单词会出现在这里，可保存为 TXT。", 14, false), 8); return;
                }
                for (SessionStore.Session s : sessions) {
                    LinearLayout item = row(); item.setPadding(0,dp(16),0,dp(16)); item.setMinimumHeight(dp(76));
                    LinearLayout label = column(); item.addView(label,new LinearLayout.LayoutParams(0,-2,1));
                    label.addView(text(date(s.started),16,true));
                    String preview = String.join(" · ", new TreeSet<>(s.words.keySet()).stream().limit(3).toArray(String[]::new));
                    if(s.status.equals("interrupted")) preview = "中断后已保存 · " + preview;
                    else if(s.status.equals("recording")) preview = "记录中 · " + preview;
                    TextView example = text(preview.isEmpty() ? "未记录到单词" : preview,13,false); example.setTextColor(color(R.color.secondary_ink)); example.setSingleLine(true); example.setEllipsize(android.text.TextUtils.TruncateAt.END); add(label,example,4);
                    TextView count = text(s.words.size()+" 词",14,false); count.setTextColor(color(R.color.primary)); count.setPadding(dp(16),0,dp(12),0); item.addView(count);
                    ImageView arrow = new ImageView(this); arrow.setImageResource(R.drawable.ic_chevron); item.addView(arrow,new LinearLayout.LayoutParams(dp(20),dp(20)));
                    item.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(color(R.color.primary_container)),null,background(R.color.surface,12)));
                    item.setFocusable(true); item.setClickable(true); item.setContentDescription(date(s.started)+"，"+s.words.size()+" 个单词，查看词表");
                    item.setOnClickListener(v -> showSession(s)); history.addView(item);
                    history.addView(divider(), new LinearLayout.LayoutParams(-1,dp(1)));
                }
            });
        });
    }
    private void showSession(SessionStore.Session s) {
        LinearLayout box = column(); box.setPadding(dp(24), 0, dp(24), 0);
        box.addView(text(s.words.size() + " 个不重复单词", 16, true));
        TextView detail = text("按字母顺序排列 · 导出为纯文本", 12, false); add(box, detail, 8);
        TextInputLayout searchField = new TextInputLayout(this); searchField.setHint("搜索单词"); searchField.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE); searchField.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        TextInputEditText search = new TextInputEditText(searchField.getContext()); search.setSingleLine(true); search.setMinHeight(dp(48)); search.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_FILTER); searchField.addView(search); add(box, searchField, 16);
        ListView list = new ListView(this); ArrayList<String> rows = new ArrayList<>();
        rows.addAll(new TreeSet<>(s.words.keySet()));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows); list.setAdapter(adapter);
        box.addView(list, new LinearLayout.LayoutParams(-1, Math.min(dp(240), getResources().getDisplayMetrics().heightPixels / 3)));
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence c,int st,int count,int after) {}
            public void onTextChanged(CharSequence c,int st,int before,int count) { adapter.getFilter().filter(c); }
            public void afterTextChanged(android.text.Editable e) {}
        });
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle(date(s.started)).setView(box).setNegativeButton("返回", null)
            .setPositiveButton("导出 TXT", (d,w) -> chooseExport(s)).setNeutralButton("删除", (d,w) -> confirmDelete(s)).create(); dialog.show();
        if (s.status.equals("recording")) dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setEnabled(false);
    }
    private void chooseExport(SessionStore.Session s) {
        new MaterialAlertDialogBuilder(this).setTitle("导出词表")
            .setItems(new String[]{"保存 TXT 到文件", "分享 TXT 文件", "复制单词"}, (d,which) -> {
                if (which == 2) {
                    getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("WordTrace", Exports.txt(s.words)));
                    inform("已复制单词"); return;
                }
                io.execute(() -> {
                    try { File file = store.export(s, "txt"); runOnUiThread(() -> exportAction(file, "txt", which == 1)); }
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
            .setMessage("1. 打开悬浮球，切换到不背单词。\n2. 点小球 → 开始，允许系统屏幕共享。请先结束系统录屏。\n3. 正常学习，让目标词稳定停留至少一秒。\n4. 点小球 → 结束保存，回到我的词表导出 TXT。\n\n小球可以拖动，松手自动贴边并记住位置。再次点小球可收起菜单。\n\n识别规则\n默认只记字号明显大于其他英文行的目标词，避开首页菜单、词书列表和小字例句。按位置重组字母，用内置英文词表校验，并在连续两次确认后保存。不会补猜缺失字母；孤立字母、未收录的生僻词、快速翻页和受保护画面可能漏记。设置里可调整内容、区域和忽略词。离开学习页前请暂停。\n\n每个单词仅保留一次，不统计出现频率。旧词表仍保留，导出时每行一个词。\n\n所有识别在本机进行，无联网权限，不保存截图。文件先保存在应用内；卸载前请另存。\n\nWordTrace " + BuildConfig.VERSION_NAME + " · MIT\n离线 OCR 使用 Google ML Kit，词表来自 CMUdict，均遵循各自条款。本项目与不背单词无隶属关系。")
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

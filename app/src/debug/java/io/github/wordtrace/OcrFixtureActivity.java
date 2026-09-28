package io.github.wordtrace;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Synthetic OCR fixture, deliberately excluded from release builds. */
public class OcrFixtureActivity extends Activity {
    @Override public void onCreate(Bundle b) { super.onCreate(b); render(getIntent()); }
    @Override public void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); render(i); }
    private void render(Intent i) {
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setGravity(Gravity.CENTER); layout.setBackgroundColor(Color.WHITE);
        TextView word = new TextView(this); word.setText(i.getStringExtra("word") == null ? "apple" : i.getStringExtra("word"));
        word.setTextSize(56); word.setLetterSpacing(i.getFloatExtra("spacing", 0)); word.setTextColor(Color.BLACK); word.setGravity(Gravity.CENTER); layout.addView(word);
        TextView meaning = new TextView(this); meaning.setText("识别测试页 · 示例词"); meaning.setTextSize(18); meaning.setTextColor(Color.DKGRAY); meaning.setGravity(Gravity.CENTER); layout.addView(meaning);
        setContentView(layout);
    }
}

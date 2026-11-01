package com.racheli.offlinenotes;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(246, 247, 251);
    private static final int INK = Color.rgb(32, 36, 55);
    private static final int MUTED = Color.rgb(119, 124, 145);
    private static final int PURPLE = Color.rgb(96, 82, 205);
    private static final int MINT = Color.rgb(32, 156, 135);
    private static final int LINE = Color.rgb(229, 232, 241);
    private static final int PICK_VAULT = 201, PICK_MODEL = 202, PERMISSION_MIC = 203;

    private NotesDb db;
    private LinearLayout root;
    private String currentTab = "home";
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor();
    private final ArrayList<String> chat = new ArrayList<>();
    private TextView aiStatus;
    private EditText aiInput;
    private String modelPath;
    private MediaRecorder recorder;
    private MediaPlayer player;
    private boolean recording = false;
    private long recordingNoteId = -1;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        db = new NotesDb(this);
        modelPath = getPreferences(MODE_PRIVATE).getString("model_path", null);
        restoreChat();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(root);
        showScreen("home");
    }

    private void restoreChat() {
        String saved = getPreferences(MODE_PRIVATE).getString("ai_chat", "");
        if (saved == null || saved.isEmpty()) return;
        for (String line : saved.split("\\n")) {
            if ((line.startsWith("U|") || line.startsWith("A|")) && chat.size() < 30) chat.add(line);
        }
    }

    private void showScreen(String tab) {
        currentTab = tab;
        root.removeAllViews();
        root.setPadding(dp(18), dp(12), dp(18), dp(8));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout page = column();
        page.setPadding(0, dp(5), 0, dp(18));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        switch (tab) {
            case "notes": showNotes(page); break;
            case "tasks": showTasks(page); break;
            case "vault": showVault(page); break;
            case "ai": showAi(page); break;
            default: showHome(page); break;
        }
        root.addView(bottomNav(), new LinearLayout.LayoutParams(-1, dp(62)));
    }

    private void showHome(LinearLayout page) {
        LinearLayout header = row();
        TextView mark = text("LV", 15, Color.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(round(PURPLE, 16));
        header.addView(mark, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout brand = column();
        brand.setPadding(dp(12), 0, 0, 0);
        brand.addView(text("LifeVault", 23, INK, true));
        brand.addView(text("המרחב האישי שלך. גם בלי אינטרנט.", 12, MUTED, false));
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(pill("●  מקומי", Color.rgb(226, 246, 240), MINT));
        page.addView(header);
        gap(page, 20);
        page.addView(text("הכול במקום אחד ✨", 25, INK, true));
        page.addView(text("רעיונות, משימות ומסמכים — נשמרים אצלך.", 13, MUTED, false));
        gap(page, 15);

        LinearLayout stats = row();
        stats.addView(statCard("פתקים", String.valueOf(db.all().size()), "✎", PURPLE), weighted());
        stats.addView(statCard("משימות פתוחות", String.valueOf(openTaskCount()), "✓", MINT), weighted());
        stats.addView(statCard("קבצים", String.valueOf(db.allVaultItems().size()), "▧", Color.rgb(222, 142, 61)), weighted());
        page.addView(stats);
        gap(page, 19);
        page.addView(sectionTitle("קיצורי דרך", "פעולות מהירות"));
        LinearLayout shortcuts = row();
        shortcuts.addView(shortcut("＋", "פתק חדש", () -> editNote(null)), weighted());
        shortcuts.addView(shortcut("✓", "משימה", this::addTaskDialog), weighted());
        shortcuts.addView(shortcut("▧", "קובץ", this::pickVaultFile), weighted());
        shortcuts.addView(shortcut("✦", "עוזר AI", () -> showScreen("ai")), weighted());
        page.addView(shortcuts);
        gap(page, 20);
        page.addView(sectionTitle("נשארים בתנועה", "המשימות הפתוחות שלך"));
        List<TaskItem> tasks = db.allTasks();
        int shown = 0;
        for (TaskItem task : tasks) {
            if (task.done) continue;
            page.addView(taskRow(task));
            if (++shown == 3) break;
        }
        if (shown == 0) page.addView(emptyCard("אין משימות פתוחות", "הוסיפי משימה חדשה והיא תופיע כאן."));
        gap(page, 18);
        page.addView(sectionTitle("מה חדש אצלך", "פתקים אחרונים"));
        List<Note> notes = db.all();
        shown = 0;
        for (Note note : notes) {
            page.addView(noteRow(note));
            if (++shown == 3) break;
        }
        if (shown == 0) page.addView(emptyCard("הכספת שלך מוכנה", "צרי את הפתק הראשון ושמרי בו רעיונות."));
        gap(page, 14);
        page.addView(infoCard("🔒  הפרטיות שלך חשובה", "המידע נשמר בקבצים ובמסד נתונים מקומי. לא נדרשת הרשאת אינטרנט."));
    }

    private void showNotes(LinearLayout page) {
        page.addView(text("הפתקים שלי", 25, INK, true));
        page.addView(text("רעיונות, מחשבות וכל מה שחשוב לזכור.", 13, MUTED, false));
        gap(page, 14);
        LinearLayout toolbar = row();
        EditText search = searchField("חיפוש בפתקים...");
        toolbar.addView(search, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView add = button("＋ חדש", PURPLE, Color.WHITE);
        add.setOnClickListener(v -> editNote(null));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-2, dp(48));
        ap.leftMargin = dp(8);
        toolbar.addView(add, ap);
        page.addView(toolbar);
        LinearLayout results = column();
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = dp(12);
        page.addView(results, rp);
        Runnable refresh = () -> {
            results.removeAllViews();
            String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            int count = 0;
            for (Note note : db.all()) {
                if (!q.isEmpty() && !(note.title + " " + note.body).toLowerCase(Locale.ROOT).contains(q)) continue;
                results.addView(noteRow(note));
                count++;
            }
            if (count == 0) results.addView(emptyCard("לא נמצאו פתקים", "אפשר ליצור פתק חדש או לחפש במילים אחרות."));
        };
        search.addTextChangedListener(watcher(refresh));
        refresh.run();
    }

    private void showTasks(LinearLayout page) {
        LinearLayout header = row();
        header.addView(text("המשימות שלי", 25, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView add = button("＋ משימה", PURPLE, Color.WHITE);
        add.setOnClickListener(v -> addTaskDialog());
        header.addView(add);
        page.addView(header);
        page.addView(text("רשימה פשוטה וברורה, שנשמרת אצלך.", 13, MUTED, false));
        gap(page, 16);
        List<TaskItem> tasks = db.allTasks();
        int open = 0;
        for (TaskItem task : tasks) if (!task.done) { page.addView(taskRow(task)); open++; }
        if (open == 0) page.addView(emptyCard("הכול רגוע כרגע", "אין משימות פתוחות. הוסיפי אחת כשצריך."));
        gap(page, 17);
        page.addView(sectionTitle("הושלמו", "משימות שסימנת כבוצעו"));
        int done = 0;
        for (TaskItem task : tasks) if (task.done) { page.addView(taskRow(task)); done++; }
        if (done == 0) page.addView(text("עדיין אין משימות שהושלמו.", 12, MUTED, false));
    }

    private void showVault(LinearLayout page) {
        LinearLayout header = row();
        header.addView(text("הכספת שלי", 25, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView add = button("＋ הוספת קובץ", PURPLE, Color.WHITE);
        add.setOnClickListener(v -> pickVaultFile());
        header.addView(add);
        page.addView(header);
        page.addView(text("מסמכים, תמונות וקבצים — נשמרים במכשיר.", 13, MUTED, false));
        gap(page, 16);
        page.addView(infoCard("📁  אחסון פרטי", "קבצים שתוסיפי יועתקו לאחסון הפרטי של LifeVault. מחיקה מהכספת תמחק גם את העותק המקומי."));
        gap(page, 12);
        List<VaultItem> items = db.allVaultItems();
        if (items.isEmpty()) {
            page.addView(emptyCard("הכספת עדיין ריקה", "הוסיפי PDF, תמונה או מסמך כדי לשמור אותם במקום אחד."));
            return;
        }
        for (VaultItem item : items) page.addView(vaultRow(item));
    }

    private void showAi(LinearLayout page) {
        page.addView(text("העוזר האישי שלך", 25, INK, true));
        page.addView(text("עוזר מקומי שמכיר את התוכן שתבחרי לשתף איתו.", 13, MUTED, false));
        gap(page, 14);
        LinearLayout modelCard = card();
        LinearLayout modelHead = row();
        modelHead.addView(text("✦", 22, PURPLE, true), new LinearLayout.LayoutParams(dp(34), -2));
        LinearLayout modelText = column();
        modelText.addView(text("מודל שפה מקומי", 14, INK, true));
        aiStatus = text(modelPath == null ? "לא הותקן מודל עדיין" : new File(modelPath).getName(), 11, MUTED, false);
        modelText.addView(aiStatus);
        modelHead.addView(modelText, new LinearLayout.LayoutParams(0, -2, 1));
        modelCard.addView(modelHead);
        LinearLayout buttons = row();
        TextView import = button("בחירת מודל .litertlm", PURPLE, Color.WHITE);
        import.setOnClickListener(v -> pickModel());
        buttons.addView(import);
        TextView reload = button("טעינה", Color.rgb(239, 237, 255), PURPLE);
        reload.setOnClickListener(v -> loadModel());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, dp(42));
        rlp.leftMargin = dp(8);
        buttons.addView(reload, rlp);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(10);
        modelCard.addView(buttons, bp);
        LinearLayout.LayoutParams mcp = new LinearLayout.LayoutParams(-1, -2);
        mcp.bottomMargin = dp(12);
        page.addView(modelCard, mcp);

        if (modelPath == null || !new File(modelPath).exists()) {
            page.addView(infoCard("AI מקומי ללא מודל", "אפשר כבר עכשיו לחפש בפתקים ולראות משימות. לשיחה יצירתית, סיכום וניתוח בשפה טבעית, ייבאי קובץ מודל LiteRT-LM מסוג .litertlm. קובץ המודל אינו כלול ב-APK כי הוא גדול בהרבה מהאפליקציה עצמה."));
        } else if (!LiteRtBridge.isLoaded()) {
            page.addView(infoCard("המודל שמור במכשיר", "לחצי על טעינה כדי להפעיל אותו. טעינה ראשונה עשויה להימשך זמן ולצרוך זיכרון."));
        } else {
            page.addView(infoCard("✓  AI מוכן לעבודה", "המודל פועל מקומית במכשיר. אין צורך בחיבור רשת לאחר הייבוא."));
        }
        gap(page, 12);
        for (String item : chat) page.addView(chatBubble(item.substring(2), item.startsWith("U|")));
        gap(page, 10);
        aiInput = new EditText(this);
        aiInput.setTextSize(14);
        aiInput.setTextColor(INK);
        aiInput.setHintTextColor(MUTED);
        aiInput.setHint("שאלי משהו על הפתקים והמשימות שלך...");
        aiInput.setMinLines(2);
        aiInput.setMaxLines(4);
        aiInput.setGravity(Gravity.TOP | Gravity.RIGHT);
        aiInput.setPadding(dp(13), dp(12), dp(13), dp(12));
        aiInput.setBackground(round(Color.WHITE, 14, LINE, 1));
        page.addView(aiInput, new LinearLayout.LayoutParams(-1, dp(82)));
        TextView send = button("שליחה  ➤", PURPLE, Color.WHITE);
        send.setOnClickListener(v -> sendAiMessage());
        LinearLayout.LayoutParams srp = new LinearLayout.LayoutParams(-1, dp(46));
        srp.topMargin = dp(8);
        page.addView(send, srp);
        TextView clear = text("ניקוי השיחה", 11, MUTED, true);
        clear.setGravity(Gravity.CENTER);
        clear.setPadding(0, dp(12), 0, dp(8));
        clear.setOnClickListener(v -> {
            chat.clear();
            getPreferences(MODE_PRIVATE).edit().remove("ai_chat").apply();
            showScreen("ai");
        });
        page.addView(clear);
    }

    private void loadModel() {
        if (modelPath == null || !new File(modelPath).exists()) {
            toast("בחרי קודם קובץ .litertlm");
            return;
        }
        if (aiStatus != null) aiStatus.setText("טוען מודל...");
        aiExecutor.execute(() -> {
            try {
                String result = LiteRtBridge.load(modelPath, getCacheDir().getAbsolutePath());
                runOnUiThread(() -> {
                    toast(result);
                    if ("ai".equals(currentTab)) showScreen("ai");
                });
            } catch (Exception e) {
                String error = errorText(e);
                runOnUiThread(() -> {
                    toast("טעינת המודל נכשלה: " + error);
                    if ("ai".equals(currentTab)) showScreen("ai");
                });
            }
        });
    }

    private void sendAiMessage() {
        if (aiInput == null) return;
        String question = aiInput.getText().toString().trim();
        if (question.isEmpty()) return;
        aiInput.setText("");
        addChat("U|" + question);
        if (LiteRtBridge.isLoaded()) {
            String prompt = LocalAssistant.buildContext(question, db)
                    + "\nשאלת המשתמשת: " + question
                    + "\nעני בעברית באופן ברור ומעשי. אם המידע אינו קיים בהקשר, אמרי זאת במפורש.";
            aiExecutor.execute(() -> {
                try {
                    String reply = LiteRtBridge.ask(prompt);
                    runOnUiThread(() -> addChat("A|" + reply));
                } catch (Exception e) {
                    String error = errorText(e);
                    runOnUiThread(() -> addChat("A|הייתה בעיה בהרצת המודל המקומי: " + error));
                }
            });
        } else {
            aiExecutor.execute(() -> {
                String reply = LocalAssistant.answer(question, db);
                runOnUiThread(() -> addChat("A|" + reply));
            });
        }
    }

    private void addChat(String value) {
        chat.add(value);
        while (chat.size() > 30) chat.remove(0);
        StringBuilder saved = new StringBuilder();
        for (String line : chat) {
            if (saved.length() > 0) saved.append("\n");
            saved.append(line.replace("\n", " "));
        }
        getPreferences(MODE_PRIVATE).edit().putString("ai_chat", saved.toString()).apply();
        if ("ai".equals(currentTab)) showScreen("ai");
    }

    private void editNote(Note existing) {
        final Note note = existing == null
                ? new Note(0, "", "", null, null, System.currentTimeMillis())
                : new Note(existing.id, existing.title, existing.body, existing.imagePath, existing.audioPath, existing.updatedAt);
        LinearLayout form = column();
        form.setPadding(dp(5), dp(8), dp(5), dp(3));
        EditText title = field("כותרת", note.title, false);
        EditText body = field("מה תרצי לזכור?", note.body, true);
        form.addView(title, new LinearLayout.LayoutParams(-1, dp(50)));
        LinearLayout.LayoutParams bodyP = new LinearLayout.LayoutParams(-1, dp(160));
        bodyP.topMargin = dp(9);
        form.addView(body, bodyP);
        TextView record = button(exists(note.audioPath) ? "▶  יש הקלטה מצורפת" : "●  הקלטת קול", Color.rgb(239, 237, 255), PURPLE);
        record.setOnClickListener(v -> {
            saveNote(note, title.getText().toString(), body.getText().toString());
            new AlertDialog.Builder(this).setMessage("הפתק יישמר לפני פתיחת ההקלטה.")
                    .setPositiveButton("המשך", (d, w) -> startRecordingForNote(note.id))
                    .setNegativeButton("ביטול", null).show();
        });
        LinearLayout.LayoutParams recP = new LinearLayout.LayoutParams(-1, dp(42));
        recP.topMargin = dp(9);
        form.addView(record, recP);
        if (exists(note.audioPath)) {
            TextView play = button("השמעת ההקלטה", Color.rgb(239, 237, 255), PURPLE);
            play.setOnClickListener(v -> playAudio(note.audioPath));
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(42));
            pp.topMargin = dp(7);
            form.addView(play, pp);
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(existing == null ? "פתק חדש" : "עריכת פתק")
                .setView(form).setNegativeButton("ביטול", null).setPositiveButton("שמירה", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            saveNote(note, title.getText().toString(), body.getText().toString());
            dialog.dismiss();
            showScreen("notes");
        }));
        dialog.show();
    }

    private void saveNote(Note note, String title, String body) {
        note.title = title == null ? "" : title.trim();
        note.body = body == null ? "" : body;
        if (note.title.isEmpty() && note.body.trim().isEmpty() && note.imagePath == null && note.audioPath == null) return;
        note.updatedAt = System.currentTimeMillis();
        note.id = db.save(note);
    }

    private void addTaskDialog() {
        LinearLayout form = column();
        EditText title = field("מה צריך לעשות?", "", false);
        EditText detail = field("הערות (לא חובה)", "", false);
        EditText due = field("מועד יעד (למשל: 12/11)", "", false);
        form.addView(title);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48));
        p.topMargin = dp(8); form.addView(detail, p);
        p = new LinearLayout.LayoutParams(-1, dp(48));
        p.topMargin = dp(8); form.addView(due, p);
        new AlertDialog.Builder(this).setTitle("משימה חדשה").setView(form)
                .setNegativeButton("ביטול", null)
                .setPositiveButton("הוספה", (d, w) -> {
                    String name = title.getText().toString().trim();
                    if (name.isEmpty()) { toast("צריך לתת שם למשימה"); return; }
                    db.saveTask(new TaskItem(0, name, detail.getText().toString(), due.getText().toString(), false, System.currentTimeMillis()));
                    showScreen(currentTab);
                }).show();
    }

    private void startRecordingForNote(long noteId) {
        recordingNoteId = noteId;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_MIC);
            return;
        }
        startRecording();
    }

    private void startRecording() {
        try {
            File dir = new File(getFilesDir(), "audio");
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("לא ניתן ליצור תיקיית הקלטות");
            File audio = new File(dir, "voice_" + System.currentTimeMillis() + ".m4a");
            recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(audio.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recording = true;
            new AlertDialog.Builder(this).setTitle("מקליטה עכשיו")
                    .setMessage("לחצי על עצירה כדי לשמור את ההקלטה בתוך הפתק.")
                    .setNegativeButton("ביטול", (d, w) -> stopRecording(false, audio))
                    .setPositiveButton("עצירה ושמירה", (d, w) -> stopRecording(true, audio))
                    .setCancelable(false).show();
        } catch (Exception e) {
            releaseRecorder();
            recording = false;
            toast("לא ניתן להתחיל הקלטה: " + errorText(e));
        }
    }

    private void stopRecording(boolean save, File audio) {
        try {
            if (recorder != null) recorder.stop();
            if (save) {
                Note note = db.get(recordingNoteId);
                if (note != null) {
                    note.audioPath = audio.getAbsolutePath();
                    db.save(note);
                    toast("ההקלטה נשמרה בפתק");
                } else audio.delete();
            } else audio.delete();
        } catch (Exception e) {
            audio.delete();
            toast("ההקלטה לא נשמרה");
        } finally {
            recording = false;
            releaseRecorder();
            showScreen("notes");
        }
    }

    private void releaseRecorder() {
        if (recorder != null) {
            try { recorder.reset(); } catch (Exception ignored) { }
            try { recorder.release(); } catch (Exception ignored) { }
            recorder = null;
        }
    }

    private void playAudio(String path) {
        try {
            if (player != null) { player.release(); player = null; }
            player = new MediaPlayer();
            player.setDataSource(path);
            player.setOnCompletionListener(mp -> { mp.release(); player = null; });
            player.prepare();
            player.start();
        } catch (Exception e) {
            toast("לא ניתן להשמיע הקלטה");
            if (player != null) { player.release(); player = null; }
        }
    }

    private void pickVaultFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_VAULT);
    }

    private void pickModel() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_MODEL);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == PICK_MODEL) importModel(data.getData());
        else if (request == PICK_VAULT) importVaultFile(data.getData());
    }

    private void importModel(Uri uri) {
        aiExecutor.execute(() -> {
            try {
                File dir = new File(getFilesDir(), "models");
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("לא ניתן ליצור תיקייה");
                File target = new File(dir, "lifevault-model.litertlm");
                copyUri(uri, target);
                modelPath = target.getAbsolutePath();
                getPreferences(MODE_PRIVATE).edit().putString("model_path", modelPath).apply();
                runOnUiThread(() -> {
                    toast("המודל הועתק לאחסון הפרטי. טוענת אותו...");
                    if ("ai".equals(currentTab)) showScreen("ai");
                    loadModel();
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("ייבוא המודל נכשל: " + errorText(e)));
            }
        });
    }

    private void importVaultFile(Uri uri) {
        aiExecutor.execute(() -> {
            try {
                String name = displayName(uri);
                String mime = getContentResolver().getType(uri);
                if (mime == null) mime = "application/octet-stream";
                File dir = new File(getFilesDir(), "vault");
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("לא ניתן ליצור תיקייה");
                String safeName = System.currentTimeMillis() + "_" + name.replaceAll("[^a-zA-Z0-9._א-ת -]", "_");
                File target = new File(dir, safeName);
                long size = copyUri(uri, target);
                db.addVaultItem(name, mime, target.getAbsolutePath(), size);
                runOnUiThread(() -> {
                    toast("הקובץ נשמר בכספת המקומית");
                    showScreen("vault");
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("הוספת הקובץ נכשלה: " + errorText(e)));
            }
        });
    }

    private long copyUri(Uri uri, File target) throws Exception {
        long total = 0;
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(target)) {
            if (in == null) throw new Exception("לא ניתן לקרוא את הקובץ");
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) { out.write(buffer, 0, n); total += n; }
            out.flush();
        }
        return total;
    }

    private String displayName(Uri uri) {
        String name = "קובץ";
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = c.getString(index);
            }
        } catch (Exception ignored) { }
        return name == null ? "קובץ" : name;
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == PERMISSION_MIC) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) startRecording();
            else toast("נדרשת הרשאת מיקרופון להקלטה");
        }
    }

    private View noteRow(Note note) {
        LinearLayout box = card();
        box.addView(text(note.title.isEmpty() ? "פתק ללא כותרת" : note.title, 15, INK, true));
        if (!note.body.trim().isEmpty()) {
            TextView body = text(note.body, 12, MUTED, false);
            body.setMaxLines(2);
            body.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
            p.topMargin = dp(5);
            box.addView(body, p);
        }
        LinearLayout footer = row();
        footer.addView(text(date(note.updatedAt), 10, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        if (exists(note.audioPath)) {
            TextView play = pill("▶ הקלטה", Color.rgb(239, 237, 255), PURPLE);
            play.setOnClickListener(v -> playAudio(note.audioPath));
            footer.addView(play);
        }
        TextView edit = pill("עריכה", Color.rgb(239, 237, 255), PURPLE);
        edit.setOnClickListener(v -> editNote(note));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-2, dp(32));
        ep.leftMargin = dp(6);
        footer.addView(edit, ep);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
        fp.topMargin = dp(9);
        box.addView(footer, fp);
        return box;
    }

    private View taskRow(TaskItem task) {
        LinearLayout box = card();
        LinearLayout line = row();
        CheckBox check = new CheckBox(this);
        check.setButtonTintList(android.content.res.ColorStateList.valueOf(task.done ? MINT : PURPLE));
        check.setChecked(task.done);
        line.addView(check, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout info = column();
        TextView title = text(task.title, 14, task.done ? MUTED : INK, true);
        if (task.done) title.setPaintFlags(title.getPaintFlags() | 16);
        info.addView(title);
        if (!task.detail.isEmpty()) info.addView(text(task.detail, 11, MUTED, false));
        if (!task.dueDate.isEmpty()) info.addView(text("יעד: " + task.dueDate, 10, MINT, true));
        line.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        TextView remove = pill("✕", Color.rgb(255, 239, 240), Color.rgb(180, 62, 75));
        remove.setOnClickListener(v -> new AlertDialog.Builder(this).setMessage("למחוק את המשימה?")
                .setNegativeButton("ביטול", null).setPositiveButton("מחיקה", (d, w) -> {
                    db.deleteTask(task.id); showScreen(currentTab);
                }).show());
        line.addView(remove);
        box.addView(line);
        check.setOnCheckedChangeListener((button, checked) -> {
            db.setTaskDone(task.id, checked);
            showScreen(currentTab);
        });
        return box;
    }

    private View vaultRow(VaultItem item) {
        LinearLayout box = card();
        LinearLayout line = row();
        String symbol = item.mimeType.startsWith("image/") ? "▧" : item.mimeType.contains("pdf") ? "PDF" : "DOC";
        TextView badge = text(symbol, 13, PURPLE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(round(Color.rgb(239, 237, 255), 11));
        line.addView(badge, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout info = column();
        info.setPadding(dp(10), 0, 0, 0);
        TextView name = text(item.name, 13, INK, true);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        info.addView(name);
        info.addView(text(formatBytes(item.size) + "  ·  " + date(item.createdAt), 10, MUTED, false));
        line.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        TextView remove = pill("מחיקה", Color.rgb(255, 239, 240), Color.rgb(180, 62, 75));
        remove.setOnClickListener(v -> new AlertDialog.Builder(this).setMessage("למחוק את העותק המקומי מהכספת?")
                .setNegativeButton("ביטול", null).setPositiveButton("מחיקה", (d, w) -> {
                    try { new File(item.path).delete(); } catch (Exception ignored) { }
                    db.deleteVaultItem(item.id); showScreen("vault");
                }).show());
        line.addView(remove);
        box.addView(line);
        return box;
    }

    private View chatBubble(String value, boolean user) {
        LinearLayout wrap = column();
        wrap.setGravity(user ? Gravity.RIGHT : Gravity.LEFT);
        TextView bubble = text(value, 13, user ? Color.WHITE : INK, false);
        bubble.setPadding(dp(13), dp(10), dp(13), dp(10));
        bubble.setBackground(round(user ? PURPLE : Color.WHITE, 15, user ? PURPLE : LINE, 1));
        bubble.setMaxWidth(getResources().getDisplayMetrics().widthPixels - dp(80));
        wrap.addView(bubble);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(7);
        wrap.setLayoutParams(p);
        return wrap;
    }

    private View bottomNav() {
        LinearLayout nav = row();
        nav.setGravity(Gravity.CENTER);
        nav.setBackground(round(Color.WHITE, 18, LINE, 1));
        String[][] items = {
                {"home", "⌂", "בית"}, {"notes", "✎", "פתקים"}, {"tasks", "✓", "משימות"},
                {"vault", "▧", "כספת"}, {"ai", "✦", "AI"}
        };
        for (String[] item : items) {
            boolean active = currentTab.equals(item[0]);
            LinearLayout cell = column();
            cell.setGravity(Gravity.CENTER);
            TextView icon = text(item[1], 19, active ? PURPLE : MUTED, true);
            icon.setGravity(Gravity.CENTER);
            TextView title = text(item[2], 9, active ? PURPLE : MUTED, active);
            title.setGravity(Gravity.CENTER);
            cell.addView(icon);
            cell.addView(title);
            cell.setOnClickListener(v -> showScreen(item[0]));
            nav.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
        }
        return nav;
    }

    private View statCard(String title, String value, String icon, int tint) {
        LinearLayout box = card();
        box.addView(text(icon, 15, tint, true));
        box.addView(text(value, 22, INK, true));
        box.addView(text(title, 10, MUTED, false));
        return box;
    }

    private View shortcut(String icon, String label, Runnable action) {
        LinearLayout box = column();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(5), dp(12), dp(5), dp(12));
        box.setBackground(round(Color.WHITE, 15, LINE, 1));
        TextView i = text(icon, 21, PURPLE, true); i.setGravity(Gravity.CENTER); box.addView(i);
        TextView t = text(label, 10, INK, true); t.setGravity(Gravity.CENTER); box.addView(t);
        box.setOnClickListener(v -> action.run());
        return box;
    }

    private View sectionTitle(String title, String subtitle) {
        LinearLayout box = column();
        box.addView(text(title, 16, INK, true));
        box.addView(text(subtitle, 11, MUTED, false));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(9);
        box.setLayoutParams(p);
        return box;
    }

    private View infoCard(String title, String body) {
        LinearLayout box = card();
        box.setBackground(round(Color.rgb(241, 240, 255), 15));
        box.addView(text(title, 13, PURPLE, true));
        TextView content = text(body, 11, MUTED, false);
        content.setPadding(0, dp(5), 0, 0);
        box.addView(content);
        return box;
    }

    private View emptyCard(String title, String body) {
        LinearLayout box = card();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(22), dp(23), dp(22), dp(23));
        TextView h = text(title, 15, INK, true); h.setGravity(Gravity.CENTER); box.addView(h);
        TextView b = text(body, 11, MUTED, false); b.setGravity(Gravity.CENTER); b.setPadding(0, dp(5), 0, 0); box.addView(b);
        return box;
    }

    private EditText searchField(String hint) {
        EditText e = new EditText(this);
        e.setSingleLine(true); e.setTextSize(13); e.setTextColor(INK); e.setHintTextColor(MUTED);
        e.setHint("⌕  " + hint); e.setPadding(dp(12), 0, dp(12), 0);
        e.setBackground(round(Color.WHITE, 13, LINE, 1));
        return e;
    }

    private EditText field(String hint, String value, boolean multi) {
        EditText e = new EditText(this);
        e.setTextSize(14); e.setTextColor(INK); e.setHintTextColor(MUTED);
        e.setHint(hint); e.setText(value); e.setPadding(dp(12), dp(9), dp(12), dp(9));
        e.setBackground(round(Color.WHITE, 12, LINE, 1));
        if (multi) { e.setGravity(Gravity.TOP | Gravity.RIGHT); e.setMinLines(5); } else e.setSingleLine(true);
        return e;
    }

    private LinearLayout card() {
        LinearLayout box = column();
        box.setPadding(dp(13), dp(12), dp(13), dp(12));
        box.setBackground(round(Color.WHITE, 15, LINE, 1));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(9);
        box.setLayoutParams(p);
        return box;
    }

    private LinearLayout row() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        return box;
    }

    private LinearLayout column() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        return box;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL); t.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private TextView button(String value, int background, int foreground) {
        TextView t = text(value, 12, foreground, true);
        t.setGravity(Gravity.CENTER); t.setPadding(dp(14), dp(8), dp(14), dp(8));
        t.setBackground(round(background, 11));
        return t;
    }

    private TextView pill(String value, int background, int foreground) {
        TextView t = text(value, 10, foreground, true);
        t.setGravity(Gravity.CENTER); t.setPadding(dp(9), dp(5), dp(9), dp(5));
        t.setBackground(round(background, 22));
        return t;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }

    private GradientDrawable round(int color, int radius, int stroke, int width) {
        GradientDrawable d = round(color, radius); d.setStroke(dp(width), stroke); return d;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.leftMargin = dp(4); p.rightMargin = dp(4); return p;
    }

    private void gap(LinearLayout parent, int height) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }

    private TextWatcher watcher(Runnable runnable) {
        return new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { runnable.run(); }
            public void afterTextChanged(Editable e) { }
        };
    }

    private int openTaskCount() {
        int count = 0;
        for (TaskItem task : db.allTasks()) if (!task.done) count++;
        return count;
    }

    private boolean exists(String path) { return path != null && new File(path).exists(); }

    private String date(long millis) {
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, new Locale("he", "IL")).format(new Date(millis));
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private String errorText(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override public void onBackPressed() {
        if (!"home".equals(currentTab)) showScreen("home");
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (recording) {
            try { if (recorder != null) recorder.stop(); } catch (Exception ignored) { }
            releaseRecorder();
        }
        if (player != null) {
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
        aiExecutor.shutdownNow();
        super.onDestroy();
    }
}

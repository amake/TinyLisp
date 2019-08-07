package org.tinylisp.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ShareCompat;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.tinylisp.engine.Engine;
import org.tinylisp.formatter.Formatter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import difflib.Chunk;
import difflib.Delta;
import difflib.DiffUtils;

public class ReplActivity extends AppCompatActivity implements TextView.OnEditorActionListener, View.OnClickListener, View.OnKeyListener, TextWatcher {

    private static final String TAG = "Repl";

    protected Engine mEngine;
    protected Engine.TLEnvironment mEnv;

    protected ScrollView mScrollView;
    protected TextView mOutput;
    protected EditText mInput;
    protected ImageButton mTabButton;
    protected ProgressBar mProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_repl);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mScrollView = findViewById(R.id.scrollview);

        mOutput = findViewById(R.id.output);

        mInput = findViewById(R.id.input);
        mInput.setOnEditorActionListener(this);
        mInput.setOnKeyListener(this);
        mInput.addTextChangedListener(this);

        mTabButton = findViewById(R.id.tab_button);
        mTabButton.setOnClickListener(this);

        mProgress = findViewById(R.id.progress);

        mEngine = new Engine();

        try {
            restoreHistory();
        } catch (Exception ex) {
            Log.d(TAG, "Error restoring history", ex);
        }

        initRepl();
    }

    /* REPL manipulation methods */

    @CallSuper
    protected void initRepl() {
        clear();
        initEnvironment();
        print("TinyLisp ", Engine.VERSION, "\n");
        mSessionStart = mHistory.size();
    }

    @CallSuper
    protected void initEnvironment() {
        mEnv = Engine.defaultEnvironment();
        mEnv.put(Engine.TLSymbolExpression.of("clear"), new Engine.TLFunction() {
            @Override
            public Engine.TLExpression invoke(Engine.TLListExpression args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        clear();
                    }
                });
                return null;
            }
        });
        mEnv.put(Engine.TLSymbolExpression.of("reset"), new Engine.TLFunction() {
            @Override
            public Engine.TLExpression invoke(Engine.TLListExpression args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        initRepl();
                    }
                });
                return null;
            }
        });
        mEnv.put(Engine.TLSymbolExpression.of("share"), new Engine.TLFunction() {
            @Override
            public Engine.TLExpression invoke(Engine.TLListExpression args) {
                Engine.TLExpression toShare = args.size() == 1 ? args.get(0) : args;
                sharePlainText(toShare.toString());
                return null;
            }
        });
        mEnv.put(Engine.TLSymbolExpression.of("history"), new Engine.TLFunction() {
            @Override
            public Engine.TLExpression invoke(Engine.TLListExpression args) {
                if (args.isEmpty()) {
                    return Engine.TLListExpression.of(mHistory);
                } else {
                    int index = ((Integer) args.get(0).getValue() + mHistory.size()) % mHistory.size();
                    return Engine.expressionOf(mHistory.get(index));
                }
            }
        });
        mEnv.put(Engine.TLSymbolExpression.of("session"), new Engine.TLFunction() {
            @Override
            public Engine.TLExpression invoke(Engine.TLListExpression args) {
                List<String> session = mHistory.subList(mSessionStart, mHistory.size());
                return Engine.TLListExpression.of(session);
            }
        });
    }

    protected void print(String... strings) {
        for (String string : strings) {
            mOutput.append(string);
        }
        mScrollView.post(new Runnable() {
            @Override public void run() {
                mScrollView.fullScroll(View.FOCUS_DOWN);
                mInput.requestFocus();
            }
        });
    }

    protected void clear() {
        mOutput.setText("");
    }

    protected void onCompletionTriggered() {
        int caret = mInput.getSelectionEnd();
        Editable input = mInput.getText();
        String before = input.subSequence(0, caret).toString();
        String after = input.subSequence(caret, input.length()).toString();
        String completion = complete(before);
        Log.d(TAG, "onCompletionTriggered: before=" + before + "; after=" + after + "; completion='" + completion + "'");
        if (completion != null) {
            mProgrammaticEditInProgress = true;
            mInput.setText(completion);
            mInput.append(after);
            mInput.setSelection(completion.length());
            mProgrammaticEditInProgress = false;
            mInput.requestFocus();
        }
    }

    protected String complete(String input) {
        if (input.isEmpty() || Character.isWhitespace(input.charAt(input.length() - 1))) {
            return null;
        }
        List<String> tokens = mEngine.tokenize(input);
        if (tokens.isEmpty()) {
            return null;
        }
        String stem = tokens.get(tokens.size() - 1);
        String leading = input.substring(0, input.length() - stem.length());
        List<String> candidates = mEnv.complete(stem);
        if (candidates.isEmpty()) {
            return null;
        } else if (candidates.size() == 1) {
            return leading + candidates.get(0) + " ";
        } else {
            printCompletionHelp(stem, candidates);
            String commonPrefix = StringUtils.getCommonPrefix(candidates.toArray(new String[0]));
            return commonPrefix.isEmpty() ? null : leading + commonPrefix;
        }
    }

    protected void printCompletionHelp(String stem, List<String> candidates) {
        if (stem.isEmpty()) {
            print("All symbols:\n");
        } else {
            print("Symbols starting with ", stem, ":\n");
        }
        for (String candidate : candidates) {
            print("    ", candidate, "\n");
        }
    }

    private ExecuteAsync mExecution;

    protected void executeAsync(String input) {
        // echo
        print("\n> ", input.replace("\n", "\n  "), "\n");
        ExecuteAsync prev = mExecution;
        if (prev != null) {
            prev.cancel(true);
        }
        mExecution = new ExecuteAsync(new WeakReference<>(this), mEngine, mEnv);
        mExecution.execute(input);
    }

    private static class ExecuteAsync extends AsyncTask<String, Void, Engine.TLExpression> {
        private final WeakReference<ReplActivity> mActivity;
        private final Engine mEngine;
        private final Engine.TLEnvironment mEnv;
        private Exception mError;

        ExecuteAsync(WeakReference<ReplActivity> weakActivity, Engine engine, Engine.TLEnvironment env) {
            mActivity = weakActivity;
            mEngine = engine;
            mEnv = env;
        }

        @Override protected void onPreExecute() {
            ReplActivity activity = mActivity.get();
            if (activity != null) {
                activity.mInput.setEnabled(false);
                activity.mProgress.setVisibility(View.VISIBLE);
            }
        }

        @Override protected Engine.TLExpression doInBackground(String... strings) {
            try {
                long start = System.currentTimeMillis();
                Engine.TLExpression result = mEngine.execute(strings[0], mEnv);
                long end = System.currentTimeMillis();
                Log.d(TAG, "Execution took " + (end - start) + "ms");
                return result;
            } catch (Exception e) {
                mError = e;
            }
            return null;
        }

        @Override protected void onPostExecute(Engine.TLExpression result) {
            if (isCancelled()) {
                return;
            }
            ReplActivity activity = mActivity.get();
            if (activity != null) {
                if (mError == null) {
                    activity.onExecutionSucceeded(result);
                } else {
                    activity.printException(mError);
                }
                activity.mProgress.setVisibility(View.GONE);
                activity.mInput.setEnabled(true);
            }
        }
    }

    protected void onExecutionSucceeded(Engine.TLExpression result) {
        mExecution = null;
        mEnv.put(Engine.TLSymbolExpression.of("_"), result);
        print(result == null ? "" : result.toString(), "\n");
    }

    protected void printException(Exception ex) {
        print(findInterestingCause(ex).toString(), "\n");
        ex.printStackTrace();
    }

    protected Throwable findInterestingCause(Throwable throwable) {
        while (true) {
            if (throwable instanceof Engine.TLRuntimeException) {
                return throwable;
            }
            Throwable cause = throwable.getCause();
            if (cause == null) {
                return throwable;
            } else {
                throwable = cause;
            }
        }
    }

    /* REPL history */

    private static final String HISTORY_KEY = "historyKey";
    private List<String> mHistory = new ArrayList<>();
    private Integer mHistoryIndex;
    private Integer mSessionStart;

    private void appendHistory(String item) {
        mHistory.add(item);
        saveHistory();
    }

    private void saveHistory() {
        SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(HISTORY_KEY, new JSONArray(mHistory).toString());
        editor.apply();
    }

    private void restoreHistory() throws JSONException {
        mHistory.clear();
        SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
        String json = preferences.getString(HISTORY_KEY, null);
        if (json != null) {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                mHistory.add(array.getString(i));
            }
        }
    }

    private boolean setPreviousHistory() {
        if (mHistoryIndex == null) {
            mHistoryIndex = mHistory.size();
        }
        mHistoryIndex = Math.max(mHistoryIndex - 1, 0);
        if (mHistoryIndex >= 0 && mHistoryIndex < mHistory.size()) {
            String replacement = mHistory.get(mHistoryIndex);
            mInput.setText(replacement);
            mInput.setSelection(mInput.length());
            return true;
        }
        return false;
    }

    private boolean setNextHistory() {
        if (mHistoryIndex == null) {
            mHistoryIndex = mHistory.size();
        }
        mHistoryIndex = Math.min(mHistoryIndex + 1, mHistory.size());
        if (mHistoryIndex == mHistory.size()) {
            mInput.setText(null);
            return true;
        } else if (mHistoryIndex >= 0 && mHistoryIndex < mHistory.size()) {
            String replacement = mHistory.get(mHistoryIndex);
            mInput.setText(replacement);
            mInput.setSelection(mInput.length());
            return true;
        }
        return false;
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_repl, menu);
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_share) {
            shareConsoleLog();
            return true;
        } else if (item.getItemId() == R.id.action_clear) {
            clear();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void shareConsoleLog() {
        sharePlainText(mOutput.getText().toString());
    }

    private void sharePlainText(String text) {
        try {
            ShareCompat.IntentBuilder.from(this)
                .setText(text)
                .setType("text/plain")
                .startChooser();
            return;
        } catch (Exception ex) {
            // Fails with TransactionTooLargeException when content too big
            Log.d(TAG, "Sharing as plain text failed", ex);
        }
        try {
            File file = saveTextToFile(text);
            Log.d(TAG, "Saved text to file: " + file);
            Uri uri = ReplFileProvider.getUriForFile(this, file);
            ShareCompat.IntentBuilder.from(this)
                .setStream(uri)
                .setType("text/plain")
                .startChooser();
        } catch (IOException ex) {
            Log.e(TAG, "Failed to save text to file", ex);
        }
    }

    private File saveTextToFile(String text) throws IOException {
        File temp = File.createTempFile(getApplication().getPackageName(), ".log", getCacheDir());
        FileOutputStream out = new FileOutputStream(temp);
        try {
            out.write(text.getBytes("utf-8"));
            return temp;
        } finally {
            out.close();
        }
    }

    /* TextView.OnEditorActionListener */

    @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        Log.d(TAG, "Input: onEditorAction; actionId=" + actionId + "; event=" + event);
        if (actionId == EditorInfo.IME_NULL) {
            if (v.length() > 0) {
                String input = v.getText().toString().trim();
                appendHistory(input);
                mHistoryIndex = null;
                executeAsync(input);
                v.setText("");
            }
            // Always consume so as to prevent inputting raw \n
            return true;
        }
        return false;
    }

    /* View.OnClickListener */

    @Override public void onClick(View v) {
        if (v == mTabButton) {
            onCompletionTriggered();
        }
    }

    /* View.OnKeyListener */

    @Override public boolean onKey(View v, int keyCode, KeyEvent event) {
        Log.d(TAG, "Input: onKey; keyCode=" + keyCode + "; event=" + event);
        if (event.getAction() == KeyEvent.ACTION_UP) {
            switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (setPreviousHistory()) {
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (setNextHistory()) {
                    return true;
                }
                break;
            default:
                return false;
            }
        } else if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (keyCode) {
            case KeyEvent.KEYCODE_TAB:
                onCompletionTriggered();
                return true;
            default:
                return false;
            }
        }
        return false;
    }

    /* TextWatcher */

    @Override public void beforeTextChanged(CharSequence s, final int start, int count, int after) {
        Log.d(TAG, "Input: beforeTextChanged; s=" + s + "; start=" + start + ", count=" + count + ", after=" + after);
        if (mProgrammaticEditInProgress) {
            return;
        }
        if (after == 0 && count == 1 && start + count < s.length()) {
            // Delete
            String deleted = s.subSequence(start, start + count).toString();
            int nextStart = start + count;
            int nextEnd = nextStart + 1;
            String next = s.subSequence(nextStart, nextEnd).toString();
            if ("(".equals(deleted) && ")".equals(next)
                    || "[".equals(deleted) && "]".equals(next)
                    || "\"".equals(deleted) && "\"".equals(next)) {
                Editable content = (Editable) s;
                content.setSpan(new ToDelete(), nextStart, nextEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }
    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
        Log.d(TAG, "Input: onTextChanged; s=" + s + "; start=" + start + ", before=" + before + ", count=" + count);
        if (mProgrammaticEditInProgress) {
            return;
        }
        if (before == 0 && count == 1) {
            // Insert
            int end = start + count;
            String inserted = s.subSequence(start, end).toString();
            final String next;
            if (end + 1 <= s.length()) {
                next = s.subSequence(end, end + 1).toString();
            } else {
                next = null;
            }
            if (inserted.equals(next) &&
                    (next.equals(")") || next.equals("]") || next.equals("\""))) {
                // Skip already-present closing char
                deleteAtIndex(end, end + next.length());
            } else if ("(".equals(inserted)) {
                insertAfterCaret(")");
            } else if ("[".equals(inserted)) {
                insertAfterCaret("]");
            } else if ("\"".equals(inserted)) {
                insertAfterCaret("\"");
            }
        }
        deletePending();
        if (s.length() > 0) {
            formatInput(s.toString());
            colorParens((Editable) s);
        }
    }
    @Override public void afterTextChanged(Editable s) {
        Log.d(TAG, "Input: afterTextChanged; s=" + s);
    }

    private final Formatter mFormatter = new Formatter();
    private boolean mProgrammaticEditInProgress;

    private void insertAfterCaret(String string) {
        if (mProgrammaticEditInProgress) {
            return;
        }
        int caret = mInput.getSelectionEnd();
        Editable content = mInput.getText();
        mProgrammaticEditInProgress = true;
        content.insert(caret, string);
        mProgrammaticEditInProgress = false;
        mInput.setSelection(caret);
    }

    private void deleteAtIndex(int start, int end) {
        if (mProgrammaticEditInProgress) {
            return;
        }
        Editable content = mInput.getText();
        if (start < end && start >= 0 && end <= content.length()) {
            mProgrammaticEditInProgress = true;
            content.delete(start, end);
            mProgrammaticEditInProgress = false;
            mInput.setSelection(start);
        }
    }

    private static class ToDelete extends CharacterStyle {
        @Override public void updateDrawState(TextPaint tp) {
        }
    }

    private void deletePending() {
        if (mProgrammaticEditInProgress) {
            return;
        }
        Editable content = mInput.getText();
        mProgrammaticEditInProgress = true;
        for (ToDelete span : content.getSpans(0, content.length(), ToDelete.class)) {
            int start = content.getSpanStart(span);
            int end = content.getSpanEnd(span);
            content.delete(start, end);
        }
        mProgrammaticEditInProgress = false;
    }

    /* Input autoformatting */

    private static final Comparator<Delta<?>> DELTA_COMPARATOR = new Comparator<Delta<?>>() {
        @Override
        public int compare(Delta<?> d1, Delta<?> d2) {
            int pos1 = d1.getOriginal().getPosition();
            int pos2 = d2.getOriginal().getPosition();
            // Smaller position is sorted last so offsets remain correct.
            // Can't use Integer.compare() at this Android API level.
            return pos1 == pos2 ? 0 : pos1 < pos2 ? 1 : -1;
        }
    };

    private void formatInput(String input) {
        String formatted = mFormatter.format(input);
        if (!input.equals(formatted)) {
            Log.d(TAG, "Formatted target: " + formatted);
            Editable content = mInput.getText();
            List<String> original = splitChars(input);
            List<String> revised = splitChars(formatted);
            List<Delta<String>> deltas = DiffUtils.diff(original, revised).getDeltas();
            Collections.sort(deltas, DELTA_COMPARATOR);
            Log.d(TAG, "formatInput: " + deltas.size() + " delta(s)");
            mProgrammaticEditInProgress = true;
            for (Delta<String> delta : deltas) {
                // Formatting only makes whitespace changes for now,
                // so should be only DELETE or INSERT
                switch (delta.getType()) {
                    case DELETE: {
                        Chunk<String> chunk = delta.getOriginal();
                        int position = chunk.getPosition();
                        content.delete(position, position + chunk.size());
                        break;
                    }
                    case INSERT:
                        int cursor = mInput.getSelectionEnd();
                        int position = delta.getOriginal().getPosition();
                        List<String> lines = delta.getRevised().getLines();
                        for (int i = 0; i < lines.size(); i++) {
                            content.insert(position + i, lines.get(i));
                        }
                        if (cursor == position) {
                            // Restore cursor if it happened to be at the insert position,
                            // to prevent it from getting moved forward unintentionally.
                            mInput.setSelection(cursor);
                        }
                        break;
                }
            }
            mProgrammaticEditInProgress = false;
        }
    }

    private static List<String> splitChars(String str) {
        List<String> result = new ArrayList<>(str.length());
        for (int i = 0; i < str.length(); i++) {
            result.add(String.valueOf(str.charAt(i)));
        }
        return result;
    }

    private static final int[] LEVEL_COLORS;
    static {
        // From rainbow-delimiters.el
        String[] colorStrings = { "#707183", "#7388d6", "#909183", "#709870", "#907373", "#6276ba", "#858580", "#80a880", "#887070" };
        int[] colorInts = new int[colorStrings.length];
        for (int i = 0; i < colorStrings.length; i++) {
            colorInts[i] = Color.parseColor(colorStrings[i]);
        }
        LEVEL_COLORS = colorInts;
    }

    private void colorParens(Editable content) {
        clearLevelColors(content);
        int level = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            switch (c) {
            case '(':
            case '[':
                applyLevelColorAt(content, level++, i, i + 1);
                break;
            case ')':
            case ']':
                applyLevelColorAt(content, --level, i, i + 1);
                break;
            }
        }
    }

    private void clearLevelColors(Editable content) {
        for (ForegroundColorSpan span : content.getSpans(0, content.length(), ForegroundColorSpan.class)) {
            content.removeSpan(span);
        }
    }

    private void applyLevelColorAt(Editable content, int level, int start, int end) {
        Log.d(TAG, "applyLevelColorAt: level=" + level + "; start=" + start + "; end=" + end);
        int color = LEVEL_COLORS[Math.max(0, Math.min(level, LEVEL_COLORS.length - 1))];
        content.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}

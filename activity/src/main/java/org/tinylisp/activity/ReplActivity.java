package org.tinylisp.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ShareCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.tinylisp.engine.Engine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

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

    protected void initRepl() {
        clear();
        initEnvironment();
        print("TinyLisp ", Engine.VERSION, "\n");
        mSessionStart = mHistory.size();
    }

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
        if (completion != null) {
            mInput.setText(completion);
            mInput.append(after);
            mInput.setSelection(completion.length());
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
        print("\n> ", input, "\n");
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
         if (after == 0 && count == 1 && start + count < s.length()) {
            // Delete
            String deleted = s.subSequence(start, start + count).toString();
            final String next = s.subSequence(start + count, start + count + 1).toString();
            if ("(".equals(deleted) && ")".equals(next)
                || "[".equals(deleted) && "]".equals(next)
                || "\"".equals(deleted) && "\"".equals(next)) {
                mInput.post(new Runnable() {
                    @Override public void run() {
                        deleteAtIndex(next, start);
                    }
                });
            }
        }
    }
    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
        Log.d(TAG, "Input: onTextChanged; s=" + s + "; start=" + start + ", before=" + before + ", count=" + count);
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
                deleteAtIndex(next, end);
            } else if ("(".equals(inserted)) {
                insertAfterCaret(")");
            } else if ("[".equals(inserted)) {
                insertAfterCaret("]");
            } else if ("\"".equals(inserted)) {
                insertAfterCaret("\"");
            }
        }
    }
    @Override public void afterTextChanged(Editable s) {
        Log.d(TAG, "Input: afterTextChanged; s=" + s);
    }

    private void insertAfterCaret(String string) {
        int caret = mInput.getSelectionEnd();
        Editable content = mInput.getText();
        String before = content.subSequence(0, caret).toString();
        String after = content.subSequence(caret, content.length()).toString();
        String result = before + string + after;
        mInput.setText(result);
        mInput.setSelection(before.length());
    }

    private void deleteAtIndex(String toDelete, int start) {
        int end = start + toDelete.length();
        Editable content = mInput.getText();
        if (start < content.length() && end <= content.length()) {
            if (content.subSequence(start, end).toString().equals(toDelete)) {
                StringBuilder builder = new StringBuilder(content);
                builder.delete(start, end);
                mInput.setText(builder);
                mInput.setSelection(start);
            }
        }
    }
}

package org.tinylisp.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements TextView.OnEditorActionListener, TinyLispRepl.PrintComponent, View.OnClickListener, View.OnKeyListener, TextWatcher {

    private static final String TAG = "Main";

    private TinyLispRepl mRepl;
    private ScrollView mScrollView;
    private TextView mOutput;
    private EditText mInput;
    private Button mTabButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mScrollView = findViewById(R.id.scrollview);

        mOutput = findViewById(R.id.output);

        mInput = findViewById(R.id.input);
        mInput.setOnEditorActionListener(this);
        mInput.setOnKeyListener(this);
        mInput.addTextChangedListener(this);

        mTabButton = findViewById(R.id.tab_button);
        mTabButton.setOnClickListener(this);

        mRepl = new TinyLispRepl(this);

        try {
            restoreHistory();
        } catch (Exception ex) {
            Log.d(TAG, "Error restoring history", ex);
        }
    }

    @Override protected void onStart() {
        super.onStart();
        mRepl.init();
    }

    /* TextView.OnEditorActionListener */

    @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        Log.d(TAG, "Input: onEditorAction; actionId=" + actionId + "; event=" + event);
        if (actionId == EditorInfo.IME_NULL) {
            if (v.length() > 0) {
                String input = v.getText().toString().trim();
                appendHistory(input);
                index = null;
                mRepl.execute(input);
                v.setText("");
                return true;
            }
        }
        return false;
    }

    /* TinyLispRepl.PrintComponent */

    @Override public void print(String string) {
        mOutput.append(string);
        mScrollView.post(new Runnable() {
            @Override public void run() {
                mScrollView.fullScroll(View.FOCUS_DOWN);
                mInput.requestFocus();
            }
        });
    }

    @Override public void clear() {
        mOutput.setText("");
    }

    /* View.OnClickListener */

    @Override public void onClick(View v) {
        if (v == mTabButton) {
            doCompletion();
        }
    }

    private void doCompletion() {
        int caret = mInput.getSelectionEnd();
        Editable input = mInput.getText();
        String before = input.subSequence(0, caret).toString();
        String after = input.subSequence(caret, input.length()).toString();
        String completion = mRepl.complete(before);
        if (completion != null) {
            mInput.setText(completion);
            mInput.append(after);
            mInput.setSelection(completion.length());
            mInput.requestFocus();
        }
    }

    private static final String HISTORY_KEY = "historyKey";
    private List<String> history = new ArrayList<>();
    private Integer index;

    private void appendHistory(String item) {
        history.add(item);
        saveHistory();
    }

    private void saveHistory() {
        SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(HISTORY_KEY, new JSONArray(history).toString());
        editor.apply();
    }

    private void restoreHistory() throws JSONException {
        history.clear();
        SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
        String json = preferences.getString(HISTORY_KEY, null);
        if (json != null) {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                history.add(array.getString(i));
            }
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
                doCompletion();
                return true;
            default:
                return false;
            }
        }
        return false;
    }

    private boolean setPreviousHistory() {
        if (index == null) {
            index = history.size();
        }
        index = Math.max(index - 1, 0);
        if (index >= 0 && index < history.size()) {
            String replacement = history.get(index);
            mInput.setText(replacement);
            mInput.setSelection(mInput.length());
            return true;
        }
        return false;
    }

    private boolean setNextHistory() {
        if (index == null) {
            index = history.size();
        }
        index = Math.min(index + 1, history.size());
        if (index == history.size()) {
            mInput.setText(null);
            return true;
        } else if (index >= 0 && index < history.size()) {
            String replacement = history.get(index);
            mInput.setText(replacement);
            mInput.setSelection(mInput.length());
            return true;
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

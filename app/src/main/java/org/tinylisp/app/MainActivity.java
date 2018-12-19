package org.tinylisp.app;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements TextView.OnEditorActionListener, TinyLispRepl.PrintComponent, View.OnClickListener, View.OnKeyListener {

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

        mTabButton = findViewById(R.id.tab_button);
        mTabButton.setOnClickListener(this);

        mRepl = new TinyLispRepl(this);
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
                history.add(input);
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
        String input = mInput.getText().toString();
        String completion = mRepl.complete(input);
        if (completion != null) {
            mInput.setText(completion);
            mInput.setSelection(mInput.length());
            mInput.requestFocus();
        }
    }

    private List<String> history = new ArrayList<>();
    private Integer index;

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
        if (index >= 0 && index < history.size()) {
            String replacement = history.get(index);
            mInput.setText(replacement);
            mInput.setSelection(mInput.length());
            return true;
        }
        return false;
    }
}

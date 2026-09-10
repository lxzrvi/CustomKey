package com.customkey;

import android.inputmethodservice.InputMethodService;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import java.util.Locale;

/** The IME. All rendering and gesture handling lives in {@link KeyboardView}. */
public class CustomKeyService extends InputMethodService {

    private KeyboardView keyboard;

    @Override
    public void onCreate() {
        Prefs.init(this);
        super.onCreate();
    }

    @Override
    public View onCreateInputView() {
        Prefs.init(this);

        keyboard = new KeyboardView(this);
        keyboard.setMode(KeyboardView.MODE_LIVE);
        keyboard.setListener(new KeyboardView.Adapter() {
            @Override
            public void onKeyPress(KeyModel key) {
                handleKey(key);
            }

            @Override
            public void onImeSwitchRequested() {
                switchKeyboard();
            }
        });
        return keyboard;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (keyboard != null) {
            keyboard.reload();
        }
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        if (keyboard != null) {
            keyboard.clearShift();
        }
        KeyPopup.hide();
    }

    @Override
    public void onDestroy() {
        KeyPopup.hide();
        super.onDestroy();
    }

    /* ================================================================== */

    private void handleKey(KeyModel key) {
        InputConnection input = getCurrentInputConnection();
        if (input == null) {
            return;
        }

        switch (key.type) {

            case KeyModel.TEXT: {
                String output = key.output == null ? key.label : key.output;
                if (output == null) {
                    return;
                }
                if (keyboard != null && keyboard.isShifted()) {
                    output = output.toUpperCase(Locale.ROOT);
                }
                input.commitText(output, 1);
                break;
            }

            case KeyModel.SPACE:
                input.commitText(" ", 1);
                break;

            case KeyModel.BACKSPACE:
                deleteBack(input);
                break;

            case KeyModel.ENTER:
                sendEnter(input);
                break;

            case KeyModel.CURSOR_LEFT:
                sendKey(input, KeyEvent.KEYCODE_DPAD_LEFT);
                break;

            case KeyModel.CURSOR_RIGHT:
                sendKey(input, KeyEvent.KEYCODE_DPAD_RIGHT);
                break;

            case KeyModel.SELECT_ALL:
                input.performContextMenuAction(android.R.id.selectAll);
                break;

            case KeyModel.COPY:
                input.performContextMenuAction(android.R.id.copy);
                break;

            case KeyModel.CUT:
                input.performContextMenuAction(android.R.id.cut);
                break;

            case KeyModel.PASTE:
                input.performContextMenuAction(android.R.id.paste);
                break;

            case KeyModel.HIDE:
                requestHideSelf(0);
                break;

            default:
                break;
        }
    }

    /** Deletes a whole code point, so emoji do not get chopped in half. */
    private void deleteBack(InputConnection input) {
        CharSequence before = input.getTextBeforeCursor(2, 0);
        if (before == null || before.length() == 0) {
            sendKey(input, KeyEvent.KEYCODE_DEL);
            return;
        }
        try {
            input.deleteSurroundingTextInCodePoints(1, 0);
        } catch (Throwable t) {
            input.deleteSurroundingText(1, 0);
        }
    }

    /** Honours the field's action (Search / Go / Done / Next) when it has one. */
    private void sendEnter(InputConnection input) {
        EditorInfo info = getCurrentInputEditorInfo();
        if (info != null) {
            int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
            if (action != EditorInfo.IME_ACTION_UNSPECIFIED
                    && action != EditorInfo.IME_ACTION_NONE
                    && input.performEditorAction(action)) {
                return;
            }
            if ((info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
                    && (info.inputType & EditorInfo.TYPE_MASK_CLASS)
                    == EditorInfo.TYPE_CLASS_TEXT
                    && (info.inputType & EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) == 0) {
                sendKey(input, KeyEvent.KEYCODE_ENTER);
                return;
            }
        }
        sendKey(input, KeyEvent.KEYCODE_ENTER);
    }

    private void sendKey(InputConnection input, int keyCode) {
        long now = android.os.SystemClock.uptimeMillis();
        input.sendKeyEvent(new KeyEvent(now, now,
                KeyEvent.ACTION_DOWN, keyCode, 0));
        input.sendKeyEvent(new KeyEvent(now, now,
                KeyEvent.ACTION_UP, keyCode, 0));
    }

    private void switchKeyboard() {
        try {
            if (shouldOfferSwitchingToNextInputMethod()) {
                switchToNextInputMethod(false);
                return;
            }
        } catch (Throwable ignored) {
        }
        InputMethodManager manager = (InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.showInputMethodPicker();
        }
    }
}

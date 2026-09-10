package com.customkey;

import android.inputmethodservice.InputMethodService;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.Locale;

public class CustomKeyService
        extends InputMethodService {

    private CustomKeyboardView keyboard;

    @Override
    public View onCreateInputView() {

        keyboard =
                new CustomKeyboardView(this);

        keyboard.setListener(
                this::handleKey
        );

        return keyboard;
    }

    @Override
    public void onStartInputView(
            EditorInfo info,
            boolean restarting
    ) {
        super.onStartInputView(
                info,
                restarting
        );

        if (keyboard != null) {
            keyboard.reloadSettings();
        }
    }

    private void handleKey(KeyModel key) {

        InputConnection input =
                getCurrentInputConnection();

        if (input == null) {
            return;
        }

        switch (key.type) {

            case KeyModel.TEXT:

                String output = key.output;

                if (keyboard.isShifted()) {
                    output =
                            output.toUpperCase(
                                    Locale.getDefault()
                            );
                }

                input.commitText(output, 1);

                if (keyboard.isShifted()) {
                    keyboard.clearShift();
                }

                break;


            case KeyModel.SPACE:

                input.commitText(" ", 1);
                break;


            case KeyModel.BACKSPACE:

                input.deleteSurroundingText(
                        1,
                        0
                );

                break;


            case KeyModel.ENTER:

                input.sendKeyEvent(
                        new KeyEvent(
                                KeyEvent.ACTION_DOWN,
                                KeyEvent.KEYCODE_ENTER
                        )
                );

                input.sendKeyEvent(
                        new KeyEvent(
                                KeyEvent.ACTION_UP,
                                KeyEvent.KEYCODE_ENTER
                        )
                );

                break;


            case KeyModel.SHIFT:

                keyboard.toggleShift();
                break;


            case KeyModel.SYMBOLS:

                keyboard.toggleSymbols();
                break;


            case KeyModel.CURSOR_LEFT:

                sendArrow(
                        input,
                        KeyEvent.KEYCODE_DPAD_LEFT
                );

                break;


            case KeyModel.CURSOR_RIGHT:

                sendArrow(
                        input,
                        KeyEvent.KEYCODE_DPAD_RIGHT
                );

                break;


            case KeyModel.SELECT_ALL:

                input.performContextMenuAction(
                        android.R.id.selectAll
                );

                break;


            case KeyModel.COPY:

                input.performContextMenuAction(
                        android.R.id.copy
                );

                break;


            case KeyModel.CUT:

                input.performContextMenuAction(
                        android.R.id.cut
                );

                break;


            case KeyModel.PASTE:

                input.performContextMenuAction(
                        android.R.id.paste
                );

                break;
        }
    }

    private void sendArrow(
            InputConnection input,
            int keyCode
    ) {

        input.sendKeyEvent(
                new KeyEvent(
                        KeyEvent.ACTION_DOWN,
                        keyCode
                )
        );

        input.sendKeyEvent(
                new KeyEvent(
                        KeyEvent.ACTION_UP,
                        keyCode
                )
        );
    }
}

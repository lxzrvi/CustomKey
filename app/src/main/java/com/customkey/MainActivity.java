package com.customkey;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView heightText;
    private TextView spacingText;
    private TextView radiusText;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_main
        );

        Button enable =
                findViewById(
                        R.id.enableKeyboard
                );

        Button select =
                findViewById(
                        R.id.selectKeyboard
                );

        SeekBar height =
                findViewById(
                        R.id.keyHeight
                );

        SeekBar spacing =
                findViewById(
                        R.id.keySpacing
                );

        SeekBar radius =
                findViewById(
                        R.id.keyRadius
                );

        heightText =
                findViewById(
                        R.id.keyHeightText
                );

        spacingText =
                findViewById(
                        R.id.keySpacingText
                );

        radiusText =
                findViewById(
                        R.id.keyRadiusText
                );


        enable.setOnClickListener(v ->

                startActivity(
                        new Intent(
                                Settings.ACTION_INPUT_METHOD_SETTINGS
                        )
                )
        );


        select.setOnClickListener(v -> {

            InputMethodManager manager =
                    (InputMethodManager)
                            getSystemService(
                                    INPUT_METHOD_SERVICE
                            );

            if (manager != null) {
                manager.showInputMethodPicker();
            }
        });


        int savedHeight =
                AppSettings.getKeyHeight(this);

        int savedSpacing =
                AppSettings.getSpacing(this);

        int savedRadius =
                AppSettings.getRadius(this);


        /*
         * SeekBar 0 = 30dp
         * SeekBar 70 = 100dp
         */

        height.setProgress(
                Math.max(
                        0,
                        savedHeight - 30
                )
        );

        spacing.setProgress(
                savedSpacing
        );

        radius.setProgress(
                savedRadius
        );

        updateLabels(
                savedHeight,
                savedSpacing,
                savedRadius
        );


        height.setOnSeekBarChangeListener(
                new SeekListener() {

                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser
                    ) {

                        int value =
                                progress + 30;

                        AppSettings.setKeyHeight(
                                MainActivity.this,
                                value
                        );

                        updateLabels(
                                value,
                                AppSettings.getSpacing(
                                        MainActivity.this
                                ),
                                AppSettings.getRadius(
                                        MainActivity.this
                                )
                        );
                    }
                }
        );


        spacing.setOnSeekBarChangeListener(
                new SeekListener() {

                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser
                    ) {

                        AppSettings.setSpacing(
                                MainActivity.this,
                                progress
                        );

                        updateLabels(
                                AppSettings.getKeyHeight(
                                        MainActivity.this
                                ),
                                progress,
                                AppSettings.getRadius(
                                        MainActivity.this
                                )
                        );
                    }
                }
        );


        radius.setOnSeekBarChangeListener(
                new SeekListener() {

                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser
                    ) {

                        AppSettings.setRadius(
                                MainActivity.this,
                                progress
                        );

                        updateLabels(
                                AppSettings.getKeyHeight(
                                        MainActivity.this
                                ),
                                AppSettings.getSpacing(
                                        MainActivity.this
                                ),
                                progress
                        );
                    }
                }
        );
    }

    private void updateLabels(
            int height,
            int spacing,
            int radius
    ) {

        heightText.setText(
                "Height: "
                        + height
                        + " dp"
        );

        spacingText.setText(
                "Spacing: "
                        + spacing
                        + " dp"
        );

        radiusText.setText(
                "Radius: "
                        + radius
                        + " dp"
        );
    }

    private abstract static class SeekListener
            implements SeekBar.OnSeekBarChangeListener {

        @Override
        public void onStartTrackingTouch(
                SeekBar seekBar
        ) {
        }

        @Override
        public void onStopTrackingTouch(
                SeekBar seekBar
        ) {
        }
    }
}

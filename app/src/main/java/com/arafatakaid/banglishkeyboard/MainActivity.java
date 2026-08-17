package com.arafatakaid.banglishkeyboard;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.startapp.sdk.adsbase.StartAppSDK;

public class MainActivity extends AppCompatActivity {

    private static final int MIC_PERMISSION_REQUEST_CODE = 101;

    // Start.io App ID
    private static final String START_IO_APP_ID = "207212666";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // ==========================================
        // Start.io SDK Initialization
        // ==========================================

        StartAppSDK.initParams(
                this,
                START_IO_APP_ID
        )
        .init();

        // ==========================================
        // Microphone Permission
        // ==========================================

        requestMicPermission();

        // ==========================================
        // Keyboard Buttons
        // ==========================================

        Button enableBtn =
                findViewById(
                        R.id.btn_enable_keyboard
                );

        Button selectBtn =
                findViewById(
                        R.id.btn_select_keyboard
                );

        if (enableBtn != null) {

            enableBtn.setOnClickListener(v -> {

                Intent intent =
                        new Intent(
                                Settings.ACTION_INPUT_METHOD_SETTINGS
                        );

                startActivity(intent);
            });
        }

        if (selectBtn != null) {

            selectBtn.setOnClickListener(v -> {

                InputMethodManager imm =
                        (InputMethodManager)
                                getSystemService(
                                        INPUT_METHOD_SERVICE
                                );

                if (imm != null) {
                    imm.showInputMethodPicker();
                }
            });
        }
    }

    // ==========================================
    // Microphone Permission
    // ==========================================

    private void requestMicPermission() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.RECORD_AUDIO
                    },
                    MIC_PERMISSION_REQUEST_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode ==
                MIC_PERMISSION_REQUEST_CODE) {

            if (grantResults.length > 0 &&
                    grantResults[0] ==
                            PackageManager.PERMISSION_GRANTED) {

                Toast.makeText(
                        this,
                        "Mic permission dewa hoyeche",
                        Toast.LENGTH_SHORT
                ).show();

            } else {

                Toast.makeText(
                        this,
                        "Voice input er jonno mic permission lagbe",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }
}

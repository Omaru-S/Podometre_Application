package com.example.podometre.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.example.podometre.R;
import com.example.podometre.utils.StepCounterService;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private boolean isRunning = false;
    private Button toggleButton;
    private Button resetButton;
    private TextView stepCountText;

    private int cumulativeSteps = 0;

    private BroadcastReceiver stepCountReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int steps = intent.getIntExtra("steps", 0);
            updateStepCount(steps);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toggleButton = findViewById(R.id.toggleButton);
        resetButton = findViewById(R.id.resetButton);
        stepCountText = findViewById(R.id.stepCountText);

        toggleButton.setOnClickListener(v -> {
            if (isRunning) {
                stopStepCounting();
            } else {
                startStepCounting();
            }
        });

        resetButton.setOnClickListener(v -> resetStepCount());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        LocalBroadcastManager.getInstance(this).registerReceiver(stepCountReceiver, new IntentFilter("StepCountUpdate"));

        Log.d(TAG, "Activity created");
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(stepCountReceiver);
        super.onDestroy();
    }

    private void startStepCounting() {
        isRunning = true;
        toggleButton.setText("Stop");
        Log.d(TAG, "Starting step counting");
        startService(new Intent(this, StepCounterService.class));
    }

    private void stopStepCounting() {
        isRunning = false;
        toggleButton.setText("Start");
        Log.d(TAG, "Stopping step counting");
        stopService(new Intent(this, StepCounterService.class));
    }

    private void updateStepCount(int steps) {
        cumulativeSteps = steps;
        stepCountText.setText("Steps: " + steps);
        Log.d(TAG, "Step count updated: " + steps);
    }

    private void resetStepCount() {
        cumulativeSteps = 0;
        stepCountText.setText("Steps: " + cumulativeSteps);
        Log.d(TAG, "Step count reset");

        // Send a broadcast to notify the StepCounterService (if needed)
        Intent intent = new Intent("StepCountUpdate");
        intent.putExtra("steps", cumulativeSteps);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}

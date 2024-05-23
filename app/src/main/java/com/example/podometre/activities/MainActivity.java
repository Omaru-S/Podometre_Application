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
/**
 * Activité principale de l'application, permettant d'afficher le nombre de pas.
 * Cette activité possède également un bouton pour réinitialiser le nombre de pas.
 */
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
            Log.d(TAG, "Réception des pas : " + steps);
            updateStepCount(steps);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeUIElements();
        setupUIListeners();
        configureInsets();
        registerStepCountReceiver();

        Log.d(TAG, "Activité créée");
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(stepCountReceiver);
        Log.d(TAG, "Récepteur de diffusion locale désenregistré");
        super.onDestroy();
    }

    /**
     * Initialise les éléments UI.
     */
    private void initializeUIElements() {
        toggleButton = findViewById(R.id.toggleButton);
        resetButton = findViewById(R.id.resetButton);
        stepCountText = findViewById(R.id.stepCountText);
        Log.d(TAG, "Éléments UI initialisés");
    }

    /**
     * Configure les listeners UI pour les boutons.
     */
    private void setupUIListeners() {
        toggleButton.setOnClickListener(v -> {
            if (isRunning) {
                stopStepCounting();
            } else {
                startStepCounting();
            }
        });

        resetButton.setOnClickListener(v -> resetStepCount());
        Log.d(TAG, "Listeners UI configurés");
    }

    /**
     * Configure les insets pour l'interface utilisateur.
     */
    private void configureInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            Log.d(TAG, "Insets appliqués");
            return insets;
        });
    }

    /**
     * Enregistre le récepteur de mise à jour du nombre de pas.
     */
    private void registerStepCountReceiver() {
        LocalBroadcastManager.getInstance(this).registerReceiver(stepCountReceiver, new IntentFilter("StepCountUpdate"));
        Log.d(TAG, "Récepteur de mise à jour des pas enregistré");
    }

    /**
     * Démarre le comptage des pas et met à jour l'interface utilisateur.
     */
    private void startStepCounting() {
        isRunning = true;
        updateToggleButton();
        Log.d(TAG, "Démarrage du comptage des pas");
        startService(new Intent(this, StepCounterService.class));
    }

    /**
     * Arrête le comptage des pas et met à jour l'interface utilisateur.
     */
    private void stopStepCounting() {
        isRunning = false;
        updateToggleButton();
        Log.d(TAG, "Arrêt du comptage des pas");
        stopService(new Intent(this, StepCounterService.class));
    }

    /**
     * Met à jour le texte du bouton de bascule en fonction de l'état du service.
     */
    private void updateToggleButton() {
        toggleButton.setText(isRunning ? "Stop" : "Start");
        Log.d(TAG, "Bouton bascule mis à jour");
    }

    /**
     * Met à jour l'affichage du nombre de pas.
     * @param steps Le nombre de pas actuel.
     */
    private void updateStepCount(int steps) {
        cumulativeSteps = steps;
        stepCountText.setText("Steps: " + steps);
        Log.d(TAG, "Nombre de pas mis à jour : " + steps);
    }

    /**
     * Réinitialise le nombre de pas et met à jour l'affichage.
     */
    private void resetStepCount() {
        cumulativeSteps = 0;
        updateStepText();
        Log.d(TAG, "Nombre de pas réinitialisé");
        broadcastStepCountUpdate();
    }

    /**
     * Met à jour l'affichage du texte des pas.
     */
    private void updateStepText() {
        stepCountText.setText("Steps: " + cumulativeSteps);
        Log.d(TAG, "Texte des pas mis à jour");
    }

    /**
     * Diffuse une mise à jour du nombre de pas.
     */
    private void broadcastStepCountUpdate() {
        Intent intent = new Intent("StepCountUpdate");
        intent.putExtra("steps", cumulativeSteps);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        Log.d(TAG, "Mise à jour du nombre de pas diffusée");
    }
}

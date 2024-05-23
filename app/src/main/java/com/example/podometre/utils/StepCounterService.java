package com.example.podometre.utils;

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StepCounterService extends Service implements SensorEventListener {

    private static final String TAG = "StepCounterService";
    private static final String SERVER_URL = "http://ec2-16-170-203-199.eu-north-1.compute.amazonaws.com:8080/Podometre_JEE/fs";
    private static final String NOM_TEL = "Louis";
    private static final int SAMPLING_FREQUENCY = 100;
    private static final int BUFFER_SIZE = 1024;
    private static final double SENSOR_THRESHOLD = 0.5;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Handler handler;
    private HandlerThread handlerThread;
    private long lastPostTimestamp;
    private final List<Float> accelerationList = Collections.synchronizedList(new ArrayList<>());

    private int cumulativeSteps = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service créé");

        initializeSensorManager();
        initializeHandlerThread();
        registerSensorListener();

        lastPostTimestamp = System.currentTimeMillis(); // Initialiser le timestamp
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service démarré");

        if (intent != null && "RESET".equals(intent.getAction())) {
            resetStepCount();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service détruit");
        unregisterSensorListener();
        stopHandlerThread();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float verticalAcceleration = event.values[2];
        Log.d(TAG, "Changement détecté dans le capteur : " + verticalAcceleration);
        synchronized (accelerationList) {
            accelerationList.add(verticalAcceleration);

            if (accelerationList.size() >= BUFFER_SIZE) {
                List<Float> bufferCopy = new ArrayList<>(accelerationList);
                accelerationList.clear();
                Log.d(TAG, "Buffer plein, envoi des données");
                sendVerticalAcceleration(bufferCopy);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "Changement de précision du capteur : " + accuracy);
    }

    /**
     * Initialise le gestionnaire de capteur.
     */
    private void initializeSensorManager() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Log.d(TAG, "Gestionnaire de capteur initialisé");
    }

    /**
     * Initialise le thread de gestionnaire.
     */
    private void initializeHandlerThread() {
        handlerThread = new HandlerThread("StepCounterThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        Log.d(TAG, "Thread de gestionnaire initialisé");
    }

    /**
     * Enregistre le listener de capteur avec un taux d'échantillonnage spécifique.
     */
    private void registerSensorListener() {
        int samplingPeriodUs = convertFrequencyToSamplingPeriodUs(SAMPLING_FREQUENCY); // 100Hz = 10,000 microseconds
        sensorManager.registerListener(this, accelerometer, samplingPeriodUs, handler);
        Log.d(TAG, "Listener de capteur enregistré avec un taux d'échantillonnage de 100Hz");
    }

    /**
     * Désenregistre le listener de capteur.
     */
    private void unregisterSensorListener() {
        sensorManager.unregisterListener(this);
        Log.d(TAG, "Listener de capteur désenregistré");
    }

    /**
     * Arrête le thread de gestionnaire.
     */
    private void stopHandlerThread() {
        handlerThread.quit();
        Log.d(TAG, "Thread de gestionnaire arrêté");
    }

    /**
     * Réinitialise le nombre de pas.
     */
    private void resetStepCount() {
        cumulativeSteps = 0;
        Log.d(TAG, "Nombre de pas réinitialisé dans le service");

        // Diffuser le nombre de pas réinitialisé
        broadcastStepCount();
    }

    /**
     * Envoie les accélérations verticales au serveur.
     * @param accelerationList Liste des accélérations verticales.
     */
    private void sendVerticalAcceleration(List<Float> accelerationList) {
        long currentTimestamp = System.currentTimeMillis();
        float timeSinceLastPost = (currentTimestamp - lastPostTimestamp) / 1000.0f;  // Convertir en secondes
        lastPostTimestamp = currentTimestamp;
        Log.d(TAG, "Envoi des accélérations verticales");

        new Thread(() -> {
            try {
                if (hasSignificantActivity(accelerationList)) {
                    sendDataToServer(accelerationList, timeSinceLastPost);
                } else {
                    Log.d(TAG, "Personne inactive");
                }
            } catch (Exception e) {
                Log.e(TAG, "Erreur lors de l'envoi des accélérations verticales", e);
            }
        }).start();
    }

    /**
     * Vérifie s'il y a une activité significative dans les données.
     * @param data Liste des données de l'accélération.
     * @return true si l'activité est significative, sinon false.
     */
    private boolean hasSignificantActivity(List<Float> data) {
        StandardDeviation sd = new StandardDeviation();
        double sdValue = sd.evaluate(listToDoubleArray(data));
        boolean significant = sdValue > SENSOR_THRESHOLD;
        Log.d(TAG, "Activité significative : " + significant);
        return significant;
    }

    /**
     * Convertit une liste de Float en tableau de double.
     * @param floatList Liste de Float.
     * @return Tableau de double.
     */
    private double[] listToDoubleArray(List<Float> floatList) {
        double[] doubleArray = new double[floatList.size()];
        for (int i = 0; i < floatList.size(); i++) {
            doubleArray[i] = floatList.get(i); // Auto-unboxing Float en float, puis conversion en double
        }
        Log.d(TAG, "Conversion de la liste en tableau de doubles");
        return doubleArray;
    }

    /**
     * Envoie les données d'accélération au serveur.
     * @param accelerationList Liste des accélérations verticales.
     * @param timeSinceLastPost Temps écoulé depuis le dernier envoi en secondes.
     * @throws Exception En cas d'erreur lors de l'envoi des données.
     */
    private void sendDataToServer(List<Float> accelerationList, float timeSinceLastPost) throws Exception {
        URL url = new URL(SERVER_URL);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestMethod("POST");
        urlConnection.setDoOutput(true);
        urlConnection.setRequestProperty("Content-Type", "application/json");

        JSONArray jsonArray = new JSONArray(accelerationList);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("verticalAccelerations", jsonArray);
        jsonObject.put("time", timeSinceLastPost);
        jsonObject.put("samplingFrequency", SAMPLING_FREQUENCY);
        jsonObject.put("fftSize", BUFFER_SIZE);
        jsonObject.put("name", NOM_TEL);

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(urlConnection.getOutputStream()));
        writer.write(jsonObject.toString());
        writer.flush();
        writer.close();
        Log.d(TAG, "Données envoyées au serveur");

        int responseCode = urlConnection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            Log.d(TAG, "Données envoyées avec succès");
            int steps = readStepsFromResponse(urlConnection);
            updateSteps(steps);
        } else {
            Log.e(TAG, "Échec de l'envoi des données : " + responseCode);
        }

        urlConnection.disconnect();
    }

    /**
     * Lit le nombre de pas depuis la réponse du serveur.
     * @param urlConnection Connexion HTTP avec le serveur.
     * @return Nombre de pas.
     */
    private int readStepsFromResponse(HttpURLConnection urlConnection) {
        int steps = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()))) {
            StringBuilder responseBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseBuilder.append(line);
            }

            String responseString = responseBuilder.toString();
            JSONObject responseObject = new JSONObject(responseString);

            if (responseObject.has("steps")) {
                steps = responseObject.getInt("steps");
                Log.d(TAG, "Nombre de pas lu de la réponse : " + steps);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de la lecture du nombre de pas de la réponse", e);
        }
        return steps;
    }

    /**
     * Met à jour le nombre de pas cumulés.
     * @param steps Nombre de pas ajoutés.
     */
    private void updateSteps(int steps) {
        cumulativeSteps += steps;
        Log.d(TAG, "Mise à jour du nombre de pas : " + cumulativeSteps);
        broadcastStepCount();
    }

    /**
     * Diffuse une mise à jour du nombre de pas.
     */
    private void broadcastStepCount() {
        Intent intent = new Intent("StepCountUpdate");
        intent.putExtra("steps", cumulativeSteps);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        Log.d(TAG, "Diffusion du nombre de pas mis à jour");
    }

    /**
     * Convertit une fréquence en période d'échantillonnage en microsecondes.
     * @param frequency Fréquence en Hz.
     * @return Période d'échantillonnage en microsecondes.
     */
    public int convertFrequencyToSamplingPeriodUs(float frequency) {
        Log.d(TAG, "Conversion de la fréquence en période d'échantillonnage");
        return (int) (1.0 / frequency * 1_000_000);
    }
}

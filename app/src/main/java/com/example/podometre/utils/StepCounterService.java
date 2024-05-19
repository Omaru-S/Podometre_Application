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
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Handler handler;
    private HandlerThread handlerThread;
    private long lastPostTimestamp;
    private final List<Float> accelerationList = Collections.synchronizedList(new ArrayList<>());
    private static final int BUFFER_SIZE = 1024;  // Example buffer size

    private int cumulativeSteps = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        handlerThread = new HandlerThread("StepCounterThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        int samplingPeriodUs = 10000; // 100Hz = 10,000 microseconds
        sensorManager.registerListener(this, accelerometer, samplingPeriodUs, handler);
        Log.d(TAG, "Sensor listener registered with 100Hz sampling rate");

        lastPostTimestamp = System.currentTimeMillis(); // Initialize the timestamp
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");

        if (intent != null && "RESET".equals(intent.getAction())) {
            resetStepCount();
        }

        return START_STICKY;
    }

    private void resetStepCount() {
        cumulativeSteps = 0;
        Log.d(TAG, "Step count reset in service");

        // Broadcast the reset step count
        Intent intent = new Intent("StepCountUpdate");
        intent.putExtra("steps", cumulativeSteps);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
        sensorManager.unregisterListener(this);
        handlerThread.quit();
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
        synchronized (accelerationList) {
            accelerationList.add(verticalAcceleration);

            if (accelerationList.size() >= BUFFER_SIZE) {
                List<Float> bufferCopy = new ArrayList<>(accelerationList);
                accelerationList.clear();
                sendVerticalAcceleration(bufferCopy);
            }
        }
    }

    private void sendVerticalAcceleration(List<Float> accelerationList) {
        long currentTimestamp = System.currentTimeMillis();
        float timeSinceLastPost = (currentTimestamp - lastPostTimestamp) / 1000.0f;  // Convert to seconds
        lastPostTimestamp = currentTimestamp;

        new Thread(() -> {
            try {
                if(hasSignificantActivity(accelerationList)){
                    URL url = new URL(SERVER_URL);
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setRequestMethod("POST");
                    urlConnection.setDoOutput(true);
                    urlConnection.setRequestProperty("Content-Type", "application/json");

                    JSONArray jsonArray = new JSONArray(accelerationList);
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("verticalAccelerations", jsonArray);
                    jsonObject.put("time", timeSinceLastPost);

                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(urlConnection.getOutputStream()));
                    writer.write(jsonObject.toString());
                    writer.flush();
                    writer.close();

                    int responseCode = urlConnection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        Log.d(TAG, "Data sent successfully");
                        int steps = readStepsFromResponse(urlConnection);
                        updateSteps(steps);
                    } else {
                        Log.e(TAG, "Failed to send data: " + responseCode);
                    }

                    urlConnection.disconnect();
                } else {
                    Log.d(TAG,"Personne inactive");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending vertical acceleration", e);
            }
        }).start();
    }
    public static boolean hasSignificantActivity(List<Float> data) {
        StandardDeviation sd = new StandardDeviation();
        double sdValue = sd.evaluate(listToDoubleArray(data));

        // Threshold needs to be set based on experimental data analysis
        return sdValue > 0.5;  // This is an example threshold
    }
    public static double[] listToDoubleArray(List<Float> floatList) {
        double[] doubleArray = new double[floatList.size()];
        for (int i = 0; i < floatList.size(); i++) {
            doubleArray[i] = floatList.get(i); // Auto-unboxing Float to float, then widening to double
        }
        return doubleArray;
    }
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
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading steps from response", e);
        }
        return steps;
    }

    private void updateSteps(int steps) {
        cumulativeSteps += steps;

        Intent intent = new Intent("StepCountUpdate");
        intent.putExtra("steps", cumulativeSteps);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }
}

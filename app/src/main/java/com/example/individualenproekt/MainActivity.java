package com.example.individualenproekt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.widget.TextViewCompat;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.example.individualenproekt.logger.LogNode;
import com.example.individualenproekt.logger.LogView;
import com.example.individualenproekt.logger.LogWrapper;
import com.example.individualenproekt.logger.MessageOnlyLogFilter;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** se koristi za definiranje akcii koi moze da se izvrshat preku google fitness api **/
enum FitActionRequestCode {
    FIND_DATA_SOURCES
}

public class MainActivity extends AppCompatActivity {

    private NotificationManager notificationManager;

    private FitnessOptions fitnessOptions = FitnessOptions.builder().addDataType(DataType.TYPE_LOCATION_SAMPLE).build();

    private boolean runningQOrLater = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q;

    private OnDataPointListener dataPointListener;
    private List dataSources;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        this.initializeLogging(); /** <- metod za printanje na log msgs **/
        this.checkPermissionsAndRun(FitActionRequestCode.FIND_DATA_SOURCES); /** metod za proverka na permissions od fintess api **/
    }

    private void checkPermissionsAndRun(FitActionRequestCode fitActionRequestCode) {
        if (this.permissionApproved()) {
            this.fitSignIn(fitActionRequestCode);
        } else {
            this.requestRuntimePermissions(fitActionRequestCode);
        }
    }

    /**
     * metod za proverka dali korisnikot e logiran vo svojot google account
     **/
    private final void fitSignIn(FitActionRequestCode requestCode) {
        if (this.oAuthPermissionsApproved()) {
            this.performActionForRequestCode(requestCode);
        } else {
            GoogleSignIn.requestPermissions(this, requestCode.ordinal(), this.getGoogleAccount(), this.fitnessOptions);
        }
    }

    private final boolean oAuthPermissionsApproved() {
        return GoogleSignIn.hasPermissions(this.getGoogleAccount(), this.fitnessOptions);
    }

    /** metod za zemanje na korisnickiot google account **/
    private final GoogleSignInAccount getGoogleAccount() {
        return GoogleSignIn.getAccountForExtension(this, this.fitnessOptions);
    }

    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (resultCode) {
            case -1:
                FitActionRequestCode postSignInAction = FitActionRequestCode.values()[requestCode];
                this.performActionForRequestCode(postSignInAction);
                break;
            default:
                this.oAuthErrorMsg(requestCode, resultCode);
        }
    }

    private final void performActionForRequestCode(FitActionRequestCode requestCode) {
        if(requestCode == FitActionRequestCode.FIND_DATA_SOURCES)
        {
            findFitnessDataSources();
        }
    }

    private final void oAuthErrorMsg(int requestCode, int resultCode) {
        String message = ("\nThere was an error signing into Fit. Check the troubleshooting section of the README\n for potential issues.\n       " +
                "Request code was: " + requestCode + "\n            " +
                "Result code was: " + resultCode + "\n");
        Log.e("LOCAL TAG ", message);
    }

    private void sendNotification(){

        Notification mNotification = new NotificationCompat.Builder(this)
                .setContentTitle("Fitness App")
                .setContentText("Your data source has been found")
                .setSmallIcon(R.drawable.ic_not_image)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build();

        notificationManager.notify(1,mNotification);
    }

    /** metod za naogjanje na konkretni podatoci za korisnickiot google account **/
    private final void findFitnessDataSources() {
        Fitness.getSensorsClient(this, this.getGoogleAccount()).findDataSources((new DataSourcesRequest.Builder())
                .setDataTypes(DataType.TYPE_LOCATION_SAMPLE)
                .setDataSourceTypes(DataSource.TYPE_RAW)
                .build())
                .addOnSuccessListener((new OnSuccessListener() {
                    public void onSuccess(Object var1) {
                        this.onSuccess((List) var1);
                    }

                    public final void onSuccess(List dataSources) {
                        Iterator var3 = dataSources.iterator();

                        sendNotification();

                        /** TUKA SAKAM DA IZVRSHAM NOTIFIKACIJA ZA PREVZEMENI PODATOCI **/

                        while (var3.hasNext()) {
                            DataSource dataSource = (DataSource) var3.next();
                            Log.i("Local Tag", "Data source found:" + dataSource);
                            Log.i("Local Tag", "Data source TYPE:" + dataSource.getDataType().getName());
                            if (dataSource.getDataType() == DataType.TYPE_LOCATION_SAMPLE && dataPointListener == null) {
                                Log.i("BasicSensorsApi", "Data source for LOCATION_SAMPLE found!  Registering.");
                                registerFitnessDataListener(dataSource, DataType.TYPE_LOCATION_SAMPLE);
                            }
                        }
                    }
                })).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.i("Local tag", "failed");
            }
        });
    }

    /** metod za registracija na listener koj kje gi koristi senzorite na telefonot **/
    private final void registerFitnessDataListener(DataSource dataSource, DataType dataType) {
        //this.dataPointListener = (OnDataPointListener);
        Fitness.getSensorsClient(this, this.getGoogleAccount()).add((new com.google.android.gms.fitness.request.SensorRequest.Builder()).setDataSource(dataSource).setDataType(dataType).setSamplingRate(10L, TimeUnit.SECONDS).build(),
                this.dataPointListener).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    Log.i("Local TAG", "Listener registered");
                } else {
                    Log.i("Local TAG", "Listener not registered");
                }
            }
        });
    }

    private final void unregisterFitnessDataListener() {
        if (this.dataPointListener != null) {
            Fitness.getSensorsClient(this, this.getGoogleAccount()).remove(this.dataPointListener).addOnCompleteListener(new OnCompleteListener<Boolean>() {
                @Override
                public void onComplete(@NonNull Task<Boolean> task) {
                    if (task.isSuccessful()) {
                        Log.i("Local TAG", "Listener was removed");
                    } else {
                        Log.i("Local TAG", "Listener was not removed");
                    }
                }
            });
        }
    }

    public boolean onCreateOptionsMenu(@NotNull Menu menu) {
        this.getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(@NotNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_unregister_listener) {
            this.unregisterFitnessDataListener();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }


    private void initializeLogging() {
        LogWrapper logWrapper = new LogWrapper();

        MessageOnlyLogFilter msgFilter = new MessageOnlyLogFilter();
        logWrapper.setNext(msgFilter);

        LogView logView = findViewById(R.id.sampleLogView);
        TextViewCompat.setTextAppearance( logView, R.style.Log);
        logView.setBackgroundColor(Color.WHITE);
        msgFilter.setNext(logView);
        Log.i("Local Tag", "Ready");
    }

    private boolean permissionApproved() {
        boolean approved = false;
        if (this.runningQOrLater) {
            approved = ActivityCompat.checkSelfPermission(this, "android.permission.ACCESS_FINE_LOCATION") == 0;
        } else {
            approved = true;
        }
        return approved;
    }

    private final void requestRuntimePermissions(final FitActionRequestCode requestCode) {
        boolean shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, "android.permission.ACCESS_FINE_LOCATION");
        if (shouldProvideRationale) {
            Log.i("BasicSensorsApi", "Displaying permission rationale to provide additional context.");
            Snackbar.make(findViewById(R.id.main_activity_view), "Location data is used as part of the Google Fit API", Snackbar.LENGTH_INDEFINITE)
                    .setAction("OK", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            ActivityCompat.requestPermissions((Activity) v.getContext(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, requestCode.ordinal());
                        }
                    }).show();
        } else {
            Log.i("BasicSensorsApi", "Requesting permission");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, requestCode.ordinal());
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NotNull String[] permissions, @NotNull int[] grantResults) {
        if (grantResults.length == 0) {
            Log.i("Local tag", "User interaction was cancelled.");
        } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            FitActionRequestCode fitActionRequestCode = FitActionRequestCode.values()[requestCode];
            this.fitSignIn(fitActionRequestCode);
        } else {
            Snackbar.make(this.findViewById(R.id.main_activity_view), "Location data is used as part of the Google Fit API", Snackbar.LENGTH_INDEFINITE)
                    .setAction("OK", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent();
                            Uri uri = Uri.fromParts("package", "com.example.individualenproekt", null);
                            intent.setData(uri);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            MainActivity.this.startActivity(intent);
                        }
                    }).show();
        }
    }

    public void recordDataButton(View view) {
        Intent intent = new Intent(MainActivity.this, RecordDataActivity.class);
        MainActivity.this.startActivity(intent);
    }

    public void recieveDataButton(View view) {
        Intent intent = new Intent(MainActivity.this, ReadDataActivity.class);
        MainActivity.this.startActivity(intent);
    }
}







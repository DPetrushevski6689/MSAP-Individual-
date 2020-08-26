package com.example.individualenproekt;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.widget.TextViewCompat;

import com.example.individualenproekt.logger.LogView;
import com.example.individualenproekt.logger.LogWrapper;
import com.example.individualenproekt.logger.MessageOnlyLogFilter;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.snackbar.Snackbar;

import org.jetbrains.annotations.NotNull;

import static android.widget.Toast.LENGTH_SHORT;

enum FitActionRequestCodeRecord{
    SUBSCRIBE,
    CANCEL_SUBSCRIBTION,
    DUMP_SUBSCRIBTIONS
}

public class RecordDataActivity extends AppCompatActivity {

    //private FitnessOptions fitnessOptions = FitnessOptions.builder().addDataType(DataType.TYPE_LOCATION_SAMPLE).build();

    public static final String TAG = "Record Data";

    private boolean runningQOrLater = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q;

    private final FitnessOptions getFitnessOptions() {
        FitnessOptions fo = FitnessOptions.builder()
                .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_WRITE)
                .build();
        return fo;
    }

    private final FitnessOptions fitnessOptions = getFitnessOptions();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.record_data_main);
        Intent intent = getIntent();
        this.initializeLogging(); /** <- metod za printanje na log msgs **/
        this.checkPermissionsAndRun(FitActionRequestCodeRecord.SUBSCRIBE); /** metod za proverka na permissions od fintess api **/
    }

    private void checkPermissionsAndRun(FitActionRequestCodeRecord fitActionRequestCode) {
        if (this.permissionApproved()) {
            this.fitSignIn(fitActionRequestCode);
        } else {
            this.requestRuntimePermissions(fitActionRequestCode);
        }
    }

    /**
     * metod za proverka dali korisnikot e logiran vo svojot google account
     *
     * @param requestCode*/
    private final void fitSignIn(FitActionRequestCodeRecord requestCode) {
        if (this.oAuthPermissionsApproved()) {
            this.performActionForRequestCode(requestCode);
        } else {
            GoogleSignIn.requestPermissions(this, requestCode.ordinal(), this.getGoogleAccount(), this.getFitnessOptions());
        }
    }

    private final boolean oAuthPermissionsApproved() {
        return GoogleSignIn.hasPermissions(this.getGoogleAccount(), this.getFitnessOptions());
    }

    private final GoogleSignInAccount getGoogleAccount() {
        return GoogleSignIn.getAccountForExtension(this, this.getFitnessOptions());
    }

    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (resultCode) {
            case -1:
                FitActionRequestCodeRecord postSignInAction = FitActionRequestCodeRecord.values()[requestCode];
                this.performActionForRequestCode(postSignInAction);
                break;
            default:
                this.oAuthErrorMsg(requestCode, resultCode);
        }
    }

    /**izbor na metod zavisno od koj request code e predaden od korisnikot **/
    private final void performActionForRequestCode(FitActionRequestCodeRecord requestCode) {
        if (requestCode == FitActionRequestCodeRecord.SUBSCRIBE) {
            subscribe();
        }else if(requestCode == FitActionRequestCodeRecord.CANCEL_SUBSCRIBTION){
            cancelSubscription();
        }else if(requestCode == FitActionRequestCodeRecord.DUMP_SUBSCRIBTIONS){
            dumpSubscribtions();
        }
    }

    private final void oAuthErrorMsg(int requestCode, int resultCode) {
        String message = ("\nThere was an error signing into Fit. Check the troubleshooting section of the README\n for potential issues.\n       " +
                "Request code was: " + requestCode + "\n            " +
                "Result code was: " + resultCode + "\n");
        Log.e(TAG, message);
    }

    /** metod koj kreira zapis za korisnikot. Podatocite se zachuvani i otkako kje se izgasi aplikacijata i moze da se koristat od razlicni uredi **/
    private void subscribe()
    {
        Fitness.getRecordingClient(this, this.getGoogleAccount()).subscribe(DataType.TYPE_CALORIES_EXPENDED)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.i(TAG,"Succesfully subscribed");
                        Toast.makeText(getApplicationContext(),"Succesfully subscribed", LENGTH_SHORT).show();
                    }
                }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.i(TAG,"There was a problem subscribing");
                Toast.makeText(getApplicationContext(),"There was a problem subscribing", LENGTH_SHORT).show();
            }
        });
    }

    /** zema lista od site aktivni zapisi i  zapishuva vo log **/
    private void dumpSubscribtions()
    {
        Fitness.getRecordingClient(this,this.getGoogleAccount()).subscribe(DataType.TYPE_CALORIES_EXPENDED)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.i(TAG,"Active subscribtion");
                        Toast.makeText(getApplicationContext(),"Active subscribtion", LENGTH_SHORT).show();
                    }
                }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.i(TAG,"No active subscribtions");
                Toast.makeText(getApplicationContext(),"No active subscribtions", LENGTH_SHORT).show();
            }
        });
    }

    /** metod za brishenje na zapis **/
    private void cancelSubscription()
    {
        String dataTypeStr = DataType.TYPE_CALORIES_EXPENDED.toString();
        Log.i(TAG,"Unsubscribing");

        Fitness.getRecordingClient(this,this.getGoogleAccount())
                .unsubscribe(DataType.TYPE_CALORIES_EXPENDED)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.i(TAG,"Unsubscribed");
                        Toast.makeText(getApplicationContext(),"Unsubscribed", LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.i(TAG,"Failed to unsubscribe");
                        Toast.makeText(getApplicationContext(),"Failed to unsubscribe", LENGTH_SHORT).show();
                    }
                });
    }



    public boolean onCreateOptionsMenu(@NotNull Menu menu) {
        this.getMenuInflater().inflate(R.menu.record_data_menu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(@NotNull MenuItem item) {
        boolean ret = false;
        switch (item.getItemId()){
            case R.id.action_create_subs:
                this.checkPermissionsAndRun(FitActionRequestCodeRecord.SUBSCRIBE);
                ret = true;
                break;
            case R.id.action_cancel_subs:
                this.checkPermissionsAndRun(FitActionRequestCodeRecord.CANCEL_SUBSCRIBTION);
                ret = true;
                break;
            case R.id.action_dump_subs:
                this.checkPermissionsAndRun(FitActionRequestCodeRecord.DUMP_SUBSCRIBTIONS);
                ret = true;
                break;
            default:
                super.onOptionsItemSelected(item);
        }
        return ret;
    }


    private void initializeLogging() {
        LogWrapper logWrapper = new LogWrapper();

        MessageOnlyLogFilter msgFilter = new MessageOnlyLogFilter();
        logWrapper.setNext(msgFilter);

        LogView logView = findViewById(R.id.sampleLogView);
        TextViewCompat.setTextAppearance(logView, R.style.Log);
        logView.setBackgroundColor(Color.WHITE);
        msgFilter.setNext(logView);
        Log.i(TAG, "Ready");
    }

    private boolean permissionApproved() {
        boolean approved = false;
        if (this.runningQOrLater) {
            approved = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == 0;
        } else {
            approved = true;
        }
        return approved;
    }

    private final void requestRuntimePermissions(final FitActionRequestCodeRecord requestCode) {
        boolean shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACTIVITY_RECOGNITION);
        if (shouldProvideRationale) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.");
            Snackbar.make(findViewById(R.id.main_activity_view), "Location data is used as part of the Google Fit API", Snackbar.LENGTH_INDEFINITE)
                    .setAction("OK", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            ActivityCompat.requestPermissions((Activity) v.getContext(), new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, requestCode.ordinal());
                        }
                    }).show();
        } else {
            Log.i(TAG, "Requesting permission");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, requestCode.ordinal());
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NotNull String[] permissions, @NotNull int[] grantResults) {
        if (grantResults.length == 0) {
            Log.i(TAG, "User interaction was cancelled.");
        } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            FitActionRequestCodeRecord fitActionRequestCode = FitActionRequestCodeRecord.values()[requestCode];
            this.fitSignIn(fitActionRequestCode);
        } else {
            Snackbar.make(this.findViewById(R.id.main_activity_view), "Location data is used as part of the Google Fit API", Snackbar.LENGTH_INDEFINITE)
                    .setAction("OK", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent();
                            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            Uri uri = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null);
                            intent.setData(uri);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            RecordDataActivity.this.startActivity(intent);
                        }
                    }).show();
        }
    }
}

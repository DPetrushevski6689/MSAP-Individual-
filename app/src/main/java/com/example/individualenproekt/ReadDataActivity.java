package com.example.individualenproekt;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.service.autofill.Dataset;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.TextViewCompat;

import com.example.individualenproekt.logger.LogView;
import com.example.individualenproekt.logger.LogWrapper;
import com.example.individualenproekt.logger.MessageOnlyLogFilter;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataDeleteRequest;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.request.DataUpdateRequest;
import com.google.android.gms.fitness.result.DataReadResponse;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

enum FitActionRequestCodeRead{
    INSERT_AND_READ_DATA,
    UPDATE_AND_READ_DATA,
    DELETE_DATA
}



public class ReadDataActivity extends AppCompatActivity {

    private static final String TAG = "ReadDataActivity";

    private final DateFormat dateFormat = DateFormat.getDateInstance();

    private FitnessOptions getFitnessOptions() {
        FitnessOptions fo = FitnessOptions.builder()
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_WRITE)
                .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA,FitnessOptions.ACCESS_WRITE)
                .build();

        return fo;
    }

    //private final FitnessOptions getFitnessOptions = getFitnessOptions();



    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.read_data_main);
        Intent intent = getIntent();

        initializeLogging();
        fitSignIn(FitActionRequestCodeRead.INSERT_AND_READ_DATA);
    }

    private final void fitSignIn(FitActionRequestCodeRead requestCode) {
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
                FitActionRequestCodeRead postSignInAction = FitActionRequestCodeRead.values()[requestCode];
                this.performActionForRequestCode(postSignInAction);
                break;
            default:
                this.oAuthErrorMsg(requestCode, resultCode);
        }
    }

    private final void oAuthErrorMsg(int requestCode, int resultCode) {
        String message = ("\nThere was an error signing into Fit. Check the troubleshooting section of the README\n for potential issues.\n       " +
                "Request code was: " + requestCode + "\n            " +
                "Result code was: " + resultCode + "\n");
        Log.e(TAG, message);
    }


    private final void performActionForRequestCode(FitActionRequestCodeRead requestCode) {
        if (requestCode == FitActionRequestCodeRead.INSERT_AND_READ_DATA) {
            insertAndReadData();
        }else if(requestCode == FitActionRequestCodeRead.UPDATE_AND_READ_DATA){
            updateAndReadData();
        }else if(requestCode == FitActionRequestCodeRead.DELETE_DATA){
            deleteData();
        }
    }

    private void insertAndReadData() {
        this.insertData().continueWithTask((Continuation<Void, Task<DataReadResponse>>) this.readHistoryData());

    }

    /** pozadinski task koj vrshi vnesuvanje na podatoci vo zapisot za dadeniot korisnicki google account **/
    private Task<Void> insertData(){
        DataSet dataSet = insertFitnessData();
        Log.i(TAG,"Inserting the dataset in the History API");

        return Fitness.getHistoryClient(this,this.getGoogleAccount())
                .insertData(dataSet)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.i(TAG,"Data insert was succesfull");
                        Toast.makeText(getApplicationContext(),"Data insert was succesfull",Toast.LENGTH_SHORT).show();
                    }
                })
        .addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.i(TAG,"There was a problem inserting the dataset");
                Toast.makeText(getApplicationContext(),"There was a problem inserting the dataset",Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Task<DataReadResponse> readHistoryData(){
        DataReadRequest readRequest = queryFitnessData();

        return Fitness.getHistoryClient(this,this.getGoogleAccount())
                .readData(readRequest)
                .addOnSuccessListener(new OnSuccessListener<DataReadResponse>() {
                    @Override
                    public void onSuccess(DataReadResponse dataReadResponse) {
                        Log.i(TAG,"Data was succesfully added");
                        Toast.makeText(getApplicationContext(),"Data was succesfully added",Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.i(TAG,"There was a problem reading the data");
                        Toast.makeText(getApplicationContext(),"There was a problem reading the data",Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private DataSet insertFitnessData(){
        Log.i(TAG,"Creating a new data insert request");

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        Date now = new Date();
        calendar.setTime(now);
        long endTime = calendar.getTimeInMillis();
        calendar.add(Calendar.HOUR_OF_DAY,-1);
        long startTime = calendar.getTimeInMillis();

        DataSource dataSource = new DataSource.Builder()
                .setAppPackageName(this)
                .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .setStreamName("step count")
                .setType(DataSource.TYPE_RAW)
                .build();

        int stepCountDelta = 950;
        return DataSet.builder(dataSource)
                .add(DataPoint.builder(dataSource)
                .setField(Field.FIELD_STEPS,stepCountDelta)
                .setTimeInterval(startTime,endTime, TimeUnit.MILLISECONDS)
                .build())
                .build();
    }


    private DataReadRequest queryFitnessData(){
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        Date now = new Date();
        calendar.setTime(now);
        long endTime = calendar.getTimeInMillis();
        calendar.add(Calendar.WEEK_OF_YEAR,-1);
        long startTime = calendar.getTimeInMillis();

        Log.i(TAG,"Range Start: " +  dateFormat.format(startTime));
        Log.i(TAG,"Range End: " +  dateFormat.format(endTime));

        return new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA,DataType.AGGREGATE_STEP_COUNT_DELTA)
                .bucketByTime(1,TimeUnit.DAYS)
                .setTimeRange(startTime,endTime,TimeUnit.MILLISECONDS)
                .build();
    }


    private void deleteData()
    {
        Log.i(TAG,"Deleting today's step count data");
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        Date now = new Date();
        calendar.setTime(now);
        long endTime = calendar.getTimeInMillis();
        calendar.add(Calendar.DAY_OF_YEAR,-1);
        long startTime = calendar.getTimeInMillis();

        DataDeleteRequest request = new DataDeleteRequest.Builder()
                .setTimeInterval(startTime,endTime,TimeUnit.MILLISECONDS)
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .build();

        Fitness.getHistoryClient(this,this.getGoogleAccount())
                .deleteData(request)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.i(TAG,"Succesfully deleted today's step count data");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG,"Failed to delete today's step count data");
                    }
                });
    }

    private void updateAndReadData()
    {
        this.updateData().continueWithTask((Continuation<Void, Task<DataReadResponse>>) this.readHistoryData());
    }

    /** pozadinski task koj vrshi update na podatocite za dadeniot korisnicki google account **/
    private Task<Void> updateData(){
        DataSet dataset = updateFitnessData();
        long startTime = dataset.getDataPoints().get(0).getStartTime(TimeUnit.MILLISECONDS);
        long endTime = dataset.getDataPoints().get(0).getEndTime(TimeUnit.MILLISECONDS);
        Log.i(TAG,"Updating the dataset in the History API");

        DataUpdateRequest request = new DataUpdateRequest.Builder()
                .setDataSet(dataset)
                .setTimeInterval(startTime,endTime,TimeUnit.MILLISECONDS)
                .build();

        return Fitness.getHistoryClient(this,this.getGoogleAccount())
                .updateData(request)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.i(TAG,"Data update was successful");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.i(TAG,"There was a problem updating the dataset");
                    }
                });
    }

    private DataSet updateFitnessData(){
        Log.i(TAG,"Creating a new data update request");

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        Date now = new Date();

        calendar.setTime(now);
        long endTime = calendar.getTimeInMillis();
        calendar.add(Calendar.MINUTE,-50);
        long startTime = calendar.getTimeInMillis();

        DataSource dataSource = new DataSource.Builder()
                .setAppPackageName(this)
                .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .setStreamName("step count")
                .setType(DataSource.TYPE_RAW)
                .build();

        int stepCountDelta = 1000;


        return DataSet.builder(dataSource)
        .add(DataPoint.builder(dataSource)
                .setField(Field.FIELD_STEPS,stepCountDelta)
                .setTimeInterval(startTime,endTime,TimeUnit.MILLISECONDS)
                .build()).build();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.read_data_menu,menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item){
        switch(item.getItemId()){
            case R.id.action_delete_data:
                fitSignIn(FitActionRequestCodeRead.DELETE_DATA);
                return true;
            case R.id.action_update_data:
                fitSignIn(FitActionRequestCodeRead.UPDATE_AND_READ_DATA);
                return true;
        }
        return super.onOptionsItemSelected(item);
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

}

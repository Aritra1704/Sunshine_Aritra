package com.arpaul.sunshine_aritra.wearableService;

import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.arpaul.sunshine_aritra.Utility;
import com.arpaul.sunshine_aritra.data.WeatherContract;
import com.arpaul.utilitieslib.LogUtils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

/**
 * Created by Aritra on 26-09-2016.
 */

public class SendWearableDataService extends Service implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

//    private WeatherDataDO objWeatherDataDO;
    private GoogleApiClient mGoogleApiClient;
    private static final String LOG_TAG = "SendWearableDataService";
    private static final String WEATHER_PATH = "/weather";
    private static final String KEY_WEATHER_ID = "KEY_WEATHER_ID";
    private static final String KEY_MAX_TEMP = "KEY_MAX_TEMP";
    private static final String KEY_MIN_TEMP = "KEY_MIN_TEMP";
    private static final String KEY_SHORT_DESC = "KEY_SHORT_DESC";
    //Shiva surya
    //https://github.com/shivasurya/go-ubiquitous


    public SendWearableDataService(){
//        super("SendWearableDataService");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        mGoogleApiClient = new GoogleApiClient.Builder(SendWearableDataService.this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mGoogleApiClient.connect();

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        LogUtils.infoLog(LOG_TAG, "onConnected: ");

        /**
         * Add Cursor calls.
         */
        Log.d("WatchService", "Updating the WatchFace");
        String locationQuery = Utility.getPreferredLocation(this);

        Uri weatherUri = WeatherContract.WeatherEntry
                .buildWeatherLocationWithDate(locationQuery, System.currentTimeMillis());

        Cursor cursor = getContentResolver().query(
                weatherUri,
                new String[]{WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
                        WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
                        WeatherContract.WeatherEntry.COLUMN_MIN_TEMP
                }, null, null, null);
        LogUtils.debugLog("data",cursor.getCount()+"");
        if (cursor.moveToFirst()) {
            int weatherId = cursor.getInt(cursor.getColumnIndex(
                    WeatherContract.WeatherEntry.COLUMN_WEATHER_ID));
            String maxTemp = Utility.formatTemperature(this, cursor.getDouble(cursor.getColumnIndex(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP)));
            String minTemp = Utility.formatTemperature(this, cursor.getDouble(cursor.getColumnIndex(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP)));
            String shortDesc = Utility.formatTemperature(this, cursor.getDouble(cursor.getColumnIndex(WeatherContract.WeatherEntry.COLUMN_SHORT_DESC)));

            final PutDataMapRequest mapRequest = PutDataMapRequest.create(WEATHER_PATH);
            mapRequest.getDataMap().putInt(KEY_WEATHER_ID, weatherId);
            mapRequest.getDataMap().putString(KEY_MAX_TEMP, maxTemp);
            mapRequest.getDataMap().putString(KEY_MIN_TEMP, minTemp);
            mapRequest.getDataMap().putString(KEY_SHORT_DESC, shortDesc);

            PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(mGoogleApiClient, mapRequest.asPutDataRequest());
        }
        cursor.close();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        LogUtils.infoLog(LOG_TAG, "onConnectionFailed: ");
    }

    @Override
    public void onConnectionSuspended(int i) {
        LogUtils.infoLog(LOG_TAG, "onConnectionSuspended: ");
    }
}

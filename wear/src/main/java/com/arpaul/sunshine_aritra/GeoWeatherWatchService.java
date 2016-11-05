package com.arpaul.sunshine_aritra;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.arpaul.sunshine_aritra.common.WearableConstants;
import com.arpaul.utilitieslib.CalendarUtils;
import com.arpaul.utilitieslib.ColorUtils;
import com.arpaul.utilitieslib.LogUtils;
import com.arpaul.utilitieslib.StringUtils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.UUID;

/**
 * Created by Aritra on 23-09-2016.
 */

public class GeoWeatherWatchService extends CanvasWatchFaceService {

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final String TAG = "GeoWeatherWatchService";

    private Paint mBackgroundPaint;
    private Bitmap mBackgroundBitmap;

    int mWeatherIcon = 0;
    String mWeatherHigh;
    String mWeatherLow;
    String mWeatherSky;

    private static final String WEATHER_PATH = "/weather";
    private static final String KEY_HIGH = "KEY_MAX_TEMP";
    private static final String KEY_LOW = "KEY_MIN_TEMP";
    private static final String KEY_SHORT_DESC = "KEY_SHORT_DESC";
    private static final String KEY_WEATHER_ID = "KEY_WEATHER_ID";

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {

        return new Engine();
    }

    private class EngineHandler extends Handler {
        private final WeakReference<Engine> mWeakReference;

        public EngineHandler(GeoWeatherWatchService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            GeoWeatherWatchService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        Context context;
        Paint mBackgroundPaint, linePaint;
        Paint mTPTimeHigh;
        Paint mTPDayHigh;
        Paint mTPMaxHigh;
        Paint mTPMinHigh;
        Paint mTextLowAmbientPaint;
        Paint mTextTempHighPaint;
        public int text_pattern = 0;
        public DecimalFormat degreeFormat;
        float xOffset, xOffsetMax, xOffsetMin, yOffsetTime, yOffsetDay, yOffsetDate, yOffsetMax, yOffsetMin, yOffsetIcon, yOffsetSkyCond;
        private static final String WEATHER_PATH = "/weather";

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(GeoWeatherWatchService.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            context = getBaseContext();
            Resources resources = GeoWeatherWatchService.this.getResources();

            degreeFormat = new DecimalFormat("##");
            degreeFormat.setRoundingMode(RoundingMode.CEILING);
            degreeFormat.setMinimumFractionDigits(0);
            degreeFormat.setMaximumFractionDigits(0);

            setWatchFaceStyle(new WatchFaceStyle.Builder(GeoWeatherWatchService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            int setBgColor = setupBaseView();
            int setTextColor = ColorUtils.getColor(context, R.color.colorWhite);
            if(text_pattern == WearableConstants.TEXT_PATTERN_DARK)
                setTextColor = ColorUtils.getColor(context, R.color.colorBlack);
            else if(text_pattern == WearableConstants.TEXT_PATTERN_NOON)
                setTextColor = ColorUtils.getColor(context, R.color.colorAccent);
            else
                setTextColor = ColorUtils.getColor(context, R.color.colorWhite);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(setBgColor);
            mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.sp_event_icon);

            mTPTimeHigh = createTextPaint(setTextColor);
            mTPDayHigh = createTextPaint(setTextColor);
            mTPMaxHigh = createTextPaint(setTextColor);
            mTPMinHigh = createTextPaint(setTextColor);
            mTextTempHighPaint = createTextPaint(setTextColor);

            mTextLowAmbientPaint = createTextPaint(ColorUtils.getColor(context, R.color.colorWhite));

            linePaint = new Paint();
            linePaint.setColor(setTextColor);
            linePaint.setStrokeWidth(2);

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            LogUtils.debugLog(TAG, "Data Changed");
            for(DataEvent dataEvent : dataEventBuffer){
                if(dataEvent.getType() == DataEvent.TYPE_CHANGED){
                    DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                    String path = dataEvent.getDataItem().getUri().getPath();
                    Log.d(TAG, path);
                    if (path.equals(WEATHER_PATH)) {
                        if (dataMap.containsKey(KEY_HIGH)) {
                            mWeatherHigh = dataMap.getString(KEY_HIGH);
                            LogUtils.debugLog(TAG, "High = " + mWeatherHigh);
                        } else {
                            Log.d(TAG, "No Data Available High temp");
                        }

                        if (dataMap.containsKey(KEY_LOW)) {
                            mWeatherLow = dataMap.getString(KEY_LOW);
                            LogUtils.debugLog(TAG, "Low = " + mWeatherLow);
                        } else {
                            LogUtils.debugLog(TAG, "No Data Available Low temp");
                        }

                        if (dataMap.containsKey(KEY_SHORT_DESC)) {
                            mWeatherSky = dataMap.getString(KEY_SHORT_DESC);
                            LogUtils.debugLog(TAG, "short desc = " + mWeatherSky);
                        } else {
                            LogUtils.debugLog(TAG, "No Data Available Low temp");
                        }

                        if (dataMap.containsKey(KEY_WEATHER_ID)) {
                            mWeatherIcon = dataMap.getInt(KEY_WEATHER_ID);
                            LogUtils.debugLog(TAG, "icon = " + mWeatherIcon);
                        } else {
                            Log.d(TAG, "No Data Available Weather ID ");
                        }

                        invalidate();
                    }
                }
            }
        }

        @Override
        public void onConnected(Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            trigger();
            Log.d(TAG, "connected Google Playservice API client");
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d(TAG, "suspended");
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            LogUtils.infoLog(TAG, "onConnectionFailed: ");
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                invalidate();
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

        }

        public void trigger() {
            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(WEATHER_PATH);
            putDataMapRequest.getDataMap().putString("DATA", UUID.randomUUID().toString());
            PutDataRequest request = putDataMapRequest.asPutDataRequest();

            Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                    .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                        @Override
                        public void onResult(DataApi.DataItemResult dataItemResult) {
                            if (!dataItemResult.getStatus().isSuccess()) {
                                Log.d(TAG, "Trigger failed for weather data");
                            } else {
                                Log.d(TAG, "Trigger success for weather data");
                            }
                        }
                    });
        }

        boolean mRegisteredTimeZoneReceiver = false;
        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            GeoWeatherWatchService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            GeoWeatherWatchService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                invalidate();
            }
        };

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = GeoWeatherWatchService.this.getResources();
            boolean isRound = insets.isRound();

            float tsTime = resources.getDimension(isRound ? R.dimen.margin_18 : R.dimen.margin_16);
            float tsDay = resources.getDimension(isRound ? R.dimen.margin_16 : R.dimen.margin_14);
            float tsMax = resources.getDimension(isRound ? R.dimen.margin_25 : R.dimen.margin_20);
            float tsMin = resources.getDimension(isRound ? R.dimen.margin_16 : R.dimen.margin_14);
            float tsLowAmbient = resources.getDimension(isRound ? R.dimen.margin_14 : R.dimen.margin_12);
            float tempTextSize = resources.getDimension(isRound ? R.dimen.margin_25 : R.dimen.margin_20);

            yOffsetTime = resources.getDimension(R.dimen.margin_10);
            yOffsetDay = resources.getDimension(R.dimen.margin_30);
            yOffsetDate = yOffsetDay;
            yOffsetMax = resources.getDimension(R.dimen.margin_30);
            yOffsetMin = yOffsetMax + resources.getDimension(R.dimen.margin_20);
            yOffsetSkyCond = yOffsetMin + resources.getDimension(R.dimen.margin_30);
            yOffsetIcon = resources.getDimension(R.dimen.margin_190);

            xOffset = resources.getDimension(isRound ? R.dimen.margin_30 : R.dimen.margin_25);
            xOffsetMax = xOffset + 20;
            xOffsetMin = xOffsetMax + 10;

            mTPTimeHigh.setTextSize(tsTime);
            mTPDayHigh.setTextSize(tsDay);
            mTPMaxHigh.setTextSize(tsMax);
            mTPMinHigh.setTextSize(tsMin);
            mTextTempHighPaint.setTextSize(tempTextSize);
            mTextLowAmbientPaint.setTextSize(tsLowAmbient);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            super.onDraw(canvas, bounds);

            canvas.drawBitmap(mBackgroundBitmap, 0, 0, mBackgroundPaint);

            // Draw the background.
            int width = bounds.width(), height = bounds.height();
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, width, height, mBackgroundPaint);
            }

            canvas.drawLine(0, height/2, width, height/2, linePaint);

            //Remove later
//            setDummyData();

            String day = CalendarUtils.getDateinPattern(WearableConstants.DATE_PATTERN_WEEKNAME_FORMAT);
            String date = CalendarUtils.getDateinPattern(WearableConstants.DATE_PATTERN_WEATHER_DETAIL);
            String textMax = mWeatherHigh;
            String textMin = mWeatherLow;

            String current_time = CalendarUtils.getDateinPattern(CalendarUtils.TIME_FORMAT);

            float iconDimen = (int) getResources().getDimension(R.dimen.margin_70);
            int xDayPos = (width / 2);
            if (mAmbient) {
                float lowTextDateLen = mTextLowAmbientPaint.measureText(day + ", " + date);
                float lowTextTempLen = mTextLowAmbientPaint.measureText(textMax + " / " + textMin);
                if(mTextLowAmbientPaint != null)
                    canvas.drawText(day + ", " + date, xDayPos - lowTextDateLen/2, height/2 - yOffsetDay, mTextLowAmbientPaint);

                if(!TextUtils.isEmpty(current_time) && mTextLowAmbientPaint != null) {
                    float lowTextCurTimeLen = mTextLowAmbientPaint.measureText(current_time);
                    canvas.drawText(current_time, xDayPos - lowTextCurTimeLen/2, height/2 - yOffsetTime, mTextLowAmbientPaint);
                }

                if(!TextUtils.isEmpty(textMax) && mTextLowAmbientPaint != null)
                    canvas.drawText(textMax + " / " + textMin, xDayPos - lowTextTempLen/2, height/2 + yOffsetMax, mTextLowAmbientPaint);
                if(!TextUtils.isEmpty(mWeatherSky) && mTextLowAmbientPaint != null) {
                    String skyCondition = mWeatherSky;
                    float highTextLen = mTPMinHigh.measureText(skyCondition);
                    canvas.drawText(skyCondition, xDayPos - highTextLen/2, height/2 + yOffsetMax + 50, mTextLowAmbientPaint);
                }
            } else {
                float lowTextLen = mTPDayHigh.measureText(day + ", " + date);
                if(mTPDayHigh != null)
                    canvas.drawText(day + ", " + date, xDayPos - lowTextLen/2, height/2 - yOffsetDay, mTPDayHigh);

                if(!TextUtils.isEmpty(current_time) && mTPTimeHigh != null) {
                    float lowTextCurTimeLen = mTPTimeHigh.measureText(current_time);
                    canvas.drawText(current_time, xDayPos - lowTextCurTimeLen/2, height/2 - yOffsetTime, mTPTimeHigh);
                }

                if(!TextUtils.isEmpty(textMax) && !TextUtils.isEmpty(textMin) && mTPMaxHigh != null) {
                    canvas.drawText(textMax, xOffsetMax, height/2 + yOffsetMax, mTPMaxHigh);
                    canvas.drawText(textMin, xOffsetMin, height/2 + yOffsetMin, mTPMinHigh);
                }

                if(mWeatherIcon > 0) {
                    //Icon
                    Drawable b = getResources().getDrawable(WearableConstants.getArtResourceForWeatherCondition(mWeatherIcon));
                    Bitmap icon = null;
                    if (((BitmapDrawable) b) != null) {
                        icon = ((BitmapDrawable) b).getBitmap();
                    }
                    Bitmap weatherIcon = Bitmap.createScaledBitmap(icon, (int) iconDimen, (int) iconDimen, true);
                    float iconXOffset = xOffset + (int) getResources().getDimension(R.dimen.margin_70);
                    if(weatherIcon != null)
                        canvas.drawBitmap(weatherIcon, iconXOffset, height/2, null);
                }

                if(!TextUtils.isEmpty(mWeatherSky) && mTPMinHigh != null) {
                    String skyCondition = mWeatherSky;
                    float highTextLen = mTPMinHigh.measureText(skyCondition);
                    canvas.drawText(skyCondition, xDayPos - highTextLen/2, height/2 + yOffsetSkyCond, mTPMinHigh);
                }
            }
//                holder.ivWeather.setImageResource(AppConstants.getArtResourceForWeatherCondition(StringUtils.getInt(icon)));
        }

        private void setDummyData(){

            mWeatherHigh = "25.6";
            mWeatherLow = "21.2";
            mWeatherSky = "Clear Sky";

            mWeatherIcon = 201;
        }

        boolean mLowBitAmbient;
        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        boolean mAmbient;
        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTPDayHigh.setAntiAlias(!inAmbientMode);

                    mTPTimeHigh.setAntiAlias(!inAmbientMode);
                    mTPMaxHigh.setAntiAlias(!inAmbientMode);
                    mTPMinHigh.setAntiAlias(!inAmbientMode);
                    mTextTempHighPaint.setAntiAlias(!inAmbientMode);
                    mTextLowAmbientPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }
        }

        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
//            if (shouldTimerBeRunning()) {
//                long timeMs = System.currentTimeMillis();
//                long delayMs = INTERACTIVE_UPDATE_RATE_MS
//                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
//                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
//            }
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        private int setupBaseView(){
            String currentTime = CalendarUtils.getDateinPattern(CalendarUtils.TIME_FORMAT);
            String morningTime = "10:00 am";
            String noonTime = "4:00 pm";
            String eveningTime = "8:00 pm";
            String nightTime = "11:59 pm";
            String dawnTime = "4:00 am";

            int setColor = 0;

            if(CalendarUtils.getDiffBtwDatesPattern(dawnTime, currentTime, CalendarUtils.DIFF_TYPE.TYPE_MINUTE, CalendarUtils.TIME_FORMAT) < 0){
                text_pattern = WearableConstants.TEXT_PATTERN_LIGHT;
                setColor = ColorUtils.getColor(context, R.color.colorNight);
            } else if(CalendarUtils.getDiffBtwDatesPattern(morningTime, currentTime, CalendarUtils.DIFF_TYPE.TYPE_MINUTE, CalendarUtils.TIME_FORMAT) < 0){
                text_pattern = WearableConstants.TEXT_PATTERN_DARK;
                setColor = ColorUtils.getColor(context, R.color.colorMorning);
            } else if(CalendarUtils.getDiffBtwDatesPattern(noonTime, currentTime, CalendarUtils.DIFF_TYPE.TYPE_MINUTE, CalendarUtils.TIME_FORMAT) < 0){
                text_pattern = WearableConstants.TEXT_PATTERN_NOON;
                setColor = ColorUtils.getColor(context, R.color.colorNoon);
            } else if(CalendarUtils.getDiffBtwDatesPattern(eveningTime, currentTime, CalendarUtils.DIFF_TYPE.TYPE_MINUTE, CalendarUtils.TIME_FORMAT) < 0){
                text_pattern = WearableConstants.TEXT_PATTERN_LIGHT;
                setColor = ColorUtils.getColor(context, R.color.colorEvening);
            } else if(CalendarUtils.getDiffBtwDatesPattern(nightTime, currentTime, CalendarUtils.DIFF_TYPE.TYPE_MINUTE, CalendarUtils.TIME_FORMAT) < 0){
                text_pattern = WearableConstants.TEXT_PATTERN_LIGHT;
                setColor = ColorUtils.getColor(context, R.color.colorNight);
            }

            return setColor;
        }
    }
}

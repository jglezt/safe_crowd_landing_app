package com.parrot.sdksample;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.parrot.sdksample.view.LandingSite;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Locale;
import java.util.Random;


public abstract class BaseActivity extends AppCompatActivity {
    private static final String TAG = "BaseActivity";
    protected HandlerThread mBackgroundThread;
    protected Handler mBackgroundHandler;
    protected Handler mUIHandler;
    protected TextView countLabel;

    protected TextView fpsView;
    protected TextView msView;
    protected TextView altitudeView;
    protected TextView rollView;
    protected TextView pitchView;
    protected TextView yawView;
    protected TextView tiltView;

    protected SeekBar scalerBar;
    protected ImageView crowdView;

    protected Button buttonHeatDensitymap;
    protected Button saveDensityMap;
    protected Button toogleCrowdModel;

    private static final String FORMAT_MS = "%dms";
    private static final String FORMAT_AVG_MS = "avg:%.0fms";
    private static final String FORMAT_FPS = "%.1fFPS";

    protected volatile boolean heatMapEnabled;
    protected volatile boolean saveDensityMapenabled;

    protected double altitude;
    protected double roll;
    protected double pitch;
    protected double yaw;
    protected double tilt;

    private float scaler = 1;

    protected String modelName;

    public enum CrowdViewState{
        SMALL,
        TRANSPARENT,
        OVERLAP
    }

    protected CrowdViewState crowdViewState;


    @Override
    protected void onCreate(@Nullable Bundle savedInstance) {

        super.onCreate(savedInstance);
        mUIHandler = new Handler(getMainLooper());
        startBackgroundThread();
        heatMapEnabled = false;
        saveDensityMapenabled = false;

        this.modelName = "LCCNN.pt";
    }

    @Override
    protected void onStart(){
        super.onStart();
        this.startBackgroundThread();
        this.startCrowdDetectionThread();
    }

    protected void startBackgroundThread(){
        mBackgroundThread = new HandlerThread("ModelInference");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread(){
        mBackgroundThread.quitSafely();
        try{
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e){
            Log.e("CrowdDemo", "Error on stopping background thread", e);
        }
    }

    // This is a hack solution, a better solution might be needed for a lighter density map generator
    private void saveData(Long tsLong, Bitmap viewMap, Bitmap sceneFrame, Bitmap densityMap){
        final Bitmap densityMapFinal = densityMap;
        final Bitmap sceneFrameFinal = sceneFrame;
        final Bitmap viewMapFinal = viewMap;

        final String path = Environment.getExternalStorageDirectory().toString();
        final String ts = tsLong.toString();

        Runnable r = new Runnable() {
            @Override
            public void run() {


                final File viewFile = new File(path + "/Captures/", ts + "VIEW.jpg");
                final File frameFile = new File(path + "/Captures/", ts + "FRAME.jpg");
                final File densityFile = new File(path + "/Captures/", ts + "DENSITY.jpg");

                File dir =  new File(path + "/Captures/");
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                OutputStream fOutputStream = null;
                try {
                    fOutputStream = new FileOutputStream(viewFile);
                    viewMapFinal.compress(Bitmap.CompressFormat.PNG, 100, fOutputStream);
                    fOutputStream.flush();
                    fOutputStream.close();

                    MediaStore.Images.Media.insertImage(getContentResolver(), viewFile.getAbsolutePath(), viewFile.getName(),
                            viewFile.getName());

                    fOutputStream = new FileOutputStream(frameFile);
                    sceneFrameFinal.compress(Bitmap.CompressFormat.PNG, 100, fOutputStream);

                    fOutputStream.flush();
                    fOutputStream.close();

                    MediaStore.Images.Media.insertImage(getContentResolver(), frameFile.getAbsolutePath(), frameFile.getName(),
                            frameFile.getName());

                    fOutputStream = new FileOutputStream(densityFile);
                    densityMapFinal.compress(Bitmap.CompressFormat.PNG, 100, fOutputStream);

                    fOutputStream.flush();
                    fOutputStream.close();

                    MediaStore.Images.Media.insertImage(getContentResolver(), densityFile.getAbsolutePath(), densityFile.getName(),
                            densityFile.getName());

                    densityMapFinal.recycle();
                    viewMapFinal.recycle();
                    sceneFrameFinal.recycle();

                } catch (FileNotFoundException e) {
                    Log.e(TAG, "Saved failed: " + e.getMessage());
                    return;
                } catch (IOException e) {
                    Log.e(TAG, "Saved failed: " + e.getMessage());
                    return;
                }
            }
        };
        Thread t = new Thread(r);
        t.start();
    }

    private void saveAltitudeAttitude(Long tsLong, double altitude, double roll, double pitch, double yaw, double tilt){
        final String path = Environment.getExternalStorageDirectory().toString();
        final String ts = tsLong.toString();

        final String data = String.format("Altitude: %f\r\nRoll: %f\r\n" +
                "Pitch: %f\r\nYaw: %f\r\nTilt: %f\r\n", altitude, roll, pitch, yaw, tilt);

        Runnable r = new Runnable() {
            @Override
            public void run() {


                final File dataFile = new File(path + "/Captures/", ts + "DATA.txt");
                File dir =  new File(path + "/Captures/");
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                OutputStreamWriter fOutputStreamWriter = null;
                OutputStream fOutputStream = null;
                try {
                    fOutputStream = new FileOutputStream(dataFile);
                    fOutputStreamWriter = new OutputStreamWriter(fOutputStream);

                    fOutputStreamWriter.write(data);


                    fOutputStreamWriter.close();
                    fOutputStream.close();

                } catch (FileNotFoundException e) {
                    Log.e(TAG, "Saved failed: " + e.getMessage());
                    return;
                } catch (IOException e) {
                    Log.e(TAG, "Saved failed: " + e.getMessage());
                    return;
                }
            }
        };
        Thread t = new Thread(r);
        t.start();
    }

    @Override
    protected void onDestroy(){
        stopBackgroundThread();
        super.onDestroy();
    }

    protected abstract void initInference();

    protected abstract void startCrowdDetectionThread();

    protected abstract void drawDesityMap(Bitmap img);

    public abstract void changeCrowdView(View view);

    public abstract void toogleHeatDensityMap(View view);

    public abstract void tootgleSaveDensityMap(View view);

    public abstract void changeCrowdModel(View view);

    protected SeekBar.OnSeekBarChangeListener seekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            scaler = (2 * ((float) progress / 100f)) + 1f;
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    };

    protected class CrowdThread implements Runnable{

        private byte[] frameData;
        private int frameSize;
        private CrowdInference inference;
        private TextureView textureView;

        public CrowdThread(Context context, TextureView textureView){
            this.inference = new CrowdInference(context);
            this.textureView = textureView;
        }

        public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
            int width = bm.getWidth();
            int height = bm.getHeight();
            float scaleWidth = ((float) newWidth) / width;
            float scaleHeight = ((float) newHeight) / height;
            // CREATE A MATRIX FOR THE MANIPULATION
            Matrix matrix = new Matrix();
            // RESIZE THE BIT MAP
            matrix.postScale(scaleWidth, scaleHeight);

            // "RECREATE" THE NEW BITMAP
            Bitmap resizedBitmap = Bitmap.createBitmap(
                    bm, 0, 0, width, height, matrix, false);
            return resizedBitmap;
        }

        @Override
        public void run() {
            int frameIndex = -1;
            float result = -1;
            final Bitmap frame = textureView.getBitmap();
            textureView = null;


            if(frame != null){
                int width = (int) (769f * scaler);
                int  height = (int) (341 * scaler);
                Bitmap scaledFrame = getResizedBitmap(frame, width, height);
                result = inference.analyzeImage(scaledFrame, modelName);
                scaledFrame.recycle();

                //Needed to be run on the UIThread;
                final float finalResult = result;
                Bitmap tempImg = null;
                if(heatMapEnabled){
                    tempImg = inference.getHeatMap();

                }
                else{
                    tempImg = inference.getDensityMap();
                    LandingSite landingSite = new LandingSite(tempImg, width, height);
                    tempImg = landingSite.getSafeZoneBitMap();
                }

                final Bitmap viewImg = tempImg;
                final Bitmap densityImg = inference.getGrayDensityMap();

                final Long tsLong = System.currentTimeMillis()/1000;


                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        BaseActivity.this.startCrowdDetectionThread();
                        BaseActivity.this.drawDesityMap(viewImg);

                        if(viewImg != null){
                            if(saveDensityMapenabled){
                                BaseActivity.this.saveData(tsLong, viewImg, frame, densityImg);
                                BaseActivity.this.saveAltitudeAttitude(tsLong, BaseActivity.this.altitude,
                                        BaseActivity.this.roll, BaseActivity.this.pitch,
                                        BaseActivity.this.yaw, BaseActivity.this.tilt);
                            }

                            else{
                                frame.recycle();
                            }

                        }
                        countLabel.setText("Count: " + Integer.toString((int) finalResult));
                        msView.setText("MS: " + String.format(Locale.US, FORMAT_MS, inference.moduleForwardDuration));
                        fpsView.setText("FPS: " + String.format(Locale.US, FORMAT_FPS, (1000.f / inference.analysisDuration)));
                    }
                });
                return;
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    BaseActivity.this.startCrowdDetectionThread();
                }
            });

        }
    }
}

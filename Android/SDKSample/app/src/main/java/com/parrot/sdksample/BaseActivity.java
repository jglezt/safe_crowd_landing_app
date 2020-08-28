package com.parrot.sdksample;

import android.content.Context;
import android.media.Image;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.SyncStateContract;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARFrame;

import java.util.Date;

public class BaseActivity extends AppCompatActivity {
    private static final String TAG = "BaseActivity";
    protected HandlerThread mBackgroundThread;
    protected Handler mBackgroundHandler;
    protected Handler mUIHandler;

    protected H264Decoding mFrameDecoder;
    protected volatile byte[] frameData;
    protected volatile int frameSize;


    @Override
    protected void onCreate(@Nullable Bundle savedInstance) {

        super.onCreate(savedInstance);
        mUIHandler = new Handler(getMainLooper());
        startBackgroundThread();
        mFrameDecoder = new H264Decoding();
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


    public void configureDecoder(ARControllerCodec codec) {
        mFrameDecoder.configureDecoder(codec);
    }

    @Override
    protected void onDestroy(){
        mFrameDecoder.destroyDecoder();
        stopBackgroundThread();
        super.onDestroy();
    }

    protected synchronized void startCrowdDetectionThread(){
        try{
            mBackgroundHandler.post(new CrowdThread(frameData, frameSize, this.getBaseContext()));
        }catch (NullPointerException e){
            Log.e(TAG, e.getMessage());
        }

    }

    protected class CrowdThread implements Runnable{

        private byte[] frameData;
        private int frameSize;
        private CrowdInference inference;

        public CrowdThread(@Nullable byte[] frameData, int frameSize, Context context){
            this.frameData = frameData;
            this.frameSize = frameSize;
            this.inference = new CrowdInference(context);
        }

        @Override
        public void run() {
            Image frame = null;
            int frameIndex = -1;
            float result = -1;

            if (frameData == null){
                Log.d(BaseActivity.TAG, "Null frame");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        BaseActivity.this.startCrowdDetectionThread();
                    }
                });
            }
            else{
                frameIndex = mFrameDecoder.getLastFrameIndex(frameData, frameSize);
                if(frameIndex < 0){
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            BaseActivity.this.startCrowdDetectionThread();
                        }
                    });
                    return;
                }

                frame =  mFrameDecoder.getLastFrame(frameIndex);
                result = inference.analyzeImage(frame, 0);
                if(frame != null)
                    Log.d(TAG, Integer.toString((int) result));
                mFrameDecoder.releaseFrame(frameIndex);


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

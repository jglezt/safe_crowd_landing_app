package com.parrot.sdksample;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.LinkedList;
import java.util.Queue;

public class CrowdInference {
    private final static String TAG = "CrowdInference";

    private Module mModule;
    private String mModuleAssetName;
    private FloatBuffer mInputTensorBuffer;
    private Tensor mInputTensor;
    private Context context;

    private float[] densityArray;
    private int width;
    private int height;

    public long moduleForwardDuration;
    public long analysisDuration;


    public CrowdInference(Context context){
        this.context = context;
    }


    protected String getModuleAssetName(String model) {
        if (!TextUtils.isEmpty(mModuleAssetName)) {
            return mModuleAssetName;
        }
        final String moduleAssetNameFromIntent = model;
        mModuleAssetName = !TextUtils.isEmpty(moduleAssetNameFromIntent)
                ? moduleAssetNameFromIntent
                : "resnet18.pt";

        return mModuleAssetName;
    }


    public float analyzeImage(Bitmap image, String model) {
        try {
            if (mModule == null) {
                final String moduleFileAbsoluteFilePath = new File(
                        Utils.assetFilePath(context, getModuleAssetName(model))).getAbsolutePath();
                mModule = Module.load(moduleFileAbsoluteFilePath);

            }

            final long startTime = SystemClock.elapsedRealtime();

            mInputTensor = TensorImageUtils.bitmapToFloat32Tensor(image,
                    TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
                    TensorImageUtils.TORCHVISION_NORM_STD_RGB);

            final long moduleForwardStartTime = SystemClock.elapsedRealtime();
            final Tensor outputInference = mModule.forward(IValue.from(mInputTensor)).toTensor();
            this.moduleForwardDuration = SystemClock.elapsedRealtime() - moduleForwardStartTime;

            this.densityArray = outputInference.getDataAsFloatArray();

            this.width = (int) outputInference.shape()[3];
            this.height = (int) outputInference.shape()[2];

            float sum = (float) 0.0;
            for (float v : densityArray) {
                sum += v;
            }


            analysisDuration = SystemClock.elapsedRealtime() - startTime;
            return sum;

        } catch (Exception e) {
            Log.e(TAG, "Error during image analysis", e);
            return -1;
        }
    }

    public Bitmap getDensityMap(){
        // Must be run after the inference.
        return arrayFloatToBitmap(densityArray, this.width, this.height);
    }

    public Bitmap getHeatMap(){
        return arrayFloatToHeatMap(densityArray, this.width, this.height);

    }

    public Bitmap getGrayDensityMap(){
        return arrayFloatToGrayBitmap(densityArray, this.width, this.height);
    }

    public float[] getDensityArray(){
        return this.densityArray;
    }

    private Bitmap arrayFloatToBitmap(float[] floatArray, int width, int height){

        byte alpha = (byte) 255 ;

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) ;

        ByteBuffer byteBuffer = ByteBuffer.allocate(width*height*4*3) ;

        float Maximum = floatArray[0], minimum = floatArray[0];

        for (int i = 1; i < floatArray.length; i++) {
            if (floatArray[i] > Maximum)
                Maximum = floatArray[i];
            if(floatArray[i] < minimum)
                minimum = floatArray[i];
        }

        float delta = Maximum - minimum ;

        int i = 0 ;
        for (float value : floatArray){
            byte temValue = (byte) ((byte) ((((value-minimum)/delta)*255)));
            byteBuffer.put(4*i, temValue) ;
            byteBuffer.put(4*i+1, (byte)0) ;
            byteBuffer.put(4*i+2, (byte) 0) ;
            if(temValue <= (byte) 0)
                byteBuffer.put(4*i+3, (byte) 0);
            else
                byteBuffer.put(4*i+3, alpha) ;
            i++ ;
        }
        bmp.copyPixelsFromBuffer(byteBuffer) ;
        return bmp ;
    }

    private Bitmap arrayFloatToGrayBitmap(float[] floatArray, int width, int height){

        byte alpha = (byte) 255 ;

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) ;

        ByteBuffer byteBuffer = ByteBuffer.allocate(width*height*4*3) ;

        float Maximum = floatArray[0], minimum = floatArray[0];

        for (int i = 1; i < floatArray.length; i++) {
            if (floatArray[i] > Maximum)
                Maximum = floatArray[i];
            if(floatArray[i] < minimum)
                minimum = floatArray[i];
        }

        float delta = Maximum - minimum ;

        int i = 0 ;
        for (float value : floatArray){
            byte temValue = (byte) ((byte) ((((value-minimum)/delta)*255)));
            byteBuffer.put(4*i, temValue);
            byteBuffer.put(4*i+1, (byte) temValue);
            byteBuffer.put(4*i+2, (byte) temValue);
            byteBuffer.put(4*i+3, alpha);
            i++ ;
        }
        bmp.copyPixelsFromBuffer(byteBuffer) ;
        return bmp ;
    }

    private Bitmap arrayFloatToHeatMap(float[] floatArray, int width, int height){

        byte alpha = (byte) 255 ;

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) ;

        ByteBuffer byteBuffer = ByteBuffer.allocate(width*height*4*3) ;

        float Maximum = floatArray[0], minimum = floatArray[0];

        for (int i = 1; i < floatArray.length; i++) {
            if (floatArray[i] > Maximum)
                Maximum = floatArray[i];
            if(floatArray[i] < minimum)
                minimum = floatArray[i];
        }

        float delta = Maximum - minimum ;

        int i = 0 ;
        for (float value : floatArray){
            float temValue = (2f * (value-minimum)/delta);
            int b = (int) Math.max(0f, 255f * (1f - temValue));
            int r = (int) Math.max(0f, 255f * (temValue -1f));
            int g =  (255 - b - r);

            Log.d(TAG, "" + b + " " + r + " " + g);
            byteBuffer.put(4*i, (byte) r) ;
            byteBuffer.put(4*i+1, (byte) g);
            byteBuffer.put(4*i+2, (byte) b) ;
            byteBuffer.put(4*i+3, alpha) ;
            i++ ;
        }
        bmp.copyPixelsFromBuffer(byteBuffer) ;
        return bmp ;
    }
}

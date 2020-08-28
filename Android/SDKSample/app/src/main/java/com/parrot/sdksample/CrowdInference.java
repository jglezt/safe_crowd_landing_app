package com.parrot.sdksample;

import android.content.Context;
import android.media.Image;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.LinkedList;
import java.util.Queue;

public class CrowdInference {
    private final static String TAG = "CrowdInference";

    public static final String INTENT_MODULE_ASSET_NAME = "LCCNN.pt";
    public static final String INTENT_INFO_VIEW_TYPE = "INTENT_INFO_VIEW_TYPE";

    private static final int INPUT_TENSOR_WIDTH = 864;
    private static final int INPUT_TENSOR_HEIGHT = 480;
    private static final int TOP_K = 3;
    private static final int MOVING_AVG_PERIOD = 10;
    private static final String FORMAT_MS = "%dms";
    private static final String FORMAT_AVG_MS = "avg:%.0fms";

    private static final String FORMAT_FPS = "%.1fFPS";
    public static final String SCORES_FORMAT = "%.2f";

    static class AnalysisResult {
        private final long analysisDuration;
        private final long moduleForwardDuration;
        final float totalCount;

        public AnalysisResult(long moduleForwardDuration, long analysisDuration, float totalCount) {
            this.moduleForwardDuration = moduleForwardDuration;
            this.analysisDuration = analysisDuration;
            this.totalCount = totalCount;
        }
    }

    private boolean mAnalyzeImageErrorState;
    private TextView mFpsText;
    private TextView mMsText;
    private TextView countingText;
    private TextView mMsAvgText;
    private Module mModule;
    private String mModuleAssetName;
    private FloatBuffer mInputTensorBuffer;
    private Tensor mInputTensor;
    private long mMovingAvgSum = 0;
    private Queue<Long> mMovingAvgQueue = new LinkedList<>();
    private Context context;

    public CrowdInference(Context context){
        this.context = context;
    }


    protected String getModuleAssetName() {
        if (!TextUtils.isEmpty(mModuleAssetName)) {
            return mModuleAssetName;
        }
        final String moduleAssetNameFromIntent = INTENT_MODULE_ASSET_NAME;
        mModuleAssetName = !TextUtils.isEmpty(moduleAssetNameFromIntent)
                ? moduleAssetNameFromIntent
                : "resnet18.pt";

        return mModuleAssetName;
    }


    public float analyzeImage(Image image, int rotationDegrees) {
        if (mAnalyzeImageErrorState) {
            return -1;
        }

        try {
            if (mModule == null) {
                final String moduleFileAbsoluteFilePath = new File(
                        Utils.assetFilePath(context, getModuleAssetName())).getAbsolutePath();
                mModule = Module.load(moduleFileAbsoluteFilePath);

                mInputTensorBuffer =
                        Tensor.allocateFloatBuffer(3 * INPUT_TENSOR_WIDTH * INPUT_TENSOR_HEIGHT);
                mInputTensor = Tensor.fromBlob(mInputTensorBuffer, new long[]{1, 3, INPUT_TENSOR_HEIGHT, INPUT_TENSOR_WIDTH});
            }

            final long startTime = SystemClock.elapsedRealtime();
            TensorImageUtils.imageYUV420CenterCropToFloatBuffer(
                    image, rotationDegrees,
                    INPUT_TENSOR_WIDTH, INPUT_TENSOR_HEIGHT,
                    TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
                    TensorImageUtils.TORCHVISION_NORM_STD_RGB,
                    mInputTensorBuffer, 0);

            final long moduleForwardStartTime = SystemClock.elapsedRealtime();
            final Tensor outputTensor = mModule.forward(IValue.from(mInputTensor)).toTensor();
            final long moduleForwardDuration = SystemClock.elapsedRealtime() - moduleForwardStartTime;

            final float[] densityMap = outputTensor.getDataAsFloatArray();

            float sum = (float) 0.0;
            for (float v : densityMap) {
                sum += v;
            }


            final long analysisDuration = SystemClock.elapsedRealtime() - startTime;
            return sum;

        } catch (Exception e) {
            Log.e(TAG, "Error during image analysis", e);
            return -1;
        }
    }
}

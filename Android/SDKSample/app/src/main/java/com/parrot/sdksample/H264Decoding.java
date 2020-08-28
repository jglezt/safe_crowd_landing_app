package com.parrot.sdksample;

import android.annotation.SuppressLint;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import com.parrot.arsdk.arcontroller.ARCONTROLLER_STREAM_CODEC_TYPE_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARFrame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class H264Decoding{

    private static final String TAG = "H264Decoding";
    private static final String VIDEO_MIME_TYPE = "video/avc";
    private static final int VIDEO_DEQUEUE_TIMEOUT = 33000;

    private MediaCodec mMediaCodec;
    private Lock mReadyLock;

    private boolean mIsCodecConfigured = false;

    private ByteBuffer mSpsBuffer;
    private ByteBuffer mPpsBuffer;

    private ByteBuffer[] mBuffers;

    private static final int VIDEO_WIDTH = 864;
    private static final int VIDEO_HEIGHT = 480;

    public H264Decoding() {
        customInit();
    }

    private void customInit() {
        mReadyLock = new ReentrantLock();
    }

    @SuppressLint("WrongConstant")
    public int getLastFrameIndex(byte[] frameData, int frameSize) {
        mReadyLock.lock();
        Image lastImage = null;
        int outIndex1 = -1, outIndex2 = -1;


        if ((mMediaCodec != null)) {
            if (mIsCodecConfigured) {
                // Here we have either a good PFrame, or an IFrame
                int index = -1;

                try {
                    index = mMediaCodec.dequeueInputBuffer(-1);
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Error while dequeue input buffer");
                }
                if (index >= 0) {
                    ByteBuffer b;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        b = mMediaCodec.getInputBuffer(index);
                    } else {
                        b = mBuffers[index];
                        b.clear();
                    }

                    if (b != null) {
                        b.put(frameData, 0, frameSize);
                    }

                    try {
                        mMediaCodec.queueInputBuffer(index, 0, frameSize, 0, 0);
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "Error while queue input buffer");
                    }
                }
            }

            // Get the most current frame index from the MediaCodec and
            // dispose all the previus frames.
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            try {
                outIndex1 = mMediaCodec.dequeueOutputBuffer(info,  0);

                while (outIndex1 >= 0) {
                    /*Image temp = mMediaCodec.getInputImage(outIndex1);
                    if(temp != null)
                        Log.d(TAG, temp.toString());*/
                    outIndex2 = mMediaCodec.dequeueOutputBuffer(info, 0);
                    if(outIndex2 < 0){
                        break;
                    }
                    this.releaseFrame(outIndex1);
                    outIndex1 = outIndex2;
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error while dequeue input buffer (outIndex)");
            }
        }


        mReadyLock.unlock();
        return outIndex1;
    }

    public Image getLastFrame(int index){
        try {
            return mMediaCodec.getOutputImage(index);
        }catch (IllegalStateException e){
            Log.d(TAG,"Error while retrieving: Frame does not exist.");
            return null;
        }
    }

    public void releaseFrame(int index) {
        try{
            mMediaCodec.releaseOutputBuffer(index, false);
        }catch (IllegalStateException e){
            Log.e(TAG, "Error while releasing: Frame does not exist.");
        }catch (NullPointerException e){
            Log.e(TAG, e.getMessage());
        }
    }

    public void configureDecoder(ARControllerCodec codec) {
        mReadyLock.lock();

        if (codec.getType() == ARCONTROLLER_STREAM_CODEC_TYPE_ENUM.ARCONTROLLER_STREAM_CODEC_TYPE_H264) {
            ARControllerCodec.H264 codecH264 = codec.getAsH264();

            mSpsBuffer = ByteBuffer.wrap(codecH264.getSps().getByteData());
            mPpsBuffer = ByteBuffer.wrap(codecH264.getPps().getByteData());
        }

        if ((mMediaCodec != null) && (mSpsBuffer != null)) {
            configureMediaCodec();
        }
        initMediaCodec(VIDEO_MIME_TYPE);
        mReadyLock.unlock();
    }

    private void configureMediaCodec() {
        mMediaCodec.stop();
        MediaFormat format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT);
        format.setByteBuffer("csd-0", mSpsBuffer);
        format.setByteBuffer("csd-1", mPpsBuffer);

        mMediaCodec.configure(format, null, null, 0);
        mMediaCodec.start();

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
            mBuffers = mMediaCodec.getInputBuffers();
        }

        mIsCodecConfigured = true;
    }

    private void initMediaCodec(String type) {
        try {
            mMediaCodec = MediaCodec.createDecoderByType(type);
        } catch (IOException e) {
            Log.e(TAG, "Exception", e);
        }

        if ((mMediaCodec != null) && (mSpsBuffer != null)) {
            configureMediaCodec();
        }
    }

    private void releaseMediaCodec() {
        if (mMediaCodec != null) {
            if (mIsCodecConfigured) {
                mMediaCodec.stop();
                mMediaCodec.release();
            }
            mIsCodecConfigured = false;
            mMediaCodec = null;
        }
    }


    public void destroyDecoder() {
        mReadyLock.lock();
        releaseMediaCodec();
        mReadyLock.unlock();
    }
}

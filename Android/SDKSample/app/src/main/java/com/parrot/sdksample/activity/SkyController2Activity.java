package com.parrot.sdksample.activity;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARFrame;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.sdksample.BaseActivity;
import com.parrot.sdksample.R;
import com.parrot.sdksample.drone.SkyController2Drone;
import com.parrot.sdksample.view.H264VideoView;

public class SkyController2Activity extends BaseActivity {
    private static final String TAG = "SkyController2Activity";
    private SkyController2Drone mSkyController2Drone;

    private ProgressDialog mConnectionProgressDialog;
    private ProgressDialog mDownloadProgressDialog;

    private H264VideoView mVideoView;

    private TextView mDroneBatteryLabel;
    private TextView mSkyController2BatteryLabel;
    private Button mTakeOffLandBt;
    private TextView mDroneConnectionLabel;

    private int mNbMaxDownload;
    private int mCurrentDownloadIndex;
    private Button toogleCrowdView;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skycontroller2);

        initIHM();
        this.initInference();

        Intent intent = getIntent();
        ARDiscoveryDeviceService service = intent.getParcelableExtra(DeviceListActivity.EXTRA_DEVICE_SERVICE);
        mSkyController2Drone = new SkyController2Drone(this, service);
        mSkyController2Drone.addListener(mSkyController2Listener);

    }

    @Override
    protected void onStart() {
        super.onStart();

        // show a loading view while the bebop drone is connecting
        if ((mSkyController2Drone != null) &&
                !(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(mSkyController2Drone.getSkyController2ConnectionState())))
        {
            mConnectionProgressDialog = new ProgressDialog(this, R.style.AppCompatAlertDialogStyle);
            mConnectionProgressDialog.setIndeterminate(true);
            mConnectionProgressDialog.setMessage("Connecting ...");
            mConnectionProgressDialog.setCancelable(false);
            mConnectionProgressDialog.show();

            // if the connection to the Bebop fails, finish the activity
            if (!mSkyController2Drone.connect()) {
                finish();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mSkyController2Drone != null)
        {
            mConnectionProgressDialog = new ProgressDialog(this, R.style.AppCompatAlertDialogStyle);
            mConnectionProgressDialog.setIndeterminate(true);
            mConnectionProgressDialog.setMessage("Disconnecting ...");
            mConnectionProgressDialog.setCancelable(false);
            mConnectionProgressDialog.show();

            if (!mSkyController2Drone.disconnect()) {
                finish();
            }
        } else {
            finish();
        }
    }

    @Override
    public void onDestroy() {
        mSkyController2Drone.dispose();
        super.onDestroy();
    }

    @Override
    protected void initInference() {
        countLabel = (TextView) findViewById(R.id.countView);

        fpsView = (TextView) findViewById((R.id.fpsView));
        msView = (TextView) findViewById(R.id.msView);

        crowdView = (ImageView) findViewById(R.id.crowdView);
        RelativeLayout frameLayout = (RelativeLayout) findViewById(R.id.piloting_view);
        frameLayout.removeView(crowdView);
        frameLayout.addView(crowdView);
        crowdView.setImageResource(R.mipmap.ic_launcher);
        crowdView.setAlpha(1f);

        toogleCrowdView = (Button) findViewById(R.id.toggleCrowdView);
        crowdViewState = BebopActivity.CrowdViewState.SMALL;

        buttonHeatDensitymap = findViewById(R.id.buttonHeatDensityMap);
        buttonHeatDensitymap.setText("DENSITY MAP");

        toogleCrowdModel = (Button) findViewById(R.id.toogleCrowdModel);
        toogleCrowdModel.setText("SH B");

        scalerBar = (SeekBar) findViewById(R.id.scalerBar);
        scalerBar.setOnSeekBarChangeListener(seekBarChangeListener);

        saveDensityMap = (Button) findViewById(R.id.saveDensityMap);
        saveDensityMap.setText("SAVE DENSITY MAP");

        //I shuld change this to somewhere else
        altitudeView = (TextView) findViewById(R.id.altitudeView);
        rollView = (TextView) findViewById(R.id.rollView);
        pitchView = (TextView) findViewById(R.id.pitchView);
        yawView = (TextView) findViewById(R.id.yawView);
        tiltView = (TextView) findViewById(R.id.tiltView);

        tilt = 0;
        altitude = 0;
        roll = 0;
        pitch = 0;
        yaw = 0;
    }

    @Override
    protected void startCrowdDetectionThread() {
        try{
            mBackgroundHandler.post(new CrowdThread(this.getBaseContext(), mVideoView));
        }catch (NullPointerException e){
            Log.e(TAG, e.getMessage());
        }
    }

    @Override
    protected void drawDesityMap(Bitmap img) {
        crowdView.setImageBitmap(img);
    }

    @Override
    public void changeCrowdView(View view) {
        switch (crowdViewState){
            case SMALL:
                crowdView.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
                        RelativeLayout.LayoutParams.MATCH_PARENT));
                crowdViewState = CrowdViewState.TRANSPARENT;
                toogleCrowdView.setText("OVERLAP");
                crowdView.setAlpha(0.5f);
                break;
            case TRANSPARENT:
                crowdView.setAlpha(1f);
                crowdViewState = CrowdViewState.OVERLAP;
                toogleCrowdView.setText("SMALL");
                countLabel.bringToFront();
                break;
            case OVERLAP:
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);
                params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                crowdView.setLayoutParams(params);
                crowdViewState = CrowdViewState.SMALL;
                toogleCrowdView.setText("TRANSPARENT");
                break;
        }
    }

    @Override
    public void toogleHeatDensityMap(View view) {
        if(buttonHeatDensitymap.getText() == "DENSITY MAP"){
            buttonHeatDensitymap.setText("Heat map");
            heatMapEnabled = true;
        }
        else{
            buttonHeatDensitymap.setText("DENSITY MAP");
            heatMapEnabled = false;
        }

    }

    @Override
    public void tootgleSaveDensityMap(View view) {
        if(saveDensityMap.getText() == "SAVE DENSITY MAP"){
            saveDensityMap.setText("STOP SAVING");
            saveDensityMapenabled = true;
        }
        else{
            saveDensityMap.setText("SAVE DENSITY MAP");
            saveDensityMapenabled = false;
        }

    }

    @Override
    public void changeCrowdModel(View view) {
        if(toogleCrowdModel.getText() == "SH B"){
            toogleCrowdModel.setText("QNRF");
            modelName = "LCCNN_SHB.pt";
        }
        else{
            toogleCrowdModel.setText("SH B");
            modelName = "LCCNN.pt";

        }
    }

    private void initIHM() {
        mVideoView = (H264VideoView) findViewById(R.id.videoView);


        mTakeOffLandBt = (Button) findViewById(R.id.takeOffOrLandBt);
        mTakeOffLandBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                switch (mSkyController2Drone.getFlyingState()) {
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                        mSkyController2Drone.takeOff();
                        break;
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                        mSkyController2Drone.land();
                        break;
                    default:
                }
            }
        });




        mSkyController2BatteryLabel = (TextView) findViewById(R.id.skyBatteryLabel);
        mDroneBatteryLabel = (TextView) findViewById(R.id.droneBatteryLabel);

        mDroneConnectionLabel = (TextView) findViewById(R.id.droneConnectionLabel);
    }

    private final SkyController2Drone.Listener mSkyController2Listener = new SkyController2Drone.Listener() {
        @Override
        public void onSkyController2ConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
            switch (state)
            {
                case ARCONTROLLER_DEVICE_STATE_RUNNING:
                    mConnectionProgressDialog.dismiss();
                    // if no drone is connected, display a message
                    if (!ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(mSkyController2Drone.getDroneConnectionState())) {
                        mDroneConnectionLabel.setVisibility(View.VISIBLE);
                    }
                    break;

                case ARCONTROLLER_DEVICE_STATE_STOPPED:
                    // if the deviceController is stopped, go back to the previous activity
                    mConnectionProgressDialog.dismiss();
                    finish();
                    break;

                default:
                    break;
            }
        }

        @Override
        public void onDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
            switch (state)
            {
                case ARCONTROLLER_DEVICE_STATE_RUNNING:
                    mDroneConnectionLabel.setVisibility(View.GONE);
                    break;

                default:
                    mDroneConnectionLabel.setVisibility(View.VISIBLE);
                    break;
            }
        }

        @Override
        public void onSkyController2BatteryChargeChanged(int batteryPercentage) {
            mSkyController2BatteryLabel.setText(String.format("%d%%", batteryPercentage));
        }

        @Override
        public void onDroneBatteryChargeChanged(int batteryPercentage) {
            mDroneBatteryLabel.setText(String.format("%d%%", batteryPercentage));
        }

        @Override
        public void onPilotingStateChanged(ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state) {
            switch (state) {
                case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                    mTakeOffLandBt.setText("Take off");
                    mTakeOffLandBt.setEnabled(true);
                    break;
                case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
                case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                    mTakeOffLandBt.setText("Land");
                    mTakeOffLandBt.setEnabled(true);
                    break;
                default:
                    mTakeOffLandBt.setEnabled(false);
            }
        }

        @Override
        public void onPictureTaken(ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error) {
            Log.i(TAG, "Picture has been taken");
        }

        @Override
        public void configureDecoder(ARControllerCodec codec) {
            mVideoView.configureDecoder(codec);
        }

        @Override
        public void onFrameReceived(ARFrame frame) {
            mVideoView.displayFrame(frame);
            //mFrameDecoding.displayFrame(frame);
        }

        @Override
        public void onMatchingMediasFound(int nbMedias) {
            mDownloadProgressDialog.dismiss();

            mNbMaxDownload = nbMedias;
            mCurrentDownloadIndex = 1;

            if (nbMedias > 0) {
                mDownloadProgressDialog = new ProgressDialog(SkyController2Activity.this, R.style.AppCompatAlertDialogStyle);
                mDownloadProgressDialog.setIndeterminate(false);
                mDownloadProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mDownloadProgressDialog.setMessage("Downloading medias");
                mDownloadProgressDialog.setMax(mNbMaxDownload * 100);
                mDownloadProgressDialog.setSecondaryProgress(mCurrentDownloadIndex * 100);
                mDownloadProgressDialog.setProgress(0);
                mDownloadProgressDialog.setCancelable(false);
                mDownloadProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mSkyController2Drone.cancelGetLastFlightMedias();
                    }
                });
                mDownloadProgressDialog.show();
            }
        }

        @Override
        public void onDownloadProgressed(String mediaName, int progress) {
            mDownloadProgressDialog.setProgress(((mCurrentDownloadIndex - 1) * 100) + progress);
        }

        @Override
        public void onDownloadComplete(String mediaName) {
            mCurrentDownloadIndex++;
            mDownloadProgressDialog.setSecondaryProgress(mCurrentDownloadIndex * 100);

            if (mCurrentDownloadIndex > mNbMaxDownload) {
                mDownloadProgressDialog.dismiss();
                mDownloadProgressDialog = null;
            }
        }

        @Override
        public void onAltitudeRecived(double altitude) {
            altitudeView.setText(String.format("Altitude: %.2f", altitude));
            SkyController2Activity.super.altitude = altitude;
        }

        @Override
        public void onAttitudeReceived(double roll, double pitch, double yaw) {
            rollView.setText(String.format("Roll : %.2f", roll));
            pitchView.setText(String.format("Pitch: %.2f", pitch));
            yawView.setText(String.format("Yaw: %.2f", yaw));
            SkyController2Activity.super.roll =roll;
            SkyController2Activity.super.pitch = pitch;
            SkyController2Activity.super.yaw = yaw;
        }

        @Override
        public void onCameraOrientationReceived(double tilt, double pan) {
            tiltView.setText(String.format("Tilt; %.2f", tilt));
            SkyController2Activity.super.tilt = tilt;
        }
    };
}

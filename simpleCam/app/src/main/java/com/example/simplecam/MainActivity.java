package com.example.simplecam;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static  final String TAG = "AndroidCameraApi";

    static {
        if(!(OpenCVLoader.initDebug())) {}
        else {}
    }

    private Button takePictureButton;
    private TextureView textureView;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static
    {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);

    }

    private String cameraID;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder; // not comfortable
    private Size imageDimension;
    private ImageReader imageReader;
    private File file;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final int REQUEST_EXTERNAL_STORAGE = 2;
    private boolean mFlashSupported;
    private Handler camBackgroundHandler;
    private HandlerThread camBackgroundThread;
    private Handler processHandler;
    private HandlerThread processThread;
    private ImageView imageView;
    static private int frameCount;
    static private  Bitmap map1;
    static private int previous_up = 0;
    static private int previous_mid = 0;
    static private int previous_down = 0;
    static private int state_up = 0;
    static private int state_mid = 0;
    static private int state_down = 0;
    static private int counter = 0;
    static private String previous_Comb = "000";
    static private String current_Comb = "000";
    static private int seqCount =0;
    static private ArrayList<Integer> packet = new ArrayList<Integer>();
    static private ArrayList<String> packetV2 = new ArrayList<String>();
    static private Boolean Control = true;
    static private String states[] = {"000","001","011","101","110","111"};
    static private String packet_v2 = "";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textureView = (TextureView) findViewById(R.id.texture);
        imageView = findViewById(R.id.imageView);
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);
        takePictureButton = (Button) findViewById(R.id.btn_takepicture);
        assert takePictureButton != null;




        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Control = true;
                Log.d("OpenCV-logInfo",  "Control: " + Control);
                Toast.makeText(MainActivity.this, packet_v2, Toast.LENGTH_LONG).show();
                packet_v2 = "";
                previous_Comb = "000";
                current_Comb = "000";
                if(!packet.isEmpty())packet.clear();
            }
        });
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() { // Group Deal
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();

        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {



        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.e(TAG, "onOpened");

            cameraDevice = camera;
            createCameraPreview(); // Handle Preview
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Toast.makeText(MainActivity.this,"Saved: " + file, Toast.LENGTH_SHORT).show(); // Like Pop up
            createCameraPreview();
        }
    }; // Capture States

    protected void startBackgroundThread()
    {
        camBackgroundThread = new HandlerThread("Camera Background");
        camBackgroundThread.start();
        camBackgroundHandler = new Handler(camBackgroundThread.getLooper());
    }
    protected void stopBackgroundThread()
    {
        camBackgroundThread.quitSafely();
        try{
            camBackgroundThread.join();
            camBackgroundThread = null;
            camBackgroundHandler = null;
        } catch (InterruptedException e) { e.printStackTrace();}
    }
    protected void takePicture()
    {
        if(cameraDevice == null) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId()); // Set camera characteristics

            Size[] jpegSizes = null;
            if(characteristics != null) jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat .YUV_420_888 );
            int width = 640;
            int height = 480;
            if(jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }

            ImageReader reader = ImageReader.newInstance(width,height, ImageFormat.JPEG, 1);

            List<Surface> outputSurface = new ArrayList<Surface>(2);
            outputSurface.add(reader.getSurface());
            outputSurface.add(new Surface(textureView.getSurfaceTexture()));

            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE); // Still capture mode
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_AE_MODE_OFF); // AE mode

            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            SimpleDateFormat unique  = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault());
            final File file = new File( "/storage/emulated/0/DCIM/Camera/IMG_" + unique.format(new Date()) + ".jpg" ); // Saving Sort of
            Log.d("OpenCV-logInfo", "Bitmap1 ");


            ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try{
                        image = reader.acquireLatestImage();
                        ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[byteBuffer.capacity()];
                        byteBuffer.get(bytes);
                        //imgProcess2(bytes);
                        save(bytes);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if(image != null) image.close();
                    }

                }
                private void save(byte[] bytes) throws IOException
                {
                    OutputStream output = null;
                    try {
                        output = new FileOutputStream(file);
                        output.write(bytes);
                    } finally {
                        if(output != null) output.close();
                    }
                }
            };
            reader.setOnImageAvailableListener(onImageAvailableListener, camBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(MainActivity.this,"Saved: " + file, Toast.LENGTH_SHORT).show(); // Like Pop up
                    createCameraPreview();
                }
            }; // Capture States
            cameraDevice .createCaptureSession(outputSurface, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, camBackgroundHandler );
                    } catch (CameraAccessException e) { e.printStackTrace(); }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) { }
            }, camBackgroundHandler);

        } catch (CameraAccessException e) { e.printStackTrace(); }
    }
    protected void createCameraPreview()
    {
        try {
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            assert surfaceTexture != null;
            surfaceTexture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(surfaceTexture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if(cameraDevice == null) return;
                    cameraCaptureSessions = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(MainActivity.this, "Configurartion change", Toast.LENGTH_SHORT).show();
                }
            }, null);

        } catch (CameraAccessException e) { e.printStackTrace(); }
    }
    private void openCamera()
    {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        Log.e(TAG, "is camera open");
        try {
            cameraID = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraID);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0]; // Below is permissions
            if(checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            if(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_EXTERNAL_STORAGE);
                return;
            }


            manager.openCamera(cameraID, stateCallback, null);

        } catch (CameraAccessException e) { e.printStackTrace(); }
        Log.e(TAG, "openCamera X");
    }
    private void updatePreview()
    {
        if(cameraDevice == null) Log.e(TAG, "updatePreview error, return");
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), setMyListener(), camBackgroundHandler);
        } catch (CameraAccessException e) { e.printStackTrace(); }
    }
    private void closeCamera()
    {
        if(cameraDevice != null)
        {
            cameraDevice.close();
            cameraDevice = null;
        }
        if(imageReader != null)
        {
            imageReader.close();
            imageReader = null;
        }
    }
    public void onRequestPermissionResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)  // Permission
    {
        super .onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_CAMERA_PERMISSION)
        {
            if(grantResults[0] == PackageManager.PERMISSION_DENIED)
            {
                Toast.makeText(MainActivity.this, "We need permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
    protected void onResume()
    {
        super.onResume();
        Log.e(TAG, "onResume");
        startBackgroundThread();
        if(textureView.isAvailable()) openCamera();
        else textureView.setSurfaceTextureListener(textureListener);
    }
    protected void onPause()
    {
        Log.e(TAG, "onPause");
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }
    public Mat threshProcess(Mat mat_input)
    {
        Mat gray = new Mat();
        Mat thresh = new Mat();
        Imgproc.cvtColor(mat_input,gray,Imgproc.COLOR_RGB2GRAY);
        Imgproc.medianBlur(gray,gray,5);
        Imgproc.threshold(gray,thresh,250,255,Imgproc.THRESH_BINARY); // 200 and 210 fine
        return thresh;
    }
    public Mat reshapeProcess(Mat mat_input)
    {
        int width = mat_input.width();
        int height = mat_input.height();
        int width_size = width /4;
        int height_size = height /4;
        return new Mat(mat_input,new Rect((width-width_size)/2,(height-height_size)/2,width_size,height_size));
    }
    public Mat reshapeProcess2(Mat mat_input)
    {
        int width = mat_input.width();
        int height = mat_input.height();
        int width_size = width /4;
        int height_size = height /4;
        return new Mat(mat_input,new Rect((width-width_size)/2-width_size*5/4,(height-height_size)/2,width_size,height_size));
    }

    public void imgProcess3()
    {
        // Basics
        Bitmap bitmap = textureView.getBitmap();
        Mat img_mat = new Mat();
        Utils.bitmapToMat(bitmap,img_mat);
        img_mat = threshProcess(img_mat);

        // Set Dimensions
        int width = img_mat.width();
        int height = img_mat.height();

        // Left
        Mat up = new Mat(img_mat,new Rect(0,0,width,height/5)); // Up
        Scalar scalar_up = Core.mean(up);
        double up_val = scalar_up.val[0];


        // Mid
        Mat mid = new Mat(img_mat, new Rect(0,height*2/5,width,height/5)); // mid 1 0 1 0  1
        Scalar scalar_mid = Core.mean(mid);
        double mid_val = scalar_mid.val[0];

        // Right
        Mat down = new Mat(img_mat, new Rect(0,height*4/5,width,height/5)); // down
        Scalar scalar_down = Core.mean(down);
        double down_val = scalar_down.val[0];

        // calculate
        frameCount ++;

        // Current Bits
        int up_bit = 0;
        if(up_val > 0) up_bit = 1;

        int mid_bit = 0;
        if(mid_val > 0) mid_bit = 1;

        int down_bit = 0;
        if(down_val > 0) down_bit = 1;

        // Now we have both previous and current bits Func
        if(previous_up != up_bit) state_up = up_bit;
        if(previous_mid != mid_bit) state_mid = mid_bit;
        if(previous_down != down_bit) state_down = down_bit;


        // setPrevious
        previous_up = up_bit;
        previous_mid = mid_bit;
        previous_down = down_bit;
        String comb = "" + state_down + state_mid + state_up;
        if(comb.equals("001") || comb.equals("011") || comb.equals("101") || comb.equals("110")|| comb.equals("111") ) current_Comb ="" + state_down + state_mid + state_up;
        //if(comb.equals("001") || comb.equals("011") || comb.equals("101") || comb.equals("110")/*|| comb.equals("111") */) current_Comb ="" + state_down + state_mid + state_up;
        Log.d("OpenCV-logInfo2",  "Bit Seq2:  "+ current_Comb);
        /*int myBit = extractBit(current_Comb,previous_Comb);
        if(myBit != -1)
        {
            Log.d("OpenCV-logInfo",  "Bit Seq:  "+ myBit + "                                                   Count: " + seqCount);
            packet.add(myBit);
            seqCount ++;

        }
        if(comb.equals("111")&& Control)
        {
            String str = "";
            for(int i=0; i<packet.size();++i)
            {
             str += packet.get(i);
            }
            Log.d("OpenCV-logInfo",  "Packet: " + str);
            Control = false;
        }*/

        String myBit = extractBit2(current_Comb,previous_Comb);
        if(!myBit.equals("-"))
        {
            Log.d("OpenCV-logInfo",  "Bit Seq:  "+ myBit + "                                                   Count: " + seqCount);
            packet_v2 += myBit;
            packetV2.add(myBit);
            seqCount ++;
        }

        previous_Comb = current_Comb;


    }
    public int extractBit(String current, String prev)
    {
        int output = -1;
        switch(prev)
        {
            case "001" :
                if(current.equals("011"))
                    output = 0;

                else if(current.equals("101"))
                    output = 1;
                break;
            case "011" :
                if(current.equals("001"))
                    output = 0;

                else if(current.equals("101"))
                    output = 1;
                break;
            case "101" :
                if(current.equals("110"))
                    output = 1;

                else if(current.equals("001"))
                    output = 0;
                break;
            case "110" :
                if(current.equals("101"))
                    output = 1;

                else if(current.equals("001"))
                    output = 0;
                break;
        }

        return output;
    }
    public String extractBit2(String current, String prev)
    {
        String output = "-";
        switch(prev)
        {
            case "000" : // State 1

                if(current.equals(states[2]))
                    output = "00";

                else if(current.equals(states[3]))
                    output = "01";

                else if(current.equals(states[4]))
                    output = "10";

                else if(current.equals(states[5]))
                    output = "11";

                break;
            case "001" : // State 1

                if(current.equals(states[2]))
                    output = "00";

                else if(current.equals(states[3]))
                    output = "01";

                else if(current.equals(states[4]))
                output = "10";

                else if(current.equals(states[5]))
                output = "11";

                break;
            case "011" : // State 2

                if(current.equals(states[1]))
                    output = "00";

                else if(current.equals(states[3])) {
                    output = "01";
                    Log.d("OpenCV-logInfo", "Abooeeeee ");
                }

                else if(current.equals(states[4]))
                    output = "10";

                else if(current.equals(states[5]))
                    output = "11";

                break;
            case "101" : // State 3

                if(current.equals(states[1]))
                    output = "00";

                else if(current.equals(states[2]))
                    output = "01";

                else if(current.equals(states[4]))
                    output = "10";

                else if(current.equals(states[5]))
                    output = "11";

                break;
            case "110" : // State 4

                if(current.equals(states[1]))
                    output = "00";

                else if(current.equals(states[2]))
                    output = "01";

                else if(current.equals(states[3]))
                    output = "10";

                else if(current.equals(states[5]))
                    output = "11";

                break;
            case "111" : // State 5

                if(current.equals(states[1]))
                    output = "00";

                else if(current.equals(states[2]))
                    output = "01";

                else if(current.equals(states[3]))
                    output = "10";

                else if(current.equals(states[4]))
                    output = "11";

                break;
        }

        return output;
    }

    public CameraCaptureSession.CaptureCallback setMyListener() {
        return  new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                super.onCaptureStarted(session, request, timestamp, frameNumber);

            }

            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                imgProcess3();
            }
        };
    }

} // end of main

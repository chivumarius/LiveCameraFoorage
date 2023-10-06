package com.chivumarius.livecamerafoorage;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.nio.ByteBuffer;


public class MainActivity extends AppCompatActivity implements ImageReader.OnImageAvailableListener{



    // ▬▬  "ON CREATE" METHOD  ▬▬
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // ▼ ASKING FOR CAMERA PERMISSION ▼
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            // ▼ IF PERMISSION IS DENIED ▼
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {

                // ▼ "ASK" FOR "PERMISSION" ▼
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.CAMERA}, 121);

            } else {
                // ▼ SHOW LIVE CAMERAS FOOTAGE ▼
                setFragment();
            }
        } else {
            // ▼ SHOW LIVE CAMERAS FOOTAGE ▼
            setFragment();
        }
    }





    // ▬▬  "ON REQUEST PERMISSIONS RESULT()" METHOD  ▬▬
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // ▼ SHOW LIVE CAMERAS FOOTAGE ▼
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // ▼ SHOW LIVE CAMERAS FOOTAGE ▼
            setFragment();
        } else {
            finish();
        }
    }





    // ▼ FRAGMENT WHICH SHOWS LIVE FOOTAGE FROM CAMERA ▼
    int previewHeight = 0;
    int previewWidth = 0;
    int sensorOrientation;



    // ▬▬  "SET FRAGMENT()" METHOD  ▬▬
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    protected void setFragment() {

        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        String cameraId = null;


        try {
            cameraId = manager.getCameraIdList()[0];
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }


        CameraConnectionFragment fragment;

        // ▼ FRAGMENT WHICH SHOWS LIVE FOOTAGE FROM CAMERA ▼
        CameraConnectionFragment camera2Fragment =
                CameraConnectionFragment.newInstance(
                        new CameraConnectionFragment.ConnectionCallback() {

                            @Override
                            public void onPreviewSizeChosen(final Size size, final int rotation) {
                                previewHeight = size.getHeight();
                                previewWidth = size.getWidth();
                                Log.d("tryOrientation","rotation: "+rotation+"   orientation: "+getScreenOrientation()+"  "+previewWidth+"   "+previewHeight);
                                sensorOrientation = rotation - getScreenOrientation();
                            }
                        },
                        this,
                        R.layout.camera_fragment,
                        new Size(640, 480)
                );

        camera2Fragment.setCamera(cameraId);
        fragment = camera2Fragment;
        getFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .commit();
    }






    // ▬▬  "GET SCREEN ORIENTATION" METHOD  ▬▬
    protected int getScreenOrientation() {

        // ▼ SWITCH CASE ▼
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }





    // ▬▬ "CONVERTING": "FRANES" INTO "BITMAPS" ▬▬
    // ▼ "GETTING FRAMES" OF "LIVE CAMERA FOOTAGE"
    //      → AND "PASSING THEM" TO "MODEL" ▼

    // ▼ VARIABLES ▼
    private boolean isProcessingFrame = false;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;
    private Runnable postInferenceCallback;
    private Runnable imageConverter;
    private Bitmap rgbFrameBitmap;



    // ▬▬  "ON IMAGE AVAILABLE" METHOD  ▬▬
    @Override
    public void onImageAvailable(ImageReader reader) {

        // ▼ WE "NEED" TO "WAIT" UNTIL WE HAVE "SOME SIZE"
        //      → FROM "ON PREVIEW SIZE CHOSEN()" ▼
        // ▼ IF "CAMERA FOOTAGE" → IS NOT BEING "DISPLAYED" ▼
        if (previewWidth == 0 || previewHeight == 0) {
            return;
        }

        if (rgbBytes == null) {
            // ▼ INITIALIZE "BYTE ARRAY"
            //      → IN WHICH WE "STORE"
            //      → THE "BYTES" OF "PARTICULAR FRAME" ▼
            rgbBytes = new int[previewWidth * previewHeight];
        }


        try {
            // ▼ "GETTING" THE "PARTICULAR FRAME"
            //      → WITH THE "READER" OBJECT ▼
            final Image image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            // ▼ ONLY "ONE FRAME" WILL BE "PROCESSED" AT A "TIME" ▼
            if (isProcessingFrame) {
                image.close();
                return;
            }


            // ▼ "GETTING" THE "PLANE" FOR THAT "PARTICULAR FRAME" ▼
            isProcessingFrame = true;
            final Image.Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);
            yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();


            // ▼ "CONVERT" "IMAGE" TO "BITMAP" ▼
            imageConverter =
                    new Runnable() {

                        // ▬▬  "RUN()" METHOD  ▬▬
                        @Override
                        public void run() {

                            // ▼ CONVERT "YUV" TO "RGB" ▼
                            ImageUtils.convertYUV420ToARGB8888(
                                    // ▼ "GETTING" THE "PLAN" OF THE "FRAME" ▼
                                    yuvBytes[0],
                                    yuvBytes[1],
                                    yuvBytes[2],
                                    previewWidth,
                                    previewHeight,
                                    yRowStride,
                                    uvRowStride,
                                    uvPixelStride,
                                    rgbBytes);
                        }
                    };



            // ▼ "CALLBACK" TO "CLOSE" THE "FRAME" ▼
            postInferenceCallback =
                    new Runnable() {

                        // ▬▬  "RUN()" METHOD  ▬▬
                        @Override
                        public void run() {
                            image.close();
                            isProcessingFrame = false;
                        }
                    };

            // ▼ CALLING THE "METHOD" ▼
            processImage();

        } catch (final Exception e) {
            Log.d("tryError",e.getMessage());
            return;
        }

    }




    // ▬▬  "PROCESS IMAGE()" METHOD  ▬▬
    private void processImage() {
        // ▼ GETTING THE "BYTES" OF THAT "PARTICULAR FRAME" ▼
        imageConverter.run();

        // ▼ CREATING A "BITMAP"
        //      → "EQUAL" TO THE "SIZE" OF THAT "PARTICULAR FRAME" ▼
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);

        // ▼ POPULATING THE "PIXELS" OF THAT "PARTICULAR BITMAP"
        //      → WITH THE "BYTES" OF "FRAME" ▼
        rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);

        // ▼ RUNNING CALLBACK ▼
        postInferenceCallback.run();
    }





    // ▬▬  "FILL BYTES()" METHOD  ▬▬
    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        // ▼ GETTING THE "PLANE" FOR THAT "PARTICULAR FRAME" ▼
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();

            // ▼ CONVERT "BUFFER" TO "BYTE ARRAY" ▼
            if (yuvBytes[i] == null) {
                yuvBytes[i] = new byte[buffer.capacity()];
            }

            buffer.get(yuvBytes[i]);
        }
    }
}
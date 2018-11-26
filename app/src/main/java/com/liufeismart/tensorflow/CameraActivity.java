package com.liufeismart.tensorflow;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;
import android.widget.TextView;

import com.liufeismart.tensorflow.tensorflow.Classifier;
import com.liufeismart.tensorflow.tensorflow.TensorFlowImageClassifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;


public class CameraActivity extends Activity {
    public  final String EXTRA_SPEAK_CONTENT = "extra_speak_content";

    private final String TAG = "CameraActivity";

    public static final String ACTION_STOP_SPEAK = "com.humax.intent.ACTION_STOP_SPEAK";
    public static final String ACTION_START_SPEAK = "com.humax.intent.ACTION_START_SPEAK";



    private static final int INPUT_SIZE = 224;
    private static final int IMAGE_MEAN = 117;
    private static final float IMAGE_STD = 1;
    private static final String INPUT_NAME = "input";
    private static final String OUTPUT_NAME = "final_result";
    private static final String MODEL_FILE = "file:///android_asset/model/graph.pb";
    private static final String LABEL_FILE = "file:///android_asset/model/labels.txt";

    private Executor executor;
    private Classifier classifier;
    private Camera camera;
    private SurfaceView sfv;
    private ImageView imgv;
    private TextView tv_result;
    private HashMap<String, String> map = new HashMap<String, String>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.v(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        sfv = findViewById(R.id.sfv);
        imgv =  findViewById(R.id.imgv);
        tv_result =  findViewById(R.id.tv_result);
        initCamera();
//        camera.setPreviewCallback(new Camera.PreviewCallback() {
//            @Override
//            public void onPreviewFrame(byte[] bytes, Camera camera) {
//                //获取图片
//                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
//                //
//                startImageClassifier(bitmap);
//
//            }
//        });
        map.put("page1","恐龙的故事封面");
        map.put("page2","恐龙的故事第一页");
        map.put("page3","恐龙的故事第二页");
        // 避免耗时任务占用 CPU 时间片造成UI绘制卡顿，提升启动页面加载速度
        Log.v(TAG, "onCreate1");
        Looper.myQueue().addIdleHandler(idleHandler);
        //
        AudioManager audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);                   audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, false);
        audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, false);
    }


    @Override
    protected void onStop() {
        super.onStop();
        Intent intent = new Intent();
        intent.setAction(ACTION_STOP_SPEAK);
        sendBroadcast(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(camera !=null) {
            camera.release();
            camera = null;
        }

        AudioManager audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);                   audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, false);
        audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true);
    }

    /**
     *  主线程消息队列空闲时（视图第一帧绘制完成时）处理耗时事件
     */
    MessageQueue.IdleHandler idleHandler = new MessageQueue.IdleHandler() {
        @Override
        public boolean queueIdle() {
            Log.v(TAG, "queueIdle");
            if (classifier == null) {
                // 创建 Classifier
                classifier = TensorFlowImageClassifier.create(CameraActivity.this.getAssets(),
                        MODEL_FILE, LABEL_FILE, INPUT_SIZE, IMAGE_MEAN, IMAGE_STD, INPUT_NAME, OUTPUT_NAME);
            }

            // 初始化线程池
            executor = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
                @Override
                public Thread newThread(@NonNull Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setDaemon(true);
                    thread.setName("ThreadPool-ImageClassifier");
                    return thread;
                }
            });

            // 请求权限
//            requestMultiplePermissions();
            takePicture();
            //

            return false;
        }
    };

    private void initCamera() {
        //1.OpenCamera
        int cameraIndex = -1;
        int cameraCount = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        if(cameraCount==1) {
            cameraIndex = 0;
        }
        else if(cameraCount>0) {
            for(int i = 0; i<cameraCount; i++){
                Camera.getCameraInfo(cameraIndex, info);
                if(info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT){
                    cameraIndex = cameraIndex;
                    break;
                }
            }
        }
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 0);
        camera = Camera.open(cameraIndex);

//        camera = Camera.open();
        //2.设置参数
        Camera.Parameters params =  camera.getParameters();
        Camera.Parameters parameters = camera.getParameters(); List<String> FocusModes = parameters.getSupportedFocusModes(); if (FocusModes != null && FocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) { parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO); }

//        int rotation = getCameraRotation(cameraIndex);
//        parameters.setRotation(rotation);
        parameters.setRecordingHint(true);
        if (parameters.getMaxNumMeteringAreas() > 0) {
            List<Camera.Area> meteringAreas = new ArrayList<>();
            meteringAreas.add(new Camera.Area(new Rect(-100, -100, 100, 100), 600));
            meteringAreas.add(new Camera.Area(new Rect(800, -1000, 1000, -800), 400));
            parameters.setMeteringAreas(meteringAreas);
        }

        Camera.Size fitPreviewSize = null;
        CameraSizeAccessor sizeAccessor = getCameraSizeAccessor();
        int minDiff = Integer.MAX_VALUE;
        for (Camera.Size size : camera.getParameters().getSupportedPreviewSizes()) {
            int diff = (int) Math.sqrt(Math.pow(sizeAccessor.getWidth(size) - 1280, 2) + Math.pow(sizeAccessor.getHeight(size) - 720, 2));
            if (diff < minDiff) {
                minDiff = diff;
                fitPreviewSize = size;
            }
        }
        parameters.setPreviewSize(fitPreviewSize.width, fitPreviewSize.height);
        camera.setParameters(parameters);
        //3.打开预览
        SurfaceHolder holder = sfv.getHolder();
        holder.setFormat(PixelFormat.RGBA_8888);
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                //
                Log.v(TAG, "surfaceCreated");
            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
                Log.v(TAG, "surfaceChanged");

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                Log.v(TAG, "surfaceDestroyed");
            }
        });
        try {
            camera.setPreviewDisplay(holder);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
            Log.v(TAG, e.getMessage());
        }
    }

    private CameraSizeAccessor getCameraSizeAccessor() {
        return new CameraSizeAccessor(this.getResources().getConfiguration().orientation);
    }

    private void takePicture() {
        Log.v(TAG, "takePicture");
        camera.takePicture(null, null, new Camera.PictureCallback() {
            public void onPictureTaken(byte[] _data, Camera _camera) {
                Log.v(TAG, "onPictureTaken2");
                /*
                 * if (Environment.getExternalStorageState().equals(
                 * Environment.MEDIA_MOUNTED)) // 判断SD卡是否存在，并且可以可以读写 {
                 *
                 * } else { Toast.makeText(EX07_16.this, "SD卡不存在或写保护",
                 * Toast.LENGTH_LONG) .show(); }
                 */
                // Log.w("============", _data[55] + "");

                try {
                    /* 取得相片 */
                    Bitmap bitmap = BitmapFactory.decodeByteArray(_data, 0,
                            _data.length);
                    imgv.setImageBitmap(bitmap);
                    Log.i(TAG, "startImageClassifier ");
                    startImageClassifier(bitmap);
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if(camera!=null) {
                                camera.startPreview();
                                takePicture();
                            }
                        }
                    }, 2000);
//                        /* 创建文件 */
//                        File myCaptureFile = new File(strCaptureFilePath, "1.jpg");
//                        BufferedOutputStream bos = new BufferedOutputStream(
//                                new FileOutputStream(myCaptureFile));
//                        /* 采用压缩转档方法 */
//                        bm.compress(Bitmap.CompressFormat.JPEG, 100, bos);
//
//                        /* 调用flush()方法，更新BufferStream */
//                        bos.flush();
//
//                        /* 结束OutputStream */
//                        bos.close();
//
//                        /* 让相片显示3秒后圳重设相机 */
//                        // Thread.sleep(2000);
//                        /* 重新设定Camera */
//                        stopCamera();
//                        initCamera();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 请求存储和相机权限
     */
    private void requestMultiplePermissions() {

//        String storagePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE;
//        String cameraPermission = Manifest.permission.CAMERA;
//
//        int hasStoragePermission = ActivityCompat.checkSelfPermission(this, storagePermission);
//        int hasCameraPermission = ActivityCompat.checkSelfPermission(this, cameraPermission);
//
//        List<String> permissions = new ArrayList<>();
//        if (hasStoragePermission != PackageManager.PERMISSION_GRANTED) {
//            permissions.add(storagePermission);
//        }
//
//        if (hasCameraPermission != PackageManager.PERMISSION_GRANTED) {
//            permissions.add(cameraPermission);
//        }
//
//        if (!permissions.isEmpty()) {
//            String[] params = permissions.toArray(new String[permissions.size()]);
//            ActivityCompat.requestPermissions(this, params, PERMISSIONS_REQUEST);
//        }
    }

    /**
     * 开始图片识别匹配
     * @param bitmap
     */
    private void startImageClassifier(final Bitmap bitmap) {
        Log.i(TAG, "startImageClassifier ");
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i(TAG, Thread.currentThread().getName() + " startImageClassifier");
                    Bitmap croppedBitmap = getScaleBitmap(bitmap, INPUT_SIZE);

                    final List<Classifier.Recognition> results = classifier.recognizeImage(croppedBitmap);
                    Log.i(TAG, "startImageClassifier results: " + results);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tv_result.setText("result = "+map.get(results.get(0).toString()));
                            Intent intent = new Intent();
                            intent.putExtra(EXTRA_SPEAK_CONTENT, map.get(results.get(0).toString()));
                            intent.setAction(ACTION_START_SPEAK);
                            sendBroadcast(intent);
                        }
                    });
                } catch (IOException e) {
                    Log.e(TAG, "startImageClassifier getScaleBitmap " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });

    }

    /**
     * 对图片进行缩放
     * @param bitmap
     * @param size
     * @return
     * @throws IOException
     */
    private static Bitmap getScaleBitmap(Bitmap bitmap, int size) throws IOException {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scaleWidth = ((float) size) / width;
        float scaleHeight = ((float) size) / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
    }


    private static class CameraSizeAccessor {
        private int orientation;

        CameraSizeAccessor(int orientation) {
            this.orientation = orientation;
        }

        int getWidth(Camera.Size size) {
            return orientation == Configuration.ORIENTATION_LANDSCAPE ? Math.max(size.width, size.height) : Math.min(size.width, size.height);
        }

        int getHeight(Camera.Size size) {
            return orientation == Configuration.ORIENTATION_LANDSCAPE ? Math.min(size.width, size.height) : Math.max(size.width, size.height);
        }
    }



}

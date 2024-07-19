package com.lihuzi.looknow;

import static android.media.MediaFormat.MIMETYPE_AUDIO_AAC;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * @Author lining
 * @Date 2024/4/22
 * @DESC TODO
 **/

public class PreviewActivity extends AppCompatActivity implements View.OnClickListener {
    private TextureView previewTextureView;
    private Button shutterClick;
    private Size previewSize;
    private static final String TAG = "VideoRecord";
    private String mCameraId;
    private Handler cameraHandler;
    private CameraCaptureSession previewCaptureSession;
    private MediaCodec videoMediaCodec;
    private AudioRecord audioRecord;
    private boolean isRecordingVideo = false;
    private int mWidth;
    private int mHeight;
    private ImageReader previewImageReader;
    private Handler previewCaptureHandler;
    private CaptureRequest.Builder videoRequest;
    private static final int WAIT_TIME = 0;
    private static final int SAMPLE_RATE = 44100;
    private long nanoTime;
    private int AudioiMinBufferSize;
    private boolean mIsAudioRecording = false;
    private static final int CHANNEL_COUNT = 2;
    private MediaCodec AudioCodec;
    private static final int BIT_RATE = 96000;
    private MediaMuxer mediaMuxer;
    private volatile int mAudioTrackIndex = -1;
    private volatile int mVideoTrackIndex = -1;
    private volatile int isMediaMuxerStarted = -1;
    private Thread audioRecordThread;
    private Thread VideoCodecThread;
    private Thread AudioCodecThread;
    private long presentationTimeUs;
    private AudioCaptureListener captureListener;
    private volatile int isStop = 0;
    CaptureRequest.Builder previewCaptureRequestBuilder;
    private volatile int videoMediaCodecIsStoped = 0;
    private volatile int AudioCodecIsStoped = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_preview);
        initView();
    }

    //1.初始化MediaCodec视频编码
    //设置mediaformat，到时候放到MediaRecodc里面
    private void initMediaCodec(int width, int height) {
        Log.d(TAG, "width:" + width);
        Log.d(TAG, "Height:" + height);
        try {
            //先拿到格式容器
            /*
              MediaFormat.createVideoFormat中的宽高参数，不能为奇数
              过小或超过屏幕尺寸，也会出现这个错误
            */
            MediaFormat videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            //设置色彩控件
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            //设置码率，码率就是数据传输单位时间传递的数据位数
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, 500_000);
            //设置帧率
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 20);
            //设置关键帧间隔
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            videoFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4000000);
            //创建MediaCodc
            videoMediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            videoMediaCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void initView() {
        previewTextureView = findViewById(R.id.previewtexture);
        shutterClick = findViewById(R.id.shutterclick);
        shutterClick.setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        previewTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
                Log.d(TAG, "onSurfaceTextureAvailable");
                setupCamera(width, height);

            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

            }
        });
        HandlerThread videoRecordThread = new HandlerThread("VideoRecordThread");
        videoRecordThread.start();
        cameraHandler = new Handler(videoRecordThread.getLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
            }
        };
        HandlerThread preivewImageReaderThread = new HandlerThread("preivewImageReaderThread");
        preivewImageReaderThread.start();
        previewCaptureHandler = new Handler(preivewImageReaderThread.getLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
            }
        };
    }

    //设置预览的ImageReader
    private void setPreviewImageReader() {
        previewImageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 1);
        previewImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader imageReader) {
                printLog(TAG, "onImageAvailable");
                Image image = imageReader.acquireNextImage();
                int width = image.getWidth();
                int height = image.getHeight();
                int I420size = width * height * 3 / 2;
                printLog(TAG, "I420size:" + I420size);
                byte[] nv21 = new byte[I420size];
                YUVToNV21_NV12(image, nv21, image.getWidth(), image.getHeight(), "NV21");

                encodeVideo(nv21);
                image.close();
            }
        }, previewCaptureHandler);
    }

    private static byte[] YUV_420_888toNV21(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        Log.d(TAG, "image.getWidth():" + image.getWidth());
        Log.d(TAG, "image.getHeight()" + image.getHeight());
        ByteBuffer yBuffer = getBufferWithoutPadding(image.getPlanes()[0].getBuffer(), image.getWidth(), image.getPlanes()[0].getRowStride(), image.getHeight(), false);
        ByteBuffer vBuffer;
        //part1 获得真正的消除padding的ybuffer和ubuffer。需要对P格式和SP格式做不同的处理。如果是P格式的话只能逐像素去做，性能会降低。
        if (image.getPlanes()[2].getPixelStride() == 1) { //如果为true，说明是P格式。
            vBuffer = getuvBufferWithoutPaddingP(image.getPlanes()[1].getBuffer(), image.getPlanes()[2].getBuffer(),
                    width, height, image.getPlanes()[1].getRowStride(), image.getPlanes()[1].getPixelStride());
        } else {
            vBuffer = getBufferWithoutPadding(image.getPlanes()[2].getBuffer(), image.getWidth(), image.getPlanes()[2].getRowStride(), image.getHeight() / 2, true);
        }

        //part2 将y数据和uv的交替数据（除去最后一个v值）赋值给nv21
        int ySize = yBuffer.remaining();
        int vSize = vBuffer.remaining();
        byte[] nv21;
        int byteSize = width * height * 3 / 2;
        nv21 = new byte[byteSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);

        //part3 最后一个像素值的u值是缺失的，因此需要从u平面取一下。
        ByteBuffer uPlane = image.getPlanes()[1].getBuffer();
        byte lastValue = uPlane.get(uPlane.capacity() - 1);
        nv21[byteSize - 1] = lastValue;
        return nv21;
    }

    //Semi-Planar格式（SP）的处理和y通道的数据
    private static ByteBuffer getBufferWithoutPadding(ByteBuffer buffer, int width, int rowStride, int times, boolean isVbuffer) {
        if (width == rowStride) return buffer;  //没有buffer,不用处理。
        int bufferPos = buffer.position();
        int cap = buffer.capacity();
        byte[] byteArray = new byte[times * width];
        int pos = 0;
        //对于y平面，要逐行赋值的次数就是height次。对于uv交替的平面，赋值的次数是height/2次
        for (int i = 0; i < times; i++) {
            buffer.position(bufferPos);
            //part 1.1 对于u,v通道,会缺失最后一个像u值或者v值，因此需要特殊处理，否则会crash
            if (isVbuffer && i == times - 1) {
                width = width - 1;
            }
            buffer.get(byteArray, pos, width);
            bufferPos += rowStride;
            pos = pos + width;
        }

        //nv21数组转成buffer并返回
        ByteBuffer bufferWithoutPaddings = ByteBuffer.allocate(byteArray.length);
        // 数组放到buffer中
        bufferWithoutPaddings.put(byteArray);
        //重置 limit 和postion 值否则 buffer 读取数据不对
        bufferWithoutPaddings.flip();
        return bufferWithoutPaddings;
    }

    //Planar格式（P）的处理
    private static ByteBuffer getuvBufferWithoutPaddingP(ByteBuffer uBuffer, ByteBuffer vBuffer, int width, int height, int rowStride, int pixelStride) {
        int pos = 0;
        byte[] byteArray = new byte[height * width / 2];
        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                int vuPos = col * pixelStride + row * rowStride;
                byteArray[pos++] = vBuffer.get(vuPos);
                byteArray[pos++] = uBuffer.get(vuPos);
            }
        }
        ByteBuffer bufferWithoutPaddings = ByteBuffer.allocate(byteArray.length);
        // 数组放到buffer中
        bufferWithoutPaddings.put(byteArray);
        //重置 limit 和postion 值否则 buffer 读取数据不对
        bufferWithoutPaddings.flip();
        return bufferWithoutPaddings;
    }

    private void encodeVideo(byte[] nv21) {
        //输入
        int index = videoMediaCodec.dequeueInputBuffer(WAIT_TIME);
        //Log.d(TAG,"video encord video index:"+index);
        if (index >= 0) {
            ByteBuffer inputBuffer = videoMediaCodec.getInputBuffer(index);
            inputBuffer.clear();
            int remaining = inputBuffer.remaining();
            inputBuffer.put(nv21, 0, nv21.length);
            videoMediaCodec.queueInputBuffer(index, 0, nv21.length, (System.nanoTime() - nanoTime) / 1000, 0);
        }
    }

    private void encodeVideoH264() {
        MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
        int videobufferindex = videoMediaCodec.dequeueOutputBuffer(videoBufferInfo, 0);
        printLog(TAG, "videobufferindex:" + videobufferindex);
        if (videobufferindex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            //添加轨道
            mVideoTrackIndex = mediaMuxer.addTrack(videoMediaCodec.getOutputFormat());
            printLog(TAG, "mVideoTrackIndex:" + mVideoTrackIndex);
            if (mAudioTrackIndex != -1) {
                printLog(TAG, "encodeVideoH264:mediaMuxer is Start");
                mediaMuxer.start();
                isMediaMuxerStarted += 1;
                setPCMListener();
            }
        } else {
            if (isMediaMuxerStarted >= 0) {
                while (videobufferindex >= 0) {
                    //获取输出数据成功
                    ByteBuffer videoOutputBuffer = videoMediaCodec.getOutputBuffer(videobufferindex);
                    printLog(TAG, "Video mediaMuxer writeSampleData");
                    mediaMuxer.writeSampleData(mVideoTrackIndex, videoOutputBuffer, videoBufferInfo);
                    videoMediaCodec.releaseOutputBuffer(videobufferindex, false);
                    videobufferindex = videoMediaCodec.dequeueOutputBuffer(videoBufferInfo, 0);
                }
            }
        }

    }

    private void encodePCMToAC() {
        MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
        //获得输出
        int audioBufferFlag = AudioCodec.dequeueOutputBuffer(audioBufferInfo, 0);
        printLog(TAG, "CALL BACK DATA FLAG:" + audioBufferFlag);
        if (audioBufferFlag == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            //這時候進行添加軌道
            mAudioTrackIndex = mediaMuxer.addTrack(AudioCodec.getOutputFormat());
            printLog(TAG, "mAudioTrackIndex:" + mAudioTrackIndex);
            if (mVideoTrackIndex != -1) {
                printLog(TAG, "encodecPCMToACC:mediaMuxer is Start");
                mediaMuxer.start();
                isMediaMuxerStarted += 1;
                //开始了再创建录音回调
                setPCMListener();
            }
        } else {
            printLog(TAG, "isMediaMuxerStarted:" + isMediaMuxerStarted);
            if (isMediaMuxerStarted >= 0) {
                while (audioBufferFlag >= 0) {
                    ByteBuffer outputBuffer = AudioCodec.getOutputBuffer(audioBufferFlag);
                    mediaMuxer.writeSampleData(mAudioTrackIndex, outputBuffer, audioBufferInfo);
                    AudioCodec.releaseOutputBuffer(audioBufferFlag, false);
                    audioBufferFlag = AudioCodec.dequeueOutputBuffer(audioBufferInfo, 0);
                }
            }
        }

    }

    private void printLog(String tag, String msg) {

    }

    private void setPCMListener() {
        setCaptureListener(new AudioCaptureListener() {
            @Override
            public void onCaptureListener(byte[] audioSource, int audioReadSize) {
                callbackData(audioSource, audioReadSize);
            }
        });
    }

    private static void YUVToNV21_NV12(Image image, byte[] nv21, int w, int h, String type) {
        Image.Plane[] planes = image.getPlanes();
        int remaining0 = planes[0].getBuffer().remaining();
        int remaining1 = planes[1].getBuffer().remaining();
        int remaining2 = planes[2].getBuffer().remaining();
        //分别准备三个数组接收YUV分量。
        byte[] yRawSrcBytes = new byte[remaining0];
        byte[] uRawSrcBytes = new byte[remaining1];
        byte[] vRawSrcBytes = new byte[remaining2];
        planes[0].getBuffer().get(yRawSrcBytes);
        planes[1].getBuffer().get(uRawSrcBytes);
        planes[2].getBuffer().get(vRawSrcBytes);
        int j = 0, k = 0;
        boolean flag = type.equals("NV21");
        for (int i = 0; i < nv21.length; i++) {
            if (i < w * h) {
                //首先填充w*h个Y分量
                nv21[i] = yRawSrcBytes[i];
            } else {
                if (flag) {
                    //若NV21类型 则Y分量分配完后第一个将是V分量
                    nv21[i] = vRawSrcBytes[j];
                    //PixelStride有用数据步长 = 1紧凑按顺序填充，=2每间隔一个填充数据
                    j += planes[1].getPixelStride();
                } else {
                    //若NV12类型 则Y分量分配完后第一个将是U分量
                    nv21[i] = uRawSrcBytes[k];
                    //PixelStride有用数据步长 = 1紧凑按顺序填充，=2每间隔一个填充数据
                    k += planes[2].getPixelStride();
                }
                //紧接着可以交错UV或者VU排列不停的改变flag标志即可交错排列
                flag = !flag;
            }
        }
    }

    private void setupCamera(int width, int height) {
        CameraManager cameraManage = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIdList = cameraManage.getCameraIdList();
            for (String cameraId : cameraIdList) {
                CameraCharacteristics cameraCharacteristics = cameraManage.getCameraCharacteristics(cameraId);
                //demo就就简单写写后摄录像
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) {
                    //表示匹配到前摄，直接跳过这次循环
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] outputSizes = map.getOutputSizes(SurfaceTexture.class);
                Size size = new Size(1920  , 1080);
                previewSize = size;
                mWidth = previewSize.getWidth();
                mHeight = previewSize.getHeight();
                Log.e(TAG,"setupCamera mWidth:"+mWidth+" mHeight:"+mHeight);
                mCameraId = cameraId;
            }
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
        openCamera();
    }

    private void initAudioRecord() {
        AudioiMinBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        audioRecord = new AudioRecord.Builder()
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setBufferSizeInBytes(AudioiMinBufferSize)
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .build();
    }

    private void openCamera() {
        Log.d(TAG, "openCamera: success");
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            cameraManager.openCamera(mCameraId, stateCallback, cameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CameraDevice mCameraDevice;
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            Log.d(TAG, "onOpen");
            mCameraDevice = cameraDevice;
            startPreview(mCameraDevice);
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            Log.d(TAG, "onDisconnected");
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            Log.d(TAG, "onError");
        }
    };

    private void startPreview(CameraDevice mCameraDevice) {
        try {
            Log.d(TAG, "startPreview");
            setPreviewImageReader();
            previewCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            SurfaceTexture previewSurfaceTexture = previewTextureView.getSurfaceTexture();
            previewSurfaceTexture.setDefaultBufferSize(mWidth, mHeight);
            Surface previewSurface = new Surface(previewSurfaceTexture);
            Surface previewImageReaderSurface = previewImageReader.getSurface();
            previewCaptureRequestBuilder.addTarget(previewSurface);
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, previewImageReaderSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Log.d(TAG, "onConfigured");
                    previewCaptureSession = cameraCaptureSession;
                    try {
                        cameraCaptureSession.setRepeatingRequest(previewCaptureRequestBuilder.build(), cameraPreviewCallback, cameraHandler);
                    } catch (CameraAccessException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                }
            }, cameraHandler);

        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private volatile boolean videoIsReadyToStop = false;
    private CameraCaptureSession.CaptureCallback cameraPreviewCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            if (videoIsReadyToStop) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                videoIsReadyToStop = false;
                stopMediaCodecThread();
            }

        }
    };

    private Size getOptimalSize(Size[] sizes, int width, int height) {
        Size tempSize = new Size(width, height);
        List<Size> adaptSize = new ArrayList<>();
        for (Size size : sizes) {
            if (width > height) {
                //横屏的时候看，或是平板形式
                if (size.getHeight() > height && size.getWidth() > width) {
                    adaptSize.add(size);
                }
            } else {
                //竖屏的时候
                if (size.getWidth() > height && size.getHeight() > width) {
                    adaptSize.add(size);
                }
            }
        }
        if (adaptSize.size() > 0) {
            tempSize = adaptSize.get(0);
            int minnum = 999999;
            for (Size size : adaptSize) {
                int num = size.getHeight() * size.getHeight() - width * height;
                if (num < minnum) {
                    minnum = num;
                    tempSize = size;
                }
            }
        }
        return tempSize;
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.shutterclick) {
            if (isRecordingVideo) {
                //stop recording video
                isRecordingVideo = false;
                //开始停止录像
                Log.d(TAG, "Stop recording video");
                stopRecordingVideo();
                Toast.makeText(this, fileName != null ? "文件保存路径为:" + fileName : "文件保存路径为空", Toast.LENGTH_SHORT).show();
                ((Button) view).setText("点击录像");
            } else {
                isRecordingVideo = true;
                ((Button) view).setText("点击暂停");
                //开始录像
                startRecording();
            }
        }
    }

    private void stopRecordingVideo() {
        stopVideoSession();
        stopAudioRecord();


    }

    private void stopMediaMuxer() {
        isMediaMuxerStarted = -1;
        mediaMuxer.stop();
        mediaMuxer.release();
    }

    private void stopMediaCodecThread() {
        isStop = 2;
        AudioCodecThread = null;
        VideoCodecThread = null;
    }

    private void stopMediaCodec() {
        AudioCodec.stop();
        AudioCodec.release();
        videoMediaCodec.stop();
        videoMediaCodec.release();
    }

    private void stopVideoSession() {
        if (previewCaptureSession != null) {
            try {
                previewCaptureSession.stopRepeating();
                videoIsReadyToStop = true;
            } catch (CameraAccessException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            previewCaptureSession.setRepeatingRequest(previewCaptureRequestBuilder.build(), cameraPreviewCallback, cameraHandler);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void stopAudioRecord() {
        mIsAudioRecording = false;
        audioRecord.stop();
        audioRecord.release();
        audioRecord = null;
        audioRecordThread = null;
    }

    private void startRecording() {
        isStop = 1;
        nanoTime = System.nanoTime();
        initMediaMuxer();
        initAudioRecord();
        initMediaCodec(mWidth, mHeight);
        initAudioCodec();
        //將MediaCodec分成獨立的線程
        initMediaCodecThread();
        //啟動MediaCodec以及线程
        startMediaCodec();
        //开启录像session
        startVideoSession();
        //音頻開始錄製
        startAudioRecord();
    }

    private void startMediaCodec() {
        if (AudioCodec != null) {
            AudioCodecIsStoped = 0;
            AudioCodec.start();
        }
        if (videoMediaCodec != null) {
            videoMediaCodecIsStoped = 0;
            videoMediaCodec.start();
        }
        if (VideoCodecThread != null) {
            VideoCodecThread.start();
        }
        if (AudioCodecThread != null) {
            AudioCodecThread.start();
        }
    }

    private void initMediaCodecThread() {
        VideoCodecThread = new Thread(new Runnable() {
            @Override
            public void run() {
                //输出为H264
                while (true) {
                    if (isStop == 2) {
                        Log.d(TAG, "videoMediaCodec is stopping");
                        break;
                    }
                    encodeVideoH264();
                }
                videoMediaCodec.stop();
                videoMediaCodec.release();
                videoMediaCodecIsStoped = 1;
                if (AudioCodecIsStoped == 1) {
                    stopMediaMuxer();
                }
            }
        });
        AudioCodecThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    if (isStop == 2) {
                        Log.d(TAG, "AudioCodec is stopping");
                        break;
                    }
                    encodePCMToAC();
                }
                AudioCodec.stop();
                AudioCodec.release();
                AudioCodecIsStoped = 1;
                if (videoMediaCodecIsStoped == 1) {
                    stopMediaMuxer();
                }
            }
        });
    }


    private void startAudioRecord() {
        audioRecordThread = new Thread(new Runnable() {
            @Override
            public void run() {
                mIsAudioRecording = true;
                audioRecord.startRecording();
                while (mIsAudioRecording) {
                    byte[] inputAudioData = new byte[AudioiMinBufferSize];
                    int res = audioRecord.read(inputAudioData, 0, inputAudioData.length);
                    if (res > 0) {
                        //Log.d(TAG,res+"");
                        if (AudioCodec != null) {
                            if (captureListener != null) {
                                captureListener.onCaptureListener(inputAudioData, res);
                            }
                            //callbackData(inputAudioData,inputAudioData.length);
                        }
                    }
                }
            }
        });
        audioRecordThread.start();
    }

    private String fileName = null;

    private void initMediaMuxer() {
        fileName = Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera/" + getCurrentTime() + ".mp4";
        try {
            mediaMuxer = new MediaMuxer(fileName, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mediaMuxer.setOrientationHint(90);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getCurrentTime() {
        Date date = new Date(System.currentTimeMillis());

        DateFormat dateFormat = new SimpleDateFormat("yyyyMMddhhmmss");
        return dateFormat.format(date);
    }

    //初始化Audio MediaCodec
    private void initAudioCodec() {
        MediaFormat format = MediaFormat.createAudioFormat(MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNEL_COUNT);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192);
        try {
            AudioCodec = MediaCodec.createEncoderByType(MIMETYPE_AUDIO_AAC);
            AudioCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void callbackData(byte[] inputAudioData, int length) {
        //已经拿到AudioRecord的byte数据
        //准备将其放入到MediaCodc中
        int index = AudioCodec.dequeueInputBuffer(-1);
        if (index < 0) {
            return;
        }
        printLog(TAG, "AudioCodec.dequeueInputBuffer:" + index);
        ByteBuffer[] inputBuffers = AudioCodec.getInputBuffers();
        ByteBuffer audioInputBuffer = inputBuffers[index];
        audioInputBuffer.clear();
        printLog(TAG, "call back Data length:" + length);
        printLog(TAG, "call back Data audioInputBuffer remain:" + audioInputBuffer.remaining());
        audioInputBuffer.put(inputAudioData);
        audioInputBuffer.limit(inputAudioData.length);
        presentationTimeUs += (long) (1.0 * length / (44100 * 2 * (16 / 8)) * 1000000.0);
        AudioCodec.queueInputBuffer(index, 0, inputAudioData.length, (System.nanoTime() - nanoTime) / 1000, 0);
    }

    /*private void getEncordData() {
        MediaCodec.BufferInfo outputBufferInfo = new MediaCodec.BufferInfo();
        //获得输出
        int flag = AudioCodec.dequeueOutputBuffer(outputBufferInfo, 0);
        Log.d(TAG, "CALL BACK DATA FLAG:" + flag);
        if (flag == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            //第一次都会执行这个
            //这时候可以加轨道到MediaMuxer中，但是先不要启动，等到两个轨道都加好再start
            mAudioTrackIndex = mediaMuxer.addTrack(AudioCodec.getOutputFormat());
            if (mAudioTrackIndex != -1 && mVideoTrackIndex != -1) {
                mediaMuxer.start();
                Log.d(TAG, "AudioMediaCodec start mediaMuxer");
                isMediaMuxerStarted = true;
            }
        } else {
            if (isMediaMuxerStarted) {
                if (flag >= 0) {
                    if (mAudioTrackIndex != -1) {
                        Log.d(TAG, "AudioCodec.getOutputBuffer:");
                        ByteBuffer outputBuffer = AudioCodec.getOutputBuffer(flag);
                        mediaMuxer.writeSampleData(mAudioTrackIndex, outputBuffer, outputBufferInfo);
                        AudioCodec.releaseOutputBuffer(flag, false);
                    }
                }
            }
        }
    }*/

    private void startVideoSession() {
        Log.d(TAG, "startVideoSession");
        if (previewCaptureSession != null) {
            try {
                previewCaptureSession.stopRepeating();
            } catch (CameraAccessException e) {
                throw new RuntimeException(e);
            }
        }
        SurfaceTexture previewSurfaceTexture = previewTextureView.getSurfaceTexture();
        Surface previewSurface = new Surface(previewSurfaceTexture);
        Surface previewImageReaderSurface = previewImageReader.getSurface();
        try {
            videoRequest = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            videoRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            videoRequest.addTarget(previewSurface);
            videoRequest.addTarget(previewImageReaderSurface);
            previewCaptureSession.setRepeatingRequest(videoRequest.build(), videosessioncallback, cameraHandler);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private CameraCaptureSession.CaptureCallback videosessioncallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }
    };

    public interface AudioCaptureListener {

        /**
         * 音频采集回调数据源
         *
         * @param audioSource   ：音频采集回调数据源
         * @param audioReadSize :每次读取数据的大小
         */
        void onCaptureListener(byte[] audioSource, int audioReadSize);
    }

    public AudioCaptureListener getCaptureListener() {
        return captureListener;
    }

    public void setCaptureListener(AudioCaptureListener captureListener) {
        this.captureListener = captureListener;
    }

}

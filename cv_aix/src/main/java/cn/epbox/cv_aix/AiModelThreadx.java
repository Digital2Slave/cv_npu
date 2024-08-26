package cn.epbox.cv_aix;

import android.graphics.Bitmap;
import android.service.carrier.CarrierMessagingService;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import cn.epbox.cv_aix.yolo.InferenceWrapper;

import static cn.epbox.cv_aix.yolo.PostProcess.INPUT_CHANNEL;

/**
 * Created by tianzx on 24-8-20.
 */

public class AiModelThreadx extends Thread {
    static {
        System.loadLibrary("rknn4j");
    }

    private final static String TAG = "AimodelThreadx";

    private String mrknn_model_path = "";
    private Bitmap mbmp = null;
    private CountDownLatch latch;

    // For inference
    private InferenceWrapper mInferenceWrapper = new InferenceWrapper();
    private InferenceResult mInferenceResult = new InferenceResult();


    public AiModelThreadx(Bitmap bmp, String rknn_model_path, CountDownLatch latch) {
        this.mbmp = bmp;
        this.mrknn_model_path = rknn_model_path;
        this.latch = latch;

        // init mInferenceResult
        try {
            mInferenceResult.init();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        // bmp 转为 byte[]
        Bitmap bmp = mbmp.copy(mbmp.getConfig(), true);

        int src_width = bmp.getWidth();
        int src_height = bmp.getHeight();
        byte[] inputData = ImageResizeAndConvert.resizeAndConvert(bmp, src_width, src_height);

        // Load rknn model
        try {
            mInferenceWrapper.initModel(src_height, src_width, INPUT_CHANNEL, mrknn_model_path);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        Log.d(TAG, "doRknnCore: "+"init " + mrknn_model_path +" done!");

        // Inference of inputData
        InferenceResult.OutputBuffer outputs = mInferenceWrapper.run(inputData);
        Log.d(TAG, "doRknnCore: "+"run rknn model done!");
        mInferenceResult.setResult(outputs);
        Log.d(TAG, "doRknnCore: "+"set outputs to inferenceResult done!");

        // 计算完成，减少计数
        latch.countDown();
    }

    public InferenceResult getResult() {
        return mInferenceResult;
    }
}

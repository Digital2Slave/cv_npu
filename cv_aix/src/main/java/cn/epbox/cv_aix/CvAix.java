package cn.epbox.cv_aix;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.RemoteException;
import android.service.carrier.CarrierMessagingService;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cn.epbox.cv_aix.yolo.InferenceWrapper;

import static cn.epbox.cv_aix.yolo.PostProcess.INPUT_CHANNEL;

/**
 * Created by tianzx on 22-10-28.
 */

public class CvAix {
    static {
        System.loadLibrary("rknn4j");
    }

    private final static String TAG = "CvAix";

    // For input and output
    private String mAiModelFolder = "";
    private String mRknnModelName = "";
    private String mRknnLabelName = "";
    private String mSaveImageName = "";
    public EpboxCV mEpboxCV = new EpboxCV();

    // For inference
    private InferenceWrapper mInferenceWrapper = new InferenceWrapper();
    private InferenceResult mInferenceResult = new InferenceResult();
    private Vector<String> mlabels = new Vector<String>();

    // Draw result
    private Canvas mTrackResultCanvas = null;
    private Paint mTrackResultPaint = null;
    private Paint mTrackResultTextPaint = null;


    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public CvAix () {
        //用于画线
        mTrackResultPaint = new Paint();
        mTrackResultPaint.setColor(Color.RED);
        mTrackResultPaint.setStrokeJoin(Paint.Join.ROUND);
        mTrackResultPaint.setStrokeCap(Paint.Cap.ROUND);
        mTrackResultPaint.setStrokeWidth(2);
        mTrackResultPaint.setStyle(Paint.Style.STROKE);
        mTrackResultPaint.setTextAlign(Paint.Align.LEFT);
        mTrackResultPaint.setTextSize(sp2px(10));
        mTrackResultPaint.setTypeface(Typeface.SANS_SERIF);
        mTrackResultPaint.setFakeBoldText(false);

        //用于文字
        mTrackResultTextPaint = new Paint();
        mTrackResultTextPaint.setColor(Color.GREEN);
        mTrackResultTextPaint.setStrokeWidth(2);
        mTrackResultTextPaint.setTextAlign(Paint.Align.LEFT);
        mTrackResultTextPaint.setTextSize(sp2px(12));
        mTrackResultTextPaint.setTypeface(Typeface.SANS_SERIF);
        mTrackResultTextPaint.setFakeBoldText(false);

        // init mInferenceResult
        try {
            mInferenceResult.init();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public EpboxCV doPicQrAndDetect(Bitmap bmp_in, String ai_model_folder, String rknn_model_name, String rknn_label_name, String img_name) {
        // !< 将assets内的模型文件保存到ai_model_folder文件夹内。
        checkModelFilesReady(ai_model_folder, rknn_model_name, rknn_label_name);
        Log.d(TAG, "doDetectRecognize: "+" save model files from " + ai_model_folder + " done!");

        // !< 设置私有变量。
        mAiModelFolder = ai_model_folder;
        mRknnModelName = rknn_model_name;
        mRknnLabelName = rknn_label_name;
        mSaveImageName = img_name;

        // !< 加载模型标签
        // resize bmp
        Log.d(TAG, "doPicQrAndDetect: bmp_in width " + bmp_in.getWidth() + " height " + bmp_in.getHeight());
        Bitmap bmp = resizeBitmap(bmp_in);
        Log.d(TAG, "doPicQrAndDetect: bmp width " + bmp.getWidth() + " height " + bmp.getHeight());
        //Bitmap bmp = bmp_in;

        String rknn_label_path = mAiModelFolder + mRknnLabelName;
        try {
            loadLabelName(rknn_label_path, mlabels);
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (int i = 0; i < mlabels.size(); i++) {
            Log.d(TAG, "doPicQrAndDetect: " + mlabels.get(i));
        }
        Log.d(TAG, "doPicQrAndDetect: "+"load rknn model label " + rknn_label_path + " done!");

        // !< 处理手机初筛图片损耗检测线程。
        mEpboxCV = doRknnCore(bmp);

        // Release
        //mInferenceWrapper.deinit();
        return mEpboxCV;

    }

    //!< 手机正面图片初筛损耗检测。
    private EpboxCV doRknnCore(Bitmap mbmp) {
        // bmp 转为 byte[]
        Bitmap bmp = mbmp.copy(Bitmap.Config.ARGB_8888, true);
        int bmpChannelNum = getBitmapChannelCount(bmp);
        Log.d(TAG, "doRknnCore: bmp channel num " + bmpChannelNum);

        int src_width = bmp.getWidth();
        int src_height = bmp.getHeight();
        byte[] inputData = ImageResizeAndConvert.resizeAndConvert(bmp, src_width, src_height);

        // Load rknn model
        String rknn_model_path = mAiModelFolder + mRknnModelName;
        try {
            mInferenceWrapper.initModel(src_height, src_width, INPUT_CHANNEL, rknn_model_path);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        Log.d(TAG, "doRknnCore: "+"init " + rknn_model_path +" done!");

        // Inference of inputData
        InferenceResult.OutputBuffer outputs = mInferenceWrapper.run(inputData);
        Log.d(TAG, "doRknnCore: "+"run rknn model done!");
        mInferenceResult.setResult(outputs);
        Log.d(TAG, "doRknnCore: "+"set outputs to inferenceResult done!");

        // Save detect result to input bitmap
        mEpboxCV.phone = saveDetectImage(mbmp);
        Log.d(TAG, "doRknnCore: "+"saveDetectImage done!");

        // Release
        mInferenceWrapper.deinit();

        return mEpboxCV;
    }

    private void checkModelFilesReady(String ai_model_folder, String rknn_model_name, String rknn_label_name) {
        // !< Check ai_model_folder exists.
        File folder = new File(ai_model_folder);
        if (!folder.exists()) {
            folder.mkdir();
        }

        // !< Model files.
        String[] model_files = {
                rknn_label_name,
                rknn_model_name
        };

        // Loop model_files
        for (String model_file: model_files) {
            // Set variables
            InputStream in = null;
            OutputStream out = null;

            try {
                // input
                String input_file_path = "/assets/" + model_file;
                Log.d(TAG, "getAbsolutePathOfAssets: input_path->" + input_file_path);
                in = getClass().getResourceAsStream(input_file_path);

                // output
                File out_file = new File(ai_model_folder, model_file);
                String output_file_path = out_file.getAbsolutePath();
                Log.d(TAG, "getAbsolutePathOfAssets: output_path->" + output_file_path);

                // Copy input to output if need.
                if (!out_file.exists()) {
                    out = new FileOutputStream(out_file);
                    // https://blog.csdn.net/qq_46574738/article/details/123581756
                    byte[] buf = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = in.read(buf)) > 0) {
                        out.write(buf, 0, bytesRead);
                    }
                }
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } finally {
                // close in and out
                try {
                    if(in != null){
                        in.close();
                    }
                    if(out != null){
                        out.close();
                    }
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

    private static int sp2px(float spValue) {
        Resources r = Resources.getSystem();
        final float scale = r.getDisplayMetrics().scaledDensity;
        return (int) (spValue * scale + 0.5f);
    }

    private int getBitmapChannelCount(Bitmap bitmap) {
        Bitmap.Config config = bitmap.getConfig();
        if (config == Bitmap.Config.ARGB_8888) {
            return 4;
        } else if (config == Bitmap.Config.RGB_565) {
            return 3;
        } else if (config == Bitmap.Config.ALPHA_8) {
            return 1;
        } else {
            // 如果是未知配置，这里可以抛出异常或者返回默认值
            throw new IllegalArgumentException("Unsupported Bitmap.Config: " + config);
        }
    }

    private static Bitmap resizeBitmap(Bitmap originalBitmap) {
        int targetWidth = 1024;
        int targetHeight = 768;
        // 使用 createScaledBitmap 方法调整尺寸
        return Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true);
    }


    private void loadLabelName(final String locationFilename, Vector<String> labels) throws IOException {
        InputStream is = new FileInputStream(locationFilename);
        final BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line;
        while ((line = reader.readLine()) != null) {
            labels.add(line);
        }
        reader.close();
    }

    private Bitmap saveDetectImage(Bitmap originalBitmap) {
        int width = originalBitmap.getWidth();
        int height = originalBitmap.getHeight();
        // 创建一个新的Bitmap，确保其大小与原始Bitmap相同
        //Bitmap bitmap = Bitmap.createBitmap(width, height, originalBitmap.getConfig());
        Bitmap mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);

        // 将新Bitmap作为参数
        mTrackResultCanvas = new Canvas(mutableBitmap);

        // clear canvas
        //mTrackResultCanvas.drawPaint(mTrackResultPaint);

        ArrayList<InferenceResult.Recognition> recognitions = mInferenceResult.getResult(mInferenceWrapper);
        int recog_size = recognitions.size();
        Log.d(TAG, "saveDetectImage: " + "recognitions num " + recog_size);

        String detect_res = "";
        if (recog_size > 0) {

            for (int i = 0; i < recog_size - 1; i++) {
                InferenceResult.Recognition rego = recognitions.get(i);
                RectF detection = rego.getLocation();
                if (rego.getConfidence() < 0.50f) {
                    continue;
                }

                int rego_get_track_id = rego.getTrackId();
                int rego_get_id = rego.getId();
                String conf_str = rego.getConfidence().toString();
                String rego_str = rego.toString();
                String label_str = mlabels.get(rego_get_id);
                if (detect_res.equals("")) {
                    detect_res = label_str;
                } else {
                    detect_res = detect_res + "," + label_str;
                }

                Log.d(TAG, "saveDetectImage: " + "trackId " + rego_get_track_id);
                Log.d(TAG, "saveDetectImage: " + "label " + label_str);
                Log.d(TAG, "saveDetectImage: " + "conf " + conf_str);
                Log.d(TAG, "saveDetectImage: " + "rego_str " + rego_str);
                Log.d(TAG, "saveDetectImage: " + "rect.left " + detection.left);
                Log.d(TAG, "saveDetectImage: " + "rect.right " + detection.right);
                Log.d(TAG, "saveDetectImage: " + "rect.top " + detection.top);
                Log.d(TAG, "saveDetectImage: " + "rect.bottom " + detection.bottom);

                // !< left, top, right, bottom -> left, top, right-left(width), bottom-top(height)
                RectF nex_rect = recognitions.get(i+1).getLocation();
                float iou = computeRectJoinUnion(detection, nex_rect);
                if (iou < 0.1f) {
                    mTrackResultCanvas.drawRect(detection, mTrackResultPaint);
                    //int x = (int)(detection.left + (detection.width() - (int) mTrackResultTextPaint.measureText(label_str))/2);
                    //int y = (int)(detection.top + (detection.height() + sp2px(12))/2);
                    //mTrackResultCanvas.drawText(label_str, x, y, mTrackResultTextPaint);
                    int x = (int)detection.left;
                    int y = (int)detection.top + sp2px(12);
                    mTrackResultCanvas.drawText(label_str, x, y, mTrackResultTextPaint);
                    //mTrackResultCanvas.drawText(label_str, detection.left + 5, detection.bottom - 5, mTrackResultTextPaint);
                }
            }
        }
        Log.d(TAG, "saveDetectImage: detect_res " + detect_res);
        if (!detect_res.equals("")) {
            mEpboxCV.detect_res = UniqueNumbers.removeDuplicates(detect_res);
        }
        Log.d(TAG, "saveDetectImage: detect_res remove duplicate " + mEpboxCV.detect_res);
        return mutableBitmap;
    }

    private static float computeRectJoinUnion(RectF box1, RectF box2) {
        float x1 = box1.left, y1 = box1.top, w1 = box1.right - box1.left, h1 = box1.bottom - box1.top;
        float x2 = box2.left, y2 = box2.top, w2 = box2.right - box2.left, h2 = box2.bottom - box2.top;

        // 检查是否没有重叠
        if (x1 > x2 + w2 || y1 > y2 + h2 || x2 > x1 + w1 || y2 > y1 + h1) {
            return 0.0f;
        }

        // 计算交集矩形的宽度和高度
        float joinRectW = Math.min(x1 + w1, x2 + w2) - Math.max(x1, x2);
        float joinRectH = Math.min(y1 + h1, y2 + h2) - Math.max(y1, y2);

        // 计算交集面积
        float joinUnionArea = joinRectW * joinRectH;

        // 计算两个矩形的面积
        float box1Area = w1 * h1;
        float box2Area = w2 * h2;

        // 返回交并比 (IoU)
        return 1.0f * joinUnionArea / (box1Area + box2Area - joinUnionArea);
    }

    private void saveDetectImage(Bitmap originalBitmap, EpboxCV epboxCV) {
        Bitmap mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);

        // 将新Bitmap作为参数
        mTrackResultCanvas = new Canvas(mutableBitmap);

        ArrayList<InferenceResult.Recognition> recognitions = mInferenceResult.getResult(mInferenceWrapper);
        int recog_size = recognitions.size();
        Log.d(TAG, "saveDetectImage: " + "recognitions num " + recog_size);

        String detect_res = "";
        if (recog_size > 0) {
            for (int i = 0; i < recog_size; ++i) {
                InferenceResult.Recognition rego = recognitions.get(i);
                RectF detection = rego.getLocation();

                Log.d(TAG, "saveDetectImage: " + "rect.left " + detection.left);
                Log.d(TAG, "saveDetectImage: " + "rect.right " + detection.right);
                Log.d(TAG, "saveDetectImage: " + "rect.top " + detection.top);
                Log.d(TAG, "saveDetectImage: " + "rect.bottom " + detection.bottom);

                mTrackResultCanvas.drawRect(detection, mTrackResultPaint);

                int rego_get_track_id = rego.getTrackId();
                int rego_get_id = rego.getId();
                String conf_str = rego.getConfidence().toString();
                String rego_str = rego.toString();
                String label_str = mlabels.get(rego_get_id);
                if (detect_res.equals("")) {
                    detect_res = label_str;
                } else {
                    detect_res = detect_res + "," + label_str;
                }

                Log.d(TAG, "saveDetectImage: " + "trackId " + rego_get_track_id);
                Log.d(TAG, "saveDetectImage: " + "label " + label_str);
                Log.d(TAG, "saveDetectImage: " + "conf " + conf_str);
                Log.d(TAG, "saveDetectImage: " + "rego_str " + rego_str);

                int x = (int)(detection.left + (detection.width() - (int) mTrackResultTextPaint.measureText(label_str))/2);
                int y = (int)(detection.top + (detection.height() + sp2px(12))/2);

                mTrackResultCanvas.drawText(label_str, x, y, mTrackResultTextPaint);
            }
        }

        Log.d(TAG, "saveDetectImage: detect_res " + detect_res);
        if (!detect_res.equals("")) {
            epboxCV.detect_res = UniqueNumbers.removeDuplicates(detect_res);
        }
        Log.d(TAG, "saveDetectImage: detect_res remove duplicate " + mEpboxCV.detect_res);
        epboxCV.phone = mutableBitmap;
    }

    private void saveDetectImage(Bitmap bmp_, EpboxCV epboxCV, InferenceResult result) {
        Bitmap mutableBitmap = bmp_.copy(Bitmap.Config.ARGB_8888, true);

        // 将新Bitmap作为参数
        mTrackResultCanvas = new Canvas(mutableBitmap);

        ArrayList<InferenceResult.Recognition> recognitions = result.getResult(mInferenceWrapper);
        int recog_size = recognitions.size();
        Log.d(TAG, "saveDetectImage: " + "recognitions num " + recog_size);

        String detect_res = "";
        if (recog_size > 0) {
            for (int i = 0; i < recog_size; ++i) {
                InferenceResult.Recognition rego = recognitions.get(i);
                RectF detection = rego.getLocation();

                Log.d(TAG, "saveDetectImage: " + "rect.left " + detection.left);
                Log.d(TAG, "saveDetectImage: " + "rect.right " + detection.right);
                Log.d(TAG, "saveDetectImage: " + "rect.top " + detection.top);
                Log.d(TAG, "saveDetectImage: " + "rect.bottom " + detection.bottom);

                mTrackResultCanvas.drawRect(detection, mTrackResultPaint);

                int rego_get_track_id = rego.getTrackId();
                int rego_get_id = rego.getId();
                String conf_str = rego.getConfidence().toString();
                String rego_str = rego.toString();
                String label_str = mlabels.get(rego_get_id);
                if (detect_res.equals("")) {
                    detect_res = label_str;
                } else {
                    detect_res = detect_res + "," + label_str;
                }

                Log.d(TAG, "saveDetectImage: " + "trackId " + rego_get_track_id);
                Log.d(TAG, "saveDetectImage: " + "label " + label_str);
                Log.d(TAG, "saveDetectImage: " + "conf " + conf_str);
                Log.d(TAG, "saveDetectImage: " + "rego_str " + rego_str);

                int x = (int)(detection.left + (detection.width() - (int) mTrackResultTextPaint.measureText(label_str))/2);
                int y = (int)(detection.top + (detection.height() + sp2px(12))/2);

                mTrackResultCanvas.drawText(label_str, x, y, mTrackResultTextPaint);
            }
        }

        Log.d(TAG, "saveDetectImage: detect_res " + detect_res);
        if (!detect_res.equals("")) {
            epboxCV.detect_res = UniqueNumbers.removeDuplicates(detect_res);
        }
        Log.d(TAG, "saveDetectImage: detect_res remove duplicate " + mEpboxCV.detect_res);
        epboxCV.phone = mutableBitmap;
    }

}

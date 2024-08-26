package cn.epbox.cv_aix;

import android.graphics.Bitmap;

/**
 * Created by tianzx on 22-10-27.
 */

public class EpboxCV {

    private static final String TAG = "EpboxCV";

    public EpboxCV() {
        phone = null;
        status = 0;
        angle_state = 0;
        angle = 0.0f;
        qr = "";
        detect_res = "";
        msg = "";
    }


    // 提取图片手机区域对象
    public Bitmap phone;
    // 记录状态
    public int status;
    // 判断摆放角度是否异常
    public int angle_state;
    // 记录摆放角度
    public float angle;
    // 二维码检测识别结果
    public String qr;
    // 记录检测结果
    public String detect_res;
    // 记录日志信息
    public String msg;
}

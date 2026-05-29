package com.label;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.util.HashMap;
import java.util.Map;

/**
 * 二维码生成工具类
 */
public class QRCodeGenerator {

    /**
     * 生成二维码图片
     *
     * @param content 二维码内容
     * @param width   图片宽度
     * @param height  图片高度
     * @return JavaFX Image 对象
     */
    public static Image generateQRCodeImage(String content, int width, int height) {
        try {
            // 配置二维码参数
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            // 生成二维码矩阵
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);

            // 转换为 JavaFX Image
            WritableImage image = new WritableImage(width, height);
            PixelWriter pixelWriter = image.getPixelWriter();

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixelWriter.setColor(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            return image;
        } catch (WriterException e) {
            throw new RuntimeException("生成二维码失败", e);
        }
    }
}

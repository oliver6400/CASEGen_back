package com.casegen.interoperability.image;

import org.opencv.core.Mat;

interface LocalOcrEngine {
    OcrResult recognize(Mat image, int pageSegmentationMode);

    boolean available();

    record OcrResult(String text, boolean available) {
        public OcrResult {
            text = text == null ? "" : text.trim();
        }
    }
}

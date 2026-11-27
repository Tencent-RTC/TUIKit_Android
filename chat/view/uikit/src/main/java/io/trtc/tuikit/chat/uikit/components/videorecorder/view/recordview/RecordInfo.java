package io.trtc.tuikit.chat.uikit.components.videorecorder.view.recordview;

import io.trtc.tuikit.chat.uikit.components.videorecorder.config.VideoRecorderConfigInternal;
import io.trtc.tuikit.chat.uikit.components.videorecorder.core.VideoRecordCoreConstant;
import io.trtc.tuikit.chat.uikit.components.videorecorder.utils.VideoRecorderData;

public class RecordInfo {

    public VideoRecorderData<RecordStatus> recordStatus = new VideoRecorderData<>(RecordStatus.IDLE);
    public VideoRecorderData<Float> recordProcess = new VideoRecorderData<>(0.0f);
    public VideoRecorderData<Integer> aspectRatio = new VideoRecorderData<>(VideoRecordCoreConstant.VIDEO_ASPECT_RATIO_9_16);
    public VideoRecorderData<Boolean> isFrontCamera = new VideoRecorderData<>(VideoRecorderConfigInternal.getInstance()
            .getIsDefaultFrontCamera());
    public VideoRecorderData<Boolean> isFlashOn = new VideoRecorderData<>(false);
    public RecordResult recordResult = new RecordResult();

    public enum RecordStatus {
        IDLE, STOP, RECORDING, TAKE_PHOTOING
    }

    static public class RecordResult {

        public String path;
        public int type;
        public boolean isSuccess;
        public int code;
    }
}

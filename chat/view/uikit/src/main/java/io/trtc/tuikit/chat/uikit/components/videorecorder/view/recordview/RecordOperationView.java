package io.trtc.tuikit.chat.uikit.components.videorecorder.view.recordview;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.tencent.imsdk.base.ThreadUtils;
import io.trtc.tuikit.chat.uikit.components.videorecorder.RecordMode;
import io.trtc.tuikit.chat.uikit.R;
import io.trtc.tuikit.chat.uikit.components.videorecorder.config.VideoRecorderConfigInternal;
import io.trtc.tuikit.chat.uikit.components.videorecorder.core.VideoRecorderRecordCore;
import io.trtc.tuikit.chat.uikit.components.videorecorder.core.VideoRecordCoreConstant;
import io.trtc.tuikit.chat.uikit.components.videorecorder.utils.VideoRecorderData.VideoRecorderDataObserver;
import io.trtc.tuikit.chat.uikit.components.videorecorder.utils.VideoRecorderFileUtil;
import io.trtc.tuikit.chat.uikit.components.videorecorder.utils.VideoRecorderFileUtil.VideoRecodeFileType;
import io.trtc.tuikit.chat.uikit.components.videorecorder.utils.VideoRecorderResourceUtils;
import io.trtc.tuikit.chat.uikit.components.videorecorder.view.VideoRecorderAuthorizationPrompter;
import io.trtc.tuikit.chat.uikit.components.videorecorder.view.VideoRecorderAuthorizationPrompter.PrompterType;
import io.trtc.tuikit.chat.uikit.components.videorecorder.view.recordview.RecordInfo.RecordStatus;

@SuppressLint("ViewConstructor")
public class RecordOperationView extends LinearLayout {

    private final String TAG = RecordOperationView.class.getSimpleName() + "_" + hashCode();
    private final Context mContext;
    private final VideoRecorderRecordCore mRecordCore;
    private final RecordInfo mRecordInfo;

    private RecordButtonView mRecordButtonView;
    private TextView mRecordOperationTipsView;
    private TextView mRecordTimeTextView;
    private ImageView mCancelRecordView;
    private ImageView mSwitchCameraView;
    private Float mLastRecordProcess = 0.0f;
    private String mLastRecordTime;
    private boolean isNeedShowOperationTipsView = true;

    private final VideoRecorderDataObserver<RecordStatus> mRecordStatusObserver = new VideoRecorderDataObserver<RecordStatus>() {
        @Override
        public void onChanged(RecordStatus recordStatus) {
            if (recordStatus == RecordStatus.RECORDING || recordStatus == RecordStatus.TAKE_PHOTOING) {
                hideRecordOperationTips();
                showRecordOperationButton(false);
            } else {
                mRecordButtonView.setProcess(0);
                mLastRecordProcess = 0.0f;
                mLastRecordTime = null;
                if (recordStatus == RecordStatus.STOP && !mRecordInfo.recordResult.isSuccess
                        && mRecordInfo.recordResult.code == VideoRecordCoreConstant.RECORD_RESULT_OK_LESS_THAN_MINDURATION) {
                    if (VideoRecorderConfigInternal.getInstance().getRecordMode() == RecordMode.MIXED
                            && mRecordCore.isUGCRecorderCore()) {
                        mRecordCore.takePhoto(
                                VideoRecorderFileUtil.generateRecodeFilePath(VideoRecodeFileType.PICTURE_FILE));
                    } else {
                        Toast.makeText(mContext, R.string.video_recorder_recode_time_short_tips, Toast.LENGTH_SHORT).show();
                    }
                }
                showRecordOperationButton(true);
            }
        }
    };
    private final VideoRecorderDataObserver<Float> mRecordProcessObserver = new VideoRecorderDataObserver<Float>() {
        @Override
        public void onChanged(Float process) {
            Log.i(TAG, "record process is " + process);
            if (process > mLastRecordProcess) {
                mLastRecordProcess = process;
                mRecordButtonView.setProcess(process);
                setRecordTime(process);
            }
        }
    };

    public RecordOperationView(@NonNull Context context, VideoRecorderRecordCore recordCore, RecordInfo recordInfo) {
        super(context);
        mContext = context;
        mRecordCore = recordCore;
        mRecordInfo = recordInfo;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        initView();
        addObserver();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeObserver();
        removeAllViews();
    }

    public void initView() {
        LayoutInflater.from(mContext).inflate(R.layout.video_recorder_operation_view, this, true);

        mCancelRecordView = findViewById(R.id.video_recorder_cancel_record_button);
        mCancelRecordView.setOnClickListener(v -> {
            ((Activity) mContext).setResult(Activity.RESULT_CANCELED, null);
            ((Activity) mContext).finish();
        });


        mSwitchCameraView = findViewById(R.id.video_recorder_switch_camera);
        mSwitchCameraView.setOnClickListener(v -> {
            boolean is_front_camera = mRecordInfo.isFrontCamera.get();
            mRecordCore.switchCamera(!is_front_camera);
        });

        mRecordTimeTextView = findViewById(R.id.video_recorder_record_time);
        mRecordOperationTipsView = findViewById(R.id.video_recorder_record_operation_tips);
        mRecordOperationTipsView.setVisibility(isNeedShowOperationTipsView ? VISIBLE : INVISIBLE);
        mRecordButtonView = findViewById(R.id.video_recorder_start_record_button);
        switch (VideoRecorderConfigInternal.getInstance().getRecordMode()) {
            case MIXED:
                initRecordButtonForMixedRecode();
                break;
            case PHOTO_ONLY:
                initRecordButtonForTakePhoto();
                break;
            case VIDEO_ONLY:
                initRecordButtonForVideoRecode();
                break;
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initRecordButtonForMixedRecode() {
        mRecordButtonView.setOnLongClickListener(v -> {
            int result = mRecordCore.startRecord(
                    VideoRecorderFileUtil.generateRecodeFilePath(VideoRecodeFileType.VIDEO_FILE));

            if (result == VideoRecordCoreConstant.START_RECORD_ERR_LICENCE_VERIFICATION_FAILED) {
                VideoRecorderAuthorizationPrompter.showPermissionPrompterDialog(mContext,  PrompterType.NO_SIGNATURE);
            }
            return false;
        });

        mRecordButtonView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                mRecordCore.stopRecord();
                mRecordTimeTextView.setVisibility(INVISIBLE);
            }
            return false;
        });

        mRecordButtonView.setOnClickListener(
                v -> {
                    int result = mRecordCore.takePhoto(
                            VideoRecorderFileUtil.generateRecodeFilePath(VideoRecodeFileType.PICTURE_FILE));

                    if (result == VideoRecordCoreConstant.START_RECORD_ERR_LICENCE_VERIFICATION_FAILED) {
                        VideoRecorderAuthorizationPrompter.showPermissionPrompterDialog(mContext, PrompterType.NO_SIGNATURE);
                    }
                });
        mRecordOperationTipsView.setText(R.string.video_recorder_mixed_mode_operation_tips);
        mRecordButtonView.setIsOnlySupportTakePhoto(false);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initRecordButtonForVideoRecode() {
        mRecordButtonView.setOnLongClickListener(v -> {
            mRecordTimeTextView.setVisibility(VISIBLE);
            int result = mRecordCore.startRecord(
                    VideoRecorderFileUtil.generateRecodeFilePath(VideoRecodeFileType.VIDEO_FILE));

            if (result == VideoRecordCoreConstant.START_RECORD_ERR_LICENCE_VERIFICATION_FAILED) {
                VideoRecorderAuthorizationPrompter.showPermissionPrompterDialog(mContext, PrompterType.NO_SIGNATURE);
            }
            return false;
        });

        mRecordButtonView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                mRecordCore.stopRecord();
                mRecordTimeTextView.setVisibility(INVISIBLE);
            }
            return false;
        });
        
        mRecordOperationTipsView.setText(R.string.video_recorder_video_mode_operation_tips);
        mRecordButtonView.setIsOnlySupportTakePhoto(false);
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private void initRecordButtonForTakePhoto() {
        mRecordButtonView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                mRecordInfo.recordStatus.set(RecordStatus.TAKE_PHOTOING);
            }
            return false;
        });

        mRecordButtonView.setOnClickListener(
                v -> {
                    int result = mRecordCore.takePhoto(
                            VideoRecorderFileUtil.generateRecodeFilePath(VideoRecodeFileType.PICTURE_FILE));

                    if (result == VideoRecordCoreConstant.START_RECORD_ERR_LICENCE_VERIFICATION_FAILED) {
                        VideoRecorderAuthorizationPrompter.showPermissionPrompterDialog(mContext, PrompterType.NO_SIGNATURE);
                    }
                });

        mRecordOperationTipsView.setText(R.string.video_recorder_phone_mode_operation_tips);
        mRecordButtonView.setIsOnlySupportTakePhoto(true);
    }

    private void setRecordTime(float process) {
        int maxDuration = mRecordCore.getMaxDuration();
        String recordTime = VideoRecorderResourceUtils.secondsToTimeString((long) (process * maxDuration));
        if (recordTime.equals(mLastRecordTime)) {
            return;
        }
        mLastRecordTime = recordTime;
        ThreadUtils.getUiThreadHandler().post(() -> mRecordTimeTextView.setText(mLastRecordTime));
    }

    private void addObserver() {
        mRecordInfo.recordStatus.observe(mRecordStatusObserver);
        mRecordInfo.recordProcess.observe(mRecordProcessObserver);
    }

    private void removeObserver() {
        mRecordInfo.recordStatus.removeObserver(mRecordStatusObserver);
        mRecordInfo.recordProcess.removeObserver(mRecordProcessObserver);
    }

    private void showRecordOperationButton(boolean isShow) {
        if (mSwitchCameraView == null || mCancelRecordView == null) {
            return;
        }

        int visibility = isShow ? VISIBLE : INVISIBLE;
        mSwitchCameraView.setVisibility(visibility);
        mCancelRecordView.setVisibility(visibility);
    }

    private void hideRecordOperationTips() {
        if (mRecordOperationTipsView != null) {
            mRecordOperationTipsView.setVisibility(INVISIBLE);
        }
        isNeedShowOperationTipsView = false;
    }
}

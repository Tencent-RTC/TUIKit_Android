package io.trtc.tuikit.chat.uikit.components.videorecorder.view.recordview;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Point;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import io.trtc.tuikit.chat.uikit.R;
import io.trtc.tuikit.chat.uikit.components.videorecorder.core.VideoRecorderRecordCore;
import io.trtc.tuikit.chat.uikit.components.videorecorder.utils.VideoRecorderData.VideoRecorderDataObserver;
import io.trtc.tuikit.chat.uikit.components.videorecorder.utils.VideoRecorderResourceUtils;
import io.trtc.tuikit.chat.uikit.components.videorecorder.view.recordview.RecordInfo.RecordStatus;

@SuppressLint("ViewConstructor")
public class RecordFunctionView extends LinearLayout {

    private final static int DEFAULT_OPERATION_VIEW_HEIGHT_DP = 215;
    private final static int MIN_OPERATION_VIEW_HEIGHT_DP = 155;
    private final static int DEFAULT_OPERATION_TIPS_VIEW_HEIGHT_DP = 40;
    private final Context mContext;
    private final VideoRecorderRecordCore mRecordCore;
    private final RecordInfo mRecordInfo;

    private RecordSettingView mRecordSettingView;
    private RecordOperationView mRecordOperationView;

    private RelativeLayout mOperationViewContainer;
    private RelativeLayout mSettingViewContainer;
    private final VideoRecorderDataObserver<RecordStatus> mRecodeStatusOnChanged = new VideoRecorderDataObserver<RecordStatus>() {
        @Override
        public void onChanged(RecordStatus recordStatus) {
            if (mSettingViewContainer != null) {
                int visibility = (recordStatus == RecordStatus.RECORDING
                        || recordStatus == RecordStatus.TAKE_PHOTOING) ? GONE : VISIBLE;
                mSettingViewContainer.setVisibility(visibility);
            }
        }
    };

    public RecordFunctionView(@NonNull Context context, VideoRecorderRecordCore recordCore, RecordInfo recordInfo) {
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

    @Override
    public void removeAllViews() {
        if (mOperationViewContainer != null) {
            mOperationViewContainer.removeAllViews();
        }

        if (mSettingViewContainer != null) {
            mSettingViewContainer.removeAllViews();
        }
        super.removeAllViews();
    }

    public void initView() {
        LayoutInflater.from(mContext).inflate(R.layout.video_recorder_function_view, this, true);

        initRecordOperationView();

        mSettingViewContainer = findViewById(R.id.video_recorder_setting_view_container);
        mSettingViewContainer.removeAllViews();
        if (mRecordSettingView == null) {
            mRecordSettingView = new RecordSettingView(mContext, mRecordCore, mRecordInfo);
        }
        mSettingViewContainer.addView(mRecordSettingView, new RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        applyEdgeToEdgeInsets();
    }

    private void applyEdgeToEdgeInsets() {
        // Reserve space for the system status bar / navigation bar / display
        // cutout so that the top settings panel and the bottom record-operation
        // panel are never obscured on EdgeToEdge enforced devices (Android 15+).
        ViewCompat.setOnApplyWindowInsetsListener(this, (view, insets) -> {
            applyInsets(insets);
            return insets;
        });
        WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(this);
        if (rootInsets != null) {
            applyInsets(rootInsets);
        }
        ViewCompat.requestApplyInsets(this);
    }

    private void applyInsets(@NonNull WindowInsetsCompat insets) {
        Insets systemBars = insets.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars());
        Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
        int leftInset = Math.max(systemBars.left, cutout.left);
        int topInset = Math.max(systemBars.top, cutout.top);
        int rightInset = Math.max(systemBars.right, cutout.right);
        int bottomInset = Math.max(systemBars.bottom, cutout.bottom);
        setPadding(leftInset, topInset, rightInset, bottomInset);
    }

    private void initRecordOperationView() {
        mOperationViewContainer = findViewById(R.id.video_recorder_operation_view_container);
        mOperationViewContainer.removeAllViews();
        if (mRecordOperationView == null) {
            mRecordOperationView = new RecordOperationView(mContext, mRecordCore, mRecordInfo);
        }
        adjustRecordOperationViewPosition(mRecordInfo.aspectRatio.get());
    }

    private void adjustRecordOperationViewPosition(int aspectRation) {
        if (mOperationViewContainer == null || mRecordOperationView == null) {
            return;
        }

        mOperationViewContainer.removeAllViews();
        Point screenSize = VideoRecorderResourceUtils.getScreenSize(mContext);
         int viewHeight = screenSize.y - screenSize.x * 16 / 9 + VideoRecorderResourceUtils
                .dip2px(mContext, DEFAULT_OPERATION_TIPS_VIEW_HEIGHT_DP);

        int minViewHeight = VideoRecorderResourceUtils.dip2px(mContext, MIN_OPERATION_VIEW_HEIGHT_DP);
        viewHeight = Math.max(viewHeight, minViewHeight);
        LayoutParams linearLayoutCompat = new LayoutParams(MATCH_PARENT, viewHeight);
        mOperationViewContainer.addView(mRecordOperationView, linearLayoutCompat);
    }

    private void addObserver() {
        mRecordInfo.recordStatus.observe(mRecodeStatusOnChanged);
        mRecordInfo.aspectRatio.observe(this::adjustRecordOperationViewPosition);
    }

    private void removeObserver() {
        mRecordInfo.recordStatus.removeObserver(mRecodeStatusOnChanged);
        mRecordInfo.aspectRatio.removeObserver(this::adjustRecordOperationViewPosition);
    }
}

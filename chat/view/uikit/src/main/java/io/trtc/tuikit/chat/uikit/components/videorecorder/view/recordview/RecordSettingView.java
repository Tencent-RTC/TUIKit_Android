package io.trtc.tuikit.chat.uikit.components.videorecorder.view.recordview;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.content.Context;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;

import io.trtc.tuikit.chat.uikit.R;
import io.trtc.tuikit.chat.uikit.components.videorecorder.config.VideoRecorderConfigInternal;
import io.trtc.tuikit.chat.uikit.components.videorecorder.core.UGCReflectVideoRecorderCore;
import io.trtc.tuikit.chat.uikit.components.videorecorder.core.VideoRecorderRecordCore;
import io.trtc.tuikit.chat.uikit.components.videorecorder.core.VideoRecorderSignatureChecker;
import io.trtc.tuikit.chat.uikit.components.videorecorder.core.VideoRecorderSignatureChecker.ResultCode;
import io.trtc.tuikit.chat.uikit.components.videorecorder.utils.VideoRecorderData.VideoRecorderDataObserver;
import io.trtc.tuikit.chat.uikit.components.videorecorder.utils.VideoRecorderResourceUtils;

public class RecordSettingView extends RelativeLayout {

    private static boolean isAppDebuggable(Context context) {
        return (context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private static final int SETTING_ITEM_VIEW_BOTTOM_MARGIN_SP = 24;
    private final String TAG = RecordSettingView.class.getSimpleName() + "_" + hashCode();
    private final Context mContext;
    private final VideoRecorderRecordCore mRecordCore;

    private RecordInfo mRecordInfo;
    private SettingItemViewHolder mSettingItemTorch;
    private RecordAspectView mAspectView;
    private LinearLayout mLayoutSetting;
    
    private final VideoRecorderDataObserver<Boolean> flashStatusObserver = isOnAndFront -> {
        if (mSettingItemTorch == null) {
            return;
        }

        Log.i(TAG,"is front camera:" + mRecordInfo.isFrontCamera.get()
                + " is flash on: " + mRecordInfo.isFlashOn.get());

        if (mRecordInfo.isFrontCamera.get()) {
            mSettingItemTorch.setIconRes(R.drawable.video_recorder_torch_close_disable);
            mSettingItemTorch.setClickable(false);
            return;
        }

        int resId = mRecordInfo.isFlashOn.get() ? R.drawable.video_recorder_torch_open : R.drawable.video_recorder_torch_close;
        mSettingItemTorch.setIconRes(resId);
        mSettingItemTorch.setClickable(true);
    };

    public RecordSettingView(@NonNull Context context, VideoRecorderRecordCore recordCore, RecordInfo recordInfo) {
        super(context);
        mContext = context;
        mRecordCore = recordCore;
        mRecordInfo = recordInfo;
    }

    @Override
    public void onAttachedToWindow() {
        Log.i(TAG, "onAttachedToWindow");
        super.onAttachedToWindow();
        initView();
        addObserver();
    }

    @Override
    public void onDetachedFromWindow() {
        Log.i(TAG, "onDetachedFromWindow");
        super.onDetachedFromWindow();
        removeAllViews();
        removeObserver();
    }

    @Override
    public void removeAllViews() {
        mLayoutSetting.removeAllViews();
        super.removeAllViews();
    }

    public void initView() {
        LayoutInflater.from(mContext).inflate(R.layout.video_recorder_setting_view, this, true);
        mLayoutSetting = findViewById(R.id.video_recorder_layout_setting_item);

        initTorchView();
        initAspectView();
    }

    private void initTorchView() {
        if (!VideoRecorderConfigInternal.getInstance().isSupportRecordTorch()) {
            return;
        }

        mSettingItemTorch = addSettingItem(R.drawable.video_recorder_torch_close, R.string.video_recorder_torch);
        mSettingItemTorch.setOnClickListener(v -> {
            boolean isFlashOn = mRecordInfo.isFlashOn.get();
            mRecordCore.toggleTorch(!isFlashOn);
        });
    }

    private void initAspectView() {
        if (!VideoRecorderConfigInternal.getInstance().isSupportRecordAspect() || !isSupportAdvanceFunction()) {
            return;
        }

        if (mAspectView == null) {
            mAspectView = new RecordAspectView(getContext(), mRecordCore, mRecordInfo);
        }

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        layoutParams.bottomMargin = VideoRecorderResourceUtils.dip2px(mContext, SETTING_ITEM_VIEW_BOTTOM_MARGIN_SP);
        layoutParams.gravity = Gravity.CENTER;
        mLayoutSetting.addView(mAspectView,layoutParams);
    }

    private SettingItemViewHolder addSettingItem(int iconResId, int titleResId) {
        View view = LayoutInflater.from(mContext)
                .inflate(R.layout.video_recorder_setting_item_view, this, false);
        SettingItemViewHolder settingItemViewHolder = new SettingItemViewHolder(view);
        settingItemViewHolder.setIconRes(iconResId);
        settingItemViewHolder.setTitle(titleResId);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        layoutParams.bottomMargin = VideoRecorderResourceUtils.dip2px(mContext, SETTING_ITEM_VIEW_BOTTOM_MARGIN_SP);
        layoutParams.gravity = Gravity.CENTER;
        mLayoutSetting.addView(view,layoutParams);
        return settingItemViewHolder;
    }

    private void addObserver() {
        mRecordInfo.isFrontCamera.observe(flashStatusObserver);
        mRecordInfo.isFlashOn.observe(flashStatusObserver);
    }

    private void removeObserver() {
        mRecordInfo.isFrontCamera.removeObserver(flashStatusObserver);
        mRecordInfo.isFlashOn.removeObserver(flashStatusObserver);
    }

    private boolean isSupportAdvanceFunction() {
        if (isAppDebuggable(getContext())) {
            return true;
        }

        return VideoRecorderSignatureChecker.getInstance().getSetSignatureResult() == ResultCode.SUCCESS
                && UGCReflectVideoRecorderCore.isAvailable();
    }

    static class SettingItemViewHolder{
        private final View mRootView;
        private final ImageView mIcon;
        private final TextView mTitle;

        public SettingItemViewHolder(View rootView) {
            mRootView = rootView;
            mIcon = mRootView.findViewById(R.id.video_recorder_setting_icon);
            mTitle = mRootView.findViewById(R.id.video_recorder_setting_title);
        }

        public void setIconRes(int iconResId) {
            mIcon.setBackgroundResource(iconResId);
        }

        public void setTitle(int titleResId) {
            mTitle.setText(VideoRecorderResourceUtils.getString(titleResId));
        }

        public void setOnClickListener(OnClickListener onClickListener) {
            mRootView.setOnClickListener(onClickListener);
        }

        public void setClickable(boolean clickable) {
            mRootView.setClickable(clickable);
        }
    }
}

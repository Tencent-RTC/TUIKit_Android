package io.trtc.tuikit.chat.uikit.components.videorecorder.view.preview;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import io.trtc.tuikit.chat.uikit.R;
import io.trtc.tuikit.chat.uikit.components.videorecorder.config.VideoRecorderConfigInternal;
import io.trtc.tuikit.chat.uikit.components.videorecorder.utils.VideoRecorderResourceUtils;

public class PreviewCommonCtrlView extends RelativeLayout {

    private final static int COMMON_FUNCTION_ICON_SIZE_DP = 28;
    private final static int BGM_FUNCTION_ICON_MARGIN_DP = 34;
    private final static int RETURN_BACK_BASE_TOP_MARGIN_DP = 16;
    private final static int RETURN_BACK_BASE_START_MARGIN_DP = 10;
    private final static int OPERATION_LAYOUT_EXTRA_BOTTOM_PADDING_DP = 32;

    private final String TAG = PreviewCommonCtrlView.class.getSimpleName() + "_" + hashCode();
    private final Context mContext;
    private final EditType mEditType;

    private ViewTreeObserver.OnGlobalLayoutListener mOnGlobalLayoutListener;
    private RelativeLayout mOperationLayout;
    private LinearLayout mFunctionButtonLayout;
    private ImageView mReturnBackView;
    private View mRootView;

    private float mAspectRatio = 9.0f / 16.0f;
    private View mMediaView;

    private TransformLayout mPreviewContainer;
    private final OnClickListener mOnPreviewClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            showOperationView(true);
        }
    };
    private Rect mPreviewRect;
    private CommonMediaEditListener mCommonMediaEditListener;

    public PreviewCommonCtrlView(Context context, EditType editType, boolean isRecordFile) {
        super(context);
        mContext = context;
        mEditType = editType;
    }

    @Override
    public void onAttachedToWindow() {
        Log.i(TAG, "onAttachedToWindow");
        super.onAttachedToWindow();
        initView();
    }

    @Override
    public void onDetachedFromWindow() {
        Log.i(TAG, "onDetachedFromWindow");
        super.onDetachedFromWindow();
        mRootView.getViewTreeObserver().removeOnGlobalLayoutListener(mOnGlobalLayoutListener);
        removeAllViews();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (!changed) {
            return;
        }

        adjustFunctionButtonLayout();
        adjustPreviewLayout();
    }

    public void initView() {
        mRootView = LayoutInflater.from(mContext).inflate(R.layout.video_recorder_preview_view, this);
        mOperationLayout = mRootView.findViewById(R.id.video_recorder_operation_layout);
        mReturnBackView = mRootView.findViewById(R.id.video_recorder_edit_return_back);
        mPreviewContainer = mRootView.findViewById(R.id.video_recorder_preview_container);
        mFunctionButtonLayout = mRootView.findViewById(R.id.video_recorder_function_button_layout);
        mPreviewContainer.enableTransform(mEditType == EditType.PHOTO);
        mPreviewContainer.setOnClickListener(mOnPreviewClickListener);
        mPreviewContainer.setOnClickListener(view -> switchOperationBtnShow());

        mReturnBackView.setOnClickListener(view -> {
            if (mCommonMediaEditListener != null) {
                mCommonMediaEditListener.onCancelEdit();
            }
        });

        initSendBtn();
        adjustPreviewLayout();
        setMediaView(mMediaView);
        applyEdgeToEdgeInsets();
        mOnGlobalLayoutListener = this::adjustFunctionButtonLayout;
        mRootView.getViewTreeObserver().addOnGlobalLayoutListener(mOnGlobalLayoutListener);
    }

    private void applyEdgeToEdgeInsets() {
        // Avoid the return-back arrow being covered by the status bar / camera
        // cutout, and the bottom operation row (with the send button) being
        // covered by the navigation bar / gesture handle on Android 15+ where
        // EdgeToEdge is enforced.
        //
        // Register the listener on `this` (the PreviewCommonCtrlView itself)
        // and immediately consult the root window insets so we apply the
        // correct padding even when the dispatch chain has been consumed by
        // an ancestor container.
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

    private void applyInsets(WindowInsetsCompat insets) {
        Insets systemBars = insets.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars());
        Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
        int topInset = Math.max(systemBars.top, cutout.top);
        int bottomInset = Math.max(systemBars.bottom, cutout.bottom);
        int leftInset = Math.max(systemBars.left, cutout.left);
        int rightInset = Math.max(systemBars.right, cutout.right);

        if (mReturnBackView != null) {
            ViewGroup.MarginLayoutParams returnLp =
                    (ViewGroup.MarginLayoutParams) mReturnBackView.getLayoutParams();
            if (returnLp != null) {
                boolean isRtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                int startInset = isRtl ? rightInset : leftInset;
                returnLp.topMargin = topInset
                        + VideoRecorderResourceUtils.dip2px(mContext, RETURN_BACK_BASE_TOP_MARGIN_DP);
                returnLp.setMarginStart(startInset
                        + VideoRecorderResourceUtils.dip2px(mContext, RETURN_BACK_BASE_START_MARGIN_DP));
                mReturnBackView.setLayoutParams(returnLp);
            }
        }

        if (mOperationLayout != null) {
            int extraBottom = VideoRecorderResourceUtils.dip2px(
                    mContext, OPERATION_LAYOUT_EXTRA_BOTTOM_PADDING_DP);
            int totalBottomMargin = bottomInset + extraBottom;
            ViewGroup.MarginLayoutParams operationLp =
                    (ViewGroup.MarginLayoutParams) mOperationLayout.getLayoutParams();
            if (operationLp != null) {
                operationLp.bottomMargin = totalBottomMargin;
                mOperationLayout.setLayoutParams(operationLp);
            }
            mOperationLayout.setPadding(leftInset, 0, rightInset, 0);
        }
    }

    public void setMediaView(View view) {
        if (view == null) {
            return;
        }

        adjustPreviewLayout();
        if (mPreviewContainer != null) {
            LayoutParams layoutParams = new LayoutParams(mPreviewRect.width(), mPreviewRect.height());
            layoutParams.leftMargin = mPreviewRect.left;
            layoutParams.topMargin = mPreviewRect.top;
            mPreviewContainer.addView(view, 0, layoutParams);
        }
        mMediaView = view;
    }

    public void setMediaAspectRatio(float aspectRatio) {
        Log.i(TAG, "set media aspect ratio. aspect ratio:" + aspectRatio);
        mAspectRatio = aspectRatio;
        adjustPreviewLayout();
    }

    private void initSendBtn() {
        TextView button = mRootView.findViewById(R.id.video_recorder_send_btn);
        button.setBackground(
                VideoRecorderResourceUtils.getDrawable(mContext, R.drawable.video_recorder_edit_send_button,
                        VideoRecorderConfigInternal.getInstance().getThemeColor()));
        button.setGravity(Gravity.CENTER);
        button.setText(VideoRecorderResourceUtils.getString(R.string.video_recorder_send));

        button.setOnClickListener(view -> {
            showOperationView(false);
            if (mCommonMediaEditListener != null) {
                mCommonMediaEditListener.onGenerateMedia();
            }
        });
    }

    public void setCommonMediaEditListener(CommonMediaEditListener commonMediaEditListener) {
        mCommonMediaEditListener = commonMediaEditListener;
    }

    public void showOperationView(boolean show) {
        int visible = show ? View.VISIBLE : View.GONE;
        mReturnBackView.setVisibility(visible);
        mOperationLayout.setVisibility(visible);
        mPreviewContainer.enableTransform(mEditType == EditType.PHOTO);
    }

    private void adjustPreviewLayout() {
        if (mPreviewContainer == null) {
            return;
        }

        int previewContainerWidth = mPreviewContainer.getWidth();
        int previewContainerHeight = mPreviewContainer.getHeight();

        if (previewContainerWidth == 0 || previewContainerHeight == 0) {
            Point screenSize = VideoRecorderResourceUtils.getScreenSize(mContext);
            previewContainerWidth = screenSize.x;
            previewContainerHeight = screenSize.y;
        }

        int previewHeight = (int) (previewContainerWidth / mAspectRatio);
        int previewTop = (previewContainerHeight - previewHeight) / 2;
        Rect previewRect = new Rect(0, previewTop, previewContainerWidth, previewTop + previewHeight);

        if (previewRect.equals(mPreviewRect)) {
            return;
        }
        mPreviewRect = previewRect;
        mPreviewContainer.initContentLayout(mPreviewRect);
    }

    private void adjustFunctionButtonLayout() {
        int width = mFunctionButtonLayout.getWidth();
        int count = mFunctionButtonLayout.getChildCount();
        int viewWidth = count * VideoRecorderResourceUtils.dip2px(mContext, COMMON_FUNCTION_ICON_SIZE_DP);
        int margin = (width - viewWidth) / (count + 1);
        for (int i = 0; i < count; i++) {
            View child = mFunctionButtonLayout.getChildAt(i);
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) child.getLayoutParams();
            layoutParams.setMarginStart(margin);
            layoutParams.setMarginEnd(0);
            child.setLayoutParams(layoutParams);
        }
    }

    private void switchOperationBtnShow() {
        if (mReturnBackView != null) {
            mReturnBackView.setVisibility(
                    mReturnBackView.getVisibility() == VISIBLE ? INVISIBLE : VISIBLE);
        }

        if (mOperationLayout != null) {
            mOperationLayout.setVisibility(
                    mOperationLayout.getVisibility() == VISIBLE ? INVISIBLE : VISIBLE);
        }
    }

    public enum EditType {
        VIDEO, PHOTO
    }

    public interface CommonMediaEditListener {

        void onGenerateMedia();

        void onCancelEdit();
    }
}
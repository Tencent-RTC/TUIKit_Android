package io.trtc.tuikit.chat.uikit.components.imageviewer.ui.photoview;
import android.view.MotionEvent;

public interface OnSingleFlingListener {
    boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY);
}

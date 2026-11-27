package io.trtc.tuikit.chat.uikit.components.videorecorder.view;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import io.trtc.tuikit.chat.uikit.R;
import io.trtc.tuikit.chat.uikit.components.videorecorder.utils.VideoRecorderResourceUtils;

public class VideoRecorderActivity extends AppCompatActivity {

    private final String TAG = VideoRecorderActivity.class.getSimpleName() + "_" + hashCode();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        VideoRecorderResourceUtils.setContext(this);
        super.onCreate(savedInstanceState);
        enableEdgeToEdgeDisplay();
        setContentView(R.layout.video_recorder_activity);
        if (savedInstanceState == null) {
            VideoRecorderFragment fragment = new VideoRecorderFragment();
            fragment.setArguments(getIntent().getExtras());
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.video_recorder_record_fragment_container, fragment)
                    .commit();
        }
    }

    private void enableEdgeToEdgeDisplay() {
        // EdgeToEdge layout: status bar / navigation bar are transparent and
        // the activity content is drawn behind them. Each foreground control
        // (record button row, top settings, return arrow, send button) is
        // responsible for consuming window insets locally so it stays clear of
        // the system bars and the display cutout. We deliberately do NOT enter
        // immersive (sticky) mode here: the preview screen needs the send /
        // return buttons to avoid the navigation bar, and the recording screen
        // should not pop transient system bars when the user swipes.
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
        }
    }
}

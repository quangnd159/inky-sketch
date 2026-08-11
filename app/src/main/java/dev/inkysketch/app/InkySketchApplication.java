package dev.inkysketch.app;

import android.app.Application;
import android.os.Build;

import com.onyx.android.sdk.rx.RxBaseAction;
import com.onyx.android.sdk.utils.ResManager;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

public final class InkySketchApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ResManager.init(this);
        RxBaseAction.init(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HiddenApiBypass.addHiddenApiExemptions("");
        }
    }
}

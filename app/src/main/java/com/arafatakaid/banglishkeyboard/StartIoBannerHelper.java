package com.arafatakaid.banglishkeyboard;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import com.startapp.sdk.ads.banner.Banner;

public final class StartIoBannerHelper {

    private StartIoBannerHelper() {
        // Utility class
    }

    public static void attach(
            Context context,
            FrameLayout container
    ) {

        if (context == null || container == null) {
            return;
        }

        // আগের Banner থাকলে সরিয়ে দাও
        container.removeAllViews();

        // Banner তৈরি
        Banner banner = new Banner(context);

        // Banner-এর layout
        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        params.gravity = Gravity.CENTER;

        // Container-এ Banner যোগ
        container.addView(
                banner,
                params
        );

        // Banner দৃশ্যমান
        container.setVisibility(View.VISIBLE);
    }
}

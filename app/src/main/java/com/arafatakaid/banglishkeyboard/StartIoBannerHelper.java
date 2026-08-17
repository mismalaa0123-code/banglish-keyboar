package com.arafatakaid.banglishkeyboard;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import com.startapp.sdk.ads.banner.Banner;
import com.startapp.sdk.ads.banner.BannerListener;

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

        // শুরুতে Banner-এর জায়গা থাকবে না
        container.setVisibility(View.GONE);

        // আগের কোনো Banner থাকলে সরিয়ে দেবে
        container.removeAllViews();

        Banner banner = new Banner(
                context,
                new BannerListener() {

                    @Override
                    public void onReceiveAd(View view) {
                        // Ad সফলভাবে load হলে
                        // Banner-এর জায়গা দেখা যাবে
                        container.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onFailedToReceiveAd(View view) {
                        // Ad না এলে কোনো জায়গা থাকবে না
                        container.setVisibility(View.GONE);
                    }

                    @Override
                    public void onImpression(View view) {
                        // Banner impression
                    }

                    @Override
                    public void onClick(View view) {
                        // Banner click
                    }
                }
        );

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        params.gravity = Gravity.CENTER;

        container.addView(
                banner,
                params
        );
    }
            }

package com.lsan.sec.ui;

import android.view.View;

/** 页面接口：每个 Tab 一个实现。 */
public interface Page {
    View build();

    void refresh();

    String title();
}

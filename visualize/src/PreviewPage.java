package local.visualize;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class PreviewPage {
    public interface Content { View markdown(String source); }

    public static void show(Activity activity, Content content) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(18 * activity.getResources().getDisplayMetrics().density);
        page.setPadding(padding, padding, padding, padding);
        scroll.addView(page);

        TextView heading = new TextView(activity);
        heading.setText("可视化示例");
        heading.setTextSize(22);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        page.addView(heading);
        TextView description = new TextView(activity);
        description.setText("直接提问即可。DeepSeek 会按内容选择文字、静态图示或交互卡片。");
        description.setTextSize(14);
        page.addView(description);

        HorizontalScrollView navigation = new HorizontalScrollView(activity);
        navigation.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(activity);
        navigation.addView(tabs);
        page.addView(navigation);

        LinearLayout panel = new LinearLayout(activity);
        page.addView(panel, new LinearLayout.LayoutParams(-1, -2));
        addTab(activity, tabs, panel, content, "图文与配色", VisualAssets.MEDIA);
        addTab(activity, tabs, panel, content, "知识展示", VisualAssets.REFERENCE);
        addTab(activity, tabs, panel, content, "项目工具", VisualAssets.WORKSPACE);
        addTab(activity, tabs, panel, content, "静态简报", VisualAssets.STATIC);
        addTab(activity, tabs, panel, content, "混合测验", VisualAssets.QUIZ);
        addTab(activity, tabs, panel, content, "数据表格", VisualAssets.TABLE);
        addTab(activity, tabs, panel, content, "播放量预测", VisualAssets.FORECAST);
        addTab(activity, tabs, panel, content, "更多组件", VisualAssets.GALLERY);
        showExample(panel, content, VisualAssets.MEDIA);
        activity.setContentView(scroll);
    }

    private static void addTab(Activity activity, LinearLayout tabs, LinearLayout panel, Content content,
                               String label, String json) {
        Button tab = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        tab.setText(label);
        tabs.addView(tab, new LinearLayout.LayoutParams(-2, -2));
        tab.setOnClickListener(view -> showExample(panel, content, json));
    }

    private static void showExample(LinearLayout panel, Content content, String json) {
        panel.removeAllViews();
        panel.addView(content.markdown("```visualize\n" + json.trim() + "\n```\n"),
                new LinearLayout.LayoutParams(-1, -2));
    }
}

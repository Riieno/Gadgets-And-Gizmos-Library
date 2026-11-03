package com.rieno.gadgetsandgizmos.lib.client.tablet;

// Project controls onto the shared 320 by 180 painted launcher canvas
public final class TabletLauncherLayout{
    public static final int APPS_PER_PAGE = 10;
    public static final int QUICK_LAUNCH_SIZE = 4;
    private static final TabletLayout CANVAS = new TabletLayout(320, 180);

    private TabletLauncherLayout(){}

    // Keep an empty launcher on its first page
    public static int pageCount(int appCount){
        return Math.max(1, (appCount + APPS_PER_PAGE - 1) / APPS_PER_PAGE);
    }

    // Match the background when a tablet surface stretches the canvas
    public static TabletLayout.Bounds bounds(int x, int y, int width, int height){
        return CANVAS.stretch(x, y, width, height);
    }

    // Keep the icon and its label above the painted quicklaunch tray
    public static TabletLayout.Rect app(TabletLayout.Bounds bounds, int idx){
        return CANVAS.project(bounds, new TabletLayout.Rect(24 + idx % 5 * 60, 28 + idx / 5 * 55, 32, 43));
    }

    // Place four shortcuts inside the painted tray
    public static TabletLayout.Rect quickLaunch(TabletLayout.Bounds bounds, int idx){
        return CANVAS.project(bounds, new TabletLayout.Rect(67 + idx * 54, 141, 24, 32));
    }

    // Fit one line of live information inside the painted status bar
    public static TabletLayout.Rect statusBar(TabletLayout.Bounds bounds){
        return CANVAS.project(bounds, new TabletLayout.Rect(10, 4, 300, 10));
    }

    // Leave page controls between the grid and the quicklaunch tray
    public static TabletLayout.Rect pageButton(TabletLayout.Bounds bounds, boolean next){
        return CANVAS.project(bounds, new TabletLayout.Rect(next ? 180 : 122, 126, 18, 10));
    }
}

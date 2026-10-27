package com.rieno.gadgetsandgizmos.lib.client.ui;

// Shared colors for documentation and shortcut help surfaces.
public record HelpModalStyle(int background, int panel, int border, int text, int muted, int accent){
    public static final HelpModalStyle DEFAULT = new HelpModalStyle(0xFF131923, 0xFF1C2633, 0xFF415264,
            0xFFE8EDF2, 0xFF9AABBD, 0xFF71C9EE);
}

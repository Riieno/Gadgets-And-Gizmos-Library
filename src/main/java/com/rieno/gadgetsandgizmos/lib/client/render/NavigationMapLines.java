package com.rieno.gadgetsandgizmos.lib.client.render;

import com.rieno.gadgetsandgizmos.lib.navigation.NavigationMapProjection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.List;

// Draw route chords inside the same map frame used by terrain and markers
public final class NavigationMapLines{
    private NavigationMapLines(){}

    public static void draw(GuiGraphics graphics, NavigationMapProjection frame, List<Vec3> points, int x, int y, int color){
        for(int idx = 1; idx < points.size(); idx++){
            var clipped = frame.clip(points.get(idx - 1), points.get(idx));
            if(clipped.isEmpty()) continue;
            var segment = clipped.get();
            double dx = segment.to().x() - segment.from().x(), dy = segment.to().y() - segment.from().y();
            double length = Math.hypot(dx, dy);
            if(length < .05) continue;
            graphics.pose().pushPose();
            try{
                graphics.pose().translate(x + segment.from().x(), y + segment.from().y(), 1);
                graphics.pose().mulPose(new Quaternionf().rotationZ((float)Math.atan2(dy, dx)));
                graphics.fill(0, -1, (int)Math.ceil(length), 2, 0xCC123237);
                graphics.fill(0, 0, (int)Math.ceil(length), 1, color);
            }finally{ graphics.pose().popPose(); }
        }
    }
}

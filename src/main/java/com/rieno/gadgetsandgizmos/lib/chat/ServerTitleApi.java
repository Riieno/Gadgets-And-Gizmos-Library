package com.rieno.gadgetsandgizmos.lib.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

// Deliver every vanilla title operation directly to a selected player audience on the server thread
public final class ServerTitleApi{
    private ServerTitleApi(){}

    public enum Action{
        TITLE, SUBTITLE, ACTIONBAR, TIMES, CLEAR, RESET;
        public String id(){ return name().toLowerCase(Locale.ROOT); }
        public static List<String> options(){ return java.util.Arrays.stream(values()).map(Action::id).toList(); }
        public static Action parse(String id){
            for(Action action : values()) if(action.id().equalsIgnoreCase(id)) return action;
            return TITLE;
        }
    }

    public enum TimeUnit{
        TICKS(1), SECONDS(20), DAYS(24000);
        private final int multiplier;
        TimeUnit(int multiplier){ this.multiplier = multiplier; }
        public String id(){ return name().toLowerCase(Locale.ROOT); }
        public static List<String> options(){ return java.util.Arrays.stream(values()).map(TimeUnit::id).toList(); }
        public static TimeUnit parse(String id){
            for(TimeUnit unit : values()) if(unit.id().equalsIgnoreCase(id)) return unit;
            return TICKS;
        }
        public int ticks(double value){
            if(!Double.isFinite(value) || value <= 0) return 0;
            return (int)Math.min(Integer.MAX_VALUE, Math.round(Math.min(Integer.MAX_VALUE, value * multiplier)));
        }
    }

    public record Animation(int fadeIn, int stay, int fadeOut){
        public static final Animation DEFAULT = new Animation(10, 70, 20);
        public Animation{ fadeIn = Math.max(0, fadeIn); stay = Math.max(0, stay); fadeOut = Math.max(0, fadeOut); }
        public static Animation of(double fadeIn, double stay, double fadeOut, TimeUnit unit){
            TimeUnit selected = unit == null ? TimeUnit.TICKS : unit;
            return new Animation(selected.ticks(fadeIn), selected.ticks(stay), selected.ticks(fadeOut));
        }
    }

    public record Text(String message, boolean json, int color, boolean customColor,
                       String bold, String italic, String underlined, String strikethrough, String obfuscated){
        public Component component(MinecraftServer server){
            var text = json ? Component.Serializer.fromJson(message, server.registryAccess()) : Component.literal(message == null ? "" : message);
            if(text == null) throw new IllegalArgumentException("Announcement text must contain a component");
            Style style = text.getStyle();
            if(customColor) style = style.withColor(color & 0xFFFFFF);
            if(flag(bold) != null) style = style.withBold(flag(bold));
            if(flag(italic) != null) style = style.withItalic(flag(italic));
            if(flag(underlined) != null) style = style.withUnderlined(flag(underlined));
            if(flag(strikethrough) != null) style = style.withStrikethrough(flag(strikethrough));
            if(flag(obfuscated) != null) style = style.withObfuscated(flag(obfuscated));
            return text.setStyle(style);
        }
        public static List<String> flagOptions(){ return List.of("inherit", "true", "false"); }
        private static Boolean flag(String value){
            return "true".equalsIgnoreCase(value) ? Boolean.TRUE : "false".equalsIgnoreCase(value) ? Boolean.FALSE : null;
        }
    }

    public static boolean send(MinecraftServer server, Collection<String> players, UUID fallbackPlayer,
                               Action action, Text text, Animation animation){
        if(server == null || action == null) return false;
        List<ServerPlayer> recipients = new ArrayList<>();
        if(players == null || players.isEmpty()){
            ServerPlayer player = fallbackPlayer == null ? null : server.getPlayerList().getPlayer(fallbackPlayer);
            if(player != null) recipients.add(player);
        }else{
            for(ServerPlayer player : server.getPlayerList().getPlayers()){
                if(ChatPlayerFilter.matches(players, player.getUUID(), player.getGameProfile().getName())) recipients.add(player);
            }
        }
        if(recipients.isEmpty()) return false;
        Component message = switch(action){
            case TITLE, SUBTITLE, ACTIONBAR -> text == null ? Component.empty() : text.component(server);
            default -> Component.empty();
        };
        Animation times = animation == null ? Animation.DEFAULT : animation;
        // Resolve dynamic JSON components for every recipient before sending any packet
        List<Packet<?>> packets = new ArrayList<>(recipients.size());
        for(ServerPlayer player : recipients){
            Component resolved = message;
            if(text != null && text.json() && (action == Action.TITLE || action == Action.SUBTITLE || action == Action.ACTIONBAR)){
                try{
                    resolved = ComponentUtils.updateForEntity(server.createCommandSourceStack(), message, player, 0);
                }catch(com.mojang.brigadier.exceptions.CommandSyntaxException err){
                    throw new IllegalArgumentException("Invalid announcement component", err);
                }
            }
            packets.add(switch(action){
                case TITLE -> new ClientboundSetTitleTextPacket(resolved);
                case SUBTITLE -> new ClientboundSetSubtitleTextPacket(resolved);
                case ACTIONBAR -> new ClientboundSetActionBarTextPacket(resolved);
                case TIMES -> new ClientboundSetTitlesAnimationPacket(times.fadeIn(), times.stay(), times.fadeOut());
                case CLEAR -> new ClientboundClearTitlesPacket(false);
                case RESET -> new ClientboundClearTitlesPacket(true);
            });
        }
        for(int idx = 0; idx < recipients.size(); idx++) recipients.get(idx).connection.send(packets.get(idx));
        return true;
    }
}

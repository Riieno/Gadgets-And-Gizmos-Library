package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;

import java.util.Map;

// Execute an operator-authored command once per pulse from the host's world position
public final class CommandGraphNode{
    private static final int MAX_OUTPUT = 32767;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Keep node construction on the static API
    private CommandGraphNode(){}

    // Describe the command node under its consuming mod's namespace
    public static GraphNodeDefinition definition(String namespace){
        return new GraphNodeDefinition(ResourceLocation.fromNamespaceAndPath(namespace, "command").toString(),
                "controller", GraphPortOrder.ports("exec", "exec", "command", "string"),
                GraphPortOrder.ports("exec", "exec", "output", "string", "success", "boolean"), true);
    }

    // Retain the last result during passive reads and leave previews without a server host inert
    public static Map<String, GraphValue> execute(GraphExecutionContext ctx, Map<String, GraphValue> inputs){
        if(!ctx.service(GraphHostServices.COMMAND_EXECUTION).map(CommandExecutionPolicy::isEnabled).orElse(true)){
            ctx.state("output", GraphValue.string(""));
            ctx.state("success", GraphValue.bool(false));
            return Map.of("output", GraphValue.string(""), "success", GraphValue.bool(false));
        }
        if(ctx.executionTriggered()){
            var host = ctx.service(GraphHostServices.BLOCK_ENTITY).orElse(null);
            if(host != null && host.getLevel() instanceof ServerLevel level){
                var capture = new Output();
                var src = new CommandSourceStack(capture, SableTransformApi.toWorldPosition(host, host.getBlockPos().getCenter()),
                        Vec2.ZERO, level, 2, "Graph Command", Component.translatable("gadgetsngizmos.text.graph_command"), level.getServer(), null)
                        .withCallback((success, result) -> capture.success |= success);
                String command = inputs.getOrDefault("command", GraphValue.string("")).asString().strip();
                if(!command.isEmpty()) level.getServer().getCommands().performPrefixedCommand(src, command);
                ctx.state("output", GraphValue.string(capture.text.toString()));
                ctx.state("success", GraphValue.bool(capture.success));
            }
        }
        GraphValue output = ctx.state("output");
        GraphValue success = ctx.state("success");
        return Map.of("output", output == null || !"string".equals(output.type()) ? GraphValue.string("") : output,
                "success", success == null || !"boolean".equals(success.type()) ? GraphValue.bool(false) : success);
    }

    // Capture command feedback without broadcasting it to players or administrators
    private static final class Output implements CommandSource{
        private final StringBuilder text = new StringBuilder();
        private boolean success;

        // Bound accumulated feedback to a graph string payload
        @Override public void sendSystemMessage(Component msg){
            if(text.length() >= MAX_OUTPUT) return;
            if(!text.isEmpty()) text.append('\n');
            String val = msg.getString();
            text.append(val, 0, Math.min(val.length(), MAX_OUTPUT - text.length()));
        }
        // Retain successful command feedback
        @Override public boolean acceptsSuccess(){ return true; }
        // Retain syntax and execution errors
        @Override public boolean acceptsFailure(){ return true; }
        // Keep feedback local to the graph outputs
        @Override public boolean shouldInformAdmins(){ return false; }
    }
}

package com.rieno.gadgetsandgizmos.lib.item;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.profile.PlayerSkinLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

// Resolve a command-supplied player name before using its textured head item
public abstract class ProfileHeadItem extends Item{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final String nameTag;
    private final Set<ItemStack> pending = Collections.newSetFromMap(new WeakHashMap<>());

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the item and its player-name custom-data key
    protected ProfileHeadItem(Properties props, String nameTag){
        super(props);
        this.nameTag = nameTag;
    }

    // Read the requested Minecraft name without accepting display-name JSON
    public final String playerName(ItemStack stack){
        return PlayerSkinLookup.normalizePlayerName(stack.getOrDefault(DataComponents.CUSTOM_DATA,
                CustomData.EMPTY).copyTag().getString(nameTag));
    }

    // Read the resolved skin only when it still belongs to the requested player
    public final @Nullable PlayerSkinLookup.LookupResult skin(ItemStack stack){
        var profile = matchingProfile(stack);
        if(profile == null) return null;
        var res = PlayerSkinLookup.fromProfile(profile.gameProfile());
        return res.found() ? res : null;
    }

    // Apply a resolved profile on the owning game thread
    public final void setProfile(ItemStack stack, PlayerSkinLookup.LookupResult res){
        if(res.found() && playerName(stack).equalsIgnoreCase(res.playerName())){
            stack.set(DataComponents.PROFILE, new ResolvableProfile(res.profile()));
        }
    }

    // Prepare a vanilla head for the normal skull renderer
    public final ItemStack headStack(ItemStack stack){
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        var profile = matchingProfile(stack);
        if(profile != null) head.set(DataComponents.PROFILE, profile);
        return head;
    }

    // Show the requested name on the command-created item
    @Override
    public Component getName(ItemStack stack){
        String name = playerName(stack);
        return name.isBlank() ? super.getName(stack) : Component.literal(name);
    }

    // Resolve carried heads without blocking either game thread
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected){
        if(level.isClientSide || playerName(stack).isBlank() || matchingProfile(stack) != null) return;
        var res = PlayerSkinLookup.lookup(playerName(stack)).getNow(null);
        if(res != null) setProfile(stack, res);
    }

    // Defer an unresolved use and recheck its player, hand and target after lookup
    @Override
    public InteractionResult useOn(UseOnContext ctx){
        ItemStack stack = ctx.getItemInHand();
        String name = playerName(stack);
        if(name.isBlank()){
            if(!ctx.getLevel().isClientSide) onProfileUnavailable(ctx, PlayerSkinLookup.Status.PLAYER_DOES_NOT_EXIST);
            return InteractionResult.FAIL;
        }
        if(ctx.getLevel().isClientSide) return InteractionResult.SUCCESS;
        var res = skin(stack);
        if(res != null) return useProfileOn(ctx, res);
        if(!(ctx.getLevel() instanceof ServerLevel level)
                || !(ctx.getPlayer() instanceof ServerPlayer player)) return InteractionResult.FAIL;
        if(!pending.add(stack)) return InteractionResult.CONSUME;
        var state = level.getBlockState(ctx.getClickedPos());
        PlayerSkinLookup.lookup(name).thenAccept(found -> level.getServer().execute(() -> {
            pending.remove(stack);
            if(player.hasDisconnected() || !player.isAlive() || player.level() != level || stack.isEmpty()
                    || player.getItemInHand(ctx.getHand()) != stack || !name.equals(playerName(stack))
                    || level.getBlockState(ctx.getClickedPos()) != state
                    || !player.canInteractWithBlock(ctx.getClickedPos(), 1)
                    || !level.mayInteract(player, ctx.getClickedPos())
                    || !player.mayUseItemAt(ctx.getClickedPos(), ctx.getClickedFace(), stack)) return;
            if(!found.found()){
                onProfileUnavailable(ctx, found.status());
                return;
            }
            setProfile(stack, found);
            useProfileOn(ctx, found);
        }));
        return InteractionResult.CONSUME;
    }

    // Perform the item's action with a resolved player skin
    protected abstract InteractionResult useProfileOn(UseOnContext ctx, PlayerSkinLookup.LookupResult skin);

    // Report a missing player or an unavailable profile service without consuming the item
    protected void onProfileUnavailable(UseOnContext ctx, PlayerSkinLookup.Status status){}

    // Reject stale profiles after commands change the requested player name
    private @Nullable ResolvableProfile matchingProfile(ItemStack stack){
        var profile = stack.get(DataComponents.PROFILE);
        String name = playerName(stack);
        return !name.isBlank() && profile != null && profile.name().orElse("").equalsIgnoreCase(name)
                && profile.properties().containsKey("textures") ? profile : null;
    }
}

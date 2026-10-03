package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Persist ship owners and player permissions independently of block entity loading
public final class ShipPermissionManager extends SavedData {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Factory<ShipPermissionManager> FACTORY =
            new Factory<>(ShipPermissionManager::new, ShipPermissionManager::load);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final Map<UUID, Claim> claims = new LinkedHashMap<>();
    private final Map<UUID, UUID> roots = new LinkedHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static ShipPermissionManager get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "gadgetsngizmos_ship_permissions");
    }

    // Claim one ship for its placer without replacing an existing owner
    public boolean claim(UUID shipId, UUID ownerId){
        if(shipId == null || ownerId == null) return false;
        Claim existing = claims.get(shipId);
        if(existing != null) return existing.ownerId.equals(ownerId);
        claims.put(shipId, new Claim(ownerId));
        roots.put(shipId, shipId);
        setDirty();
        return true;
    }

    // Remove the owner, members and linked bodies after a control block is destroyed
    public void unclaim(UUID shipId){
        UUID root = root(shipId);
        if(root == null || claims.remove(root) == null) return;
        roots.entrySet().removeIf(entry -> root.equals(entry.getValue()));
        setDirty();
    }

    // Associate a physically connected sublevel with its controlled root
    public void bind(UUID shipId, UUID subLevelId){
        if(shipId == null || subLevelId == null || !claims.containsKey(shipId)) return;
        if(claims.containsKey(subLevelId) && !shipId.equals(subLevelId)) return;
        UUID existing = roots.get(subLevelId);
        if(existing != null && !existing.equals(shipId) && claims.containsKey(existing)) return;
        if(!shipId.equals(roots.put(subLevelId, shipId))) setDirty();
    }

    // Release bodies that no longer belong to this ship before binding its current bodies
    public void syncBindings(UUID shipId, Set<UUID> subLevelIds){
        if(shipId == null || subLevelIds == null || !claims.containsKey(shipId)) return;
        boolean removed = roots.entrySet().removeIf(entry -> shipId.equals(entry.getValue())
                && !shipId.equals(entry.getKey()) && !subLevelIds.contains(entry.getKey()));
        if(removed) setDirty();
        for(UUID subLevelId : subLevelIds) bind(shipId, subLevelId);
    }

    public @Nullable UUID root(UUID subLevelId){
        return subLevelId == null ? null : roots.getOrDefault(subLevelId, subLevelId);
    }

    public @Nullable UUID owner(UUID subLevelId){
        Claim claim = claims.get(root(subLevelId));
        return claim == null ? null : claim.ownerId;
    }

    // World positions and unclaimed sublevels have no ship permission claim
    public boolean isClaimed(@Nullable UUID subLevelId){
        return subLevelId != null && claims.containsKey(root(subLevelId));
    }

    public boolean isOwner(UUID subLevelId, UUID playerId){
        UUID ownerId = owner(subLevelId);
        return ownerId != null && ownerId.equals(playerId);
    }

    // An unclaimed ship retains ordinary game and claim-mod behavior
    public boolean allows(@Nullable UUID subLevelId, UUID playerId, ShipPermission permission){
        if(subLevelId == null) return true;
        Claim claim = claims.get(root(subLevelId));
        if(claim == null) return true;
        if(playerId == null || permission == null) return false;
        if(claim.ownerId.equals(playerId)) return true;
        Member member = claim.members.get(playerId);
        return member != null && member.permissions.contains(permission);
    }

    // Only the immutable owner may update another player's rights
    public boolean set(UUID subLevelId, UUID actorId, UUID playerId, String name,
                       ShipPermission permission, boolean enabled){
        Claim claim = claims.get(root(subLevelId));
        if(claim == null || actorId == null || !actorId.equals(claim.ownerId)
                || playerId == null || playerId.equals(claim.ownerId) || permission == null) return false;
        Member member = claim.members.computeIfAbsent(playerId, ignored -> new Member());
        member.name = name == null ? "" : name.strip();
        if(enabled) member.permissions.add(permission);
        else member.permissions.remove(permission);
        if(member.permissions.isEmpty()) claim.members.remove(playerId);
        setDirty();
        return true;
    }

    public List<MemberView> members(UUID subLevelId){
        Claim claim = claims.get(root(subLevelId));
        if(claim == null) return List.of();
        return claim.members.entrySet().stream().map(entry -> new MemberView(entry.getKey(),
                entry.getValue().name, Set.copyOf(entry.getValue().permissions))).toList();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        SERIALIZATION
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider){
        ListTag rows = new ListTag();
        claims.forEach((shipId, claim) -> {
            CompoundTag row = new CompoundTag();
            row.putUUID("Ship", shipId);
            row.putUUID("Owner", claim.ownerId);
            ListTag members = new ListTag();
            claim.members.forEach((playerId, member) -> {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("Player", playerId);
                entry.putString("Name", member.name);
                ListTag permissions = new ListTag();
                member.permissions.forEach(permission -> {
                    CompoundTag value = new CompoundTag();
                    value.putString("Id", permission.id());
                    permissions.add(value);
                });
                entry.put("Permissions", permissions);
                members.add(entry);
            });
            row.put("Members", members);
            ListTag bodies = new ListTag();
            roots.forEach((bodyId, rootId) -> {
                if(!shipId.equals(rootId) || shipId.equals(bodyId)) return;
                CompoundTag body = new CompoundTag();
                body.putUUID("Id", bodyId);
                bodies.add(body);
            });
            row.put("Bodies", bodies);
            rows.add(row);
        });
        tag.put("Claims", rows);
        return tag;
    }

    private static ShipPermissionManager load(CompoundTag tag, HolderLookup.Provider provider){
        ShipPermissionManager manager = new ShipPermissionManager();
        ListTag rows = tag.getList("Claims", Tag.TAG_COMPOUND);
        for(int idx = 0; idx < rows.size() && idx < 16384; idx++){
            CompoundTag row = rows.getCompound(idx);
            if(!row.hasUUID("Ship") || !row.hasUUID("Owner")) continue;
            UUID shipId = row.getUUID("Ship");
            Claim claim = new Claim(row.getUUID("Owner"));
            ListTag members = row.getList("Members", Tag.TAG_COMPOUND);
            for(int memberIdx = 0; memberIdx < members.size() && memberIdx < 4096; memberIdx++){
                CompoundTag entry = members.getCompound(memberIdx);
                if(!entry.hasUUID("Player")) continue;
                UUID playerId = entry.getUUID("Player");
                if(playerId.equals(claim.ownerId)) continue;
                Member member = new Member();
                member.name = entry.getString("Name");
                ListTag permissions = entry.getList("Permissions", Tag.TAG_COMPOUND);
                for(int permissionIdx = 0; permissionIdx < permissions.size(); permissionIdx++){
                    ShipPermission permission = ShipPermission.fromId(permissions.getCompound(permissionIdx).getString("Id"));
                    if(permission != null) member.permissions.add(permission);
                }
                if(!member.permissions.isEmpty()) claim.members.put(playerId, member);
            }
            manager.claims.put(shipId, claim);
            manager.roots.put(shipId, shipId);
            ListTag bodies = row.getList("Bodies", Tag.TAG_COMPOUND);
            for(int bodyIdx = 0; bodyIdx < bodies.size() && bodyIdx < 4096; bodyIdx++){
                CompoundTag body = bodies.getCompound(bodyIdx);
                if(body.hasUUID("Id")) manager.roots.put(body.getUUID("Id"), shipId);
            }
        }
        return manager;
    }

    public record MemberView(UUID playerId, String name, Set<ShipPermission> permissions){}

    private static final class Claim {
        private final UUID ownerId;
        private final Map<UUID, Member> members = new LinkedHashMap<>();
        private Claim(UUID ownerId){ this.ownerId = ownerId; }
    }

    private static final class Member {
        private String name = "";
        private final EnumSet<ShipPermission> permissions = EnumSet.noneOf(ShipPermission.class);
    }
}

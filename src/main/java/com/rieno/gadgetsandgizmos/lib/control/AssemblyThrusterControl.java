package com.rieno.gadgetsandgizmos.lib.control;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

// Share attached-thruster control and persistent aliases across assembly owners
public final class AssemblyThrusterControl{
    private final String type;
    private final Supplier<List<ThrusterControl>> thrusters;
    private final Supplier<List<RcsThrusterControl>> rcsThrusters;
    private final Map<String, String> aliases;
    private final Runnable changed;

    public AssemblyThrusterControl(String type, Supplier<List<ThrusterControl>> thrusters,
            Supplier<List<RcsThrusterControl>> rcsThrusters, Map<String, String> aliases, Runnable changed){
        this.type = type;
        this.thrusters = thrusters;
        this.rcsThrusters = rcsThrusters;
        this.aliases = aliases;
        this.changed = changed;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Preserve the existing position-derived peripheral IDs
    public static String targetId(String kind, BlockPos pos){
        return UUID.nameUUIDFromBytes((kind + ":" + pos.asLong()).getBytes(StandardCharsets.UTF_8)).toString();
    }

    // Retain aliases while a mounted body is temporarily unloaded
    public boolean refreshBindings(){
        boolean dirty = false;
        for(ThrusterControl target : thrusters.get()){
            String id = targetId("thruster", target.getBlockPos());
            String alias = alias(id, "thruster_");
            dirty |= !id.equals(target.getCcId()) || !alias.equals(target.getAssemblyComputerCraftAlias());
            target.setCcId(id);
            target.setAssemblyComputerCraftAlias(alias);
        }
        for(RcsThrusterControl target : rcsThrusters.get()){
            String id = targetId("rcs_thruster", target.getBlockPos());
            String alias = alias(id, "rcs_thruster_");
            dirty |= !id.equals(target.getCcId()) || !alias.equals(target.getAssemblyComputerCraftAlias());
            target.setCcId(id);
            target.setAssemblyComputerCraftAlias(alias);
        }
        return dirty;
    }

    private String alias(String id, String prefix){
        String alias = aliases.get(id);
        if(alias != null && !alias.isBlank()) return alias;
        int idx = 0;
        while(aliases.containsValue(prefix + idx)) idx++;
        alias = prefix + idx;
        aliases.put(id, alias);
        changed.run();
        return alias;
    }

    private Map<String, ThrusterControl> getThrusters(){
        refreshBindings();
        Map<String, ThrusterControl> res = new LinkedHashMap<>();
        for(ThrusterControl target : thrusters.get()) res.put(targetId("thruster", target.getBlockPos()), target);
        return res;
    }

    private Map<String, RcsThrusterControl> getRcsThrusters(){
        refreshBindings();
        Map<String, RcsThrusterControl> res = new LinkedHashMap<>();
        for(RcsThrusterControl target : rcsThrusters.get()) res.put(targetId("rcs_thruster", target.getBlockPos()), target);
        return res;
    }

    private String getType(){ return type; }
    private String getThrusterId(ThrusterControl target){ return targetId("thruster", target.getBlockPos()); }
    private String getThrusterAlias(ThrusterControl target){ return aliases.getOrDefault(getThrusterId(target), ""); }
    private String getRcsThrusterAlias(RcsThrusterControl target){
        return aliases.getOrDefault(targetId("rcs_thruster", target.getBlockPos()), "");
    }

    // Use the same NBT key as the original bearing
    public void write(CompoundTag tag){
        CompoundTag saved = new CompoundTag();
        aliases.forEach(saved::putString);
        tag.put("ThrusterAliases", saved);
    }

    public void read(CompoundTag tag){
        aliases.clear();
        CompoundTag saved = tag.getCompound("ThrusterAliases");
        for(String id : saved.getAllKeys()) aliases.put(id, saved.getString(id));
    }

    private void setThrusterAlias(String id, String val){
        if(val.isEmpty()) aliases.remove(id);
        else aliases.put(id, val);
        refreshBindings();
        changed.run();
    }

    private double getAssemblyRealThrust(){
        return getThrusters().values().stream().mapToDouble(ThrusterControl::getRealThrust).sum();
    }

    private double getAssemblyLiftCapacity(){
        return getThrusters().values().stream().mapToDouble(ThrusterControl::getLiftCapacity).sum();
    }

    // Resolve one RCS thruster by id or alias
    private RcsThrusterControl resolveRcsThruster(String idOrAlias){
        Map<String, RcsThrusterControl> thrusters = getRcsThrusters();
        RcsThrusterControl direct = thrusters.get(idOrAlias);
        if(direct != null) return direct;
        String normalized = idOrAlias == null ? "" : idOrAlias.trim();
        for(RcsThrusterControl thruster : thrusters.values()){
            if(normalized.equals(getRcsThrusterAlias(thruster))) return thruster;
        }
        throw new IllegalArgumentException("unknown RCS thruster id or alias '" + idOrAlias + "'");
    }

    // Parse an RCS nozzle name
    private static Direction parseRcsNozzle(String nozzle){
        String normalized = nozzle == null ? "" : nozzle.trim().toLowerCase(java.util.Locale.ROOT);
        Direction direction = Direction.byName(normalized);
        if(direction == null || direction.getAxis().isVertical()){
            throw new IllegalArgumentException("nozzle must be 'north', 'east', 'south' or 'west'");
        }
        return direction;
    }

    // Build one RCS thruster status table
    private Map<String, Object> buildRcsThrusterStatus(String id, RcsThrusterControl thruster){
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("id", id);
        status.put("alias", getRcsThrusterAlias(thruster));
        status.put("rpm", thruster.getSpeed());
        status.put("maxThrust", thruster.getMaxNozzleThrust());
        for(Direction nozzle : Direction.Plane.HORIZONTAL){
            status.put(nozzle.getSerializedName(), Map.of(
                    "throttle", thruster.getThrottle(nozzle),
                    "thrust", thruster.getNozzleThrust(nozzle),
                    "active", thruster.isNozzleActive(nozzle)));
        }
        return status;
    }

    // Find the thruster id by alias
    private String findThrusterIdByAlias(String alias){
        String normalized = alias == null ? "" : alias.trim();
        if(normalized.isEmpty()){
            return null;
        }
        for(Map.Entry<String, ThrusterControl> entry : getThrusters().entrySet()){
            if(normalized.equals(getThrusterAlias(entry.getValue()))){
                return entry.getKey();
            }
        }
        return null;
    }

    // Resolve the thrusters
    private List<ThrusterControl> resolveThrusters(String thrusterId){
        Map<String, ThrusterControl> thrusters = getThrusters();
        if(thrusters.isEmpty()){
            throw new IllegalArgumentException("no attached thrusters found");
        }
        if(thrusterId == null || thrusterId.isBlank() || "all".equalsIgnoreCase(thrusterId.trim())){
            return List.copyOf(thrusters.values());
        }
        ThrusterControl thruster = thrusters.get(thrusterId);
        if(thruster == null){
            String resolvedId = findThrusterIdByAlias(thrusterId);
            if(resolvedId != null){
                thruster = thrusters.get(resolvedId);
            }
        }
        if(thruster == null){
            throw new IllegalArgumentException("unknown thruster id or alias '" + thrusterId + "'");
        }
        return List.of(thruster);
    }

    // Get the thruster value map
    private Map<String, Object> thrusterValueMap(Function<ThrusterControl, Object> mapper){
        Map<String, Object> values = new LinkedHashMap<>();
        for(Map.Entry<String, ThrusterControl> entry : getThrusters().entrySet()){
            values.put(entry.getKey(), mapper.apply(entry.getValue()));
        }
        return values;
    }

    // Apply the thrusters
    private void applyToThrusters(String thrusterId, Consumer<ThrusterControl> action){
        for(ThrusterControl thruster : resolveThrusters(thrusterId)){
            action.accept(thruster);
        }
    }

    // Build the thruster status
    private Map<String, Object> buildThrusterStatus(String thrusterId, ThrusterControl thruster){
        Map<String, Object> status = new LinkedHashMap<>();
        if(thrusterId != null){
            status.put("id", thrusterId);
        }
        status.put("alias", getThrusterAlias(thruster));
        status.put("enabled", thruster.isEnabled());
        status.put("throttle", thruster.getThrottle());
        status.put("computerThrottle", thruster.getComputerThrottle());
        status.put("controlMode", thruster.getControlMode());
        status.put("fuel", thruster.getFuelAmount());
        status.put("fuelCapacity", thruster.getFuelCapacity());
        status.put("fuelType", thruster.getFuelTypeId());
        status.put("burnTimeSeconds", thruster.getEstimatedBurnSeconds());
        status.put("thrust", thruster.getThrust());
        status.put("realThrust", thruster.getRealThrust());
        status.put("liftCapacity", thruster.getLiftCapacity());
        status.put("airflow", thruster.getAirflow());
        status.put("active", thruster.isActive());
        status.put("soulMode", thruster.isSoulThruster());
        status.put("redstoneSignal", thruster.getSignalStrength());
        return status;
    }

    // Normalize the throttle
    private static float normalizeThrottle(double throttle){
        if(Double.isNaN(throttle) || Double.isInfinite(throttle) || throttle < 0.0D){
            throw new IllegalArgumentException("throttle must be between 0..1 or 0..100");
        }
        if(throttle <= 1.0D){
            return (float) throttle;
        }
        if(throttle <= 100.0D){
            return (float) (throttle / 100.0D);
        }
        throw new IllegalArgumentException("throttle must be between 0..1 or 0..100");
    }

    // Get the list thrusters
    public Map<String, Object> listThrusters(){
        Map<String, Object> thrusters = new LinkedHashMap<>();
        for(Map.Entry<String, ThrusterControl> entry : getThrusters().entrySet()){
            ThrusterControl thruster = entry.getValue();
            Map<String, Object> pos = thruster.position();
            thrusters.put(entry.getKey(), Map.of(
                    "alias", getThrusterAlias(thruster),
                    "pos", List.of(pos.get("x"), pos.get("y"), pos.get("z")),
                    "position", pos,
                    "localPos", List.of(thruster.getBlockPos().getX(), thruster.getBlockPos().getY(), thruster.getBlockPos().getZ()),
                    "fuelType", thruster.getFuelTypeId(),
                    "controlMode", thruster.getControlMode(),
                    "enabled", thruster.isEnabled(),
                    "throttle", thruster.getThrottle()));
        }
        return thrusters;
    }

    // Get the list of RCS thrusters
    public Map<String, Object> listRcsThrusters(){
        Map<String, Object> thrusters = new LinkedHashMap<>();
        for(Map.Entry<String, RcsThrusterControl> entry : getRcsThrusters().entrySet()){
            RcsThrusterControl thruster = entry.getValue();
            Map<String, Object> pos = thruster.position();
            Map<String, Object> data = buildRcsThrusterStatus(entry.getKey(), thruster);
            data.put("pos", List.of(pos.get("x"), pos.get("y"), pos.get("z")));
            data.put("position", pos);
            data.put("localPos", List.of(thruster.getBlockPos().getX(),
                    thruster.getBlockPos().getY(), thruster.getBlockPos().getZ()));
            thrusters.put(entry.getKey(), data);
        }
        return thrusters;
    }

    // Get the RCS thruster count
    public int getRcsThrusterCount(){
        return getRcsThrusters().size();
    }

    // Set one RCS nozzle throttle
    public void setRcsThrottle(String id, String nozzle, double throttle){
        Direction direction = parseRcsNozzle(nozzle);
        float normalized = normalizeThrottle(throttle);
        if(id == null || id.isBlank() || "all".equalsIgnoreCase(id.trim())){
            for(RcsThrusterControl thruster : getRcsThrusters().values()){
                thruster.setComputerThrottle(direction, normalized);
            }
            return;
        }
        resolveRcsThruster(id).setComputerThrottle(direction, normalized);
    }

    // Get one RCS nozzle throttle
    public double getRcsThrottle(String id, String nozzle){
        return resolveRcsThruster(id).getThrottle(parseRcsNozzle(nozzle));
    }

    // Clear one RCS nozzle throttle
    public void clearRcsThrottle(String id, String nozzle){
        Direction direction = parseRcsNozzle(nozzle);
        if(id == null || id.isBlank() || "all".equalsIgnoreCase(id.trim())){
            for(RcsThrusterControl thruster : getRcsThrusters().values()){
                thruster.clearComputerThrottle(direction);
            }
            return;
        }
        resolveRcsThruster(id).clearComputerThrottle(direction);
    }

    // Get one RCS thruster status
    public Map<String, Object> getRcsThrusterStatus(String id){
        RcsThrusterControl thruster = resolveRcsThruster(id);
        return buildRcsThrusterStatus(id, thruster);
    }

    // Get the thruster count
    public int getThrusterCount(){
        return getThrusters().size();
    }

    // Get the owned thrusters
    public List<String> getOwnedThrusters(){
        return List.copyOf(getThrusters().keySet());
    }

    // Get the ids
    public List<String> ids(){
        return getThrusters().values().stream()
                .map(this::getThrusterAlias)
                .filter(alias -> alias != null && !alias.isBlank())
                .toList();
    }

    // Get the network info
    public Map<String, Object> getNetworkInfo(){
        Map<String, ThrusterControl> thrusters = getThrusters();
        Map<String, Object> aliases = new LinkedHashMap<>();
        for(Map.Entry<String, ThrusterControl> entry : thrusters.entrySet()){
            aliases.put(entry.getKey(), getThrusterAlias(entry.getValue()));
        }

        return Map.of(
                "bearingType", getType(),
                "thrusterCount", thrusters.size(),
                "rcsThrusterCount", getRcsThrusters().size(),
                "ownedThrusters", List.copyOf(thrusters.keySet()),
                "aliases", aliases,
                "thrusters", listThrusters(),
                "rcsThrusters", listRcsThrusters()
        );
    }

    // Handle the thruster alias
    public void thrusterAlias(String thrusterId, String alias){
        Map<String, ThrusterControl> thrusters = getThrusters();
        ThrusterControl thruster = thrusters.get(thrusterId);
        String resolvedId = thrusterId;
        if(thruster == null){
            resolvedId = findThrusterIdByAlias(thrusterId);
            thruster = resolvedId == null ? null : thrusters.get(resolvedId);
        }
        if(thruster == null){
            throw new IllegalArgumentException("unknown thruster id or alias '" + thrusterId + "'");
        }
        String normalizedAlias = alias == null ? "" : alias.trim();
        if(!normalizedAlias.isEmpty()){
            String existingId = findThrusterIdByAlias(normalizedAlias);
            if(existingId != null && !existingId.equals(resolvedId)){
                throw new IllegalArgumentException("alias already assigned to another thruster");
            }
        }
        setThrusterAlias(resolvedId, normalizedAlias);
    }

    // Set the throttle
    public void setThrottle(String thrusterId, double throttle){
        float normalizedThrottle = normalizeThrottle(throttle);
        applyToThrusters(thrusterId, thruster -> thruster.setThrottle(normalizedThrottle));
    }

    // Get the throttle
    public Object getThrottle(String thrusterId){
        if("all".equalsIgnoreCase(thrusterId)){
            return thrusterValueMap(ThrusterControl::getThrottle);
        }
        return resolveThrusters(thrusterId).get(0).getThrottle();
    }

    // Get the throttle map
    public Map<String, Object> getThrottleMap(String thrusterId){
        if(thrusterId == null || thrusterId.isBlank() || "all".equalsIgnoreCase(thrusterId.trim())){
            return thrusterValueMap(ThrusterControl::getThrottle);
        }

        Map<String, ThrusterControl> thrusters = getThrusters();
        String resolvedId = thrusterId;
        ThrusterControl thruster = thrusters.get(resolvedId);
        if(thruster == null){
            resolvedId = findThrusterIdByAlias(thrusterId);
            thruster = resolvedId == null ? null : thrusters.get(resolvedId);
        }
        if(thruster == null){
            throw new IllegalArgumentException("unknown thruster id or alias '" + thrusterId + "'");
        }

        Map<String, Object> throttleMap = new LinkedHashMap<>();
        throttleMap.put(resolvedId, thruster.getThrottle());
        return throttleMap;
    }

    // Set the enabled
    public void setEnabled(String thrusterId, boolean enabled){
        applyToThrusters(thrusterId, thruster -> thruster.setEnabled(enabled));
    }

    // Check if the bearing is enabled
    public Object isEnabled(String thrusterId){
        if("all".equalsIgnoreCase(thrusterId)){
            return thrusterValueMap(ThrusterControl::isEnabled);
        }
        return resolveThrusters(thrusterId).get(0).isEnabled();
    }

    // Get the fuel
    public Object getFuel(String thrusterId){
        if("all".equalsIgnoreCase(thrusterId)){
            return thrusterValueMap(ThrusterControl::getFuelAmount);
        }
        return resolveThrusters(thrusterId).get(0).getFuelAmount();
    }

    // Get the fuel capacity
    public Object getFuelCapacity(String thrusterId){
        if("all".equalsIgnoreCase(thrusterId)){
            return thrusterValueMap(ThrusterControl::getFuelCapacity);
        }
        return resolveThrusters(thrusterId).get(0).getFuelCapacity();
    }

    // Get the fuel type
    public Object getFuelType(String thrusterId){
        if("all".equalsIgnoreCase(thrusterId)){
            return thrusterValueMap(ThrusterControl::getFuelTypeId);
        }
        return resolveThrusters(thrusterId).get(0).getFuelTypeId();
    }

    // Get the burn time seconds
    public Object getBurnTimeSeconds(String thrusterId){
        if("all".equalsIgnoreCase(thrusterId)){
            return thrusterValueMap(ThrusterControl::getEstimatedBurnSeconds);
        }
        return resolveThrusters(thrusterId).get(0).getEstimatedBurnSeconds();
    }

    // Get the control mode
    public Object getControlMode(String thrusterId){
        if("all".equalsIgnoreCase(thrusterId)){
            return thrusterValueMap(thruster -> thruster.getControlMode());
        }
        return resolveThrusters(thrusterId).get(0).getControlMode();
    }

    // Set the control mode
    public void setControlMode(String thrusterId, String mode){
        if(mode == null){
            throw new IllegalArgumentException("mode must be 'auto', 'redstone' or 'computer'");
        }
        String normalized = mode.trim().toLowerCase(java.util.Locale.ROOT);
        String controlMode = switch(normalized){
            case "auto" -> "auto";
            case "redstone" -> "redstone";
            case "computer" -> "computer";
            default -> throw new IllegalArgumentException("mode must be 'auto', 'redstone' or 'computer'");
        };
        applyToThrusters(thrusterId, thruster -> thruster.setControlMode(controlMode));
    }

    // Get the thrust
    public Object getThrust(String thrusterId){
        if("all".equalsIgnoreCase(thrusterId)){
            return thrusterValueMap(ThrusterControl::getThrust);
        }
        return resolveThrusters(thrusterId).get(0).getThrust();
    }

    // Get the real thrust
    public Object getRealThrust(String thrusterId){
        if("all".equalsIgnoreCase(thrusterId)){
            return thrusterValueMap(ThrusterControl::getRealThrust);
        }
        return resolveThrusters(thrusterId).get(0).getRealThrust();
    }

    // Get the lift capacity
    public Object getLiftCapacity(String thrusterId){
        if("all".equalsIgnoreCase(thrusterId)){
            return thrusterValueMap(ThrusterControl::getLiftCapacity);
        }
        return resolveThrusters(thrusterId).get(0).getLiftCapacity();
    }

    // Get the total real thrust
    public double getTotalRealThrust(){
        return getAssemblyRealThrust();
    }

    // Get the total lift capacity
    public double getTotalLiftCapacity(){
        return getAssemblyLiftCapacity();
    }

    // Get the airflow
    public Object getAirflow(String thrusterId){
        if("all".equalsIgnoreCase(thrusterId)){
            return thrusterValueMap(ThrusterControl::getAirflow);
        }
        return resolveThrusters(thrusterId).get(0).getAirflow();
    }

    // Check if the bearing is active
    public Object isActive(String thrusterId){
        if("all".equalsIgnoreCase(thrusterId)){
            return thrusterValueMap(ThrusterControl::isActive);
        }
        return resolveThrusters(thrusterId).get(0).isActive();
    }

    // Check if soul mode is enabled
    public Object isSoulMode(String thrusterId){
        if("all".equalsIgnoreCase(thrusterId)){
            return thrusterValueMap(ThrusterControl::isSoulThruster);
        }
        return resolveThrusters(thrusterId).get(0).isSoulThruster();
    }

    // Set the soul mode
    public void setSoulMode(String thrusterId, boolean enabled){
        applyToThrusters(thrusterId, thruster -> {
            if(enabled){
                thruster.enableSoulThruster();
            }else{
                thruster.disableSoulThruster();
            }
        });
    }

    // Clear the throttle override
    public void clearThrottleOverride(String thrusterId){
        applyToThrusters(thrusterId, ThrusterControl::clearCcThrottleOverride);
    }

    // Get the redstone signal
    public Object getRedstoneSignal(String thrusterId){
        if("all".equalsIgnoreCase(thrusterId)){
            return thrusterValueMap(ThrusterControl::getSignalStrength);
        }
        return resolveThrusters(thrusterId).get(0).getSignalStrength();
    }

    // Get the thruster status
    public Object getThrusterStatus(String thrusterId){
        if("all".equalsIgnoreCase(thrusterId)){
            return thrusterValueMap(thruster -> buildThrusterStatus(null, thruster));
        }
        ThrusterControl thruster = resolveThrusters(thrusterId).get(0);
        return buildThrusterStatus(thrusterId, thruster);
    }

}

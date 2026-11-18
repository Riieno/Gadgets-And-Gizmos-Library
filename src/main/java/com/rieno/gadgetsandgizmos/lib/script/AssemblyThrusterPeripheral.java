package com.rieno.gadgetsandgizmos.lib.script;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.script.ScriptBlockPeripheral;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.lib.script.ScriptFunctionBinding;
import com.rieno.gadgetsandgizmos.lib.script.ScriptMethodDoc;
import com.rieno.gadgetsandgizmos.lib.script.ScriptPeripheralNetwork;
import com.rieno.gadgetsandgizmos.lib.physics.BlockEntityPosition;
import com.rieno.gadgetsandgizmos.lib.control.AssemblyThrusterControl;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.Map;

// Bind the library's assembly controls to the addon's documented Lua surface
public abstract class AssemblyThrusterPeripheral<T extends BlockEntity> extends ScriptBlockPeripheral<T>{
    protected AssemblyThrusterPeripheral(T be, String type){ super(be, type); }

    protected abstract AssemblyThrusterControl control();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the list thrusters
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "listThrusters", signature = "listThrusters(): table",
            description = "Returns the list thrusters.")
    public final Map<String, Object> listThrusters(){
        return control().listThrusters();
    }

    // Get the list of RCS thrusters
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "listRcsThrusters", signature = "listRcsThrusters(): table",
            description = "Returns the RCS thrusters attached to the bearing head.")
    public final Map<String, Object> listRcsThrusters(){
        return control().listRcsThrusters();
    }

    // Get the RCS thruster count
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getRcsThrusterCount", signature = "getRcsThrusterCount(): number",
            description = "Returns the attached RCS thruster count.")
    public final int getRcsThrusterCount(){
        return control().getRcsThrusterCount();
    }

    // Set one RCS nozzle throttle
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setRcsThrottle", signature = "setRcsThrottle(id|'all', nozzle, throttle: number 0..1 or 0..100)",
            description = "Sets one nozzle throttle on an attached RCS thruster.")
    public final void setRcsThrottle(String id, String nozzle, double throttle) throws ScriptException{
        try{ control().setRcsThrottle(id, nozzle, throttle); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Get one RCS nozzle throttle
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getRcsThrottle", signature = "getRcsThrottle(id, nozzle): number",
            description = "Returns one nozzle throttle from an attached RCS thruster.")
    public final double getRcsThrottle(String id, String nozzle) throws ScriptException{
        try{ return control().getRcsThrottle(id, nozzle); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Clear one RCS nozzle throttle
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "clearRcsThrottle", signature = "clearRcsThrottle(id|'all', nozzle)",
            description = "Clears one nozzle throttle on an attached RCS thruster.")
    public final void clearRcsThrottle(String id, String nozzle) throws ScriptException{
        try{ control().clearRcsThrottle(id, nozzle); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Get one RCS thruster status
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getRcsThrusterStatus", signature = "getRcsThrusterStatus(id): table",
            description = "Returns the status of one attached RCS thruster.")
    public final Map<String, Object> getRcsThrusterStatus(String id) throws ScriptException{
        try{ return control().getRcsThrusterStatus(id); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Get the thruster count
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getThrusterCount", signature = "getThrusterCount(): number",
            description = "Returns the thruster count.")
    public final int getThrusterCount(){
        return control().getThrusterCount();
    }

    // Get the owned thrusters
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getOwnedThrusters", signature = "getOwnedThrusters(): string[]",
            description = "Returns the owned thrusters.")
    public final List<String> getOwnedThrusters(){
        return control().getOwnedThrusters();
    }

    // Get the ids
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "ids", signature = "ids(): string[]",
            description = "Returns the ids.")
    public final List<String> ids(){
        return control().ids();
    }

    // Get the network info
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getNetworkInfo", signature = "getNetworkInfo(): table",
            description = "Returns the network info.")
    public final Map<String, Object> getNetworkInfo(){
        return control().getNetworkInfo();
    }

    // Handle the thruster alias
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "thrusterAlias", signature = "thrusterAlias(id|alias, alias: string)",
            description = "Handle the thruster alias.")
    public final void thrusterAlias(String thrusterId, String alias) throws ScriptException{
        try{ control().thrusterAlias(thrusterId, alias); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Set the throttle
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setThrottle", signature = "setThrottle(id|'all', throttle: number 0..1 or 0..100)",
            description = "Sets the throttle.")
    public final void setThrottle(String thrusterId, double throttle) throws ScriptException{
        try{ control().setThrottle(thrusterId, throttle); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Get the throttle
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getThrottle", signature = "getThrottle(id|'all'): number|table",
            description = "Returns the throttle.")
    public final Object getThrottle(String thrusterId) throws ScriptException{
        try{ return control().getThrottle(thrusterId); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Get the throttle map
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getThrottleMap", signature = "getThrottleMap(id|'all'): table",
            description = "Returns the throttle map.")
    public final Map<String, Object> getThrottleMap(String thrusterId) throws ScriptException{
        try{ return control().getThrottleMap(thrusterId); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Set the enabled
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setEnabled", signature = "setEnabled(id|'all', enabled: boolean)",
            description = "Sets the enabled.")
    public final void setEnabled(String thrusterId, boolean enabled) throws ScriptException{
        try{ control().setEnabled(thrusterId, enabled); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Check if the bearing is enabled
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "isEnabled", signature = "isEnabled(id|'all'): boolean|table",
            description = "Returns whether the bearing is enabled.")
    public final Object isEnabled(String thrusterId) throws ScriptException{
        try{ return control().isEnabled(thrusterId); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Get the fuel
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getFuel", signature = "getFuel(id|'all'): number|table",
            description = "Returns the fuel.")
    public final Object getFuel(String thrusterId) throws ScriptException{
        try{ return control().getFuel(thrusterId); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Get the fuel capacity
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getFuelCapacity", signature = "getFuelCapacity(id|'all'): number|table",
            description = "Returns the fuel capacity.")
    public final Object getFuelCapacity(String thrusterId) throws ScriptException{
        try{ return control().getFuelCapacity(thrusterId); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Get the fuel type
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getFuelType", signature = "getFuelType(id|'all'): string|table",
            description = "Returns the fuel type.")
    public final Object getFuelType(String thrusterId) throws ScriptException{
        try{ return control().getFuelType(thrusterId); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Get the burn time seconds
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getBurnTimeSeconds", signature = "getBurnTimeSeconds(id|'all'): number|table",
            description = "Returns the burn time seconds.")
    public final Object getBurnTimeSeconds(String thrusterId) throws ScriptException{
        try{ return control().getBurnTimeSeconds(thrusterId); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Get the thrust
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getThrust", signature = "getThrust(id|'all'): number|table",
            description = "Returns the thrust.")
    public final Object getThrust(String thrusterId) throws ScriptException{
        try{ return control().getThrust(thrusterId); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Get the real thrust
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getRealThrust", signature = "getRealThrust(id|'all'): number|table",
            description = "Returns the real thrust.")
    public final Object getRealThrust(String thrusterId) throws ScriptException{
        try{ return control().getRealThrust(thrusterId); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Get the lift capacity
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getLiftCapacity", signature = "getLiftCapacity(id|'all'): number|table",
            description = "Returns the lift capacity.")
    public final Object getLiftCapacity(String thrusterId) throws ScriptException{
        try{ return control().getLiftCapacity(thrusterId); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Get the total real thrust
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getTotalRealThrust", signature = "getTotalRealThrust(): number",
            description = "Returns the total real thrust.")
    public final double getTotalRealThrust(){
        return control().getTotalRealThrust();
    }

    // Get the total lift capacity
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getTotalLiftCapacity", signature = "getTotalLiftCapacity(): number",
            description = "Returns the total lift capacity.")
    public final double getTotalLiftCapacity(){
        return control().getTotalLiftCapacity();
    }

    // Get the airflow
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getAirflow", signature = "getAirflow(id|'all'): number|table",
            description = "Returns the airflow.")
    public final Object getAirflow(String thrusterId) throws ScriptException{
        try{ return control().getAirflow(thrusterId); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Check if the bearing is active
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "isActive", signature = "isActive(id|'all'): boolean|table",
            description = "Returns whether the bearing is active.")
    public final Object isActive(String thrusterId) throws ScriptException{
        try{ return control().isActive(thrusterId); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Check if soul mode is enabled
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "isSoulMode", signature = "isSoulMode(id|'all'): boolean|table",
            description = "Returns whether soul mode is enabled.")
    public final Object isSoulMode(String thrusterId) throws ScriptException{
        try{ return control().isSoulMode(thrusterId); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Set the soul mode
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setSoulMode", signature = "setSoulMode(id|'all', enabled: boolean)",
            description = "Sets the soul mode.")
    public final void setSoulMode(String thrusterId, boolean enabled) throws ScriptException{
        try{ control().setSoulMode(thrusterId, enabled); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Clear the throttle override
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "clearThrottleOverride", signature = "clearThrottleOverride(id|'all')",
            description = "Clears the throttle override.")
    public final void clearThrottleOverride(String thrusterId) throws ScriptException{
        try{ control().clearThrottleOverride(thrusterId); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Get the redstone signal
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getRedstoneSignal", signature = "getRedstoneSignal(id|'all'): number|table",
            description = "Returns the redstone signal.")
    public final Object getRedstoneSignal(String thrusterId) throws ScriptException{
        try{ return control().getRedstoneSignal(thrusterId); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Get the thruster status
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getThrusterStatus", signature = "getThrusterStatus(id|'all'): table",
            description = "Returns the thruster status.")
    public final Object getThrusterStatus(String thrusterId) throws ScriptException{
        try{ return control().getThrusterStatus(thrusterId); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }
}

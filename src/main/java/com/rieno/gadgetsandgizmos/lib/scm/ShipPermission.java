package com.rieno.gadgetsandgizmos.lib.scm;

// Identify player actions controlled by a claimed ship
public enum ShipPermission {
    INTERACT("interact"),
    ACC_GRAPH("acc_graph"),
    PLACE("place"),
    DESTROY("destroy"),
    PHYSICS_STAFF("physics_staff"),
    STORE("store");

    private final String id;

    ShipPermission(String id){
        this.id = id;
    }

    public String id(){
        return id;
    }

    public static ShipPermission fromId(String id){
        for(ShipPermission permission : values()) if(permission.id.equals(id)) return permission;
        return null;
    }
}

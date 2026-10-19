package com.rieno.gadgetsandgizmos.lib.client.ui;

// Filter list entries by their current selection state
public enum SelectionFilter{
    ALL,
    SELECTED,
    UNSELECTED;

    public boolean matches(boolean selected){
        return switch(this){
            case ALL -> true;
            case SELECTED -> selected;
            case UNSELECTED -> !selected;
        };
    }

    // Cycle the available selection filters
    public SelectionFilter next(){
        return switch(this){
            case ALL -> SELECTED;
            case SELECTED -> UNSELECTED;
            case UNSELECTED -> ALL;
        };
    }
}

package com.rieno.gadgetsandgizmos.lib.client.ui;

// Keep two ordered values on one rail without coupling pointer input to a host screen
public final class DualThumbSlider{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        VARIABLES
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public enum Thumb{ LOWER, UPPER }

    private final double minimum;
    private final double maximum;
    private double lower;
    private double upper;
    private Thumb selected = Thumb.UPPER;
    private boolean dragging;
    private double dragStartFraction;
    private double dragStartValue;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public DualThumbSlider(double minimum, double maximum, double lower, double upper){
        if(!Double.isFinite(minimum) || !Double.isFinite(maximum) || maximum <= minimum
                || !Double.isFinite(maximum - minimum)){
            throw new IllegalArgumentException("Slider bounds must form a finite increasing range");
        }
        this.minimum = minimum;
        this.maximum = maximum;
        setRange(lower, upper);
    }

    public double lower(){ return lower; }
    public double upper(){ return upper; }
    public double lowerFraction(){ return (lower - minimum) / (maximum - minimum); }
    public double upperFraction(){ return (upper - minimum) / (maximum - minimum); }
    public Thumb selectedThumb(){ return selected; }
    public boolean dragging(){ return dragging; }

    // Accept external values while retaining their endpoint order
    public void setRange(double lower, double upper){
        requireFinite(lower);
        requireFinite(upper);
        this.lower = Math.clamp(Math.min(lower, upper), minimum, maximum);
        this.upper = Math.clamp(Math.max(lower, upper), minimum, maximum);
        dragging = false;
    }

    // Stop the lower thumb at the upper thumb
    public boolean setLower(double val){
        requireFinite(val);
        double next = Math.clamp(val, minimum, upper);
        if(next == lower) return false;
        lower = next;
        return true;
    }

    // Stop the upper thumb at the lower thumb
    public boolean setUpper(double val){
        requireFinite(val);
        double next = Math.clamp(val, lower, maximum);
        if(next == upper) return false;
        upper = next;
        return true;
    }

    // Alternate equal-distance picks so coincident thumbs remain independently usable
    public Thumb nearestThumb(double fraction){
        requireFinite(fraction);
        double lowerDistance = Math.abs(fraction - lowerFraction());
        double upperDistance = Math.abs(fraction - upperFraction());
        if(Math.abs(lowerDistance - upperDistance) <= 1.0E-9){
            return selected == Thumb.LOWER ? Thumb.UPPER : Thumb.LOWER;
        }
        return lowerDistance < upperDistance ? Thumb.LOWER : Thumb.UPPER;
    }

    // Preserve the pointer offset on a thumb and optionally jump when clicking the rail
    public boolean beginDrag(double fraction, boolean jumpToPointer){
        requireFinite(fraction);
        selected = nearestThumb(fraction);
        boolean changed = false;
        if(jumpToPointer){
            double val = minimum + Math.clamp(fraction, 0, 1) * (maximum - minimum);
            changed = selected == Thumb.LOWER ? setLower(val) : setUpper(val);
        }
        dragStartFraction = Math.clamp(fraction, 0, 1);
        dragStartValue = selected == Thumb.LOWER ? lower : upper;
        dragging = true;
        return changed;
    }

    // Apply host-selected fine movement and optional snapping from the original drag value
    public boolean dragTo(double fraction, double sensitivity, double step){
        requireFinite(fraction);
        if(!Double.isFinite(sensitivity) || sensitivity <= 0 || !Double.isFinite(step) || step < 0){
            throw new IllegalArgumentException("Slider sensitivity must be positive and snapping must be nonnegative");
        }
        if(!dragging) return false;
        double val = dragStartValue + (Math.clamp(fraction, 0, 1) - dragStartFraction)
                * (maximum - minimum) * sensitivity;
        if(step > 0) val = Math.round(val / step) * step;
        return selected == Thumb.LOWER ? setLower(val) : setUpper(val);
    }

    // Finish the drag without changing either endpoint
    public boolean endDrag(){
        boolean prev = dragging;
        dragging = false;
        return prev;
    }

    private static void requireFinite(double val){
        if(!Double.isFinite(val)) throw new IllegalArgumentException("Slider values must be finite");
    }
}

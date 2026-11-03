package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.color.DensityColorPalette;
import com.rieno.gadgetsandgizmos.lib.geometry.BandedFluidMesh;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import java.util.Arrays;

// Advect dye concentrations through one small volume and let density restore the resting layers
public final class DensityFluidField{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int WIDTH = 6, HEIGHT = 8, DEPTH = 16, CELLS = WIDTH * HEIGHT * DEPTH;
    private final AABB bounds;
    private final float fill;
    private final DensityColorPalette palette;
    private final int layers;
    private float[] dye, next;
    private final float[] target, occupancy = new float[CELLS], mass, totals, factors;
    private final float[] restoration;
    private final float[] phaseSin, phaseCos, surfaceScores;
    private final float[] rgb = new float[CELLS * 3], prevRgb = new float[CELLS * 3];
    private final float[] x = new float[CELLS], y = new float[CELLS], z = new float[CELLS];
    private final float[] sinX = new float[CELLS], cosX = new float[CELLS], sinY = new float[CELLS], cosY = new float[CELLS];
    private final float[] sinZ = new float[CELLS], cosZ = new float[CELLS];
    private final int[] indices = new int[8];
    private final float[] weights = new float[8];
    private final Vector3f up = new Vector3f();
    private double stepTime = Double.NaN;
    private boolean settled = true;
    private long steps;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public DensityFluidField(AABB bounds, float fill, DensityColorPalette palette){
        if(!Float.isFinite(fill) || fill <= 0 || fill > 1 || palette.layers().isEmpty()){
            throw new IllegalArgumentException("A dye field requires a positive fill and at least one layer");
        }
        this.bounds = bounds;
        this.fill = fill;
        this.palette = palette;
        layers = palette.layers().size();
        dye = new float[CELLS * layers]; next = new float[dye.length]; target = new float[dye.length];
        mass = new float[layers]; totals = new float[layers]; factors = new float[layers];
        restoration = new float[layers];
        phaseSin = new float[layers]; phaseCos = new float[layers]; surfaceScores = new float[layers];
        for(int layer = 0; layer < layers; layer++){
            restoration[layer] = (1 - palette.mobility(palette.layers().get(layer).color())) * .2F + .2F;
            phaseSin[layer] = (float)Math.sin(layer * 2.399963229728653D);
            phaseCos[layer] = (float)Math.cos(layer * 2.399963229728653D);
        }
        for(int zz = 0; zz < DEPTH; zz++) for(int yy = 0; yy < HEIGHT; yy++) for(int xx = 0; xx < WIDTH; xx++){
            int idx = (zz * HEIGHT + yy) * WIDTH + xx;
            x[idx] = (xx + .5F) / WIDTH; y[idx] = (yy + .5F) / HEIGHT; z[idx] = (zz + .5F) / DEPTH;
            sinX[idx] = (float)Math.sin(Math.PI * x[idx]); cosX[idx] = (float)Math.cos(Math.PI * x[idx]);
            sinY[idx] = (float)Math.sin(Math.PI * y[idx]); cosY[idx] = (float)Math.cos(Math.PI * y[idx]);
            sinZ[idx] = (float)Math.sin(Math.PI * z[idx]); cosZ[idx] = (float)Math.cos(Math.PI * z[idx]);
        }
    }

    // Update at twenty steps per second, with bounded catch-up and no work after the dyes settle
    public boolean update(DensityFluidMotion.Frame frame, double time){
        if(!Double.isFinite(time)) time = 0;
        var direction = frame.up().normalize();
        if(!Double.isFinite(stepTime) || time < stepTime || time - stepTime > 10){
            prepareTarget(direction);
            System.arraycopy(target, 0, dye, 0, dye.length);
            updateRgb();
            System.arraycopy(rgb, 0, prevRgb, 0, rgb.length);
            stepTime = time;
            settled = true;
            return true;
        }
        boolean moving = frame.mixing() > .002F || Math.abs(frame.waveX()) + Math.abs(frame.waveZ()) > .002F;
        if(settled && !moving && up.distanceSquared(direction) < 1.0E-6F){
            stepTime = time;
            return false;
        }
        if(time - stepTime < 1) return false;
        if(up.distanceSquared(direction) > 1.0E-6F) prepareTarget(direction);
        System.arraycopy(rgb, 0, prevRgb, 0, rgb.length);
        int count = 0;
        while(time - stepTime >= 1 && count++ < 4){
            stepTime += 1;
            step(frame, moving);
        }
        if(time - stepTime >= 1) stepTime = time;
        updateRgb();
        return true;
    }

    public boolean settled(){ return settled; }

    // Sample the shared concentration field rather than the colour of an individual band face
    public int sample(double px, double py, double pz, float alpha){
        lookup((float)((px - bounds.minX) / bounds.getXsize()), (float)((py - bounds.minY) / bounds.getYsize()),
                (float)((pz - bounds.minZ) / bounds.getZsize()));
        float red = 0, green = 0, blue = 0;
        for(int corner = 0; corner < 8; corner++){
            int idx = indices[corner] * 3;
            float weight = weights[corner];
            red += (prevRgb[idx] + (rgb[idx] - prevRgb[idx]) * alpha) * weight;
            green += (prevRgb[idx + 1] + (rgb[idx + 1] - prevRgb[idx + 1]) * alpha) * weight;
            blue += (prevRgb[idx + 2] + (rgb[idx + 2] - prevRgb[idx + 2]) * alpha) * weight;
        }
        return Math.clamp(Math.round(red), 0, 255) << 16 | Math.clamp(Math.round(green), 0, 255) << 8 | Math.clamp(Math.round(blue), 0, 255);
    }

    // Keep dye identities visible as moving patches instead of averaging their RGB colours
    public int samplePalette(double px, double py, double pz){
        float xx = (float)((px - bounds.minX) / bounds.getXsize());
        float yy = (float)((py - bounds.minY) / bounds.getYsize());
        float zz = (float)((pz - bounds.minZ) / bounds.getZsize());
        lookup(xx, yy, zz);
        double phase = xx * 1.913D + yy * .713D + zz * 3.137D + .3D * Math.sin((xx - zz) * Math.PI * 2);
        float selector = (float)(phase - Math.floor(phase));
        float accumulated = 0;
        for(int layer = 0; layer < layers; layer++){
            for(int corner = 0; corner < 8; corner++) accumulated += dye[indices[corner] * layers + layer] * weights[corner];
            if(selector < accumulated || layer == layers - 1) return palette.layers().get(layer).color();
        }
        return palette.layers().getLast().color();
    }

    // Sample continuous dye regions with a narrow blend only where two colours meet
    public int sampleSurface(double px, double py, double pz){
        float xx = (float)((px - bounds.minX) / bounds.getXsize());
        float yy = (float)((py - bounds.minY) / bounds.getYsize());
        float zz = (float)((pz - bounds.minZ) / bounds.getZsize());
        lookup(xx, yy, zz);
        double phase = (xx * 1.7D + yy * .8D + zz * 2.1D) * Math.PI + .35D * Math.sin((xx - zz) * Math.PI * 2);
        float sine = (float)Math.sin(phase), cosine = (float)Math.cos(phase);
        float maximum = 0;
        for(int layer = 0; layer < layers; layer++){
            float amount = 0;
            for(int corner = 0; corner < 8; corner++) amount += dye[indices[corner] * layers + layer] * weights[corner];
            float shape = .5F + .5F * (sine * phaseCos[layer] + cosine * phaseSin[layer]);
            surfaceScores[layer] = amount * (.08F + .92F * shape * shape * shape * shape);
            maximum = Math.max(maximum, surfaceScores[layer]);
        }
        if(maximum <= 0) return palette.layers().getLast().color();
        float red = 0, green = 0, blue = 0, total = 0;
        for(int layer = 0; layer < layers; layer++){
            float edge = Math.clamp((surfaceScores[layer] / maximum - .8F) * 5, 0, 1);
            float weight = edge * edge * (3 - 2 * edge);
            int color = palette.layers().get(layer).color();
            red += (color >> 16 & 255) * weight;
            green += (color >> 8 & 255) * weight;
            blue += (color & 255) * weight;
            total += weight;
        }
        return Math.round(red / total) << 16 | Math.round(green / total) << 8 | Math.round(blue / total);
    }

    // Expose the represented dye share for diagnostics without changing the stored inventory
    public double fraction(int color){
        double amount = 0, total = 0;
        for(int cell = 0; cell < CELLS; cell++) for(int layer = 0; layer < layers; layer++){
            double val = dye[cell * layers + layer] * occupancy[cell];
            total += val;
            if(palette.layers().get(layer).color() == (color & 0xFFFFFF)) amount += val;
        }
        return total > 0 ? amount / total : 0;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    long steps(){ return steps; }

    private void prepareTarget(Vector3f direction){
        up.set(direction);
        var projection = new BandedFluidMesh.Projection(bounds, direction);
        var bands = palette.gradient().bands();
        double[] low = new double[layers], high = new double[layers];
        for(int layer = 0; layer < layers; layer++){
            low[layer] = projection.height(fill * bands.get(layer).start());
            high[layer] = projection.height(fill * bands.get(layer).end());
        }
        double radius = (Math.abs(up.x) * bounds.getXsize() / WIDTH + Math.abs(up.y) * bounds.getYsize() / HEIGHT
                + Math.abs(up.z) * bounds.getZsize() / DEPTH) * .5D;
        float volume = 0;
        for(int cell = 0; cell < CELLS; cell++){
            double position = up.x * (bounds.minX + x[cell] * bounds.getXsize()) + up.y * (bounds.minY + y[cell] * bounds.getYsize())
                    + up.z * (bounds.minZ + z[cell] * bounds.getZsize());
            double start = Math.max(position - radius, low[0]), end = Math.min(position + radius, high[layers - 1]);
            occupancy[cell] = (float)Math.clamp((end - start) / (2 * radius), 0, 1);
            volume += occupancy[cell];
            float total = 0;
            for(int layer = 0; layer < layers; layer++){
                float val = (float)Math.max(0, Math.min(end, high[layer]) - Math.max(start, low[layer]));
                target[cell * layers + layer] = val;
                total += val;
            }
            if(total > 0){
                for(int layer = 0; layer < layers; layer++) target[cell * layers + layer] /= total;
            }else{
                target[cell * layers + (position < low[0] ? 0 : layers - 1)] = 1;
            }
        }
        for(int layer = 0; layer < layers; layer++) mass[layer] = (float)(volume * (bands.get(layer).end() - bands.get(layer).start()));
        balance(target, 8);
    }

    private void step(DensityFluidMotion.Frame frame, boolean moving){
        steps++;
        float rollX = frame.waveX() * 2.2F, rollZ = frame.waveZ() * 2.2F;
        float error = 0;
        for(int cell = 0; cell < CELLS; cell++){
            if(occupancy[cell] < .0001F){
                System.arraycopy(target, cell * layers, next, cell * layers, layers);
                continue;
            }
            float vx = -rollX * sinX[cell] * cosY[cell] + rollZ * .35F * sinX[cell] * cosZ[cell];
            float vy = rollX * cosX[cell] * sinY[cell] + rollZ * cosZ[cell] * sinY[cell];
            float vz = -rollZ * sinZ[cell] * cosY[cell] - rollX * .35F * sinZ[cell] * cosX[cell];
            lookup(x[cell] - vx * .075F, y[cell] - vy * .075F, z[cell] - vz * .075F);
            float total = 0;
            for(int layer = 0; layer < layers; layer++){
                float val = 0;
                for(int corner = 0; corner < 8; corner++) val += dye[indices[corner] * layers + layer] * weights[corner];
                float restore = restoration[layer];
                if(moving) restore *= Math.max(.06F, 1 - frame.mixing());
                val += (target[cell * layers + layer] - val) * restore;
                next[cell * layers + layer] = Math.max(0, val);
                total += val;
            }
            if(total > 0) for(int layer = 0; layer < layers; layer++) next[cell * layers + layer] /= total;
        }
        float[] old = dye; dye = next; next = old;
        balance(dye, 3);
        for(int idx = 0; idx < dye.length; idx++) error = Math.max(error, Math.abs(dye[idx] - target[idx]));
        settled = !moving && error < .004F;
        if(settled) System.arraycopy(target, 0, dye, 0, dye.length);
    }

    // Correct numerical advection loss while keeping every cell's concentrations normalized
    private void balance(float[] values, int iterations){
        for(int iteration = 0; iteration < iterations; iteration++){
            Arrays.fill(totals, 0);
            for(int cell = 0; cell < CELLS; cell++) for(int layer = 0; layer < layers; layer++){
                totals[layer] += values[cell * layers + layer] * occupancy[cell];
            }
            for(int layer = 0; layer < layers; layer++) factors[layer] = totals[layer] > 1.0E-10F ? mass[layer] / totals[layer] : 1;
            for(int cell = 0; cell < CELLS; cell++){
                if(occupancy[cell] < .0001F) continue;
                float total = 0;
                for(int layer = 0; layer < layers; layer++) total += values[cell * layers + layer] *= factors[layer];
                if(total > 0) for(int layer = 0; layer < layers; layer++) values[cell * layers + layer] /= total;
            }
        }
    }

    private void updateRgb(){
        Arrays.fill(rgb, 0);
        for(int cell = 0; cell < CELLS; cell++) for(int layer = 0; layer < layers; layer++){
            int color = palette.layers().get(layer).color();
            float val = dye[cell * layers + layer];
            rgb[cell * 3] += (color >> 16 & 255) * val;
            rgb[cell * 3 + 1] += (color >> 8 & 255) * val;
            rgb[cell * 3 + 2] += (color & 255) * val;
        }
    }

    private void lookup(float px, float py, float pz){
        float xx = Math.clamp(px * WIDTH - .5F, 0, WIDTH - 1), yy = Math.clamp(py * HEIGHT - .5F, 0, HEIGHT - 1);
        float zz = Math.clamp(pz * DEPTH - .5F, 0, DEPTH - 1);
        int ix = (int)xx, iy = (int)yy, iz = (int)zz;
        float fx = xx - ix, fy = yy - iy, fz = zz - iz;
        for(int corner = 0; corner < 8; corner++){
            int cx = Math.min(WIDTH - 1, ix + (corner & 1));
            int cy = Math.min(HEIGHT - 1, iy + (corner >> 1 & 1)), cz = Math.min(DEPTH - 1, iz + (corner >> 2 & 1));
            indices[corner] = (cz * HEIGHT + cy) * WIDTH + cx;
            weights[corner] = ((corner & 1) == 0 ? 1 - fx : fx) * ((corner & 2) == 0 ? 1 - fy : fy) * ((corner & 4) == 0 ? 1 - fz : fz);
        }
    }
}

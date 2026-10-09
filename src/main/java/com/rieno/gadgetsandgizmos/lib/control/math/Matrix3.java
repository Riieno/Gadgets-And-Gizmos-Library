package com.rieno.gadgetsandgizmos.lib.control.math;

// Store a row-major linear transform acting on column vectors
public record Matrix3(double m00, double m01, double m02,
                      double m10, double m11, double m12,
                      double m20, double m21, double m22){
    public static final Matrix3 ZERO = new Matrix3(0, 0, 0, 0, 0, 0, 0, 0, 0);
    public static final Matrix3 IDENTITY = diagonal(new Vector3(1, 1, 1));


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static Matrix3 diagonal(Vector3 val){
        return new Matrix3(val.x(), 0, 0, 0, val.y(), 0, 0, 0, val.z());
    }

    public static Matrix3 fromRows(Vector3 x, Vector3 y, Vector3 z){
        return new Matrix3(x.x(), x.y(), x.z(), y.x(), y.y(), y.z(), z.x(), z.y(), z.z());
    }

    public Vector3 transform(Vector3 val){
        return new Vector3(m00 * val.x() + m01 * val.y() + m02 * val.z(),
                m10 * val.x() + m11 * val.y() + m12 * val.z(),
                m20 * val.x() + m21 * val.y() + m22 * val.z());
    }

    // Apply the other transform first
    public Matrix3 multiply(Matrix3 other){
        Matrix3 columns = other.transpose();
        return new Matrix3(
                row(0).dot(columns.row(0)), row(0).dot(columns.row(1)), row(0).dot(columns.row(2)),
                row(1).dot(columns.row(0)), row(1).dot(columns.row(1)), row(1).dot(columns.row(2)),
                row(2).dot(columns.row(0)), row(2).dot(columns.row(1)), row(2).dot(columns.row(2)));
    }

    public Matrix3 add(Matrix3 other){
        return fromRows(row(0).add(other.row(0)), row(1).add(other.row(1)), row(2).add(other.row(2)));
    }

    public Matrix3 subtract(Matrix3 other){
        return add(other.scale(-1));
    }

    public Matrix3 scale(double val){
        return fromRows(row(0).scale(val), row(1).scale(val), row(2).scale(val));
    }

    public Matrix3 transpose(){
        return new Matrix3(m00, m10, m20, m01, m11, m21, m02, m12, m22);
    }

    public double determinant(){
        return m00 * (m11 * m22 - m12 * m21) - m01 * (m10 * m22 - m12 * m20)
                + m02 * (m10 * m21 - m11 * m20);
    }

    // Scale before inversion so uniformly small or large matrices remain usable
    public Matrix3 inverse(){
        double scale = maxAbs();
        if(!Double.isFinite(scale) || scale == 0) throw new IllegalArgumentException("Matrix is singular");
        Matrix3 n = scale(1 / scale);
        double det = n.determinant();
        if(!Double.isFinite(det) || Math.abs(det) < 1.0E-12D){
            throw new IllegalArgumentException("Matrix is singular or ill-conditioned");
        }
        return new Matrix3(
                n.m11 * n.m22 - n.m12 * n.m21, n.m02 * n.m21 - n.m01 * n.m22, n.m01 * n.m12 - n.m02 * n.m11,
                n.m12 * n.m20 - n.m10 * n.m22, n.m00 * n.m22 - n.m02 * n.m20, n.m02 * n.m10 - n.m00 * n.m12,
                n.m10 * n.m21 - n.m11 * n.m20, n.m01 * n.m20 - n.m00 * n.m21, n.m00 * n.m11 - n.m01 * n.m10)
                .scale((1 / det) / scale);
    }

    public Vector3 row(int idx){
        return switch(idx){
            case 0 -> new Vector3(m00, m01, m02);
            case 1 -> new Vector3(m10, m11, m12);
            case 2 -> new Vector3(m20, m21, m22);
            default -> throw new IndexOutOfBoundsException(idx);
        };
    }

    public double maxAbs(){
        double res = 0;
        for(int idx = 0; idx < 3; idx++){
            Vector3 row = row(idx);
            res = Math.max(res, Math.max(Math.abs(row.x()), Math.max(Math.abs(row.y()), Math.abs(row.z()))));
        }
        return res;
    }
}

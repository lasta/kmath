package kscience.kmath.gsl

import kotlinx.cinterop.CStructVar
import kotlinx.cinterop.DeferScope
import kotlinx.cinterop.pointed
import kscience.kmath.linear.MatrixContext
import kscience.kmath.linear.Point
import kscience.kmath.operations.Complex
import kscience.kmath.operations.ComplexField
import kscience.kmath.operations.toComplex
import kscience.kmath.structures.Matrix
import org.gnu.gsl.*

internal inline fun <T : Any, H : CStructVar> GslMatrix<T, H>.fill(initializer: (Int, Int) -> T): GslMatrix<T, H> =
    apply {
        (0 until rowNum).forEach { row -> (0 until colNum).forEach { col -> this[row, col] = initializer(row, col) } }
    }

internal inline fun <T : Any, H : CStructVar> GslVector<T, H>.fill(initializer: (Int) -> T): GslVector<T, H> =
    apply { (0 until size).forEach { index -> this[index] = initializer(index) } }

public abstract class GslMatrixContext<T : Any, H1 : CStructVar, H2 : CStructVar> internal constructor(
    internal val scope: DeferScope
) : MatrixContext<T> {
    @Suppress("UNCHECKED_CAST")
    public fun Matrix<T>.toGsl(): GslMatrix<T, H1> = (if (this is GslMatrix<*, *>)
        this as GslMatrix<T, H1>
    else
        produce(rowNum, colNum) { i, j -> this[i, j] }).copy()

    @Suppress("UNCHECKED_CAST")
    public fun Point<T>.toGsl(): GslVector<T, H2> =
        (if (this is GslVector<*, *>) this as GslVector<T, H2> else produceDirtyVector(size).fill { this[it] }).copy()

    internal abstract fun produceDirtyMatrix(rows: Int, columns: Int): GslMatrix<T, H1>
    internal abstract fun produceDirtyVector(size: Int): GslVector<T, H2>

    public override fun produce(rows: Int, columns: Int, initializer: (i: Int, j: Int) -> T): GslMatrix<T, H1> =
        produceDirtyMatrix(rows, columns).fill(initializer)
}

public class GslRealMatrixContext(scope: DeferScope) : GslMatrixContext<Double, gsl_matrix, gsl_vector>(scope) {
    override fun produceDirtyMatrix(rows: Int, columns: Int): GslMatrix<Double, gsl_matrix> =
        GslRealMatrix(nativeHandle = requireNotNull(gsl_matrix_alloc(rows.toULong(), columns.toULong())), scope = scope)

    override fun produceDirtyVector(size: Int): GslVector<Double, gsl_vector> =
        GslRealVector(nativeHandle = requireNotNull(gsl_vector_alloc(size.toULong())), scope = scope)

    public override fun Matrix<Double>.dot(other: Matrix<Double>): GslMatrix<Double, gsl_matrix> {
        val x = toGsl().nativeHandle
        val a = other.toGsl().nativeHandle
        val result = requireNotNull(gsl_matrix_calloc(a.pointed.size1, a.pointed.size2))
        gsl_blas_dgemm(CblasNoTrans, CblasNoTrans, 1.0, x, a, 1.0, result)
        return GslRealMatrix(result, scope = scope)
    }

    public override fun Matrix<Double>.dot(vector: Point<Double>): GslVector<Double, gsl_vector> {
        val x = toGsl().nativeHandle
        val a = vector.toGsl().nativeHandle
        val result = requireNotNull(gsl_vector_calloc(a.pointed.size))
        gsl_blas_dgemv(CblasNoTrans, 1.0, x, a, 1.0, result)
        return GslRealVector(result, scope = scope)
    }

    public override fun Matrix<Double>.times(value: Double): GslMatrix<Double, gsl_matrix> {
        val g1 = toGsl()
        gsl_matrix_scale(g1.nativeHandle, value)
        return g1
    }

    public override fun add(a: Matrix<Double>, b: Matrix<Double>): GslMatrix<Double, gsl_matrix> {
        val g1 = a.toGsl()
        gsl_matrix_add(g1.nativeHandle, b.toGsl().nativeHandle)
        return g1
    }

    public override fun multiply(a: Matrix<Double>, k: Number): GslMatrix<Double, gsl_matrix> {
        val g1 = a.toGsl()
        gsl_matrix_scale(g1.nativeHandle, k.toDouble())
        return g1
    }
}

public class GslFloatMatrixContext(scope: DeferScope) :
    GslMatrixContext<Float, gsl_matrix_float, gsl_vector_float>(scope) {
    override fun produceDirtyMatrix(rows: Int, columns: Int): GslMatrix<Float, gsl_matrix_float> =
        GslFloatMatrix(requireNotNull(gsl_matrix_float_alloc(rows.toULong(), columns.toULong())), scope = scope)

    override fun produceDirtyVector(size: Int): GslVector<Float, gsl_vector_float> =
        GslFloatVector(requireNotNull(gsl_vector_float_alloc(size.toULong())), scope)

    public override fun Matrix<Float>.dot(other: Matrix<Float>): GslMatrix<Float, gsl_matrix_float> {
        val x = toGsl().nativeHandle
        val a = other.toGsl().nativeHandle
        val result = requireNotNull(gsl_matrix_float_calloc(a.pointed.size1, a.pointed.size2))
        gsl_blas_sgemm(CblasNoTrans, CblasNoTrans, 1f, x, a, 1f, result)
        return GslFloatMatrix(nativeHandle = result, scope = scope)
    }

    public override fun Matrix<Float>.dot(vector: Point<Float>): GslVector<Float, gsl_vector_float> {
        val x = toGsl().nativeHandle
        val a = vector.toGsl().nativeHandle
        val result = requireNotNull(gsl_vector_float_calloc(a.pointed.size))
        gsl_blas_sgemv(CblasNoTrans, 1f, x, a, 1f, result)
        return GslFloatVector(nativeHandle = result, scope = scope)
    }

    public override fun Matrix<Float>.times(value: Float): GslMatrix<Float, gsl_matrix_float> {
        val g1 = toGsl()
        gsl_matrix_float_scale(g1.nativeHandle, value.toDouble())
        return g1
    }

    public override fun add(a: Matrix<Float>, b: Matrix<Float>): GslMatrix<Float, gsl_matrix_float> {
        val g1 = a.toGsl()
        gsl_matrix_float_add(g1.nativeHandle, b.toGsl().nativeHandle)
        return g1
    }

    public override fun multiply(a: Matrix<Float>, k: Number): GslMatrix<Float, gsl_matrix_float> {
        val g1 = a.toGsl()
        gsl_matrix_float_scale(g1.nativeHandle, k.toDouble())
        return g1
    }
}

public class GslComplexMatrixContext(scope: DeferScope) :
    GslMatrixContext<Complex, gsl_matrix_complex, gsl_vector_complex>(scope) {
    override fun produceDirtyMatrix(rows: Int, columns: Int): GslMatrix<Complex, gsl_matrix_complex> = GslComplexMatrix(
        nativeHandle = requireNotNull(gsl_matrix_complex_alloc(rows.toULong(), columns.toULong())),
        scope = scope
    )

    override fun produceDirtyVector(size: Int): GslVector<Complex, gsl_vector_complex> =
        GslComplexVector(requireNotNull(gsl_vector_complex_alloc(size.toULong())), scope)

    public override fun Matrix<Complex>.dot(other: Matrix<Complex>): GslMatrix<Complex, gsl_matrix_complex> {
        val x = toGsl().nativeHandle
        val a = other.toGsl().nativeHandle
        val result = requireNotNull(gsl_matrix_complex_calloc(a.pointed.size1, a.pointed.size2))
        gsl_blas_zgemm(CblasNoTrans, CblasNoTrans, ComplexField.one.toGsl(), x, a, ComplexField.one.toGsl(), result)
        return GslComplexMatrix(nativeHandle = result, scope = scope)
    }

    public override fun Matrix<Complex>.dot(vector: Point<Complex>): GslVector<Complex, gsl_vector_complex> {
        val x = toGsl().nativeHandle
        val a = vector.toGsl().nativeHandle
        val result = requireNotNull(gsl_vector_complex_calloc(a.pointed.size))
        gsl_blas_zgemv(CblasNoTrans, ComplexField.one.toGsl(), x, a, ComplexField.one.toGsl(), result)
        return GslComplexVector(result, scope)
    }

    public override fun Matrix<Complex>.times(value: Complex): GslMatrix<Complex, gsl_matrix_complex> {
        val g1 = toGsl()
        gsl_matrix_complex_scale(g1.nativeHandle, value.toGsl())
        return g1
    }

    public override fun add(a: Matrix<Complex>, b: Matrix<Complex>): GslMatrix<Complex, gsl_matrix_complex> {
        val g1 = a.toGsl()
        gsl_matrix_complex_add(g1.nativeHandle, b.toGsl().nativeHandle)
        return g1
    }

    public override fun multiply(a: Matrix<Complex>, k: Number): GslMatrix<Complex, gsl_matrix_complex> {
        val g1 = a.toGsl()
        gsl_matrix_complex_scale(g1.nativeHandle, k.toComplex().toGsl())
        return g1
    }
}
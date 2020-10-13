package kscience.kmath.kotlingrad

import edu.umontreal.kotlingrad.experimental.*
import kscience.kmath.ast.MST
import kscience.kmath.ast.MstExtendedField
import kscience.kmath.ast.MstExtendedField.unaryMinus
import kscience.kmath.operations.*

/**
 * Maps [SFun] objects to [MST]. Some unsupported operations like [Derivative] are bound and converted then.
 * [Power] operation is limited to constant right-hand side arguments.
 *
 * Detailed mapping is:
 *
 * - [SVar] -> [MstExtendedField.symbol];
 * - [SConst] -> [MstExtendedField.number];
 * - [Sum] -> [MstExtendedField.add];
 * - [Prod] -> [MstExtendedField.multiply];
 * - [Power] -> [MstExtendedField.power] (limited);
 * - [Negative] -> [MstExtendedField.unaryMinus];
 * - [Log] -> [MstExtendedField.ln] (left) / [MstExtendedField.ln] (right);
 * - [Sine] -> [MstExtendedField.sin];
 * - [Cosine] -> [MstExtendedField.cos];
 * - [Tangent] -> [MstExtendedField.tan];
 * - [DProd] is vector operation, and it is requested to be evaluated;
 * - [SComposition] is also requested to be evaluated eagerly;
 * - [VSumAll] is requested to be evaluated;
 * - [Derivative] is requested to be evaluated.
 *
 * @receiver the scalar function.
 * @return a node.
 */
public fun <X : SFun<X>> SFun<X>.toMst(): MST = MstExtendedField {
    when (this@toMst) {
        is SVar -> symbol(name)
        is SConst -> number(doubleValue)
        is Sum -> left.toMst() + right.toMst()
        is Prod -> left.toMst() * right.toMst()
        is Power -> power(left.toMst(), (right as SConst<*>).doubleValue)
        is Negative -> -input.toMst()
        is Log -> ln(left.toMst()) / ln(right.toMst())
        is Sine -> sin(input.toMst())
        is Cosine -> cos(input.toMst())
        is Tangent -> tan(input.toMst())
        is DProd -> this@toMst().toMst()
        is SComposition -> this@toMst().toMst()
        is VSumAll<X, *> -> this@toMst().toMst()
        is Derivative -> this@toMst().toMst()
    }
}

/**
 * Maps [MST.Numeric] to [SConst] directly.
 *
 * @receiver the node.
 * @return a new constant.
 */
public fun <X : SFun<X>> MST.Numeric.toSConst(): SConst<X> = SConst(value)

/**
 * Maps [MST.Symbolic] to [SVar] directly.
 *
 * @receiver the node.
 * @param proto the prototype instance.
 * @return a new variable.
 */
public fun <X : SFun<X>> MST.Symbolic.toSVar(proto: X): SVar<X> = SVar(proto, value)

/**
 * Maps [MST] objects to [SFun]. Unsupported operations throw [IllegalStateException].
 *
 * Detailed mapping is:
 *
 * - [MST.Numeric] -> [SConst];
 * - [MST.Symbolic] -> [SVar];
 * - [MST.Unary] -> [Negative], [Sine], [Cosine], [Tangent], [Power], [Log];
 * - [MST.Binary] -> [Sum], [Prod], [Power].
 *
 * @receiver the node.
 * @param proto the prototype instance.
 * @return a scalar function.
 */
public fun <X : SFun<X>> MST.tSFun(proto: X): SFun<X> = when (this) {
    is MST.Numeric -> toSConst()
    is MST.Symbolic -> toSVar(proto)

    is MST.Unary -> when (operation) {
        SpaceOperations.PLUS_OPERATION -> value.tSFun(proto)
        SpaceOperations.MINUS_OPERATION -> Negative(value.tSFun(proto))
        TrigonometricOperations.SIN_OPERATION -> Sine(value.tSFun(proto))
        TrigonometricOperations.COS_OPERATION -> Cosine(value.tSFun(proto))
        TrigonometricOperations.TAN_OPERATION -> Tangent(value.tSFun(proto))
        PowerOperations.SQRT_OPERATION -> Power(value.tSFun(proto), SConst(0.5))
        ExponentialOperations.EXP_OPERATION -> Power(value.tSFun(proto), E())
        ExponentialOperations.LN_OPERATION -> Log(value.tSFun(proto))
        else -> error("Unary operation $operation not defined in $this")
    }

    is MST.Binary -> when (operation) {
        SpaceOperations.PLUS_OPERATION -> Sum(left.tSFun(proto), right.tSFun(proto))
        SpaceOperations.MINUS_OPERATION -> Sum(left.tSFun(proto), Negative(right.tSFun(proto)))
        RingOperations.TIMES_OPERATION -> Prod(left.tSFun(proto), right.tSFun(proto))
        FieldOperations.DIV_OPERATION -> Prod(left.tSFun(proto), Power(right.tSFun(proto), Negative(One())))
        PowerOperations.POW_OPERATION -> Power(left.tSFun(proto), SConst((right as MST.Numeric).value))
        else -> error("Binary operation $operation not defined in $this")
    }
}
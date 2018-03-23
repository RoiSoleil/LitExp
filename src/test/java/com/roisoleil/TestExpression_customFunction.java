package com.roisoleil;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.Test;

import com.roisoleil.Expression.AbstractFunction;
import com.roisoleil.Expression.AbstractLazyOperand;
import com.roisoleil.Expression.Operand;

public class TestExpression_customFunction {

	@Test
	public void test() {
		Expression expression = new Expression("addFive(0)");
		expression.addFunction(new AbstractFunction<BigDecimal>(expression, "addFive", 1) {
			@Override
			public Operand<BigDecimal> eval(List<Operand<?>> operands) {
				return new AbstractLazyOperand<BigDecimal>(expression) {
					@Override
					protected BigDecimal doEval() {
						return operands.get(0).getValue(BigDecimal.class).add(new BigDecimal(5));
					}
				};
			}
		});
		assertEquals(5, expression.eval(BigDecimal.class).doubleValue(), 0);
	}

}

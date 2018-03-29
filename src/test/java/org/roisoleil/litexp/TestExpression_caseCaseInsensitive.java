package org.roisoleil.litexp;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.Test;
import org.roisoleil.litexp.Expression;
import org.roisoleil.litexp.Expression.AbstractFunction;
import org.roisoleil.litexp.Expression.Operand;

public class TestExpression_caseCaseInsensitive {

	@Test
	public void testVariableIsCaseInsensitive() {
		Expression expression = new Expression("a");
		expression.setVariable("A", new BigDecimal(20));
		assertEquals(new BigDecimal(20), expression.eval().getValue(BigDecimal.class));
		expression = new Expression("a + B");
		expression.setVariable("A", new BigDecimal(10));
		expression.setVariable("b", new BigDecimal(10));
		assertEquals(new BigDecimal(20), expression.eval().getValue(BigDecimal.class));
	}

	@Test
	public void testFunctionCaseInsensitive() {
		Expression expression = new Expression("a+testsum(1,3)");
		expression.setVariable("A", new BigDecimal(1));
		expression.addFunction(new AbstractFunction(expression, "testSum", -1) {
			@Override
			protected Object doEval(List<Operand> operands) {
				BigDecimal value = null;
				for (Operand d : operands) {
					BigDecimal dValue = d.getValue(BigDecimal.class);
					value = value == null ? dValue : value.add(dValue);
				}
				return value;
			}
		});
		assertEquals(new BigDecimal(5), expression.eval().getValue(BigDecimal.class));
	}

}

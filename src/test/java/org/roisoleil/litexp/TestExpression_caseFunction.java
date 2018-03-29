package org.roisoleil.litexp;

import static org.roisoleil.litexp.TestUtils.assertStriclyEquals;
import static org.roisoleil.litexp.TestUtils.evalToDouble;

import org.junit.Test;
import org.roisoleil.litexp.Expression.LitExpException;

public class TestExpression_caseFunction {

	@Test
	public void testMax() {
		assertStriclyEquals(15, evalToDouble("max(1,9,6,4,15)"));
		assertStriclyEquals(45, evalToDouble("max(1,9,6,4,\"45\")"));
	}

	@Test(expected = LitExpException.class)
	public void testMaxFailNoOperand() {
		evalToDouble("max()");
	}

	@Test(expected = NumberFormatException.class)
	public void testMaxFailOperandNotBigDecimal() {
		evalToDouble("max(1,5,9,\"6a\")");
	}

	@Test
	public void testMin() {
		assertStriclyEquals(-45, evalToDouble("min(-45,9,6,4,15)"));
		assertStriclyEquals(0, evalToDouble("min(1,0,6,4,\"45\")"));
	}

	@Test(expected = LitExpException.class)
	public void testMinFailNoOperand() {
		evalToDouble("min()");
	}

	@Test(expected = NumberFormatException.class)
	public void testMinFailOperandNotBigDecimal() {
		evalToDouble("min(1,5,9,\"6a\")");
	}

}

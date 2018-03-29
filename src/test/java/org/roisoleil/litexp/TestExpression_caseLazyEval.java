package org.roisoleil.litexp;

import static org.roisoleil.litexp.TestUtils.assertStriclyEquals;
import static org.roisoleil.litexp.TestUtils.evalToDouble;

import org.junit.Test;

public class TestExpression_caseLazyEval {

	@Test
	public void testLazyEval() {
		assertStriclyEquals(0, evalToDouble("if(1, 0, 5/0)"));
		assertStriclyEquals(0, evalToDouble("if(1, 0, A)"));
	}

	@Test(expected = ArithmeticException.class)
	public void testLazyEval_withException() {
		evalToDouble("if(0, 0, 5/0)");
	}

}

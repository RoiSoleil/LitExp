package com.roisoleil.litexp;

import static com.roisoleil.litexp.TestUtils.assertStriclyEquals;
import static com.roisoleil.litexp.TestUtils.evalToDouble;

import org.junit.Test;

public class TestExpression_caseTrigonometricFunction {

	@Test
	public void testSin() {
		assertStriclyEquals(1, evalToDouble("sin(pi/2)"));
		assertStriclyEquals(0, evalToDouble("sin(pi)"));
	}

}

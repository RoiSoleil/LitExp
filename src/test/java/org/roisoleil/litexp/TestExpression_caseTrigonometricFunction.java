package org.roisoleil.litexp;

import static org.roisoleil.litexp.TestUtils.assertStriclyEquals;
import static org.roisoleil.litexp.TestUtils.evalToDouble;

import org.junit.Test;

public class TestExpression_caseTrigonometricFunction {

	@Test
	public void testSin() {
		assertStriclyEquals(1, evalToDouble("sin(pi/2)"));
		assertStriclyEquals(0, evalToDouble("sin(pi)"));
	}

}

package org.roisoleil.litexp;

import static org.junit.Assert.assertEquals;
import static org.roisoleil.litexp.TestUtils.assertStriclyEquals;
import static org.roisoleil.litexp.TestUtils.evalToDouble;
import static org.roisoleil.litexp.TestUtils.evalToString;

import org.junit.Test;

public class TestExpression_caseString {

	@Test
	public void testLitExp_caseIf() {
		assertStriclyEquals(5, evalToDouble("if(0 = 0, 5, 8)"));
		assertStriclyEquals(5, evalToDouble("if(\"a\" = \"a\", 5, 8)"));
		assertStriclyEquals(8, evalToDouble("if(\"a\" = \"b\", 5, 8)"));
		assertEquals("vvvv", evalToString("if(\"a\"=\"bsss\", \"tttt\", \"vvvv\")"));
		assertEquals("vvvv", evalToString("if(\"a\"=5, \"tttt\", \"vvvv\")"));
		assertEquals("tttt", evalToString("if(\"a\"=\"a\", \"tttt\", \"vvvv\")"));
	}

}

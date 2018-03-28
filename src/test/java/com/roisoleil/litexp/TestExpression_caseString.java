package com.roisoleil.litexp;

import static com.roisoleil.litexp.TestUtils.assertStriclyEquals;
import static com.roisoleil.litexp.TestUtils.evalToDouble;
import static com.roisoleil.litexp.TestUtils.evalToString;
import static org.junit.Assert.assertEquals;

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

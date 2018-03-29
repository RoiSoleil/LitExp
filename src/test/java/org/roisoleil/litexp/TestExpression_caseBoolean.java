package org.roisoleil.litexp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.roisoleil.litexp.TestUtils.assertStriclyEquals;
import static org.roisoleil.litexp.TestUtils.evalToBoolean;
import static org.roisoleil.litexp.TestUtils.evalToDouble;

import org.junit.Test;

public class TestExpression_caseBoolean {

	@Test
	public void testAndEval() {
		assertFalse(evalToBoolean("1&&0"));
		assertTrue(evalToBoolean("1&&1"));
		assertFalse(evalToBoolean("0&&0"));
		assertFalse(evalToBoolean("0&&1"));
	}

	@Test
	public void testOrEval() {
		assertTrue(evalToBoolean("1||0"));
		assertTrue(evalToBoolean("1||1"));
		assertFalse(evalToBoolean("0||0"));
		assertTrue(evalToBoolean("0||1"));
	}

	@Test
	public void testCompare() {
		assertTrue(evalToBoolean("2>1"));
		assertFalse(evalToBoolean("2<1"));
		assertFalse(evalToBoolean("1>2"));
		assertTrue(evalToBoolean("1<2"));
		assertFalse(evalToBoolean("1=2"));
		assertTrue(evalToBoolean("1=1"));
		assertTrue(evalToBoolean("1>=1"));
		assertTrue(evalToBoolean("1.1>=1"));
		assertFalse(evalToBoolean("1>=2"));
		assertTrue(evalToBoolean("1<=1"));
		assertFalse(evalToBoolean("1.1<=1"));
		assertTrue(evalToBoolean("1<=2"));
		assertFalse(evalToBoolean("1=2"));
		assertTrue(evalToBoolean("1=1"));
		assertTrue(evalToBoolean("1!=2"));
		assertFalse(evalToBoolean("1!=1"));
	}

	@Test
	public void testCompareCombined() {
		assertTrue(evalToBoolean("(2>1)||(1=0)"));
		assertFalse(evalToBoolean("(2>3)||(1=0)"));
		assertTrue(evalToBoolean("(2>3)||(1=0)||(1&&1)"));
	}

	@Test
	public void testMixed() {
		assertFalse(evalToBoolean("1.5 * 7 = 3"));
		assertTrue(evalToBoolean("1.5 * 7 = 10.5"));
	}

	@Test
	public void testNot() {
		assertFalse(evalToBoolean("not(1)"));
		assertTrue(evalToBoolean("not(0)"));
		assertTrue(evalToBoolean("not(1.5 * 7 = 3)"));
		assertFalse(evalToBoolean("not(1.5 * 7 = 10.5)"));
	}

	@Test
	public void testConstants() {
		assertTrue(evalToBoolean("TRUE!=FALSE"));
		assertFalse(evalToBoolean("TRUE==2"));
		assertTrue(evalToBoolean("NOT(TRUE)==FALSE"));
		assertTrue(evalToBoolean("NOT(FALSE)==TRUE"));
		assertFalse(evalToBoolean("TRUE && FALSE"));
		assertTrue(evalToBoolean("TRUE || FALSE"));
	}

	@Test
	public void testIf() {
		assertStriclyEquals(5, evalToDouble("if(TRUE, 5, 3)"));
		assertStriclyEquals(3, evalToDouble("IF(FALSE, 5, 3)"));
		assertStriclyEquals(5.35, evalToDouble("If(2, 5.35, 3)"));
	}

	@Test
	public void testDecimals() {
		assertFalse(evalToBoolean("if(0.0, 1, 0)"));
		assertFalse(evalToBoolean("0.0 || 0.0"));
		assertTrue(evalToBoolean("not(0.0)"));
		assertFalse(evalToBoolean("0.0 && 0.0"));
	}

}

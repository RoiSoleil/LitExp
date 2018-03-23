package com.roisoleil;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;

import org.junit.Test;

public class TestExpression {

	@Test
	public void testLitExp_caseAdd() {
		assertEquals(14, new Expression("5 + 9").eval(BigDecimal.class).doubleValue(), 0);
		assertEquals(14, new Expression("5+9").eval(BigDecimal.class).doubleValue(), 0);
		assertEquals(9, new Expression("0+9").eval(BigDecimal.class).doubleValue(), 0);
		assertEquals(0, new Expression("0+0").eval(BigDecimal.class).doubleValue(), 0);
		assertEquals(0.5, new Expression("0+0.5").eval(BigDecimal.class).doubleValue(), 0);
	}

	@Test
	public void testLitExp_caseSubstract() {
		assertEquals(-4, new Expression("5 - 9").eval(BigDecimal.class).doubleValue(), 0);
	}
	
	@Test
	public void testLitExp_caseMultiply() {
		assertEquals(45, new Expression("5 * 9").eval(BigDecimal.class).doubleValue(), 0);
		assertEquals(45, new Expression("9*5").eval(BigDecimal.class).doubleValue(), 0);
		assertEquals(0, new Expression("0 * 9").eval(BigDecimal.class).doubleValue(), 0);
		assertEquals(0, new Expression("0 * 0").eval(BigDecimal.class).doubleValue(), 0);
	}
}

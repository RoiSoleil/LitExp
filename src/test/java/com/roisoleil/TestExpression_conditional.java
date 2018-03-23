package com.roisoleil;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;

import org.junit.Test;

public class TestExpression_conditional {

//	@Test
	public void testLitExp_caseIf() {
		assertEquals(6, new Expression("if(0, 5, 6)").eval(BigDecimal.class).doubleValue(), 0);
	}

}

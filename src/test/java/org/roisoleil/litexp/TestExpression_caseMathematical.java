package org.roisoleil.litexp;

import static org.roisoleil.litexp.TestUtils.*;

import org.junit.Test;

public class TestExpression_caseMathematical {

  @Test
  public void testLitExp_caseAdd() {
    assertStriclyEquals(14, evalToDouble("5 + 9"));
    assertStriclyEquals(14, evalToDouble("5+9"));
    assertStriclyEquals(9, evalToDouble("0+9"));
    assertStriclyEquals(0, evalToDouble("0+0"));
    assertStriclyEquals(0.5, evalToDouble("0+0.5"));
  }

  @Test
  public void testLitExp_caseSubstract() {
    assertStriclyEquals(-4, evalToDouble("5 - 9"));
  }

  @Test
  public void testLitExp_caseMultiply() {
    assertStriclyEquals(45, evalToDouble("5 * 9"));
    assertStriclyEquals(45, evalToDouble("9*5"));
    assertStriclyEquals(0, evalToDouble("0 * 9"));
    assertStriclyEquals(0, evalToDouble("0 * 0"));
  }

}

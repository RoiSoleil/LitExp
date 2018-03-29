package org.roisoleil.litexp;

import java.math.BigDecimal;

import org.junit.Assert;
import org.roisoleil.litexp.Expression;

public class TestUtils {

  public static boolean evalToBoolean(String expression) {
    return new Expression(expression).eval(Boolean.class);
  }

  public static double evalToDouble(String expression) {
    return new Expression(expression).eval(BigDecimal.class).doubleValue();
  }

  public static String evalToString(String expression) {
    return new Expression(expression).eval(String.class);
  }

  public static void assertStriclyEquals(double expected, double actual) {
    Assert.assertEquals(expected, actual, 0);
  }

}

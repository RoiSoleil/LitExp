/*
 * MIT License
 * 
 * Copyright (c) 2018 Udo Klimaschewski, Hélios GILLES
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.roisoleil.litexp;

import static com.roisoleil.litexp.Expression.Utils.adapt;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class Expression {

	public static final int OPERATOR_PRECEDENCE_UNARY = 60;

	public static final int OPERATOR_PRECEDENCE_EQUALITY = 7;

	public static final int OPERATOR_PRECEDENCE_COMPARISON = 10;

	public static final int OPERATOR_PRECEDENCE_OR = 2;

	public static final int OPERATOR_PRECEDENCE_AND = 4;

	public static final int OPERATOR_PRECEDENCE_POWER = 40;

	public static final int OPERATOR_PRECEDENCE_MULTIPLICATIVE = 30;

	public static final int OPERATOR_PRECEDENCE_ADDITIVE = 20;

	private static final Operand PARAMS_START = new Operand() {

		@Override
		public Void getValue() {
			return null;
		}

		@Override
		public <U> U getValue(Class<U> valueClass) {
			return null;
		}

	};

	private final String originalExpression;

	private String firstVarChars = "_";

	private Map<String, Operator> operators = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	private Map<String, UnaryOperator> unaryOperators = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	private Map<String, Function> functions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	private Map<String, Operand> variables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	private OperandFactory operandFactory = new DefaultOperandFactory(this);

	private static final char decimalSeparator = '.';

	enum TokenType {
		FUNCTION, OPERATOR, UNARY_OPERATOR, NUMBER, STRING, VARIABLE, OPEN_BRACKET, CLOSE_BRACKET, COMMA
	}

	class Token {

		public TokenType type;

		public int rawStart;
		public String rawSurface = "";

		public int start;
		public String surface = "";

		public int end;

		public void append(char c) {
			surface += c;
		}

		public void append(String s) {
			surface += s;
		}

		public boolean isEmpty() {
			return surface.isEmpty();
		}

		@Override
		public String toString() {
			return Objects.toString(type) + ":" + surface;
		}

	}

	/**
	 * Expression tokenizer that allows to iterate over a {@link String} expression
	 * token by token. Blank characters will be skipped.
	 */
	public class Tokenizer implements Iterator<Token> {

		private int actualPosition = 0;
		private String input;
		private Token previousToken;

		public Tokenizer(String input) {
			this.input = input;
		}

		@Override
		public boolean hasNext() {
			return actualPosition < input.length();
		}

		private char peekNextChar() {
			return actualPosition < input.length() - 1 ? input.charAt(actualPosition + 1) : 0;
		}

		@Override
		public Token next() {
			if (actualPosition >= input.length()) {
				return previousToken = null;
			}
			// init
			Token token = new Token();
			token.rawStart = actualPosition;
			char ch = input.charAt(actualPosition);
			// pre-trim
			while (Character.isWhitespace(ch) && actualPosition < input.length()) {
				ch = input.charAt(++actualPosition);
			}
			token.start = actualPosition;
			// case number
			if (Character.isDigit(ch) || (ch == decimalSeparator && Character.isDigit(peekNextChar()))) {
				while (actualPosition < input.length() && (Character.isDigit(ch) || ch == decimalSeparator)) {
					if (!(++actualPosition < input.length()))
						break;
					ch = input.charAt(actualPosition);
				}
				token.rawSurface = input.substring(token.rawStart, actualPosition);
				token.surface = input.substring(token.start, actualPosition);
				token.type = TokenType.NUMBER;
			} else if (ch == '"') {
				actualPosition++;
				if (previousToken.type != TokenType.STRING) {
					ch = input.charAt(actualPosition);
					while (ch != '"') {
						token.append(input.charAt(actualPosition++));
						ch = actualPosition == input.length() ? 0 : input.charAt(actualPosition);
					}
					token.type = TokenType.STRING;
				} else {
					return next();
				}
			} else if (Character.isLetter(ch) || firstVarChars.indexOf(ch) >= 0) {
				while (actualPosition < input.length() && (Character.isLetter(ch) || Character.isDigit(ch)
						|| token.isEmpty() && firstVarChars.indexOf(ch) >= 0)) {
					token.append(input.charAt(actualPosition++));
					ch = actualPosition == input.length() ? 0 : input.charAt(actualPosition);
				}
				if (isSpace(ch)) {
					while (actualPosition < input.length() && isSpace(ch)) {
						ch = input.charAt(actualPosition++);
					}
					actualPosition--;
				}
				token.type = ch == '(' ? isFunctionOrOperator(token.surface) : TokenType.VARIABLE;
			} else if (ch == '(' || ch == ')' || ch == ',') {
				if (ch == '(') {
					token.type = TokenType.OPEN_BRACKET;
				} else if (ch == ')') {
					token.type = TokenType.CLOSE_BRACKET;
				} else {
					token.type = TokenType.COMMA;
				}
				token.append(ch);
				actualPosition++;
			} else {
				String greedyMatch = "";
				int initialPos = actualPosition;
				ch = input.charAt(actualPosition);
				int validOperatorSeenUntil = -1;
				while (!Character.isLetter(ch) && !Character.isDigit(ch) && firstVarChars.indexOf(ch) < 0
						&& !Character.isWhitespace(ch) && ch != '(' && ch != ')' && ch != ','
						&& (actualPosition < input.length())) {
					greedyMatch += ch;
					actualPosition++;
					if (operators.containsKey(greedyMatch)) {
						validOperatorSeenUntil = actualPosition;
					}
					ch = actualPosition == input.length() ? 0 : input.charAt(actualPosition);
				}
				if (validOperatorSeenUntil != -1) {
					token.append(input.substring(initialPos, validOperatorSeenUntil));
					actualPosition = validOperatorSeenUntil;
				} else {
					token.append(greedyMatch);
				}
				if (previousToken == null || previousToken.type == TokenType.OPERATOR
						|| previousToken.type == TokenType.OPEN_BRACKET || previousToken.type == TokenType.COMMA) {
					token.surface += "u";
					token.type = TokenType.UNARY_OPERATOR;
				} else {
					token.type = TokenType.OPERATOR;
				}
			}
			return previousToken = token;
		}

		private TokenType isFunctionOrOperator(String name) {
			return operators.containsKey(name) ? TokenType.OPERATOR : TokenType.FUNCTION;
		}

		@Override
		public void remove() {
			throw new LitExpException("remove() not supported");
		}

	}

	public Expression(String expression) {
		this.originalExpression = expression;
		initializeVariable();
		initializeFunction();
		initializeOperator();
		initializeUnaryOperator();
	}

	protected void initializeVariable() {
		setVariable("false", BigDecimal.ZERO);
		setVariable("true", BigDecimal.ONE);
		setVariable("pi", BigDecimal.valueOf(Math.PI));
		setVariable("e", BigDecimal.valueOf(Math.E));
	}

	protected void initializeFunction() {
		initializeBooleanFunction();
		initializeMathematicalFunction();
		initializeTrigonometricFunction();
	}

	protected void initializeBooleanFunction() {
		addFunction(new AbstractFunction(this, "if", 3) {
			@Override
			protected Object doEval(List<Operand> operands) {
				return Utils.getAndAssertNotNullOperandAtIndex(this, operands, 0, Boolean.class)
						? operands.get(1).getValue()
						: operands.get(2).getValue();
			}
		});
		addFunction(new AbstractFunction(this, "not", 1) {
			@Override
			protected Object doEval(List<Operand> operands) {
				return !Utils.getAndAssertNotNullOperandAtIndex(this, operands, 0, Boolean.class);
			}
		});
	}

	protected void initializeMathematicalFunction() {
		addFunction(new AbstractFunction(this, "max", -1) {
			@Override
			protected Object doEval(List<Operand> operands) {
				Utils.assertAtLeastOneOperand(this, operands);
				return IntStream.range(0, operands.size()).mapToObj(
						index -> Utils.getAndAssertNotNullOperandAtIndex(this, operands, index, BigDecimal.class))
						.max(Comparator.naturalOrder()).get();
			}
		});
		addFunction(new AbstractFunction(this, "min", -1) {
			@Override
			protected Object doEval(List<Operand> operands) {
				Utils.assertAtLeastOneOperand(this, operands);
				return IntStream.range(0, operands.size()).mapToObj(
						index -> Utils.getAndAssertNotNullOperandAtIndex(this, operands, index, BigDecimal.class))
						.min(Comparator.naturalOrder()).get();
			}
		});
	}

	protected void initializeTrigonometricFunction() {
		addFunction(new AbstractFunction(this, "sin", 1) {
			@Override
			protected Object doEval(List<Operand> operands) {
				return new BigDecimal(Math.sin(
						Utils.getAndAssertNotNullOperandAtIndex(this, operands, 0, BigDecimal.class).doubleValue()));
			}
		});
	}

	protected void initializeOperator() {
		addOperator(new AbstractOperator(this, "+", OPERATOR_PRECEDENCE_ADDITIVE, true) {
			@Override
			public Object doEval(Operand leftOperand, Operand rightOperand) {
				return leftOperand.getValue(BigDecimal.class).add(rightOperand.getValue(BigDecimal.class));
			}
		});
		addOperator(new AbstractOperator(this, "-", OPERATOR_PRECEDENCE_ADDITIVE, true) {
			@Override
			protected Object doEval(Operand leftOperand, Operand rightOperand) {
				return leftOperand.getValue(BigDecimal.class).subtract(rightOperand.getValue(BigDecimal.class));
			}
		});
		addOperator(new AbstractOperator(this, "*", OPERATOR_PRECEDENCE_MULTIPLICATIVE, true) {
			@Override
			protected Object doEval(Operand leftOperand, Operand rightOperand) {
				return leftOperand.getValue(BigDecimal.class).multiply(rightOperand.getValue(BigDecimal.class));
			}
		});
		addOperator(new AbstractOperator(this, "/", OPERATOR_PRECEDENCE_MULTIPLICATIVE, true) {
			@Override
			protected Object doEval(Operand leftOperand, Operand rightOperand) {
				return leftOperand.getValue(BigDecimal.class).divide(rightOperand.getValue(BigDecimal.class));
			}
		});
		// addOperator(new AbstractOperator<BigDecimal>("/",
		// OPERATOR_PRECEDENCE_MULTIPLICATIVE, true) {
		//
		// @Override
		// public BigDecimal doEval(Operand<?> leftOperand, Operand<?> rightOperand) {
		// return
		// leftOperand.getValue(BigDecimal.class).divide(rightOperand.getValue(BigDecimal.class));
		// }
		//
		// });
		// addOperator(new AbstractOperator<BigDecimal>("%",
		// OPERATOR_PRECEDENCE_MULTIPLICATIVE, true) {
		//
		// @Override
		// public BigDecimal doEval(Operand<?> leftOperand, Operand<?> rightOperand) {
		// return
		// leftOperand.getValue(BigDecimal.class).remainder(rightOperand.getValue(BigDecimal.class));
		// }
		//
		// });
		// addOperator(new AbstractOperator<BigDecimal>("^", OPERATOR_PRECEDENCE_POWER,
		// false) {
		//
		// @Override
		// public BigDecimal doEval(Operand<?> leftOperand, Operand<?> rightOperand) {
		// /*-
		// * Thanks to Gene Marin:
		// *
		// http://stackoverflow.com/questions/3579779/how-to-do-a-fractional-power-on-bigdecimal-in-java
		// */
		// BigDecimal v1 = leftOperand.getValue(BigDecimal.class);
		// BigDecimal v2 = leftOperand.getValue(BigDecimal.class);
		// int signOf2 = v2.signum();
		// double dn1 = v1.doubleValue();
		// v2 = v2.multiply(new BigDecimal(signOf2)); // n2 is now positive
		// BigDecimal remainderOf2 = v2.remainder(BigDecimal.ONE);
		// BigDecimal n2IntPart = v2.subtract(remainderOf2);
		// BigDecimal intPow = v1.pow(n2IntPart.intValueExact());
		// BigDecimal doublePow = new BigDecimal(Math.pow(dn1,
		// remainderOf2.doubleValue()));
		//
		// BigDecimal result = intPow.multiply(doublePow);
		// if (signOf2 == -1) {
		// result = BigDecimal.ONE.divide(result, RoundingMode.HALF_UP);
		// }
		// return result;
		// }
		// });
		// addOperator(new AbstractOperator<Boolean>("and", OPERATOR_PRECEDENCE_AND,
		// false) {
		//
		// @Override
		// public Operand<Boolean> lazyEval(Operand<?> leftLazyOperand, Operand<?>
		// rightLazyOperand) {
		// Boolean result = Boolean.TRUE.equals(leftLazyOperand.getValue(Boolean.class))
		// && Boolean.TRUE.equals(rightLazyOperand.getValue(Boolean.class));
		// return operandFactory.createOperand(this, leftLazyOperand, rightLazyOperand,
		// result);
		// }
		//
		// });
		// addOperator(new AbstractOperator<Boolean>("&&", OPERATOR_PRECEDENCE_AND,
		// false) {
		//
		// @Override
		// public Operand<Boolean> lazyEval(Operand<?> leftLazyOperand, Operand<?>
		// rightLazyOperand) {
		// return (Operand<Boolean>) operators.get("and").lazyEval(leftLazyOperand,
		// rightLazyOperand);
		// }
		//
		// });
		// addOperator(new AbstractOperator<Boolean>("or", OPERATOR_PRECEDENCE_OR,
		// false) {
		//
		// @Override
		// public Operand<Boolean> lazyEval(Operand<?> leftLazyOperand, Operand<?>
		// rightLazyOperand) {
		// Boolean result = Boolean.TRUE.equals(leftLazyOperand.getValue(Boolean.class))
		// || Boolean.TRUE.equals(rightLazyOperand.getValue(Boolean.class));
		// return operandFactory.createOperand(this, leftLazyOperand, rightLazyOperand,
		// result);
		// }
		//
		// });
		// addOperator(new AbstractOperator<Boolean>("||", OPERATOR_PRECEDENCE_AND,
		// false) {
		//
		// @Override
		// public Operand<Boolean> lazyEval(Operand<?> leftLazyOperand, Operand<?>
		// rightLazyOperand) {
		// return (Operand<Boolean>) operators.get("or").lazyEval(leftLazyOperand,
		// rightLazyOperand);
		// }
		//
		// });
		// addOperator(new AbstractOperator<Boolean>("==", OPERATOR_PRECEDENCE_EQUALITY,
		// false) {
		//
		// @Override
		// public Operand<Boolean> eval(Operand<?> leftOperand, Operand<?> rightOperand)
		// {
		// return (Operand<Boolean>) operators.get("=").eval(leftOperand, rightOperand);
		// }
		//
		// });
		// addOperator(new AbstractOperator<Boolean>("!=", OPERATOR_PRECEDENCE_EQUALITY,
		// false) {
		//
		// @Override
		// public Operand<Boolean> eval(Operand<?> leftOperand, Operand<?> rightOperand)
		// {
		// BigDecimal v1 = leftOperand.getValue(BigDecimal.class);
		// BigDecimal v2 = rightOperand.getValue(BigDecimal.class);
		// Boolean result = null;
		// if (v1 == v2) {
		// result = Boolean.FALSE;
		// } else if (v1 == null || v2 == null) {
		// result = Boolean.TRUE;
		// } else {
		// result = Boolean.valueOf(v1.compareTo(v2) != 0);
		// }
		// return operandFactory.createOperand(this, leftOperand, rightOperand, result);
		// }
		//
		// });
		// addOperator(new AbstractOperator<Boolean>("<>", OPERATOR_PRECEDENCE_EQUALITY,
		// false) {
		//
		// @Override
		// public Operand<Boolean> eval(Operand<?> leftOperand, Operand<?> rightOperand)
		// {
		// return (Operand<Boolean>) operators.get("!=").eval(leftOperand,
		// rightOperand);
		// }
		//
		// });
		// addOperator(new AbstractOperator<Boolean>(">",
		// OPERATOR_PRECEDENCE_COMPARISON, false) {
		// @Override
		// public Operand<Boolean> eval(Operand<?> leftOperand, Operand<?> rightOperand)
		// {
		// BigDecimal v1 = leftOperand.getValue(BigDecimal.class);
		// BigDecimal v2 = rightOperand.getValue(BigDecimal.class);
		// Boolean result = Boolean.valueOf(v1.compareTo(v2) == 1);
		// return operandFactory.createOperand(this, leftOperand, rightOperand, result);
		// }
		// });
		// addOperator(new AbstractOperator<Boolean>(">=",
		// OPERATOR_PRECEDENCE_COMPARISON, false) {
		// @Override
		// public Operand<Boolean> eval(Operand<?> leftOperand, Operand<?> rightOperand)
		// {
		// BigDecimal v1 = leftOperand.getValue(BigDecimal.class);
		// BigDecimal v2 = rightOperand.getValue(BigDecimal.class);
		// Boolean result = Boolean.valueOf(v1.compareTo(v2) >= 0);
		// return operandFactory.createOperand(this, leftOperand, rightOperand, result);
		// }
		// });
		// addOperator(new AbstractOperator<Boolean>("<",
		// OPERATOR_PRECEDENCE_COMPARISON, false) {
		// @Override
		// public Operand<Boolean> eval(Operand<?> leftOperand, Operand<?> rightOperand)
		// {
		// BigDecimal v1 = leftOperand.getValue(BigDecimal.class);
		// BigDecimal v2 = rightOperand.getValue(BigDecimal.class);
		// Boolean result = Boolean.valueOf(v1.compareTo(v2) == -1);
		// return operandFactory.createOperand(this, leftOperand, rightOperand, result);
		// }
		// });
		// addOperator(new AbstractOperator<Boolean>("<=",
		// OPERATOR_PRECEDENCE_COMPARISON, false) {
		// @Override
		// public Operand<Boolean> eval(Operand<?> leftOperand, Operand<?> rightOperand)
		// {
		// BigDecimal v1 = leftOperand.getValue(BigDecimal.class);
		// BigDecimal v2 = rightOperand.getValue(BigDecimal.class);
		// Boolean result = Boolean.valueOf(v1.compareTo(v2) <= 0);
		// return operandFactory.createOperand(this, leftOperand, rightOperand, result);
		// }
		// });
		initializeBooleanOperator();
	}

	protected void initializeBooleanOperator() {
		addOperator(new AbstractOperator(this, "=", OPERATOR_PRECEDENCE_EQUALITY, false) {
			@Override
			protected Object doEval(Operand leftOperand, Operand rightOperand) {
				return Utils.equals(leftOperand, rightOperand);
			}
		});
		addOperator(new ProxyOperator("==", operators.get("=")));
		addOperator(new AbstractOperator(this, "!=", OPERATOR_PRECEDENCE_EQUALITY, false) {
			@Override
			protected Object doEval(Operand leftOperand, Operand rightOperand) {
				return !Utils.equals(leftOperand, rightOperand);
			}
		});
		addOperator(new ProxyOperator("<>", operators.get("!=")));
		addOperator(new AbstractOperator(this, "&&", OPERATOR_PRECEDENCE_AND, false) {
			@Override
			protected Object doEval(Operand leftOperand, Operand rightOperand) {
				Boolean leftValue = Utils.getAndAssertNotNullLeftOperand(this, leftOperand, Boolean.class);
				Boolean rightValue = Utils.getAndAssertNotNullRightOperand(this, rightOperand, Boolean.class);
				return leftValue.booleanValue() && rightValue.booleanValue();
			}
		});
		addOperator(new ProxyOperator("and", operators.get("&&")));
		addOperator(new AbstractOperator(this, "||", OPERATOR_PRECEDENCE_OR, false) {
			@Override
			protected Object doEval(Operand leftOperand, Operand rightOperand) {
				Boolean leftValue = Utils.getAndAssertNotNullLeftOperand(this, leftOperand, Boolean.class);
				Boolean rightValue = Utils.getAndAssertNotNullRightOperand(this, rightOperand, Boolean.class);
				return leftValue.booleanValue() || rightValue.booleanValue();
			}
		});
		addOperator(new ProxyOperator("or", operators.get("&&")));
		addOperator(new AbstractOperator(this, "<", OPERATOR_PRECEDENCE_COMPARISON, false) {
			@Override
			protected Object doEval(Operand leftOperand, Operand rightOperand) {
				BigDecimal leftValue = Utils.getAndAssertNotNullLeftOperand(this, leftOperand, BigDecimal.class);
				BigDecimal rightValue = Utils.getAndAssertNotNullRightOperand(this, rightOperand, BigDecimal.class);
				return leftValue.compareTo(rightValue) == -1;
			}
		});
		addOperator(new AbstractOperator(this, ">", OPERATOR_PRECEDENCE_COMPARISON, false) {
			@Override
			protected Object doEval(Operand leftOperand, Operand rightOperand) {
				BigDecimal leftValue = Utils.getAndAssertNotNullLeftOperand(this, leftOperand, BigDecimal.class);
				BigDecimal rightValue = Utils.getAndAssertNotNullRightOperand(this, rightOperand, BigDecimal.class);
				return leftValue.compareTo(rightValue) == 1;
			}
		});
		addOperator(new AbstractOperator(this, "<=", OPERATOR_PRECEDENCE_COMPARISON, false) {
			@Override
			protected Object doEval(Operand leftOperand, Operand rightOperand) {
				BigDecimal leftValue = Utils.getAndAssertNotNullLeftOperand(this, leftOperand, BigDecimal.class);
				BigDecimal rightValue = Utils.getAndAssertNotNullRightOperand(this, rightOperand, BigDecimal.class);
				return leftValue.compareTo(rightValue) != 1;
			}
		});
		addOperator(new AbstractOperator(this, ">=", OPERATOR_PRECEDENCE_COMPARISON, false) {
			@Override
			protected Object doEval(Operand leftOperand, Operand rightOperand) {
				BigDecimal leftValue = Utils.getAndAssertNotNullLeftOperand(this, leftOperand, BigDecimal.class);
				BigDecimal rightValue = Utils.getAndAssertNotNullRightOperand(this, rightOperand, BigDecimal.class);
				return leftValue.compareTo(rightValue) != -1;
			}
		});
	}

	protected void initializeUnaryOperator() {
		addUnaryOperator(new AbstractUnaryOperator(this, "-", OPERATOR_PRECEDENCE_UNARY) {
			@Override
			protected Object doEval(Operand operand) {
				return Utils.getAndAssertNotNullOperand(this, operand, BigDecimal.class).multiply(new BigDecimal(-1));
			}
		});
		addUnaryOperator(new AbstractUnaryOperator(this, "+", OPERATOR_PRECEDENCE_UNARY) {
			@Override
			protected Object doEval(Operand operand) {
				return Utils.getAndAssertNotNullOperand(this, operand, BigDecimal.class);
			}
		});
	}

	private List<Token> shuntingYard(String expression) {
		List<Token> outputQueue = new ArrayList<>();
		Stack<Token> stack = new Stack<>();
		Tokenizer tokenizer = new Tokenizer(expression);
		Token lastFunction = null;
		Token previousToken = null;
		while (tokenizer.hasNext()) {
			Token token = tokenizer.next();
			switch (token.type) {
			case STRING:
			case NUMBER:
			case VARIABLE:
				outputQueue.add(token);
				break;
			case FUNCTION:
				stack.push(token);
				lastFunction = token;
				break;
			case COMMA:
				if (previousToken != null && previousToken.type == TokenType.OPERATOR) {
					throw new LitExpException("Missing parameter(s) for operator " + previousToken
							+ " at character position " + previousToken.end);
				}
				while (!stack.isEmpty() && stack.peek().type != TokenType.OPEN_BRACKET) {
					outputQueue.add(stack.pop());
				}
				if (stack.isEmpty()) {
					if (lastFunction == null) {
						throw new LitExpException("Unexpected comma at character position " + token.end);
					}
					throw new LitExpException(
							"Parse error for function '" + lastFunction + "' at character position " + token.end);
				}
				break;
			case OPERATOR: {
				if (previousToken != null
						&& (previousToken.type == TokenType.COMMA || previousToken.type == TokenType.OPEN_BRACKET)) {
					throw new LitExpException(
							"Missing parameter(s) for operator " + token + " at character position " + token.end);
				}
				Operator o1 = operators.get(token.surface);
				if (o1 == null) {
					throw new LitExpException("Unknown operator '" + token + "' at position " + (token.end + 1));
				}

				shuntOperators(outputQueue, stack, o1);
				stack.push(token);
				break;
			}
			case UNARY_OPERATOR: {
				if (previousToken != null && previousToken.type != TokenType.OPERATOR
						&& previousToken.type != TokenType.COMMA && previousToken.type != TokenType.OPEN_BRACKET) {
					throw new LitExpException(
							"Invalid position for unary operator " + token + " at character position " + token.end);
				}
				UnaryOperator o1 = unaryOperators.get(token.surface);
				if (o1 == null) {
					throw new LitExpException(
							"Unknown unary operator '" + token.surface.substring(0, token.surface.length() - 1)
									+ "' at position " + (token.end + 1));
				}

				shuntOperators(outputQueue, stack, o1);
				stack.push(token);
				break;
			}
			case OPEN_BRACKET:
				if (previousToken != null) {
					if (previousToken.type == TokenType.NUMBER || previousToken.type == TokenType.CLOSE_BRACKET
							|| previousToken.type == TokenType.VARIABLE) {
						// Implicit multiplication, e.g. 23(a+b) or (a+b)(a-b)
						Token multiplication = new Token();
						multiplication.append("*");
						multiplication.type = TokenType.OPERATOR;
						stack.push(multiplication);
					}
					// if the ( is preceded by a valid function, then it
					// denotes the start of a parameter list
					if (previousToken.type == TokenType.FUNCTION) {
						outputQueue.add(token);
					}
				}
				stack.push(token);
				break;
			case CLOSE_BRACKET:
				if (previousToken != null && previousToken.type == TokenType.OPERATOR) {
					throw new LitExpException("Missing parameter(s) for operator " + previousToken
							+ " at character position " + previousToken.end);
				}
				while (!stack.isEmpty() && stack.peek().type != TokenType.OPEN_BRACKET) {
					outputQueue.add(stack.pop());
				}
				if (stack.isEmpty()) {
					throw new LitExpException("Mismatched parentheses");
				}
				stack.pop();
				if (!stack.isEmpty() && stack.peek().type == TokenType.FUNCTION) {
					outputQueue.add(stack.pop());
				}
			}
			previousToken = token;
		}
		while (!stack.isEmpty()) {
			Token element = stack.pop();
			if (element.type == TokenType.OPEN_BRACKET || element.type == TokenType.CLOSE_BRACKET) {
				throw new LitExpException("Mismatched parentheses");
			}
			outputQueue.add(element);
		}
		return outputQueue;
	}

	private void shuntOperators(List<Token> outputQueue, Stack<Token> stack, Operator o1) {
		Expression.Token nextToken = stack.isEmpty() ? null : stack.peek();
		while (nextToken != null
				&& (nextToken.type == Expression.TokenType.OPERATOR
						|| nextToken.type == Expression.TokenType.UNARY_OPERATOR)
				&& ((o1.isLeftAssociative() && o1.getPrecedence() <= operators.get(nextToken.surface).getPrecedence())
						|| (o1.getPrecedence() < operators.get(nextToken.surface).getPrecedence()))) {
			outputQueue.add(stack.pop());
			nextToken = stack.isEmpty() ? null : stack.peek();
		}
	}

	private void shuntOperators(List<Token> outputQueue, Stack<Token> stack, UnaryOperator o1) {
		Expression.Token nextToken = stack.isEmpty() ? null : stack.peek();
		while (nextToken != null
				&& (nextToken.type == Expression.TokenType.OPERATOR
						|| nextToken.type == Expression.TokenType.UNARY_OPERATOR)
				&& ((o1.getPrecedence() <= operators.get(nextToken.surface).getPrecedence())
						|| (o1.getPrecedence() < operators.get(nextToken.surface).getPrecedence()))) {
			outputQueue.add(stack.pop());
			nextToken = stack.isEmpty() ? null : stack.peek();
		}
	}

	/**
	 * Evaluates the expression.
	 * 
	 * @param stripTrailingZeros
	 *            If set to <code>true</code> trailing zeros in the result are
	 *            stripped.
	 * 
	 * @return The result of the expression.
	 */
	public Operand eval() {
		Deque<Operand> stack = new ArrayDeque<>();
		Operand result = null;
		for (final Token token : getRPN()) {
			switch (token.type) {
			case UNARY_OPERATOR: {
				Operand value = stack.pop();
				result = unaryOperators.get(token.surface).eval(value);
				stack.push(result);
				break;
			}
			case OPERATOR:
				Operand rightValue = stack.pop();
				Operand leftValue = stack.pop();
				result = operators.get(token.surface).eval(leftValue, rightValue);
				stack.push(result);
				break;
			case VARIABLE:
				result = operandFactory.createOperand(token, () -> {
					if (!variables.containsKey(token.surface)) {
						throw new LitExpException("Unknown variable: " + token);
					}
					return variables.get(token.surface).getValue();
				});
				stack.push(result);
				break;
			case FUNCTION:
				Function function = functions.get(token.surface.toUpperCase(Locale.ROOT));
				List<Operand> arguments = new ArrayList<>(
						function.isVariableArguments() ? 0 : function.getNumberArguments());
				while (!stack.isEmpty() && stack.peek() != PARAMS_START) {
					arguments.add(0, stack.pop());
				}
				if (stack.peek() == PARAMS_START) {
					stack.pop();
				}
				result = function.eval(arguments);
				stack.push(result);
				break;
			case OPEN_BRACKET:
				stack.push(PARAMS_START);
				break;
			case NUMBER:
				result = operandFactory.createOperand(token, () -> token.surface);
				stack.push(result);
				break;
			case STRING:
				result = operandFactory.createOperand(token, () -> token.surface);
				stack.push(result);
				break;
			default:
				throw new LitExpException(
						"Unexpected token '" + token.surface + "' at character position " + token.end);
			}
		}
		return stack.pop();
	}

	public <T> T eval(Class<T> resultClass) {
		return eval().getValue(resultClass);
	}

	public Expression setFirstVariableCharacters(String chars) {
		this.firstVarChars = chars;
		return this;
	}

	public Operator addOperator(Operator operator) {
		return operators.put(operator.getOperator(), operator);
	}

	public UnaryOperator addUnaryOperator(UnaryOperator unaryOperator) {
		return unaryOperators.put(unaryOperator.getOperator() + "u", unaryOperator);
	}

	public Function addFunction(Function function) {
		return functions.put(function.getName(), function);
	}

	public void setOperandFactory(OperandFactory operandFactory) {
		this.operandFactory = operandFactory;
	}

	public OperandFactory getOperandFactory() {
		return operandFactory;
	}

	public Expression setVariable(String variable, Object value) {
		variables.put(variable, operandFactory.createOperand(() -> value));
		return this;
	}

	public Expression with(String variable, Object value) {
		return setVariable(variable, value);
	}

	public Iterator<Token> getExpressionTokenizer() {
		return new Tokenizer(originalExpression);
	}

	/**
	 * Cached access to the RPN notation of this expression, ensures only one
	 * calculation of the RPN per expression instance. If no cached instance exists,
	 * a new one will be created and put to the cache.
	 * 
	 * @return The cached RPN instance.
	 */
	private List<Token> getRPN() {
		List<Token> rpn = shuntingYard(originalExpression);
		validate(rpn);
		return rpn;
	}

	/**
	 * Check that the expression has enough numbers and variables to fit the
	 * requirements of the operators and functions, also check for only 1 result
	 * stored at the end of the evaluation.
	 */
	private void validate(List<Token> rpn) {
		Stack<Integer> stack = new Stack<>();
		stack.push(0);
		for (final Token token : rpn) {
			switch (token.type) {
			case UNARY_OPERATOR:
				if (stack.peek() < 1) {
					throw new LitExpException("Missing parameter(s) for unary operator " + token);
				}
				break;
			case OPERATOR:
				if (stack.peek() < 2) {
					throw new LitExpException("Missing parameter(s) for operator " + token);
				}
				// pop the operator's 2 parameters and add the result
				stack.set(stack.size() - 1, stack.peek() - 2 + 1);
				break;
			case FUNCTION:
				Function function = functions.get(token.surface.toUpperCase(Locale.ROOT));
				if (function == null) {
					throw new LitExpException("Unknown function '" + token + "' at position " + (token.end + 1));
				}

				int numberArguments = stack.pop();
				if (!function.isVariableArguments() && numberArguments != function.getNumberArguments()) {
					throw new LitExpException("Function " + token + " expected " + function.getNumberArguments()
							+ " parameters, got " + numberArguments);
				}
				if (stack.size() <= 0) {
					throw new LitExpException("Too many function calls, maximum scope exceeded");
				}
				// push the result of the function
				stack.set(stack.size() - 1, stack.peek() + 1);
				break;
			case OPEN_BRACKET:
				stack.push(0);
				break;
			default:
				stack.set(stack.size() - 1, stack.peek() + 1);
			}
		}
		if (stack.size() > 1) {
			throw new LitExpException("Too many unhandled function parameter lists");
		} else if (stack.peek() > 1) {
			throw new LitExpException("Too many numbers or variables");
		} else if (stack.peek() < 1) {
			throw new LitExpException("Empty expression");
		}
	}

	/**
	 * Get a string representation of the RPN (Reverse Polish Notation) for this
	 * expression.
	 * 
	 * @return A string with the RPN representation for this expression.
	 */
	public String toRPN() {
		StringBuilder result = new StringBuilder();
		for (Token t : getRPN()) {
			if (result.length() != 0) {
				result.append(" ");
			}
			result.append(t.toString());
		}
		return result.toString();
	}

	private static boolean isSpace(char c) {
		return ' ' == c || '\u00A0' == c;
	}

	public static class LitExpException extends RuntimeException {

		private static final long serialVersionUID = 4579617239233899089L;

		public LitExpException(String message) {
			super(message);
		}

	}

	public interface Operand {

		Object getValue();

		<U> U getValue(Class<U> valueClass);

	}

	public static abstract class AbstractOperand implements Operand {

		protected Expression litExp;

		private Object value;

		public AbstractOperand(Expression litExp, Object value) {
			this.litExp = litExp;
			this.value = value;
		}

		@Override
		public Object getValue() {
			return value;
		}

	}

	public static abstract class AbstractLazyOperand implements Operand {

		protected Expression litExp;

		private boolean evaluated = false;
		private Object value;

		public AbstractLazyOperand(Expression litExp) {
			this.litExp = litExp;
		}

		@Override
		public final Object getValue() {
			eval();
			return value;
		}

		@Override
		public final <U> U getValue(Class<U> valueClass) {
			eval();
			return doGetValue(valueClass);
		}

		protected void eval() {
			if (!evaluated) {
				evaluated = true;
				value = doEval();
			}
		}

		protected <U> U doGetValue(Class<U> valueClass) {
			return (U) (value != null && valueClass.equals(value.getClass()) ? value : null);
		}

		protected abstract Object doEval();

	}

	public interface Function {

		String getName();

		int getNumberArguments();

		default boolean isVariableArguments() {
			return getNumberArguments() < 0;
		}

		Operand eval(List<Operand> operands);

	}

	public static class ProxyFunction implements Function {

		protected Function function;

		private String name;

		public ProxyFunction(String name, Function function) {
			this.name = name;
			this.function = function;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public int getNumberArguments() {
			return function.getNumberArguments();
		}

		@Override
		public boolean isVariableArguments() {
			return function.isVariableArguments();
		}

		@Override
		public Operand eval(List<Operand> operands) {
			return function.eval(operands);
		}

	}

	public static abstract class AbstractFunction implements Function {

		protected Expression litExp;

		private String name;
		private int numberArguments;

		public AbstractFunction(Expression litExp, String name, int numberArguments) {
			this.litExp = litExp;
			this.name = name;
			this.numberArguments = numberArguments;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public int getNumberArguments() {
			return numberArguments;
		}

		@Override
		public final Operand eval(List<Operand> operands) {
			return litExp.getOperandFactory().createOperand(this, operands, () -> doEval(operands));
		}

		protected abstract Object doEval(List<Operand> operands);

	}

	public interface Operator {

		String getOperator();

		int getPrecedence();

		boolean isLeftAssociative();

		Operand eval(Operand leftOperand, Operand rightOperand);

	}

	public static class ProxyOperator implements Operator {

		protected Operator operator;

		private String name;

		public ProxyOperator(String name, Operator operator) {
			this.name = name;
			this.operator = operator;
		}

		@Override
		public String getOperator() {
			return name;
		}

		@Override
		public int getPrecedence() {
			return operator.getPrecedence();
		}

		@Override
		public boolean isLeftAssociative() {
			return operator.isLeftAssociative();
		}

		@Override
		public Operand eval(Operand leftOperand, Operand rightOperand) {
			return operator.eval(leftOperand, rightOperand);
		}

	}

	public static abstract class AbstractOperator implements Operator {

		protected Expression expression;

		private String operator;
		private int precedence;
		private boolean leftAssociative;

		public AbstractOperator(Expression litExp, String operator, int precedence, boolean leftAssociative) {
			this.expression = litExp;
			this.operator = operator;
			this.precedence = precedence;
			this.leftAssociative = leftAssociative;
		}

		@Override
		public String getOperator() {
			return operator;
		}

		@Override
		public int getPrecedence() {
			return precedence;
		}

		@Override
		public boolean isLeftAssociative() {
			return leftAssociative;
		}

		@Override
		public final Operand eval(Operand leftOperand, Operand rightOperand) {
			return expression.getOperandFactory().createOperand(this, leftOperand, rightOperand,
					() -> doEval(leftOperand, rightOperand));
		}

		protected abstract Object doEval(Operand leftOperand, Operand rightOperand);

	}

	public interface UnaryOperator {

		String getOperator();

		int getPrecedence();

		Operand eval(Operand operand);

	}

	public static class ProxyUnaryOperator implements UnaryOperator {

		protected UnaryOperator unaryOperator;

		private String name;

		public ProxyUnaryOperator(String name, UnaryOperator unaryOperator) {
			this.name = name;
			this.unaryOperator = unaryOperator;
		}

		@Override
		public String getOperator() {
			return name;
		}

		@Override
		public int getPrecedence() {
			return unaryOperator.getPrecedence();
		}

		@Override
		public Operand eval(Operand operand) {
			return unaryOperator.eval(operand);
		}

	}

	public static abstract class AbstractUnaryOperator implements UnaryOperator {

		protected Expression litExp;

		private String operator;
		private int precedence;

		public AbstractUnaryOperator(Expression litExp, String operator, int precedence) {
			this.litExp = litExp;
			this.operator = operator;
			this.precedence = precedence;
		}

		@Override
		public String getOperator() {
			return operator;
		}

		@Override
		public int getPrecedence() {
			return precedence;
		}

		@Override
		public final Operand eval(Operand operand) {
			return litExp.getOperandFactory().createOperand(this, operand, () -> doEval(operand));
		}

		protected abstract Object doEval(Operand operand);

	}

	public interface OperandFactory {

		Operand createOperand(Supplier<Object> valueSupplier);

		Operand createOperand(Token token, Supplier<Object> valueSupplier);

		Operand createOperand(Function function, List<Operand> operands, Supplier<Object> valueSupplier);

		Operand createOperand(Operator operator, Operand leftOperand, Operand rightOperand,
				Supplier<Object> valueSupplier);

		Operand createOperand(UnaryOperator unaryOperator, Operand operand, Supplier<Object> valueSupplier);

	}

	public static class DefaultOperandFactory implements OperandFactory {

		private Expression expression;

		public DefaultOperandFactory(Expression expression) {
			this.expression = expression;
		}

		@Override
		public Operand createOperand(Supplier<Object> valueSupplier) {
			return createDefaultOperand(valueSupplier);
		}

		@Override
		public Operand createOperand(Token token, Supplier<Object> valueSupplier) {
			if (TokenType.NUMBER == token.type) {
				return createDefaultOperand(() -> new BigDecimal(Objects.toString(valueSupplier.get())));
			} else if (TokenType.STRING == token.type) {
				return createDefaultOperand(() -> Objects.toString(valueSupplier.get()));
			}
			return createDefaultLazyOperand(valueSupplier);
		}

		@Override
		public Operand createOperand(Function function, List<Operand> operands, Supplier<Object> valueSupplier) {
			return createDefaultLazyOperand(valueSupplier);
		}

		@Override
		public Operand createOperand(Operator operator, Operand leftOperand, Operand rightOperand,
				Supplier<Object> valueSupplier) {
			return createDefaultLazyOperand(valueSupplier);
		}

		@Override
		public Operand createOperand(UnaryOperator unaryOperator, Operand operand, Supplier<Object> valueSupplier) {
			return createDefaultLazyOperand(valueSupplier);
		}

		protected Operand createDefaultOperand(Supplier<Object> valueSupplier) {
			return new AbstractOperand(expression, valueSupplier.get()) {
				@Override
				public <U> U getValue(Class<U> valueClass) {
					return adapt(getValue(), valueClass);
				}
			};
		}

		protected Operand createDefaultLazyOperand(Supplier<Object> valueSupplier) {
			return new AbstractLazyOperand(expression) {
				@Override
				protected Object doEval() {
					return valueSupplier.get();
				}

				@Override
				protected <U> U doGetValue(Class<U> valueClass) {
					return adapt(getValue(), valueClass);
				}
			};
		}

	}

	public static class Utils {

		public static void assertAtLeastOneOperand(Function function, List<Operand> operands) {
			if (operands.isEmpty())
				throw new LitExpException(
						String.format("no operand specified for function \"%s\"", function.getName()));
		}

		public static <U> U getAndAssertNotNullOperandAtIndex(Function function, List<Operand> operands, int index,
				Class<U> clazz) {
			return assertNotNull(index >= 0 && index < operands.size() ? operands.get(index).getValue(clazz) : null,
					"operand %d can't be null for function %s", index, function.getName());
		}

		public static <U> U getAndAssertNotNullLeftOperand(Operator operator, Operand leftOperand, Class<U> clazz) {
			return assertNotNull(leftOperand.getValue(clazz), "left operand can't be null for operator %s",
					operator.getOperator());
		}

		public static <U> U getAndAssertNotNullRightOperand(Operator operator, Operand rightOperand, Class<U> clazz) {
			return assertNotNull(rightOperand.getValue(clazz), "right operand can't be null for operator %s",
					operator.getOperator());
		}

		public static <U> U getAndAssertNotNullOperand(UnaryOperator unaryOperator, Operand operand, Class<U> clazz) {
			return assertNotNull(operand.getValue(clazz), "operand can't be null for unary operator %s",
					unaryOperator.getOperator());
		}

		private static <U> U assertNotNull(U u, String message, Object... data) {
			if (u == null)
				throw new LitExpException(String.format(message, data));
			return u;
		}

		public static <U> U adapt(Object value, Class<U> clazz) {
			if (value == null) {
				return (U) value;
			}
			if (clazz.isInstance(value)) {
				return (U) value;
			}
			if (Boolean.class.equals(clazz) || boolean.class.equals(clazz)) {
				if (value instanceof Number) {
					return (U) Boolean.valueOf(((Number) value).doubleValue() != 0.0);
				}
			} else if (Number.class.isAssignableFrom(clazz)) {
				if (value instanceof Boolean) {
					return (U) (((Boolean) value).booleanValue() ? BigDecimal.ONE : BigDecimal.ZERO);
				} else if (value instanceof String) {
					return (U) new BigDecimal((String) value);
				}
			} else if (String.class.equals(clazz)) {
				return (U) Objects.toString(value);
			}
			return null;
		}

		public static boolean equals(Operand leftOperand, Operand rightOperand) {
			Object leftValue = leftOperand.getValue();
			Object rightValue = rightOperand.getValue();
			return Objects.equals(leftValue, rightValue) || tryEquals(leftOperand, rightOperand, leftValue)
					|| tryEquals(leftOperand, rightOperand, rightValue);
		}

		private static boolean tryEquals(Operand leftOperand, Operand rightOperand, Object value) {
			if (value == null) {
				return false;
			}
			Class<?> clazz = value.getClass();
			try {
				Object leftValue = leftOperand.getValue(clazz);
				Object rightValue = rightOperand.getValue(clazz);
				return leftValue != null && rightValue != null && Objects.equals(leftValue, rightValue);
			} catch (Exception exception) {
				return false;
			}
		}

	}

}

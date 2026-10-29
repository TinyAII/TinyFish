package com.tinyaii.tinyfish.util;

/**
 * 极简四则运算求值器（递归下降）。
 *
 * <p>为什么要自己写：配置里写了 {@code speed-formula: "50"} 或
 * {@code "%vault_eco_balance% / 1000"} 这种，不想为了一个公式引入 Nashorn / 脚本引擎
 * （Java 15 之后 Nashorn 已被移除，跨版本不可用）。
 *
 * <p>支持：{@code + - * /}、括号、小数、一元负号。不支持函数、幂运算。
 * PlaceholderAPI 的变量由调用方先替换成数字再传进来。
 */
public final class Expression {

    private final String source;
    private int pos;

    private Expression(String source) {
        this.source = source;
    }

    /**
     * 求值。
     *
     * @return 结果；表达式非法时返回 {@code fallback}
     */
    public static double eval(String expression, double fallback) {
        if (expression == null) {
            return fallback;
        }
        String text = expression.trim();
        if (text.isEmpty()) {
            return fallback;
        }
        try {
            Expression parser = new Expression(text);
            double value = parser.parseExpression();
            parser.skipSpaces();
            if (parser.pos != text.length()) {
                // 有剩余字符说明表达式有问题，不猜
                return fallback;
            }
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return fallback;
            }
            return value;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private double parseExpression() {
        double value = parseTerm();
        while (true) {
            skipSpaces();
            if (peek() == '+') {
                pos++;
                value += parseTerm();
            } else if (peek() == '-') {
                pos++;
                value -= parseTerm();
            } else {
                return value;
            }
        }
    }

    private double parseTerm() {
        double value = parseFactor();
        while (true) {
            skipSpaces();
            if (peek() == '*') {
                pos++;
                value *= parseFactor();
            } else if (peek() == '/') {
                pos++;
                double divisor = parseFactor();
                if (divisor == 0.0) {
                    throw new ArithmeticException("divide by zero");
                }
                value /= divisor;
            } else {
                return value;
            }
        }
    }

    private double parseFactor() {
        skipSpaces();
        char c = peek();
        if (c == '-') {
            pos++;
            return -parseFactor();
        }
        if (c == '+') {
            pos++;
            return parseFactor();
        }
        if (c == '(') {
            pos++;
            double value = parseExpression();
            skipSpaces();
            if (peek() != ')') {
                throw new IllegalArgumentException("missing )");
            }
            pos++;
            return value;
        }
        return parseNumber();
    }

    private double parseNumber() {
        skipSpaces();
        int start = pos;
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if ((c >= '0' && c <= '9') || c == '.') {
                pos++;
            } else {
                break;
            }
        }
        if (start == pos) {
            throw new IllegalArgumentException("number expected at " + pos);
        }
        return Double.parseDouble(source.substring(start, pos));
    }

    private char peek() {
        return pos < source.length() ? source.charAt(pos) : '\0';
    }

    private void skipSpaces() {
        while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) {
            pos++;
        }
    }
}

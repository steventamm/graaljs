/*
 * Copyright (c) 2010, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.oracle.js.parser.ir;

/**
 * Common superclass for all expression nodes. Expression nodes can have an associated symbol as
 * well as a type.
 *
 */
public abstract class Expression extends Node {
    /**
     * Determines whether this expression is enclosed in parenthesis.
     */
    private boolean parenthesized;
    private int parensStart;
    private int parensFinish;

    Expression(final long token, final int start, final int finish) {
        super(token, start, finish);
    }

    Expression(final long token, final int finish) {
        super(token, finish);
    }

    Expression(final Expression expr) {
        super(expr);
    }

    /**
     * Is this a self modifying assignment?
     *
     * @return true if self modifying, e.g. a++, or a*= 17
     */
    public boolean isSelfModifying() {
        return false;
    }

    /**
     * Returns true if the runtime value of this expression is always false when converted to
     * boolean as per ECMAScript ToBoolean conversion. Used in control flow calculations.
     *
     * @return true if this expression's runtime value converted to boolean is always false.
     */
    public boolean isAlwaysFalse() {
        return false;
    }

    /**
     * Returns true if the runtime value of this expression is always true when converted to boolean
     * as per ECMAScript ToBoolean conversion. Used in control flow calculations.
     *
     * @return true if this expression's runtime value converted to boolean is always true.
     */
    public boolean isAlwaysTrue() {
        return false;
    }

    /**
     * Determines whether this expression is enclosed in parenthesis.
     *
     * @return {@code true} if this expression is enclosed in parenthesis, returns {@code false}
     *         otherwise.
     */
    public final boolean isParenthesized() {
        return parenthesized;
    }

    /**
     * Marks this expression as enclosed in parenthesis.
     */
    public final void makeParenthesized(int parenStart, int parenFinish) {
        assert parenthesized ? (parenStart <= parensStart && parensFinish <= parenFinish) : (parenStart <= super.getStart() && super.getFinish() <= parenFinish);
        parenthesized = true;
        parensStart = parenStart;
        parensFinish = parenFinish;
    }

    @Override
    public int getStart() {
        return parenthesized ? parensStart : super.getStart();
    }

    public final int getStartWithoutParens() {
        return super.getStart();
    }

    @Override
    public int getFinish() {
        return parenthesized ? parensFinish : super.getFinish();
    }

    public final int getFinishWithoutParens() {
        return super.getFinish();
    }

}

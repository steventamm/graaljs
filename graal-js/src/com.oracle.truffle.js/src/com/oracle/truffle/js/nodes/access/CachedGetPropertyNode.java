/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.nodes.access;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.nodes.cast.ToArrayIndexNode;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.util.JSClassProfile;

@ReportPolymorphism
@ImportStatic(JSRuntime.class)
abstract class CachedGetPropertyNode extends JavaScriptBaseNode {
    static final int MAX_DEPTH = 2;

    protected final JSContext context;

    CachedGetPropertyNode(JSContext context) {
        this.context = context;
    }

    public abstract Object execute(DynamicObject target, Object propertyKey, Object receiver, Object defaultValue);

    static CachedGetPropertyNode create(JSContext context) {
        return CachedGetPropertyNodeGen.create(context);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"cachedKey != null", "!isArrayIndex(cachedKey)", "propertyKeyEquals(cachedKey, key)"}, limit = "MAX_DEPTH")
    Object doCachedKey(DynamicObject target, Object key, Object receiver, Object defaultValue,
                    @Cached("cachedPropertyKey(key)") Object cachedKey,
                    @Cached("create(cachedKey, context)") PropertyGetNode propertyNode) {
        return propertyNode.getValueOrDefault(target, receiver, defaultValue);
    }

    @Specialization(guards = {"isArrayIndex(index)", "!isJSProxy(target)"})
    Object doIntIndex(DynamicObject target, int index, Object receiver, Object defaultValue,
                    @Cached("create()") JSClassProfile jsclassProfile) {
        return JSObject.getOrDefault(target, index, receiver, defaultValue, jsclassProfile);
    }

    @Specialization(guards = {"!isJSProxy(target)", "toArrayIndexNode.isResultArrayIndex(maybeIndex)"}, replaces = {"doIntIndex"})
    Object doArrayIndex(DynamicObject target, @SuppressWarnings("unused") Object key, Object receiver, Object defaultValue,
                    @Cached("create()") RequireObjectCoercibleNode requireObjectCoercibleNode,
                    @Cached("createNoToPropertyKey()") @SuppressWarnings("unused") ToArrayIndexNode toArrayIndexNode,
                    @Bind("toArrayIndexNode.execute(key)") Object maybeIndex,
                    @Cached("create()") JSClassProfile jsclassProfile) {
        requireObjectCoercibleNode.executeVoid(target);
        long index = (long) maybeIndex;
        return JSObject.getOrDefault(target, index, receiver, defaultValue, jsclassProfile);
    }

    @Specialization(guards = {"isJSProxy(target)"})
    protected Object doProxy(DynamicObject target, Object index, Object receiver, @SuppressWarnings("unused") Object defaultValue,
                    @Cached("create(context)") JSProxyPropertyGetNode proxyGet) {
        return proxyGet.executeWithReceiver(target, receiver, index);
    }

    @Specialization(replaces = {"doCachedKey", "doArrayIndex", "doProxy"})
    Object doGeneric(DynamicObject target, Object key, Object receiver, Object defaultValue,
                    @Cached("create()") RequireObjectCoercibleNode requireObjectCoercibleNode,
                    @Cached("create()") ToArrayIndexNode toArrayIndexNode,
                    @Cached("createBinaryProfile()") ConditionProfile getType,
                    @Cached("create()") JSClassProfile jsclassProfile) {
        requireObjectCoercibleNode.executeVoid(target);
        Object arrayIndex = toArrayIndexNode.execute(key);
        if (getType.profile(arrayIndex instanceof Long)) {
            return JSObject.getOrDefault(target, (long) arrayIndex, receiver, defaultValue, jsclassProfile);
        } else {
            assert JSRuntime.isPropertyKey(arrayIndex);
            return JSObject.getOrDefault(target, arrayIndex, receiver, defaultValue, jsclassProfile);
        }
    }

    public static Object cachedPropertyKey(Object key) {
        CompilerAsserts.neverPartOfCompilation();
        if (JSRuntime.isPropertyKey(key)) {
            return key;
        } else if (JSRuntime.isLazyString(key)) {
            return key.toString();
        } else {
            return null;
        }
    }
}

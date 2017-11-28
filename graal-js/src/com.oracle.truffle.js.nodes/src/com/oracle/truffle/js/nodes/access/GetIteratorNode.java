/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.oracle.truffle.js.nodes.access;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.nodes.JavaScriptNode;
import com.oracle.truffle.js.nodes.function.JSFunctionCallNode;
import com.oracle.truffle.js.nodes.interop.JSUnboxOrGetNode;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSArguments;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.Symbol;
import com.oracle.truffle.js.runtime.builtins.JSUserObject;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.oracle.truffle.js.runtime.truffleinterop.JSInteropUtil;

/**
 * ES8 7.4.1 GetIterator(obj, hint).
 */
@ImportStatic(JSInteropUtil.class)
@NodeChild(value = "iteratedObject", type = JavaScriptNode.class)
public abstract class GetIteratorNode extends JavaScriptNode {
    @Child private PropertySetNode setState;
    @Child private GetMethodNode getIteratorMethodNode;
    @Child private GetMethodNode getAsyncIteratorMethodNode;

    private final boolean asyncHint;
    private final JSContext context;

    private final ConditionProfile asyncToSync = ConditionProfile.createBinaryProfile();

    protected GetIteratorNode(JSContext context, boolean asyncHint) {
        this.asyncHint = asyncHint;
        this.context = context;
        this.setState = PropertySetNode.create(new HiddenKey("SyncIterator"), false, context, false);
        this.getAsyncIteratorMethodNode = GetMethodNode.create(context, null, Symbol.SYMBOL_ASYNC_ITERATOR);
    }

    public static GetIteratorNode create(JSContext context) {
        return create(context, null);
    }

    public static GetIteratorNode createAsync(JSContext context, JavaScriptNode iteratedObject) {
        return GetIteratorNodeGen.create(context, true, iteratedObject);
    }

    public static GetIteratorNode create(JSContext context, JavaScriptNode iteratedObject) {
        return GetIteratorNodeGen.create(context, false, iteratedObject);
    }

    public static GetIteratorNode create(JSContext context, boolean asyncHint, JavaScriptNode iteratedObject) {
        return GetIteratorNodeGen.create(context, asyncHint, iteratedObject);
    }

    protected JSContext getContext() {
        return getAsyncIteratorMethodNode.getContext();
    }

    @Specialization(guards = {"!isForeignObject(iteratedObject)"})
    protected DynamicObject doGetAsyncIterator(Object iteratedObject,
                    @Cached("createCall()") JSFunctionCallNode methodCallNode,
                    @Cached("create()") IsObjectNode isObjectNode) {
        Object method;
        if (asyncHint) {
            method = getAsyncIteratorMethodNode.executeWithTarget(iteratedObject);
            if (asyncToSync.profile(method == Undefined.instance)) {
                Object syncMethod = getIteratorMethodNode().executeWithTarget(iteratedObject);
                Object syncIterator = getIterator(iteratedObject, syncMethod, methodCallNode, isObjectNode, this);
                return createAsyncFromSyncIterator(syncIterator);
            }
        } else {
            method = getIteratorMethodNode().executeWithTarget(iteratedObject);
        }
        return getIterator(iteratedObject, method, methodCallNode, isObjectNode, this);
    }

    public static DynamicObject getIterator(Object iteratedObject, Object method, JSFunctionCallNode methodCallNode, IsObjectNode isObjectNode, JavaScriptBaseNode origin) {
        Object iterator = methodCallNode.executeCall(JSArguments.createZeroArg(iteratedObject, method));
        if (isObjectNode.executeBoolean(iterator)) {
            return (DynamicObject) iterator;
        } else {
            throw Errors.createNotAnObjectError(origin);
        }
    }

    @Specialization(guards = "isForeignObject(iteratedObject)")
    protected DynamicObject doGetIteratorWithForeignObject(TruffleObject iteratedObject,
                    @Cached("createEnumerateValues()") EnumerateNode enumerateNode,
                    @Cached("createIsBoxed()") Node isBoxedNode,
                    @Cached("create()") JSUnboxOrGetNode unboxNode,
                    @Cached("create(getContext())") GetIteratorNode getIteratorNode) {
        if (ForeignAccess.sendIsBoxed(isBoxedNode, iteratedObject)) {
            Object unboxed = unboxNode.executeWithTarget(iteratedObject);
            return getIteratorNode.execute(unboxed);
        } else {
            return enumerateNode.execute(iteratedObject);
        }
    }

    protected EnumerateNode createEnumerateValues() {
        return EnumerateNode.create(getAsyncIteratorMethodNode.getContext(), null, true);
    }

    @Override
    public abstract DynamicObject execute(VirtualFrame frame);

    public abstract DynamicObject execute(Object iteratedObject);

    abstract JavaScriptNode getIteratedObject();

    @Override
    protected JavaScriptNode copyUninitialized() {
        return GetIteratorNodeGen.create(getAsyncIteratorMethodNode.getContext(), asyncHint, cloneUninitialized(getIteratedObject()));
    }

    private GetMethodNode getIteratorMethodNode() {
        if (getIteratorMethodNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getIteratorMethodNode = insert(GetMethodNode.create(context, null, Symbol.SYMBOL_ITERATOR));
        }
        return getIteratorMethodNode;
    }

    private DynamicObject createAsyncFromSyncIterator(Object syncIterator) {
        if (!JSObject.isJSObject(syncIterator)) {
            throw Errors.createTypeError("Object expected");
        }
        DynamicObject obj = JSObject.create(context.getRealm(), context.getRealm().getAsyncFromSyncIteratorPrototype(), JSUserObject.INSTANCE);
        setState.setValue(obj, syncIterator);
        return obj;
    }
}

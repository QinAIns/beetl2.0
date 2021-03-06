package org.beetl.core.statement;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

import org.beetl.core.Context;
import org.beetl.core.InferContext;
import org.beetl.core.exception.BeetlException;
import org.beetl.core.om.ObjectMethodMatchConf;
import org.beetl.core.om.ObjectUtil;
import org.beetl.core.statement.nat.ClassNode;
import org.beetl.core.statement.nat.InstanceNode;
import org.beetl.core.statement.nat.NativeArrayNode;
import org.beetl.core.statement.nat.NativeAtrributeNode;
import org.beetl.core.statement.nat.NativeMethodNode;
import org.beetl.core.statement.nat.NativeNode;

/**
 * @xxx.xx()[0].xxx
 * @author joelli
 *
 */
public class NativeCallExpression extends Expression
{

	InstanceNode insNode;
	ClassNode clsNode;
	NativeNode[] chain;

	public NativeCallExpression(InstanceNode insNode, NativeNode[] chain, GrammarToken token)
	{
		super(token);
		this.insNode = insNode;
		this.chain = chain;
	}

	public NativeCallExpression(ClassNode clsNode, NativeNode[] chain, GrammarToken token)
	{
		super(token);
		//可以做某些优化，如提前得到final 属性

		this.clsNode = clsNode;
		this.chain = chain;
	}

	public Object evaluate(Context ctx)
	{
		Class targetCls = null;
		Object targetObj = null;
		if (insNode != null)
		{
			targetObj = insNode.ref.evaluate(ctx);
			//todo check nullpointer
			targetCls = targetObj.getClass();
		}
		else
		{
			targetCls = ctx.gt.loadClassBySimpleName(this.clsNode.cls);

		}

		for (NativeNode node : chain)
		{
			if (node instanceof NativeAtrributeNode)
			{
				String attr = ((NativeAtrributeNode) node).attribute;
				try
				{
					Field f = targetCls.getField(attr);
					targetObj = f.get(targetObj);
					targetCls = f.getType();

				}
				catch (SecurityException e)
				{
					BeetlException be = new BeetlException(BeetlException.NATIVE_CALL_EXCEPTION, "不能调用属性", e);
					be.token = GrammarToken.createToken(attr, token.line);
					throw be;
				}
				catch (NoSuchFieldException e)
				{
					BeetlException be = new BeetlException(BeetlException.NATIVE_CALL_EXCEPTION, "无此属性", e);
					be.token = GrammarToken.createToken(attr, token.line);
					throw be;
				}
				catch (IllegalArgumentException e)
				{
					BeetlException be = new BeetlException(BeetlException.NATIVE_CALL_EXCEPTION, "访问属性出错", e);
					be.token = GrammarToken.createToken(attr, token.line);
					throw be;
				}
				catch (IllegalAccessException e)
				{
					BeetlException be = new BeetlException(BeetlException.NATIVE_CALL_EXCEPTION, "访问属性出错", e);
					be.token = GrammarToken.createToken(attr, token.line);
					throw be;
				}

			}
			else if (node instanceof NativeArrayNode)
			{
				if (!targetCls.isArray())
				{
					BeetlException be = new BeetlException(BeetlException.ARRAY_TYPE_ERROR);
					//最好是把上一个字符显示出来
					be.token = GrammarToken.createToken("[]", token.line);
					throw be;
				}
				Expression exp = ((NativeArrayNode) node).exp;
				Object value = exp.evaluate(ctx);
				if (value instanceof Number)
				{
					int index = ((Number) value).intValue();
					targetObj = ((Object[]) targetObj)[index];
					if (targetObj != null)
					{
						targetCls = targetObj.getClass();
					}
					else
					{
						//todo or component of array
						targetCls = null;
					}
				}
				else
				{
					BeetlException be = new BeetlException(BeetlException.ARRAY_INDEX_ERROR, "数组指针必须是Number类型");
					be.token = GrammarToken.createToken("[]", token.line);
					throw be;
				}
			}
			else if (node instanceof NativeMethodNode)
			{
				NativeMethodNode methodNode = (NativeMethodNode) node;
				String method = methodNode.method;
				Expression[] expList = methodNode.paras;
				//use 
				Object[] args = expList.length == 0 ? ObjectUtil.EMPTY_OBJECT_ARRAY : new Object[expList.length];

				for (int i = 0; i < expList.length; i++)
				{
					args[i] = expList[i].evaluate(ctx);

				}

				try
				{
					if (!ctx.gt.getNativeSecurity().permit(ctx.template.program.id, targetCls, targetObj, method))
					{
						BeetlException be = new BeetlException(BeetlException.NATIVE_SECUARITY_EXCEPTION);
						be.token = GrammarToken.createToken(method, token.line);
						throw be;
					}
					if (targetObj != null)
					{
						targetObj = ObjectUtil.invoke(targetObj, method, args);
					}
					else
					{
						targetObj = ObjectUtil.invokeStatic(targetCls, method, args);
					}

					if (targetObj != null)
					{
						targetCls = targetObj.getClass();
					}
					else
					{
						targetCls = null;
					}

				}
				catch (SecurityException e)
				{
					BeetlException be = new BeetlException(BeetlException.NATIVE_CALL_EXCEPTION, "不能调用方法", e);
					be.token = GrammarToken.createToken(method, token.line);
					throw be;
				}

				catch (IllegalArgumentException e)
				{
					BeetlException be = new BeetlException(BeetlException.NATIVE_CALL_EXCEPTION, "无法访问方法", e);
					be.token = GrammarToken.createToken(method, token.line);
					throw be;
				}
				catch (IllegalAccessException e)
				{
					BeetlException be = new BeetlException(BeetlException.NATIVE_CALL_EXCEPTION, "无法访问方法", e);
					be.token = GrammarToken.createToken(method, token.line);
					throw be;
				}
				catch (InvocationTargetException e)
				{
					BeetlException be = new BeetlException(BeetlException.NATIVE_CALL_EXCEPTION, "内部调用报错",
							e.getTargetException());
					be.token = GrammarToken.createToken(method, token.line);
					throw be;
				}
			}

		}
		return targetObj;
	}

	@Override
	public void infer(InferContext inferCtx)
	{

		Type type = null;
		if (insNode != null)
		{
			insNode.ref.infer(inferCtx);
			type = insNode.ref.type.copy();
		}
		else
		{
			Class cls = inferCtx.gt.loadClassBySimpleName(this.clsNode.cls);
			type = new Type(cls);

		}

		for (NativeNode node : chain)
		{
			if (type.cls == Object.class)
			{
				this.type = type;
				// do not infer since it's object
				return;
			}
			if (node instanceof NativeAtrributeNode)
			{
				String attr = ((NativeAtrributeNode) node).attribute;
				try
				{
					Field f = type.cls.getField(attr);
					Class c = f.getType();
					type.cls = c;
				}
				catch (SecurityException e)
				{
					BeetlException be = new BeetlException(BeetlException.NATIVE_CALL_EXCEPTION, "无法访问属性", e);
					be.token = GrammarToken.createToken(attr, token.line);
					throw be;
				}
				catch (NoSuchFieldException e)
				{
					BeetlException be = new BeetlException(BeetlException.NATIVE_CALL_EXCEPTION, "无此属性", e);
					be.token = GrammarToken.createToken(attr, token.line);
					throw be;
				}

			}
			else if (node instanceof NativeArrayNode)
			{
				if (!type.cls.isArray())
				{
					BeetlException be = new BeetlException(BeetlException.ARRAY_TYPE_ERROR);
					//最好是把上一个字符显示出来
					be.token = GrammarToken.createToken("[]", token.line);
					throw be;
				}
				type.cls = type.cls.getComponentType();
			}
			else if (node instanceof NativeMethodNode)
			{
				NativeMethodNode methodNode = (NativeMethodNode) node;
				String method = methodNode.method;
				Expression[] expList = methodNode.paras;

				Class[] argTypes = expList.length == 0 ? ObjectUtil.EMPTY_CLASS_ARRAY : new Class[expList.length];

				for (int i = 0; i < expList.length; i++)
				{
					expList[i].infer(inferCtx);
					argTypes[i] = expList[i].type.cls;

				}

				try
				{
					ObjectMethodMatchConf conf = ObjectUtil.findMethod(type.cls, method, argTypes);
					if (conf == null)
					{
						BeetlException be = new BeetlException(BeetlException.NATIVE_CALL_EXCEPTION);
						be.token = GrammarToken.createToken(method, token.line);
						throw be;
					}
					else
					{
						type.cls = conf.method.getReturnType();
					}

				}
				catch (SecurityException e)
				{
					BeetlException be = new BeetlException(BeetlException.NATIVE_CALL_EXCEPTION, "不能调用方法");
					be.token = GrammarToken.createToken(method, token.line);
					throw be;
				}

			}

		}
		this.type = type;

	}

}

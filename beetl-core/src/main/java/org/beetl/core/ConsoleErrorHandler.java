/*
 [The "BSD license"]
 Copyright (c) 2011-2014 Joel Li (李家智)
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:
 1. Redistributions of source code must retain the above copyright
     notice, this list of conditions and the following disclaimer.
 2. Redistributions in binary form must reproduce the above copyright
     notice, this list of conditions and the following disclaimer in the
     documentation and/or other materials provided with the distribution.
 3. The name of the author may not be used to endorse or promote products
     derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.beetl.core;

import java.io.IOException;
import java.io.Writer;

import org.beetl.core.exception.BeetlException;
import org.beetl.core.exception.ErrorInfo;

/** 向控制台输出错误，并不抛出异常
 * @author joelli
 *
 */
public class ConsoleErrorHandler implements ErrorHandler
{

	@Override
	public void processExcption(BeetlException ex, Writer writer)
	{

		ErrorInfo error = new ErrorInfo(ex);
		int line = error.getErrorTokenLine();
		StringBuilder sb = new StringBuilder(">>").append(error.getType()).append(":")
				.append(error.getErrorTokenText()).append(" 位于").append(line).append("行").append(" 资源:")
				.append(getResourceName(ex.resourceId));
		;
		println(writer, sb.toString());
		if (error.getErrorCode().equals(BeetlException.TEMPLATE_LOAD_ERROR))
		{
			printCause(error, writer);
			return;
		}

		ResourceLoader resLoader = ex.gt.getResourceLoader();
		//潜在问题，此时可能得到是一个新的模板，不过可能性很小，忽略！

		String content = null;
		;
		try
		{
			Resource res = resLoader.getResource(ex.resourceId);
			//显示前后三行的内容
			int[] range = this.getRange(line);
			content = res.getContent(range[0], range[1]);
			if (content != null)
			{
				String[] strs = content.split(ex.cr);
				int lineNumber = range[0];
				for (int i = 0; i < strs.length; i++)
				{
					print(writer, "" + lineNumber);
					print(writer, "|");
					println(writer, strs[i]);
					lineNumber++;
				}

			}
		}
		catch (IOException e)
		{

			//ingore

		}

		printCause(error, writer);

	}

	protected void printCause(ErrorInfo error, Writer writer)
	{
		Throwable t = error.getCause();
		if (t != null)
		{
			printThrowable(writer, t);
		}

	}

	protected String getResourceName(String resourceId)
	{
		return resourceId;
	}

	protected void println(Writer w, String msg)
	{
		System.out.println(msg);
	}

	protected void print(Writer w, String msg)
	{
		System.out.print(msg);
	}

	protected void printThrowable(Writer w, Throwable t)
	{
		t.printStackTrace();
	}

	protected int[] getRange(int line)
	{
		int startLine = 0;
		int endLine = 0;
		if (line > 3)
		{
			startLine = line - 3;
		}
		else
		{
			startLine = 1;
		}

		endLine = startLine + 6;
		return new int[]
		{ startLine, endLine };
	}

}

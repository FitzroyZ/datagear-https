/*
 * Copyright 2018 datagear.tech
 *
 * Licensed under the LGPLv3 license:
 * http://www.gnu.org/licenses/lgpl-3.0.html
 */

package org.datagear.util.html;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认{@linkplain FilterHandler}。
 * 
 * @author datagear@163.com
 *
 */
public class DefaultFilterHandler implements FilterHandler
{
	private Writer out;

	private boolean aborted = false;

	private Map<String, String> prevTagAttrs = new HashMap<String, String>();

	private List<String> prevTagAttrTokens = new ArrayList<String>();

	/**
	 * 创建HTML过滤上下文。
	 * 
	 * @param out
	 *            输出流
	 */
	public DefaultFilterHandler(Writer out)
	{
		super();
		this.out = out;
	}

	public Writer getOut()
	{
		return out;
	}

	public void setOut(Writer out)
	{
		this.out = out;
	}

	@Override
	public boolean isAborted()
	{
		return aborted;
	}

	public void setAborted(boolean aborted)
	{
		this.aborted = aborted;
	}

	@Override
	public void write(char c) throws IOException
	{
		this.out.write(c);
	}

	@Override
	public void write(int c) throws IOException
	{
		this.out.write(c);
	}

	@Override
	public void write(String str) throws IOException
	{
		this.out.write(str);
	}

	@Override
	public Map<String, String> availableTagAttrs()
	{
		if (!this.prevTagAttrs.isEmpty())
			this.prevTagAttrs = new HashMap<String, String>();

		return this.prevTagAttrs;
	}

	@Override
	public List<String> availableTagAttrTokens()
	{
		if (!this.prevTagAttrTokens.isEmpty())
			this.prevTagAttrTokens = new ArrayList<String>();

		return this.prevTagAttrTokens;
	}

	@Override
	public void beforeWriteTagStart(Reader in, String tagName) throws IOException
	{
	}

	@Override
	public boolean isResolveTagAttrs(Reader in, String tagName)
	{
		return false;
	}

	@Override
	public void beforeWriteTagEnd(Reader in, String tagName, String tagEnd, Map<String, String> attrs)
			throws IOException
	{
	}

	@Override
	public void afterWriteTagEnd(Reader in, String tagName, String tagEnd) throws IOException
	{
	}

	/**
	 * 是否忽略大小写相等。
	 * 
	 * @param str0
	 *            允许为{@code null}
	 * @param str1
	 *            允许为{@code null}
	 * @return
	 */
	public boolean equalsIgnoreCase(String str0, String str1)
	{
		if (str0 == null)
			return (str1 == null);
		else
			return str0.equalsIgnoreCase(str1);
	}

	/**
	 * 是否是自关闭标签结束符：{@code />}
	 * 
	 * @param tagEnd
	 * @return
	 */
	public boolean isSelfCloseTagEnd(String tagEnd)
	{
		return HtmlFilter.TAG_END_STR_SELF_CLOSE.equals(tagEnd);
	}

	/**
	 * 给定标签名是否是关闭标签名，即：以{@code '/'}字符开头。
	 * 
	 * @param tagName
	 * @return
	 */
	public boolean isCloseTagName(String tagName)
	{
		return (tagName != null && tagName.length() > 0 && tagName.charAt(0) == '/');
	}

	/**
	 * 删除字符串首位空格。
	 * 
	 * @param str
	 *            允许为{@code null}
	 * @return
	 */
	public String trim(String str)
	{
		return (str == null ? null : str.trim());
	}
}

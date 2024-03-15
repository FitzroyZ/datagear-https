/*
 * Copyright 2018-present datagear.tech
 *
 * This file is part of DataGear.
 *
 * DataGear is free software: you can redistribute it and/or modify it under the terms of
 * the GNU Lesser General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * DataGear is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with DataGear.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package org.datagear.dataexchange;

/**
 * 数据交换监听器。
 * 
 * @author datagear@163.com
 *
 */
public interface DataExchangeListener
{
	/**
	 * 开始。
	 */
	void onStart();

	/**
	 * 异常。
	 * <p>
	 * 数据交换异常交由此处理后，不会再向上抛出。
	 * </p>
	 * 
	 * @param e
	 */
	void onException(DataExchangeException e);

	/**
	 * 成功。
	 */
	void onSuccess();

	/**
	 * 完成。
	 * <p>
	 * 此方法将在{@linkplain #onException(DataExchangeException)}或者{@linkplain #onSuccess()}之后被调用。
	 * </p>
	 */
	void onFinish();
}
